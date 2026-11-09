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
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OBS Studio Real-Time 1x Synchronized Recording Manager.
 * Matches encoded PTS frames to actual elapsed wall-clock time so 60s of gameplay = 60s of 1x speed MP4 video,
 * even if the game's live FPS fluctuates between 10 FPS and 120 FPS.
 */
public class RecordingManager {
    private static final RecordingManager INSTANCE = new RecordingManager();

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private final VideoCaptureManager videoCaptureManager = new VideoCaptureManager();
    private final AudioCaptureManager audioCaptureManager = new AudioCaptureManager();
    private final PerformanceManager performanceManager = new PerformanceManager();
    private FFmpegEncoder ffmpegEncoder;

    private ArrayBlockingQueue<CapturedFrame> encodeQueue;
    private Thread videoEncoderThread;

    private File currentOutputFile;
    private int sessionSourceWidth;
    private int sessionSourceHeight;
    private int sessionTargetFps;
    private long recordingStartTimeMs;
    private long pausedAccumulatedMs = 0;
    private long pauseStartMs = 0;
    private long lastCapturedFrameNs;

    private String lastStatusMessage = "OBS Ready";
    private boolean lastStatusIsError = false;

    private record CapturedFrame(ByteBuffer buffer, long elapsedActiveMs) {}

    public static RecordingManager getInstance() {
        return INSTANCE;
    }

    public synchronized boolean startRecording() {
        if (recording.get() || stopping.get()) {
            return false;
        }

        RecordingConfig cfg = RecordingConfig.get();

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

        this.sessionSourceWidth = fb.textureWidth;
        this.sessionSourceHeight = fb.textureHeight;
        // Lock target FPS to realistic smooth 30 FPS on mobile if current FPS is low, or configured FPS
        int liveFps = Math.max(15, client.getCurrentFps());
        this.sessionTargetFps = Math.min(cfg.getTargetFps(), Math.max(24, Math.min(60, liveFps)));

        int frameBytes = sessionSourceWidth * sessionSourceHeight * 4;
        int queueCapacity = Math.max(8, Math.min(24, cfg.maxFrameQueueSize));

        try {
            performanceManager.initializePool(frameBytes, queueCapacity + 3);
            encodeQueue = new ArrayBlockingQueue<>(queueCapacity);

            currentOutputFile = RecordingFileManager.createUniqueRecordingFile();
            ffmpegEncoder = new FFmpegEncoder();
            ffmpegEncoder.start(currentOutputFile, sessionSourceWidth, sessionSourceHeight);

            audioCaptureManager.start(ffmpegEncoder.getAudioOutputStream());

            recordingStartTimeMs = System.currentTimeMillis();
            pausedAccumulatedMs = 0;
            paused.set(false);
            lastCapturedFrameNs = 0L;
            recording.set(true);

            videoEncoderThread = new Thread(this::runWallClockSyncedEncodeLoop, "OBS-VideoEncoder");
            videoEncoderThread.setDaemon(true);
            PerformanceManager.applyThreadPriority(videoEncoderThread);
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

        int targetFps = RecordingConfig.get().getTargetFps();
        long frameIntervalNs = 1_000_000_000L / targetFps;
        long nowNs = System.nanoTime();

        if (nowNs - lastCapturedFrameNs < frameIntervalNs) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null) return;

        if (fb.textureWidth != sessionSourceWidth || fb.textureHeight != sessionSourceHeight) {
            stopRecording();
            return;
        }

        lastCapturedFrameNs = nowNs;

        ByteBuffer buffer = performanceManager.acquireBuffer();
        if (buffer == null) {
            performanceManager.recordFrameDropped();
            return;
        }

        boolean captured = videoCaptureManager.captureFramebuffer(buffer, sessionSourceWidth, sessionSourceHeight);
        if (captured) {
            long activeMs = Math.max(0L, System.currentTimeMillis() - recordingStartTimeMs - pausedAccumulatedMs);
            if (!encodeQueue.offer(new CapturedFrame(buffer, activeMs))) {
                performanceManager.releaseBuffer(buffer);
                performanceManager.recordFrameDropped();
            }
        } else {
            performanceManager.releaseBuffer(buffer);
        }
    }

    /**
     * Ensures 1x Real-Time Video Speed!
     * Calculates exact target frame index from wall-clock elapsed milliseconds so if the game
     * runs at 13 FPS while recording at 60 FPS, frames are properly paced to match 1x real time.
     */
    private void runWallClockSyncedEncodeLoop() {
        int fps = RecordingConfig.get().getTargetFps();
        long writtenFrames = 0;

        try {
            while (recording.get() || !encodeQueue.isEmpty()) {
                CapturedFrame item = encodeQueue.poll(20, TimeUnit.MILLISECONDS);
                if (item != null) {
                    ByteBuffer buf = item.buffer();
                    long targetFrameCount = Math.max(writtenFrames + 1, (item.elapsedActiveMs() * fps) / 1000L);
                    long repeatCount = Math.min(8, targetFrameCount - writtenFrames);

                    for (long r = 0; r < repeatCount; r++) {
                        buf.rewind();
                        ffmpegEncoder.writeVideoFrame(buf);
                        writtenFrames++;
                        performanceManager.recordFrameEncoded();
                    }

                    performanceManager.releaseBuffer(buf);
                }
            }
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.error("Encoder error", e);
            recording.set(false);
        }
    }

    private void performSafeStop() {
        try {
            audioCaptureManager.stop();

            if (videoEncoderThread != null && Thread.currentThread() != videoEncoderThread) {
                try {
                    videoEncoderThread.join(8000);
                } catch (InterruptedException ignored) {}
            }

            if (ffmpegEncoder != null) {
                ffmpegEncoder.stopAndFinalize();
                ffmpegEncoder = null;
            }

            performanceManager.cleanup();

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
        performanceManager.cleanup();
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
