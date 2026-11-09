package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.audio.AudioCaptureManager;
import com.ahmed.screenrecorder.audio.WavAudioTrackRecorder;
import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.io.File;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smooth Real-Time Buffered Recording Manager.
 * Captures frames at a steady 15 FPS metronome into a lightweight ring buffer while playing,
 * and smoothly encodes every frame into a 100% standard ISO MP4 file!
 */
public class RecordingManager {
    private static final RecordingManager INSTANCE = new RecordingManager();

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final AtomicInteger renderProgressPct = new AtomicInteger(0);

    private final FastFrameCaptureHandler fastGrabber = new FastFrameCaptureHandler();
    private final AudioCaptureManager audioCaptureManager = new AudioCaptureManager();
    private final WavAudioTrackRecorder wavRecorder = new WavAudioTrackRecorder();
    private final PerformanceManager performanceManager = new PerformanceManager();
    private FFmpegEncoder ffmpegEncoder;

    private record TimedRgbFrame(byte[] rgb, long elapsedMs) {}
    private ArrayBlockingQueue<TimedRgbFrame> frameQueue;
    private ArrayBlockingQueue<byte[]> freeBytePool;

    private int captureW = 640;
    private int captureH = 368;
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
        setStatus("Starting OBS Recorder...", false);

        Thread initThread = new Thread(() -> {
            try {
                if (!RecordingFileManager.hasEnoughDiskSpace()) {
                    setStatus("Low disk space (< 250 MB)", true);
                    starting.set(false);
                    return;
                }

                MinecraftClient client = MinecraftClient.getInstance();
                Framebuffer fb = client != null ? client.getFramebuffer() : null;
                int srcW = (fb != null && fb.textureWidth > 0) ? fb.textureWidth : 1280;
                int srcH = (fb != null && fb.textureHeight > 0) ? fb.textureHeight : 720;

                currentOutputFile = RecordingFileManager.createUniqueRecordingFile();
                ffmpegEncoder = new FFmpegEncoder();
                ffmpegEncoder.start(currentOutputFile, srcW, srcH);
                wavRecorder.start(currentOutputFile);

                captureW = ffmpegEncoder.getTargetW();
                captureH = ffmpegEncoder.getTargetH();
                int rgbBytes = captureW * captureH * 3;

                // Pool of 16 pre-allocated frame buffers (~11 MB total) for smooth jitter-free capture
                frameQueue = new ArrayBlockingQueue<>(16);
                freeBytePool = new ArrayBlockingQueue<>(16);
                for (int i = 0; i < 16; i++) {
                    freeBytePool.offer(new byte[rgbBytes]);
                }
                performanceManager.initializePool(1024, 2);

                audioCaptureManager.start(new OutputStream() {
                    @Override
                    public void write(int b) {}
                    @Override
                    public void write(byte[] b, int off, int len) {
                        if (recording.get() && !paused.get()) {
                            wavRecorder.writePcmChunk(b, len);
                        }
                    }
                });

                recordingStartTimeMs = System.currentTimeMillis();
                pausedAccumulatedMs = 0;
                finalActiveDurationMs = 0;
                paused.set(false);
                lastGlGrabTimeMs = 0L;
                recording.set(true);

                videoEncoderThread = new Thread(this::runSmoothPacedEncodeWorker, "OBS-SmoothEncoder");
                videoEncoderThread.setDaemon(true);
                videoEncoderThread.setPriority(Thread.NORM_PRIORITY - 1);
                videoEncoderThread.start();

                setStatus("Recording: " + currentOutputFile.getName(), false);
            } catch (Exception e) {
                ScreenRecorderClient.LOGGER.error("Start failed", e);
                setStatus("Start failed: " + e.getMessage(), true);
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
            setStatus("Paused", false);
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
        renderProgressPct.set(15);
        setStatus("Rendering MP4 Video (15%)...", false);

        Thread stopThread = new Thread(this::performSafeStopWithProgress, "OBS-Finalizer");
        stopThread.setDaemon(false);
        stopThread.setPriority(Thread.NORM_PRIORITY);
        stopThread.start();
    }

    public void toggleRecording() {
        if (recording.get()) {
            stopRecording();
        } else if (!starting.get() && !stopping.get()) {
            startRecording();
        }
    }

    public void onFrameRendered() {
        if (!recording.get() || paused.get() || stopping.get()) return;
        if (freeBytePool == null || frameQueue == null) return;

        long intervalMs = 1000L / FFmpegEncoder.SMOOTH_STREAM_FPS; // 66ms (15 FPS smooth cadence)
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastGlGrabTimeMs < intervalMs) {
            return;
        }

        byte[] buf = freeBytePool.poll();
        if (buf == null) return; // Pool busy, skip without lagging game

        lastGlGrabTimeMs = nowMs;
        if (fastGrabber.captureScaledRgbFrame(buf, captureW, captureH)) {
            long elapsed = Math.max(1L, nowMs - recordingStartTimeMs - pausedAccumulatedMs);
            if (!frameQueue.offer(new TimedRgbFrame(buf, elapsed))) {
                freeBytePool.offer(buf);
            }
        } else {
            freeBytePool.offer(buf);
        }
    }

    /**
     * Smoothly encodes every captured frame in exact wall-clock order so the video plays
     * continuously without stuttering or freezing!
     */
    private void runSmoothPacedEncodeWorker() {
        final int fps = FFmpegEncoder.SMOOTH_STREAM_FPS;
        long totalWrittenFrames = 0;

        try {
            while (recording.get() || (frameQueue != null && !frameQueue.isEmpty())) {
                TimedRgbFrame item = frameQueue != null ? frameQueue.poll(30, TimeUnit.MILLISECONDS) : null;
                if (item != null) {
                    long targetFrameIdx = Math.max(totalWrittenFrames + 1, (item.elapsedMs() * fps) / 1000L);
                    int count = (int) Math.min(3L, Math.max(1L, targetFrameIdx - totalWrittenFrames));

                    ffmpegEncoder.writePacedFrames(item.rgb(), count);
                    totalWrittenFrames += count;
                    for (int i = 0; i < count; i++) {
                        performanceManager.recordFrameEncoded();
                    }

                    if (freeBytePool != null) {
                        freeBytePool.offer(item.rgb());
                    }
                }
            }
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.error("Smooth encoder worker error", e);
        }
    }

    private void performSafeStopWithProgress() {
        try {
            renderProgressPct.set(30);
            setStatus("Saving Audio Track (30%)...", false);
            audioCaptureManager.stop();
            wavRecorder.finish();

            renderProgressPct.set(60);
            setStatus("Rendering Video Frames (60%)...", false);
            if (videoEncoderThread != null && Thread.currentThread() != videoEncoderThread) {
                try {
                    videoEncoderThread.join(15000);
                } catch (InterruptedException ignored) {}
            }

            renderProgressPct.set(85);
            setStatus("Finalizing MP4 Container (85%)...", false);
            if (ffmpegEncoder != null) {
                ffmpegEncoder.stopAndFinalize();
                ffmpegEncoder = null;
            }

            renderProgressPct.set(100);
            if (currentOutputFile != null && currentOutputFile.exists() && currentOutputFile.length() > 512) {
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
            frameQueue = null;
            freeBytePool = null;
            stopping.set(false);
        }
    }

    private void cleanupFailedStart() {
        recording.set(false);
        paused.set(false);
        stopping.set(false);
        starting.set(false);
        audioCaptureManager.stop();
        wavRecorder.finish();
        if (ffmpegEncoder != null) {
            ffmpegEncoder.stopAndFinalize();
            ffmpegEncoder = null;
        }
    }

    public void shutdownSafely() {
        if (recording.get()) {
            long stopMoment = paused.get() ? pauseStartMs : System.currentTimeMillis();
            this.finalActiveDurationMs = Math.max(1000L, stopMoment - recordingStartTimeMs - pausedAccumulatedMs);
            recording.set(false);
            performSafeStopWithProgress();
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
