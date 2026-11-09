package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class FFmpegEncoder {
    private NativeMjpegMp4Muxer h264Muxer;
    private int targetW = 854;
    private int targetH = 480;
    private final OutputStream silentPipe = new OutputStream() {
        @Override
        public void write(int b) {}
        @Override
        public void write(byte[] b, int off, int len) {}
    };

    public static boolean isFFmpegAvailable() {
        return true;
    }

    public void start(File outputFile, int sourceWidth, int sourceHeight) throws IOException {
        RecordingConfig cfg = RecordingConfig.get();
        this.targetW = Math.min(960, cfg.getTargetWidth());
        this.targetH = Math.min(540, cfg.getTargetHeight());
        if (sourceWidth > 0 && sourceHeight > 0) {
            this.targetH = Math.max(240, ((this.targetW * sourceHeight) / sourceWidth / 2) * 2);
        }
        this.targetW = Math.max(320, (this.targetW / 2) * 2);

        this.h264Muxer = new NativeMjpegMp4Muxer(outputFile, targetW, targetH, cfg.getTargetFps());
        ScreenRecorderClient.LOGGER.info("OBS Single-Pass H.264 Encoder started ({}x{}) -> {}",
                targetW, targetH, outputFile.getName());
    }

    public int getTargetW() { return targetW; }
    public int getTargetH() { return targetH; }

    public void writeSinglePassFrame(byte[] rgbBytes) throws IOException {
        if (h264Muxer != null) {
            h264Muxer.encodeSingleFrame(rgbBytes);
        }
    }

    public OutputStream getAudioOutputStream() {
        return silentPipe;
    }

    public void stopAndFinalize(long actualElapsedMs) {
        if (h264Muxer != null) {
            try {
                h264Muxer.finishAndCalibrate(actualElapsedMs);
            } catch (Exception e) {
                ScreenRecorderClient.LOGGER.error("Error finalizing MP4", e);
            }
            h264Muxer = null;
        }
    }
}
