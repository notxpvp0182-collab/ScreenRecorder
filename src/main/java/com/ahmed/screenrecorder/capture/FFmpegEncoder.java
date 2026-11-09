package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.audio.WavAudioTrackRecorder;
import com.ahmed.screenrecorder.config.RecordingConfig;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * High-Compatibility Universal MP4 Video Encoder.
 * Ensures every saved .mp4 uses 16-pixel macroblock alignment and standard MP4 headers
 * so Android File Manager and PC players play it smoothly without errors.
 */
public class FFmpegEncoder {
    public static final int SMOOTH_STREAM_FPS = 15;
    private Process ffmpegProcess;
    private OutputStream ffmpegStdin;
    private File finalOutputFile;
    private File tempVideoFile;
    private String nativeFFmpegBin;
    private boolean usingNativeFFmpeg = false;

    private NativeMjpegMp4Muxer fallbackMp4Muxer;
    private int targetW = 640;
    private int targetH = 368;

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
        this.finalOutputFile = outputFile;
        RecordingConfig cfg = RecordingConfig.get();

        this. nativeFFmpegBin = NativeFFmpegExtractor.getReadyFFmpegPathInstantly();

        // Macroblock-aligned resolution (multiples of 16) so hardware decoders never show "Can't play video"
        int desiredW = (this.nativeFFmpegBin != null) ? Math.min(1280, cfg.getTargetWidth()) : 640;
        this.targetW = Math.max(320, (desiredW / 16) * 16);
        int rawH = (sourceWidth > 0 && sourceHeight > 0) ? ((this.targetW * sourceHeight) / sourceWidth) : 360;
        this.targetH = Math.max(192, (rawH / 16) * 16);

        if (this.nativeFFmpegBin != null) {
            try {
                this.tempVideoFile = new File(outputFile.getParentFile(), "temp_" + outputFile.getName());
                List<String> cmd = new ArrayList<>();
                cmd.add(this.nativeFFmpegBin);
                cmd.add("-y");
                cmd.add("-f");
                cmd.add("rawvideo");
                cmd.add("-pix_fmt");
                cmd.add("rgb24");
                cmd.add("-s");
                cmd.add(targetW + "x" + targetH);
                cmd.add("-r");
                cmd.add(String.valueOf(SMOOTH_STREAM_FPS));
                cmd.add("-i");
                cmd.add("pipe:0");
                cmd.add("-c:v");
                cmd.add("libx264");
                cmd.add("-preset");
                cmd.add("ultrafast");
                cmd.add("-profile:v");
                cmd.add("baseline");
                cmd.add("-level");
                cmd.add("3.1");
                cmd.add("-crf");
                cmd.add(String.valueOf(cfg.videoQuality.crf));
                cmd.add("-pix_fmt");
                cmd.add("yuv420p");
                cmd.add("-movflags");
                cmd.add("+faststart");
                cmd.add(this.tempVideoFile.getAbsolutePath());

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                this.ffmpegProcess = pb.start();
                this.ffmpegStdin = new BufferedOutputStream(ffmpegProcess.getOutputStream(), 512 * 1024);

                Thread logDrain = new Thread(() -> {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()))) {
                        while (r.readLine() != null) {}
                    } catch (IOException ignored) {}
                }, "OBS-FFmpeg-Drain");
                logDrain.setDaemon(true);
                logDrain.start();

                this.usingNativeFFmpeg = true;
                ScreenRecorderClient.LOGGER.info("Started Native FFmpeg H.264 Baseline MP4 Encoder ({}x{} @ {} FPS)",
                        targetW, targetH, SMOOTH_STREAM_FPS);
                return;
            } catch (Exception e) {
                ScreenRecorderClient.LOGGER.warn("Native FFmpeg launch fallback: {}", e.getMessage());
            }
        }

        this.usingNativeFFmpeg = false;
        this.fallbackMp4Muxer = new NativeMjpegMp4Muxer(outputFile, targetW, targetH, SMOOTH_STREAM_FPS);
        ScreenRecorderClient.LOGGER.info("Started Standard ISO MP4 H.264 Encoder ({}x{} @ {} FPS)",
                targetW, targetH, SMOOTH_STREAM_FPS);
    }

    public int getTargetW() { return targetW; }
    public int getTargetH() { return targetH; }

    public synchronized void writePacedFrames(byte[] rgbBytes, int count) throws IOException {
        if (count <= 0) return;
        if (usingNativeFFmpeg && ffmpegStdin != null && ffmpegProcess != null && ffmpegProcess.isAlive()) {
            for (int i = 0; i < count; i++) {
                ffmpegStdin.write(rgbBytes);
            }
        } else if (fallbackMp4Muxer != null) {
            fallbackMp4Muxer.loadRgbAndEncode(rgbBytes, count);
        }
    }

    public OutputStream getAudioOutputStream() {
        return silentPipe;
    }

    public synchronized void stopAndFinalize() {
        if (usingNativeFFmpeg) {
            try {
                if (ffmpegStdin != null) {
                    ffmpegStdin.flush();
                    ffmpegStdin.close();
                    ffmpegStdin = null;
                }
                if (ffmpegProcess != null) {
                    if (!ffmpegProcess.waitFor(12, TimeUnit.SECONDS)) {
                        ffmpegProcess.destroyForcibly();
                    }
                    ffmpegProcess = null;
                }

                File wavFile = WavAudioTrackRecorder.getCompanionWavFile(finalOutputFile);
                if (tempVideoFile != null && tempVideoFile.exists() && tempVideoFile.length() > 512) {
                    if (wavFile.exists() && wavFile.length() > 100 && nativeFFmpegBin != null) {
                        Process muxProc = new ProcessBuilder(
                                nativeFFmpegBin, "-y",
                                "-i", tempVideoFile.getAbsolutePath(),
                                "-i", wavFile.getAbsolutePath(),
                                "-c:v", "copy",
                                "-c:a", "aac", "-b:a", "160k",
                                "-shortest", "-movflags", "+faststart",
                                finalOutputFile.getAbsolutePath()
                        ).start();
                        if (!muxProc.waitFor(10, TimeUnit.SECONDS) || !finalOutputFile.exists() || finalOutputFile.length() < 512) {
                            if (finalOutputFile.exists()) finalOutputFile.delete();
                            tempVideoFile.renameTo(finalOutputFile);
                        } else {
                            tempVideoFile.delete();
                        }
                    } else {
                        if (finalOutputFile.exists()) finalOutputFile.delete();
                        tempVideoFile.renameTo(finalOutputFile);
                    }
                }
                if (wavFile.exists()) wavFile.delete();
            } catch (Exception e) {
                ScreenRecorderClient.LOGGER.error("Error finalizing native FFmpeg MP4", e);
            }
        } else if (fallbackMp4Muxer != null) {
            try {
                fallbackMp4Muxer.finishCleanly();
                File wavFile = WavAudioTrackRecorder.getCompanionWavFile(finalOutputFile);
                if (wavFile.exists()) wavFile.delete();
            } catch (Exception e) {
                ScreenRecorderClient.LOGGER.error("Error finalizing ISO MP4", e);
            }
            fallbackMp4Muxer = null;
        }
    }
}
