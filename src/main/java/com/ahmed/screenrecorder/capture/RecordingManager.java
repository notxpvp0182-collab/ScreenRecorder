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
 * Non-Stop Background Display Capture Recording Manager:
 * Continues recording seamlessly across Main Menu, World Join/Leave, Server Transfers, F5 Views, and GUIs
 * with zero FPS drop and millisecond-exact 1x video duration.
 */
public class RecordingManager {
    private static final RecordingManager INSTANCE = new RecordingManager();

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean encoderBusy = new AtomicBoolean(false);

    private final VideoCaptureManager videoCaptureManager = new VideoCaptureManager();
    private final AudioCaptureManager audioCaptureManager = new AudioCaptureManager();
    private final PerformanceManager performanceManager = new PerformanceManager();
    private FFmpegEncoder ffmpegEncoder;

    private ArrayBlockingQueue<CapturedFrame> encodeQueue;
    private Thread videoEncoderThread;

    private File currentOutputFile;
    private int maxAllocatedBytes;
    private long recordingStartTimeMs;
    private long pausedAccumulatedMs = 0;
    private long pauseStartMs = 0;
    private long lastCapturedFrameNs;

    private String lastStatusMessage = "OBS Ready";
    private boolean lastStatusIsError = false;

    private record CapturedFrame(ByteBuffer buffer, int width, int height, long elapsedActiveMs) {}

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
        this.maxAllocatedBytes = Math.max(w * h * 4, 3840 * 2160 * 4);

        try {
            performanceManager.initializePool(this.maxAllocatedBytes, 6);
            encodeQueue = new ArrayBlockingQueue<>(4);

            currentOutputFile = RecordingFileManager.createUniqueRecordingFile();
            ffmpegEncoder = new FFmpegEncoder();
            ffmpegEncoder.start(currentOutputFile, w, h);

            audioCaptureManager.start(ffmpegEncoder.getAudioOutputStream());

            recordingStartTimeMs = System.currentTimeMillis();
            pausedAccumulatedMs = 0;
            paused.set(false);
            lastCapturedFrameNs = 0L;
            recording.set(true);

            videoEncoderThread = new Thread(this::runSinglePassTimestampedEncodeLoop, "OBS-AsyncEncoder");
            videoEncoderThread.setDaemon(true);
            videoEncoderThread.setPriority(Thread.NORM_PRIORITY - 1); // Never starve Minecraft Render Thread!
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
        setStatus("Saving MP4...", false);

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

        // If background encoder queue has a frame ready, skip glReadPixels this tick so game runs at 100% full FPS!
        if (encodeQueue.remainingCapacity() == 0) {
            return;
        }

        int targetFps = Math.min(30, RecordingConfig.get().getTargetFps());
        long frameIntervalNs = 1_000_000_000L / targetFps;
        long nowNs = System.nanoTime();

        if (nowNs - lastCapturedFrameNs < frameIntervalNs) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.textureWidth <= 0 || fb.textureHeight <= 0) return;

        int curW = fb.textureWidth;
        int curH = fb.textureHeight;
        if (curW * curH * 4 > maxAllocatedBytes) return;

        lastCapturedFrameNs = nowNs;

        ByteBuffer buffer = performanceManager.acquireBuffer();
        if (buffer == null) return;

        boolean captured = videoCaptureManager.captureFramebuffer(buffer, curW, curH);
        if (captured) {
            long activeMs = Math.max(1L, System.currentTimeMillis() - recordingStartTimeMs - pausedAccumulatedMs);
            if (!encodeQueue.offer(new CapturedFrame(buffer, curW, curH, activeMs))) {
                performanceManager.releaseBuffer(buffer);
            }
        } else {
            performanceManager.releaseBuffer(buffer);
        }
    }

    private void runSinglePassTimestampedEncodeLoop() {
        try {
            while (recording.get() || !encodeQueue.isEmpty()) {
                CapturedFrame item = encodeQueue.poll(25, TimeUnit.MILLISECONDS);
                if (item != null) {
                    ByteBuffer buf = item.buffer();
                    buf.rewind();
                    ffmpegEncoder.writeVideoFrame(buf, item.elapsedActiveMs(), item.width(), item.height());
                    performanceManager.recordFrameEncoded();
                    performanceManager.releaseBuffer(buf);
                }
            }
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.error("Encoder loop error", e);
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
