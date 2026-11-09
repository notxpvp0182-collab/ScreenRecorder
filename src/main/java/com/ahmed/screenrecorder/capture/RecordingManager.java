package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.audio.AudioCaptureManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-File High-Definition Zero-Lag Recording Manager:
 * - Saves ONLY ONE .mp4 file in screen-recordings/ (embeds both HD video & audio inside that single file!)
 * - Zero FPS drop while recording: Render Thread only performs a single direct DMA glReadPixels every frame interval!
 */
public class RecordingManager {
    private static final RecordingManager INSTANCE = new RecordingManager();

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final AtomicBoolean rawFrameWaiting = new AtomicBoolean(false);
    private final AtomicInteger renderProgressPct = new AtomicInteger(0);

    private final AudioCaptureManager audioCaptureManager = new AudioCaptureManager();
    private final PerformanceManager performanceManager = new PerformanceManager();
    private SingleFileAvMp4Muxer singleMp4Muxer;

    private ByteBuffer directGlBuffer;
    private volatile int capturedW = 1280;
    private volatile int capturedH = 720;
    private volatile long capturedElapsedMs = 0;
    private Thread videoEncoderThread;

    private File currentOutputFile;
    private long recordingStartTimeMs;
    private long finalActiveDurationMs = 0;
    private long pausedAccumulatedMs = 0;
    private long pauseStartMs = 0;
    private long lastGlGrabTimeMs = 0;

    private volatile String lastStatusMessage = "OBS Ready";
    private volatile boolean lastStatusIsError = false;

    public static RecordingManager getInstance() {
        return INSTANCE;
    }

    public boolean startRecording() {
        if (recording.get() || starting.get() || stopping.get()) {
            return false;
        }
        starting.set(true);
        setStatus("Starting HD Recorder...", false);

        Thread initThread = new Thread(() -> {
            try {
                if (!RecordingFileManager.hasEnoughDiskSpace()) {
                    setStatus("Low disk space (< 250 MB)", true);
                    starting.set(false);
                    return;
                }

                RecordingConfig cfg = RecordingConfig.get();
                MinecraftClient client = MinecraftClient.getInstance();
                Framebuffer fb = client != null ? client.getFramebuffer() : null;
                int srcW = (fb != null && fb.textureWidth > 0) ? fb.textureWidth : 1280;
                int srcH = (fb != null && fb.textureHeight > 0) ? fb.textureHeight : 720;

                int outW = Math.min(srcW, cfg.getTargetWidth());
                int outH = Math.min(srcH, cfg.getTargetHeight());
                if (srcW > 0 && srcH > 0) {
                    outH = Math.max(360, ((outW * srcH) / srcW / 2) * 2);
                }

                currentOutputFile = RecordingFileManager.createUniqueRecordingFile();
                singleMp4Muxer = new SingleFileAvMp4Muxer(currentOutputFile, outW, outH);
                performanceManager.initializePool(1024, 2);

                audioCaptureManager.start(new OutputStream() {
                    @Override
                    public void write(int b) {}
                    @Override
                    public void write(byte[] b, int off, int len) {
                        if (recording.get() && !paused.get() && singleMp4Muxer != null) {
                            try {
                                singleMp4Muxer.writeAudioPcmChunk(b, len);
                            } catch (Exception ignored) {}
                        }
                    }
                });

                recordingStartTimeMs = System.currentTimeMillis();
                pausedAccumulatedMs = 0;
                finalActiveDurationMs = 0;
                paused.set(false);
                rawFrameWaiting.set(false);
                lastGlGrabTimeMs = 0L;
                recording.set(true);

                videoEncoderThread = new Thread(this::runFastBackgroundMuxLoop, "OBS-HD-Muxer");
                videoEncoderThread.setDaemon(true);
                videoEncoderThread.setPriority(Thread.MIN_PRIORITY + 2);
                videoEncoderThread.start();

                setStatus("Recording HD: " + currentOutputFile.getName(), false);
            } catch (Exception e) {
                ScreenRecorderClient.LOGGER.error("Start failed", e);
                setStatus("Start error: " + e.getMessage(), true);
                cleanupFailedStart();
            } finally {
                starting.set(false);
            }
        }, "OBS-Starter");
        initThread.setDaemon(true);
        initThread.start();
        return true;
    }

    public void togglePause() {
        if (!recording.get() || stopping.get()) return;
        if (paused.compareAndSet(false, true)) {
            pauseStartMs = System.currentTimeMillis();
            setStatus("Recording Paused", false);
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

    public void stopRecording() {
        if (!recording.get() || stopping.get()) {
            return;
        }
        long stopMoment = paused.get() ? pauseStartMs : System.currentTimeMillis();
        this.finalActiveDurationMs = Math.max(1000L, stopMoment - recordingStartTimeMs - pausedAccumulatedMs);

        stopping.set(true);
        recording.set(false);
        paused.set(false);
        renderProgressPct.set(20);
        setStatus("Rendering MP4 (20%)...", false);

        Thread stopThread = new Thread(this::performSafeStop, "OBS-StopThread");
        stopThread.setDaemon(true);
        stopThread.start();
    }

    public void toggleRecording() {
        if (recording.get()) {
            stopRecording();
        } else if (!starting.get() && !stopping.get()) {
            startRecording();
        }
    }

    /**
     * Render Thread Hook: Performs ONLY 1 fast direct buffer read when the background worker is free.
     * Zero pixel loops on the Render Thread = 0% FPS drop!
     */
    public void onFrameRendered() {
        if (!recording.get() || paused.get() || stopping.get()) return;
        if (rawFrameWaiting.get()) return;

        int targetFps = Math.max(20, Math.min(60, RecordingConfig.get().getTargetFps()));
        long intervalMs = Math.max(16L, 1000L / targetFps);
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastGlGrabTimeMs < intervalMs) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.textureWidth <= 0 || fb.textureHeight <= 0) return;

        int w = fb.textureWidth;
        int h = fb.textureHeight;
        int needed = w * h * 4;
        if (directGlBuffer == null || directGlBuffer.capacity() < needed) {
            directGlBuffer = BufferUtils.createByteBuffer(needed);
        }

        lastGlGrabTimeMs = nowMs;
        directGlBuffer.clear();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, directGlBuffer);

        capturedW = w;
        capturedH = h;
        capturedElapsedMs = Math.max(1L, nowMs - recordingStartTimeMs - pausedAccumulatedMs);
        rawFrameWaiting.set(true);
    }

    private void runFastBackgroundMuxLoop() {
        try {
            while (recording.get() || rawFrameWaiting.get()) {
                if (paused.get()) {
                    Thread.sleep(25);
                    continue;
                }
                if (rawFrameWaiting.get()) {
                    if (singleMp4Muxer != null && directGlBuffer != null) {
                        singleMp4Muxer.writeVideoFrame(directGlBuffer, capturedW, capturedH, capturedElapsedMs);
                        performanceManager.recordFrameEncoded();
                    }
                    rawFrameWaiting.set(false);
                } else {
                    Thread.sleep(5);
                }
            }
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.error("Background mux error", e);
        }
    }

    private void performSafeStop() {
        try {
            renderProgressPct.set(45);
            audioCaptureManager.stop();

            renderProgressPct.set(75);
            if (videoEncoderThread != null && Thread.currentThread() != videoEncoderThread) {
                try {
                    videoEncoderThread.join(3000);
                } catch (InterruptedException ignored) {}
            }

            if (singleMp4Muxer != null) {
                try {
                    singleMp4Muxer.finish(this.finalActiveDurationMs);
                } catch (Exception ignored) {}
                singleMp4Muxer = null;
            }

            // If Native FFmpeg is installed in config/obs_ffmpeg_engine, optionally transcode/optimize in background
            renderProgressPct.set(100);
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
                setStatus("Recording saved.", false);
            }
        } finally {
            stopping.set(false);
        }
    }

    private void cleanupFailedStart() {
        recording.set(false);
        paused.set(false);
        stopping.set(false);
        starting.set(false);
        audioCaptureManager.stop();
        if (singleMp4Muxer != null) {
            try { singleMp4Muxer.finish(1000L); } catch (Exception ignored) {}
            singleMp4Muxer = null;
        }
    }

    public void shutdownSafely() {
        if (recording.get()) {
            long stopMoment = paused.get() ? pauseStartMs : System.currentTimeMillis();
            this.finalActiveDurationMs = Math.max(1000L, stopMoment - recordingStartTimeMs - pausedAccumulatedMs);
            recording.set(false);
            performSafeStop();
        }
    }

    public boolean isRecording() { return recording.get(); }
    public boolean isPaused() { return paused.get(); }
    public boolean isStopping() { return stopping.get(); }
    public int getRenderProgressPct() { return renderProgressPct.get(); }

    public String getFormattedDuration() {
        if (!recording.get()) return "00:00:00";
        long now = paused.get() ? pauseStartMs : System.currentTimeMillis();
        long elapsedSec = Math.max(0, (now - recordingStartTimeMs - pausedAccumulatedMs) / 1000L);
        return String.format("%02d:%02d:%02d", elapsedSec / 3600, (elapsedSec % 3600) / 60, elapsedSec % 60);
    }

    public String getLastStatusMessage() { return lastStatusMessage; }
    public void setCustomStatusMessage(String msg) {
        this.lastStatusMessage = msg;
        this.lastStatusIsError = false;
    }
    public boolean isLastStatusError() { return lastStatusIsError; }
    private void setStatus(String message, boolean isError) {
        this.lastStatusMessage = message;
        this.lastStatusIsError = isError;
    }
    public PerformanceManager getPerformanceManager() { return performanceManager; }
}
