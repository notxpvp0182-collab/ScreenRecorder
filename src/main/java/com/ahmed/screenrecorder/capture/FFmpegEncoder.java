package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class FFmpegEncoder {
    private NativeMjpegMp4Muxer h264Muxer;
    private final OutputStream silentPipe = new OutputStream() {
        @Override
        public void write(int b) {}
        @Override
        public void write(byte[] b, int off, int len) {}
    };
    private int srcWidth;
    private int srcHeight;

    public static boolean isFFmpegAvailable() {
        return true;
    }

    public void start(File outputFile, int sourceWidth, int sourceHeight) throws IOException {
        this.srcWidth = sourceWidth;
        this.srcHeight = sourceHeight;
        RecordingConfig cfg = RecordingConfig.get();

        // Scale to crisp 16:9 resolution optimized for zero-lag H.264 encoding on mobile & PC
        int targetW = Math.min(sourceWidth, cfg.getTargetWidth());
        int targetH = Math.min(sourceHeight, cfg.getTargetHeight());
        if (targetW > 1280) {
            targetH = (targetH * 1280) / targetW;
            targetW = 1280;
        }
        targetW = Math.max(256, (targetW / 2) * 2);
        targetH = Math.max(144, (targetH / 2) * 2);

        this.h264Muxer = new NativeMjpegMp4Muxer(outputFile, targetW, targetH, cfg.getTargetFps());
        ScreenRecorderClient.LOGGER.info("OBS H.264 MP4 Encoder started ({}x{} @ {} FPS) -> {}",
                targetW, targetH, cfg.getTargetFps(), outputFile.getName());
    }

    public void writeVideoFrame(ByteBuffer frameBuffer) throws IOException {
        if (h264Muxer != null) {
            h264Muxer.writeRgbaFrame(frameBuffer, srcWidth, srcHeight);
        }
    }

    public OutputStream getAudioOutputStream() {
        return silentPipe;
    }

    public void stopAndFinalize() {
        if (h264Muxer != null) {
            try {
                h264Muxer.finish();
            } catch (Exception e) {
                ScreenRecorderClient.LOGGER.error("Error finalizing H.264 MP4 file", e);
            }
            h264Muxer = null;
        }
    }
}
