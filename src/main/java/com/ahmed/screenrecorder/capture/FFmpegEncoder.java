package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class FFmpegEncoder {
    public static final int REALTIME_FPS = 10;
    private NativeMjpegMp4Muxer h264Muxer;
    private int targetW = 640;
    private int targetH = 360;
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
        this.targetW = 640;
        this.targetH = 360;
        if (sourceWidth > 0 && sourceHeight > 0) {
            this.targetH = Math.max(240, ((this.targetW * sourceHeight) / sourceWidth / 2) * 2);
        }

        this.h264Muxer = new NativeMjpegMp4Muxer(outputFile, targetW, targetH, REALTIME_FPS);
        ScreenRecorderClient.LOGGER.info("OBS 1x Real-Time H.264 Encoder started ({}x{} @ {} FPS) -> {}",
                targetW, targetH, REALTIME_FPS, outputFile.getName());
    }

    public int getTargetW() { return targetW; }
    public int getTargetH() { return targetH; }

    public void updateAndWriteFrame(byte[] rgbBytes, int repeatCount) throws IOException {
        if (h264Muxer != null) {
            h264Muxer.loadRgbBytes(rgbBytes);
            for (int i = 0; i < repeatCount; i++) {
                h264Muxer.writeTickFrame(rgbBytes);
            }
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
                ScreenRecorderClient.LOGGER.error("Error finalizing MP4", e);
            }
            h264Muxer = null;
        }
    }
}
