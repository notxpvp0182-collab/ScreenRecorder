package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.audio.AudioCaptureManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ultra-Smooth Zero-Lag Recording Manager:
 * 1. Performs ONE single-pass glReadPixels on the Render Thread only when the background encoder is idle.
 * 2. Converts RGBA -> RGB and encodes H.264 100% on the background thread (0% lag on gameplay!).
 * 3. Calibrates the MP4 header duration upon stop so 50s of recording is ALWAYS 50.00s in every player!
 */
public class RecordingManager {
    private static final RecordingManager INSTANCE = new RecordingManager();

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean rawFrameWaiting = new AtomicBoolean(false);

    private final SinglePassFrameGrabber frameGrabber = new SinglePassFrameGrabber();
    private final AudioCaptureManager audioCaptureManager = new AudioCaptureManager();
    private final PerformanceManager performanceManager = new PerformanceManager();
    private FFmpegEncoder ffmpegEncoder;

    private byte[] workerRgbBuffer;
    private Thread videoEncoderThread;

    private File currentOutputFile;
    private long recordingStartTimeMs;
    private long finalActiveDurationMs = 0;
    private long pausedAccumulatedMs = 0;
    private long pauseStartMs = 0;
    private long lastGlGrabTimeMs = 0;

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

        try {
            currentOutputFile = RecordingFileManager.createUniqueRecordingFile();
            ffmpegEncoder = new FFmpegEncoder();
            ffmpegEncoder.start(currentOutputFile, fb.textureWidth, fb.textureHeight);

            int rgbSize = ffmpegEncoder.getTargetW() * ffmpegEncoder.getTargetH() * 3;
            workerRgbBuffer = new byte[rgbSize];
            performanceManager.initializePool(1024, 2);

            audioCaptureManager.start(ffmpegEncoder.getAudioOutputStream());

            recordingStartTimeMs = System.currentTimeMillis();
            pausedAccumulatedMs = 0;
            finalActiveDurationMs = 0;
            paused.set(false);
            rawFrameWaiting.set(false);
            lastGlGrabTimeMs = 0L;
            recording.set(true);

            videoEncoderThread = new Thread(this::runBackgroundEncodeWorker, "OBS-ZeroLagEncoder");
            videoEncoderThread.setDaemon(true);
            videoEncoderThread.setPriority(Thread.MIN_PRIORITY + 2);
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
                setStatus("Screenshot saved", false);
            });
        }
    }

    public synchronized void stopRecording() {
        if (!recording.get() || stopping.get()) {
            return;
        }
        long stopMoment = paused.get() ? pauseStartMs : System.currentTimeMillis();
        this.finalActiveDurationMs = Math.max(500L, stopMoment - recordingStartTimeMs - pausedAccumulatedMs);

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

    public void onFrameRendered() {
        if (!recording.get() || paused.get() || stopping.get()) return;
        if (rawFrameWaiting.get()) return; // Background thread is still encoding previous frame; do NOT stall game!

        int targetFps = Math.max(15, Math.min(60, RecordingConfig.get().getTargetFps()));
        long minIntervalMs = Math.max(16L, 1000L / targetFps);
        long nowMs = System.currentTimeMillis();

        if (nowMs - lastGlGrabTimeMs < minIntervalMs) {
            return;
        }

        lastGlGrabTimeMs = nowMs;
        if (frameGrabber.grabFullFramebufferFast()) {
            rawFrameWaiting.set(true);
        }
    }

    private void runBackgroundEncodeWorker() {
        try {
            while (recording.get() || rawFrameWaiting.get()) {
                if (paused.get()) {
                    Thread.sleep(30);
                    continue;
                }

                if (rawFrameWaiting.get()) {
                    frameGrabber.convertOnBackgroundThread(workerRgbBuffer, ffmpegEncoder.getTargetW(), ffmpegEncoder.getTargetH());
                    rawFrameWaiting.set(false);

                    ffmpegEncoder.writeSinglePassFrame(workerRgbBuffer);
                    performanceManager.recordFrameEncoded();
                } else {
                    Thread.sleep(8);
                }
            }
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.error("Background encoder error", e);
        }
    }

    private void performSafeStop() {
        try {
            audioCaptureManager.stop();

            if (videoEncoderThread != null && Thread.currentThread() != videoEncoderThread) {
                try {
                    videoEncoderThread.join(10000);
                } catch (InterruptedException ignored) {}
            }

            if (ffmpegEncoder != null) {
                ffmpegEncoder.stopAndFinalize(this.finalActiveDurationMs);
                ffmpegEncoder = null;
            }

            if (currentOutputFile != null && currentOutputFile.exists()) {
                double sizeMb = currentOutputFile.length() / (1024.0 * 1024.0);
                long sec = Math.round(this.finalActiveDurationMs / 1000.0);
                String savedMsg = String.format("Saved: %s (%ds, %.2f MB)", currentOutputFile.getName(), sec, sizeMb);
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
            ffmpegEncoder.stopAndFinalize(1000L);
            ffmpegEncoder = null;
        }
    }

    public void shutdownSafely() {
        if (recording.get()) {
            long stopMoment = paused.get() ? pauseStartMs : System.currentTimeMillis();
            this.finalActiveDurationMs = Math.max(500L, stopMoment - recordingStartTimeMs - pausedAccumulatedMs);
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
