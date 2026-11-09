package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.audio.AudioCaptureManager;
import com.ahmed.screenrecorder.gui.LivePreviewRenderer;
import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordingManager {
    private static final RecordingManager INSTANCE = new RecordingManager();

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean frameReady = new AtomicBoolean(false);

    private final FastFrameCaptureHandler fastCapture = new FastFrameCaptureHandler();
    private final AudioCaptureManager audioCaptureManager = new AudioCaptureManager();
    private final PerformanceManager performanceManager = new PerformanceManager();
    private FFmpegEncoder ffmpegEncoder;

    private byte[] sharedRgbBuffer;
    private byte[] encoderWorkBuffer;
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

        try {
            currentOutputFile = RecordingFileManager.createUniqueRecordingFile();
            ffmpegEncoder = new FFmpegEncoder();
            ffmpegEncoder.start(currentOutputFile, fb.textureWidth, fb.textureHeight);

            int rgbSize = ffmpegEncoder.getTargetW() * ffmpegEncoder.getTargetH() * 3;
            sharedRgbBuffer = new byte[rgbSize];
            encoderWorkBuffer = new byte[rgbSize];
            performanceManager.initializePool(1024, 2);

            audioCaptureManager.start(ffmpegEncoder.getAudioOutputStream());

            recordingStartTimeMs = System.currentTimeMillis();
            pausedAccumulatedMs = 0;
            paused.set(false);
            frameReady.set(false);
            lastGlCaptureTimeMs = 0L;
            recording.set(true);

            videoEncoderThread = new Thread(this::runExactWallClockEncoderLoop, "OBS-RealtimeEncoder");
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
        MinecraftClient client = MinecraftClient.getInstance();
        // When OBS GUI is not open, keep live preview updated so opening OBS shows the full game screen immediately!
        if (client.currentScreen == null) {
            LivePreviewRenderer.captureFullScreenLightweight();
        }

        if (!recording.get() || paused.get() || stopping.get()) return;
        if (frameReady.get()) return;

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastGlCaptureTimeMs < (1000 / FFmpegEncoder.REALTIME_FPS)) {
            return;
        }

        if (ffmpegEncoder == null || sharedRgbBuffer == null) return;
        lastGlCaptureTimeMs = nowMs;

        if (fastCapture.captureScaledRgbFrame(sharedRgbBuffer, ffmpegEncoder.getTargetW(), ffmpegEncoder.getTargetH())) {
            frameReady.set(true);
        }
    }

    /**
     * Exact 1.0x Wall-Clock Loop:
     * At 10 FPS (100ms per frame), mobile CPUs easily encode each frame in ~35ms!
     * If a frame takes slightly longer, repeatCount catches up to exact wall-clock elapsed time
     * so 46 seconds of recording ALWAYS produces a 46-second video!
     */
    private void runExactWallClockEncoderLoop() {
        final int fps = FFmpegEncoder.REALTIME_FPS;
        long writtenFrames = 0;
        boolean hasFrame = false;

        try {
            while (recording.get()) {
                if (paused.get()) {
                    Thread.sleep(30);
                    continue;
                }

                if (frameReady.compareAndSet(true, false)) {
                    System.arraycopy(sharedRgbBuffer, 0, encoderWorkBuffer, 0, sharedRgbBuffer.length);
                    hasFrame = true;
                }

                if (!hasFrame) {
                    Thread.sleep(10);
                    continue;
                }

                long elapsedMs = Math.max(100L, System.currentTimeMillis() - recordingStartTimeMs - pausedAccumulatedMs);
                long targetTotalFrames = Math.max(1L, (elapsedMs * fps) / 1000L);

                if (writtenFrames < targetTotalFrames) {
                    int catchUp = (int) Math.min(3L, targetTotalFrames - writtenFrames);
                    ffmpegEncoder.updateAndWriteFrame(encoderWorkBuffer, catchUp);
                    writtenFrames += catchUp;
                    for (int i = 0; i < catchUp; i++) {
                        performanceManager.recordFrameEncoded();
                    }
                } else {
                    Thread.sleep(15);
                }
            }
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.error("Realtime encoder error", e);
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
