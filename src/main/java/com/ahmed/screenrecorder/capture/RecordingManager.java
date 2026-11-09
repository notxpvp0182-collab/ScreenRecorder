package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.audio.AudioCaptureManager;
import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real-Time Clock-Locked Recording Manager:
 * Guarantees that 50 seconds of recording produces an exact 50-second MP4 file (never 00:00),
 * and continues recording seamlessly across Main Menu, World Leave, Server Join, F5, and GUIs.
 */
public class RecordingManager {
    private static final RecordingManager INSTANCE = new RecordingManager();

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean frameReadyForUpload = new AtomicBoolean(false);

    private final VideoCaptureManager videoCaptureManager = new VideoCaptureManager();
    private final AudioCaptureManager audioCaptureManager = new AudioCaptureManager();
    private final PerformanceManager performanceManager = new PerformanceManager();
    private FFmpegEncoder ffmpegEncoder;

    private ByteBuffer sharedCaptureBuffer;
    private volatile int latestWidth = 854;
    private volatile int latestHeight = 480;
    private Thread videoEncoderThread;

    private File currentOutputFile;
    private long recordingStartTimeMs;
    private long pausedAccumulatedMs = 0;
    private long pauseStartMs = 0;
    private long lastGlCaptureTimeMs = 0;

    private String lastStatusMessage = "OBS Ready";
    private boolean lastStatusIsError = false;

    public static RecordingManager getInstance() {
        return INSTANCE;
    }

    public synchronized boolean startRecording() {
        if (recording.get() || stopping.get()) {
            return false;
        }

        if (!RecordingFileManager.hasEnoughDiskSpace()) {
            setStatus("Low disk space (< 250 MB)", true);
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.textureWidth <= 0 || fb.textureHeight <= 0) {
            setStatus("Display framebuffer not ready", true);
            return false;
        }

        int w = fb.textureWidth;
        int h = fb.textureHeight;
        int maxBytes = Math.max(w * h * 4, 2560 * 1440 * 4);

        try {
            if (sharedCaptureBuffer == null || sharedCaptureBuffer.capacity() < maxBytes) {
                sharedCaptureBuffer = ByteBuffer.allocateDirect(maxBytes).order(ByteOrder.nativeOrder());
            }
            performanceManager.initializePool(1024, 2);

            currentOutputFile = RecordingFileManager.createUniqueRecordingFile();
            ffmpegEncoder = new FFmpegEncoder();
            ffmpegEncoder.start(currentOutputFile, w, h);

            audioCaptureManager.start(ffmpegEncoder.getAudioOutputStream());

            recordingStartTimeMs = System.currentTimeMillis();
            pausedAccumulatedMs = 0;
            paused.set(false);
            frameReadyForUpload.set(false);
            lastGlCaptureTimeMs = 0L;
            recording.set(true);

            videoEncoderThread = new Thread(this::runMetronomeEncodeLoop, "OBS-MetronomeEncoder");
            videoEncoderThread.setDaemon(true);
            videoEncoderThread.setPriority(Thread.NORM_PRIORITY - 1);
            videoEncoderThread.start();

            setStatus("Recording: " + currentOutputFile.getName(), false);
            return true;
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.error("Recording failed to start", e);
            setStatus("Error: " + e.getMessage(), true);
            cleanupFailedStart();
            return false;
        }
    }

    public synchronized void togglePause() {
        if (!recording.get() || stopping.get()) return;
        if (paused.compareAndSet(false, true)) {
            pauseStartMs = System.currentTimeMillis();
            setStatus("Paused: " + currentOutputFile.getName(), false);
        } else if (paused.compareAndSet(true, false)) {
            pausedAccumulatedMs += (System.currentTimeMillis() - pauseStartMs);
            setStatus("Recording: " + currentOutputFile.getName(), false);
        }
    }

    public void takeScreenshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getFramebuffer() != null) {
            ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getFramebuffer(), msg -> {
                setStatus("Screenshot saved to screenshots/", false);
            });
        }
    }

    public synchronized void stopRecording() {
        if (!recording.get() || stopping.get()) {
            return;
        }
        stopping.set(true);
        recording.set(false);
        paused.set(false);
        setStatus("Finalizing MP4...", false);

        Thread stopThread = new Thread(this::performSafeStop, "OBS-Finalizer");
        stopThread.setDaemon(false);
        stopThread.start();
    }

    public void toggleRecording() {
        if (recording.get()) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    /**
     * Runs on Render Thread: Captures framebuffer at most once every 50ms (20 FPS)
     * and only when the background encoder is ready, so gameplay stays 100% lag-free!
     */
    public void onFrameRendered() {
        if (!recording.get() || paused.get() || stopping.get()) return;
        if (frameReadyForUpload.get()) return; // Encoder is still converting previous frame

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastGlCaptureTimeMs < (1000 / FFmpegEncoder.STREAM_FPS)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.textureWidth <= 0 || fb.textureHeight <= 0) return;

        int curW = fb.textureWidth;
        int curH = fb.textureHeight;
        if (sharedCaptureBuffer == null || curW * curH * 4 > sharedCaptureBuffer.capacity()) return;

        lastGlCaptureTimeMs = nowMs;
        if (videoCaptureManager.captureFramebuffer(sharedCaptureBuffer, curW, curH)) {
            latestWidth = curW;
            latestHeight = curH;
            frameReadyForUpload.set(true);
        }
    }

    /**
     * Metronome Loop: Guarantees exact 1x real-time video duration!
     * For every 50ms of active recording time (20 FPS), writes exactly 1 frame to the MP4 stream.
     * 50 seconds of recording = 1,000 frames = 00:00:50 duration in every media player!
     */
    private void runMetronomeEncodeLoop() {
        final int fps = FFmpegEncoder.STREAM_FPS;
        final long stepMs = 1000L / fps;
        long encodedFramesCount = 0;
        boolean hasAtLeastOneFrame = false;

        try {
            while (recording.get()) {
                if (paused.get()) {
                    Thread.sleep(25);
                    continue;
                }

                if (frameReadyForUpload.compareAndSet(true, false)) {
                    ffmpegEncoder.updateFramePixels(sharedCaptureBuffer, latestWidth, latestHeight);
                    hasAtLeastOneFrame = true;
                }

                if (!hasAtLeastOneFrame) {
                    Thread.sleep(10);
                    continue;
                }

                long activeElapsedMs = Math.max(0L, System.currentTimeMillis() - recordingStartTimeMs - pausedAccumulatedMs);
                long expectedFrames = (activeElapsedMs * fps) / 1000L;

                if (encodedFramesCount <= expectedFrames) {
                    long catchUp = Math.min(4, (expectedFrames - encodedFramesCount) + 1);
                    for (long i = 0; i < catchUp; i++) {
                        ffmpegEncoder.writeEncodedTickFrame();
                        encodedFramesCount++;
                        performanceManager.recordFrameEncoded();
                    }
                } else {
                    Thread.sleep(Math.max(4, stepMs / 3));
                }
            }
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.error("Metronome encoder error", e);
        }
    }

    private void performSafeStop() {
        try {
            audioCaptureManager.stop();

            if (videoEncoderThread != null && Thread.currentThread() != videoEncoderThread) {
                try {
                    videoEncoderThread.join(6000);
                } catch (InterruptedException ignored) {}
            }

            if (ffmpegEncoder != null) {
                ffmpegEncoder.stopAndFinalize();
                ffmpegEncoder = null;
            }

            if (currentOutputFile != null && currentOutputFile.exists()) {
                double sizeMb = currentOutputFile.length() / (1024.0 * 1024.0);
                String savedMsg = String.format("Saved: %s (%.2f MB)", currentOutputFile.getName(), sizeMb);
                setStatus(savedMsg, false);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    client.execute(() -> client.player.sendMessage(Text.literal("§9[OBS] §f" + savedMsg), false));
                }
            } else {
                setStatus("Recording stopped.", false);
            }
        } finally {
            stopping.set(false);
        }
    }

    private void cleanupFailedStart() {
        recording.set(false);
        paused.set(false);
        stopping.set(false);
        audioCaptureManager.stop();
        if (ffmpegEncoder != null) {
            ffmpegEncoder.stopAndFinalize();
            ffmpegEncoder = null;
        }
    }

    public void shutdownSafely() {
        if (recording.get()) {
            recording.set(false);
            performSafeStop();
        }
    }

    public boolean isRecording() {
        return recording.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    public boolean isStopping() {
        return stopping.get();
    }

    public String getFormattedDuration() {
        if (!recording.get()) return "00:00:00";
        long now = paused.get() ? pauseStartMs : System.currentTimeMillis();
        long elapsedSec = Math.max(0, (now - recordingStartTimeMs - pausedAccumulatedMs) / 1000L);
        return String.format("%02d:%02d:%02d", elapsedSec / 3600, (elapsedSec % 3600) / 60, elapsedSec % 60);
    }

    public String getLastStatusMessage() {
        return lastStatusMessage;
    }

    public boolean isLastStatusError() {
        return lastStatusIsError;
    }

    private void setStatus(String message, boolean isError) {
        this.lastStatusMessage = message;
        this.lastStatusIsError = isError;
    }

    public PerformanceManager getPerformanceManager() {
        return performanceManager;
    }
}
