package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages the external FFmpeg process, feeding raw video via stdin and synchronized
 * PCM audio via a local loopback socket, finalizing clean MP4 files with +faststart.
 */
public class FFmpegEncoder {
    private Process ffmpegProcess;
    private OutputStream videoStdin;
    private WritableByteChannel videoChannel;
    private ServerSocket audioServerSocket;
    private Socket audioClientSocket;
    private OutputStream audioOutputStream;
    private Thread logReaderThread;

    public static boolean isFFmpegAvailable() {
        String bin = RecordingConfig.get().ffmpegPath;
        if (bin == null || bin.trim().isEmpty()) bin = "ffmpeg";
        try {
            Process p = new ProcessBuilder(bin, "-version").start();
            return p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void start(File outputFile, int sourceWidth, int sourceHeight) throws IOException {
        RecordingConfig cfg = RecordingConfig.get();
        String ffmpegBin = (cfg.ffmpegPath == null || cfg.ffmpegPath.isBlank()) ? "ffmpeg" : cfg.ffmpegPath;

        audioServerSocket = new ServerSocket(0, 1);
        audioServerSocket.setSoTimeout(8000);
        int audioPort = audioServerSocket.getLocalPort();

        int outWidth = cfg.getTargetWidth();
        int outHeight = cfg.getTargetHeight();
        int fps = cfg.getTargetFps();
        int bitrateKbps = cfg.getTargetBitrateKbps();
        int gop = Math.max(fps, fps * cfg.keyframeInterval);

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBin);
        cmd.add("-y");
        cmd.add("-loglevel");
        cmd.add("warning");

        // Input 0: Raw RGBA Video from stdin
        cmd.add("-f");
        cmd.add("rawvideo");
        cmd.add("-pix_fmt");
        cmd.add("rgba");
        cmd.add("-s");
        cmd.add(sourceWidth + "x" + sourceHeight);
        cmd.add("-r");
        cmd.add(String.valueOf(fps));
        cmd.add("-i");
        cmd.add("pipe:0");

        // Input 1: Synchronized 44.1kHz Stereo 16-bit PCM Audio from local TCP loopback
        cmd.add("-f");
        cmd.add("s16le");
        cmd.add("-ar");
        cmd.add("44100");
        cmd.add("-ac");
        cmd.add("2");
        cmd.add("-i");
        cmd.add("tcp://127.0.0.1:" + audioPort);

        // Video Filter: OpenGL vertical flip + hardware-friendly scaling + even YUV420P format
        String vf = String.format("vflip,scale=%d:%d:flags=bicubic,format=yuv420p", outWidth, outHeight);
        cmd.add("-vf");
        cmd.add(vf);

        // Video Encoder configuration
        cmd.add("-c:v");
        cmd.add(cfg.encoder);
        if (cfg.encoder.contains("264") || cfg.encoder.contains("265")) {
            cmd.add("-preset");
            cmd.add(cfg.encoderPreset);
        }
        cmd.add("-b:v");
        cmd.add(bitrateKbps + "k");
        cmd.add("-maxrate");
        cmd.add((int) (bitrateKbps * 1.25) + "k");
        cmd.add("-bufsize");
        cmd.add((bitrateKbps * 2) + "k");
        cmd.add("-g");
        cmd.add(String.valueOf(gop));

        // Audio Encoder configuration
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add(cfg.audioQuality.bitrateKbps + "k");

        // MP4 Container with faststart so headers are finalized cleanly
        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add(outputFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        this.ffmpegProcess = pb.start();

        this.videoStdin = new BufferedOutputStream(ffmpegProcess.getOutputStream(), 1024 * 1024);
        this.videoChannel = Channels.newChannel(this.videoStdin);

        logReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ScreenRecorderClient.LOGGER.info("[FFmpeg] {}", line);
                }
            } catch (IOException ignored) {}
        }, "ScreenRecorder-FFmpegLogger");
        logReaderThread.setDaemon(true);
        logReaderThread.start();

        // Accept FFmpeg's connection to our local audio loopback port
        this.audioClientSocket = audioServerSocket.accept();
        this.audioOutputStream = new BufferedOutputStream(audioClientSocket.getOutputStream(), 65536);

        ScreenRecorderClient.LOGGER.info("FFmpeg encoder started -> {}", outputFile.getName());
    }

    public void writeVideoFrame(ByteBuffer frameBuffer) throws IOException {
        if (videoChannel != null && ffmpegProcess != null && ffmpegProcess.isAlive()) {
            while (frameBuffer.hasRemaining()) {
                videoChannel.write(frameBuffer);
            }
        }
    }

    public OutputStream getAudioOutputStream() {
        return audioOutputStream;
    }

    public void stopAndFinalize() {
        try {
            if (videoStdin != null) {
                videoStdin.flush();
                videoStdin.close();
            }
        } catch (IOException ignored) {}

        try {
            if (audioOutputStream != null) {
                audioOutputStream.flush();
                audioOutputStream.close();
            }
        } catch (IOException ignored) {}

        try {
            if (audioClientSocket != null) audioClientSocket.close();
        } catch (IOException ignored) {}

        try {
            if (audioServerSocket != null) audioServerSocket.close();
        } catch (IOException ignored) {}

        if (ffmpegProcess != null) {
            try {
                if (!ffmpegProcess.waitFor(10, TimeUnit.SECONDS)) {
                    ScreenRecorderClient.LOGGER.warn("FFmpeg timed out during finalization, forcing termination.");
                    ffmpegProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                ffmpegProcess.destroyForcibly();
            }
            ffmpegProcess = null;
        }
        ScreenRecorderClient.LOGGER.info("FFmpeg encoder stopped and finalized.");
    }
}
