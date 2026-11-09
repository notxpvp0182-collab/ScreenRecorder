package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.audio.AudioCaptureManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates Video Capture, Audio Capture, Frame Pacing, Background Encoding,
 * and Crash-Safe Finalization.
 */
public class RecordingManager {
    private static final RecordingManager INSTANCE = new RecordingManager();

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private final VideoCaptureManager videoCaptureManager = new VideoCaptureManager();
    private final AudioCaptureManager audioCaptureManager = new AudioCaptureManager();
    private final PerformanceManager performanceManager = new PerformanceManager();
    private FFmpegEncoder ffmpegEncoder;

    private ArrayBlockingQueue<ByteBuffer> encodeQueue;
    private Thread videoEncoderThread;
    private ByteBuffer lastFrameBackup;

    private File currentOutputFile;
    private int sessionSourceWidth;
    private int sessionSourceHeight;
    private long recordingStartTimeMs;
    private long lastCapturedFrameNs;

    private String lastStatusMessage = "Ready to record.";
    private boolean lastStatusIsError = false;

    public static RecordingManager getInstance() {
        return INSTANCE;
    }

    public synchronized boolean startRecording() {
        if (recording.get() || stopping.get()) {
            return false;
        }

        RecordingConfig cfg = RecordingConfig.get();

        // 1. Validate FFmpeg availability
        if (!FFmpegEncoder.isFFmpegAvailable()) {
            setStatus("FFmpeg encoder is unavailable. Check FFmpeg installation.", true);
            ScreenRecorderClient.LOGGER.error("Recording could not start: FFmpeg not found.");
            return false;
        }

        // 2. Validate Disk Space
        if (!RecordingFileManager.hasEnoughDiskSpace()) {
            setStatus("Not enough disk space.", true);
            ScreenRecorderClient.LOGGER.error("Recording could not start: Insufficient disk space.");
            return false;
        }

        // 3. Validate Framebuffer
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.textureWidth <= 0 || fb.textureHeight <= 0) {
            setStatus("Recording could not start: Invalid window resolution.", true);
            return false;
        }

        this.sessionSourceWidth = fb.textureWidth;
        this.sessionSourceHeight = fb.textureHeight;
        int frameBytes = sessionSourceWidth * sessionSourceHeight * 4;
        int queueCapacity = Math.max(4, Math.min(30, cfg.maxFrameQueueSize));

        try {
            performanceManager.initializePool(frameBytes, queueCapacity + 2);
            encodeQueue = new ArrayBlockingQueue<>(queueCapacity);
            lastFrameBackup = ByteBuffer.allocateDirect(frameBytes);

            currentOutputFile = RecordingFileManager.createUniqueRecordingFile();
            ffmpegEncoder = new FFmpegEncoder();
            ffmpegEncoder.start(currentOutputFile, sessionSourceWidth, sessionSourceHeight);

            audioCaptureManager.start(ffmpegEncoder.getAudioOutputStream());

            recordingStartTimeMs = System.currentTimeMillis();
            lastCapturedFrameNs = System.nanoTime();
            recording.set(true);

            videoEncoderThread = new Thread(this::runVideoEncodeLoop, "ScreenRecorder-VideoEncoder");
            videoEncoderThread.setDaemon(true);
            PerformanceManager.applyThreadPriority(videoEncoderThread);
            videoEncoderThread.start();

            setStatus("Recording in progress: " + currentOutputFile.getName(), false);
            ScreenRecorderClient.LOGGER.info("Recording started: {}", currentOutputFile.getName());
            return true;
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.error("Recording failed to initialize.", e);
            setStatus("Recording could not start: " + e.getMessage(), true);
            cleanupFailedStart();
            return false;
        }
    }

    public synchronized void stopRecording() {
        if (!recording.get() || stopping.get()) {
            return;
        }
        stopping.set(true);
        recording.set(false);
        setStatus("Finalizing MP4 file...", false);

        Thread stopThread = new Thread(this::performSafeStop, "ScreenRecorder-Finalizer");
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
     * Invoked at the end of every rendered Minecraft frame on the Render Thread.
     */
    public void onFrameRendered() {
        if (!recording.get() || stopping.get()) return;

        int targetFps = RecordingConfig.get().getTargetFps();
        long frameIntervalNs = 1_000_000_000L / targetFps;
        long nowNs = System.nanoTime();

        if (nowNs - lastCapturedFrameNs < frameIntervalNs) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null) return;

        // If window was resized during recording, safely stop and finalize current MP4
        if (fb.textureWidth != sessionSourceWidth || fb.textureHeight != sessionSourceHeight) {
            ScreenRecorderClient.LOGGER.info("Window resolution changed during recording. Finalizing current segment safely.");
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
            if (!encodeQueue.offer(buffer)) {
                performanceManager.releaseBuffer(buffer);
                performanceManager.recordFrameDropped();
            }
        } else {
            performanceManager.releaseBuffer(buffer);
        }
    }

    private void runVideoEncodeLoop() {
        int targetFps = RecordingConfig.get().getTargetFps();
        long frameStepNs = 1_000_000_000L / targetFps;
        long nextPtsNs = System.nanoTime();
        boolean hasBackup = false;

        try {
            while (recording.get() || !encodeQueue.isEmpty()) {
                ByteBuffer buf = encodeQueue.poll(10, TimeUnit.MILLISECONDS);
                if (buf != null) {
                    buf.rewind();
                    ffmpegEncoder.writeVideoFrame(buf);
                    performanceManager.recordFrameEncoded();

                    // Store copy for constant frame rate pacing if game briefly lags
                    buf.rewind();
                    lastFrameBackup.clear();
                    lastFrameBackup.put(buf);
                    lastFrameBackup.flip();
                    hasBackup = true;

                    performanceManager.releaseBuffer(buf);
                    nextPtsNs = System.nanoTime() + frameStepNs;
                } else if (recording.get() && hasBackup && System.nanoTime() >= nextPtsNs) {
                    lastFrameBackup.rewind();
                    ffmpegEncoder.writeVideoFrame(lastFrameBackup);
                    nextPtsNs += frameStepNs;
                }
            }
        } catch (Exception e) {
            if (recording.get()) {
                ScreenRecorderClient.LOGGER.error("Encoder failure during recording.", e);
                setStatus("Encoder failure: Recording stopped.", true);
                recording.set(false);
                performSafeStop();
            }
        }
    }

    private void performSafeStop() {
        try {
            audioCaptureManager.stop();

            if (videoEncoderThread != null && Thread.currentThread() != videoEncoderThread) {
                try {
                    videoEncoderThread.join(4000);
                } catch (InterruptedException ignored) {}
            }

            if (ffmpegEncoder != null) {
                ffmpegEncoder.stopAndFinalize();
                ffmpegEncoder = null;
            }

            performanceManager.cleanup();
            lastFrameBackup = null;

            if (currentOutputFile != null && currentOutputFile.exists()) {
                String savedMsg = "Recording saved: " + currentOutputFile.getName();
                setStatus(savedMsg, false);
                ScreenRecorderClient.LOGGER.info(savedMsg);

                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    client.execute(() -> client.player.sendMessage(Text.literal("§c[Screen Recorder] §f" + savedMsg), false));
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
            ScreenRecorderClient.LOGGER.info("Finalizing active recording before shutdown...");
            recording.set(false);
            performSafeStop();
        }
    }

    public boolean isRecording() {
        return recording.get();
    }

    public boolean isStopping() {
        return stopping.get();
    }

    public String getFormattedDuration() {
        if (!recording.get()) return "00:00:00";
        long elapsedSec = Math.max(0, (System.currentTimeMillis() - recordingStartTimeMs) / 1000L);
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
