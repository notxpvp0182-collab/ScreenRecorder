package com.ahmed.screenrecorder.audio;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages synchronized 44.1kHz 16-bit Stereo Audio capture (Game Loopback + Microphone)
 * and feeds a continuous real-time PCM stream to the FFmpeg audio pipe.
 */
public class AudioCaptureManager {
    private TargetDataLine gameAudioLine;
    private final MicrophoneManager microphoneManager = new MicrophoneManager();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread audioWorkerThread;

    public static List<String> getAvailableGameAudioDevices() {
        List<String> list = new ArrayList<>();
        list.add("Default");
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, MicrophoneManager.AUDIO_FORMAT);
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            if (mixer.isLineSupported(info)) {
                list.add(mixerInfo.getName());
            }
        }
        return list;
    }

    public void start(OutputStream ffmpegAudioPipe) {
        if (running.get()) return;
        RecordingConfig cfg = RecordingConfig.get();

        if (cfg.gameAudioEnabled) {
            initGameAudioLoopback(cfg.gameAudioDevice);
        }

        if (cfg.microphoneEnabled) {
            microphoneManager.start();
        }

        running.set(true);
        audioWorkerThread = new Thread(() -> runAudioLoop(ffmpegAudioPipe), "ScreenRecorder-AudioThread");
        audioWorkerThread.setDaemon(true);
        audioWorkerThread.start();
    }

    private void initGameAudioLoopback(String preferredDevice) {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, MicrophoneManager.AUDIO_FORMAT);
            TargetDataLine line = null;

            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                String name = mixerInfo.getName().toLowerCase();
                boolean matchesPref = !"Default".equalsIgnoreCase(preferredDevice) && mixerInfo.getName().equalsIgnoreCase(preferredDevice);
                boolean isLoopback = "Default".equalsIgnoreCase(preferredDevice) &&
                        (name.contains("stereo mix") || name.contains("loopback") || name.contains("what u hear") || name.contains("monitor") || name.contains("cable"));

                if (matchesPref || isLoopback) {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(info)) {
                        line = (TargetDataLine) mixer.getLine(info);
                        break;
                    }
                }
            }

            if (line != null) {
                line.open(MicrophoneManager.AUDIO_FORMAT, 8820);
                line.start();
                this.gameAudioLine = line;
                ScreenRecorderClient.LOGGER.info("Game audio capture line initialized.");
            } else {
                ScreenRecorderClient.LOGGER.info("No hardware loopback mixer found; game audio stream will use silent baseline unless loopback device is selected.");
            }
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.warn("Game audio line initialization fallback: {}", e.getMessage());
        }
    }

    private void runAudioLoop(OutputStream audioPipe) {
        // 20ms audio frame = 44100 * 0.020 * 4 bytes (16-bit stereo) = 3528 bytes
        final int chunkSize = 3528;
        final long chunkDurationNs = 20_000_000L;

        byte[] gameBuf = new byte[chunkSize];
        byte[] micBuf = new byte[chunkSize];
        byte[] mixBuf = new byte[chunkSize];

        long nextTickNs = System.nanoTime();

        while (running.get()) {
            RecordingConfig cfg = RecordingConfig.get();

            // 1. Read Game Audio
            if (cfg.gameAudioEnabled && gameAudioLine != null) {
                int avail = gameAudioLine.available();
                if (avail >= chunkSize) {
                    gameAudioLine.read(gameBuf, 0, chunkSize);
                } else if (avail > 0) {
                    int r = gameAudioLine.read(gameBuf, 0, avail);
                    Arrays.fill(gameBuf, r, chunkSize, (byte) 0);
                } else {
                    Arrays.fill(gameBuf, 0, chunkSize, (byte) 0);
                }
                AudioProcessor.applyVolume(gameBuf, chunkSize, cfg.gameVolume);
            } else {
                Arrays.fill(gameBuf, 0, chunkSize, (byte) 0);
            }

            // 2. Read & Process Microphone Audio
            if (cfg.microphoneEnabled) {
                microphoneManager.readProcessedBytes(micBuf, chunkSize);
            } else {
                Arrays.fill(micBuf, 0, chunkSize, (byte) 0);
            }

            // 3. Mix synchronized streams
            AudioProcessor.mixPcmStreams(gameBuf, micBuf, mixBuf, chunkSize);

            // 4. Write to FFmpeg audio pipe
            if (audioPipe != null) {
                try {
                    audioPipe.write(mixBuf, 0, chunkSize);
                } catch (IOException e) {
                    if (running.get()) {
                        ScreenRecorderClient.LOGGER.debug("Audio pipe closed.");
                    }
                    break;
                }
            }

            nextTickNs += chunkDurationNs;
            long sleepNs = nextTickNs - System.nanoTime();
            if (sleepNs > 0) {
                try {
                    Thread.sleep(sleepNs / 1_000_000L, (int) (sleepNs % 1_000_000L));
                } catch (InterruptedException ignored) {
                    break;
                }
            } else if (sleepNs < -100_000_000L) {
                nextTickNs = System.nanoTime();
            }
        }
    }

    public void stop() {
        running.set(false);
        if (audioWorkerThread != null) {
            try {
                audioWorkerThread.join(1000);
            } catch (InterruptedException ignored) {}
            audioWorkerThread = null;
        }
        if (gameAudioLine != null) {
            try {
                gameAudioLine.stop();
                gameAudioLine.close();
            } catch (Exception ignored) {}
            gameAudioLine = null;
        }
        microphoneManager.stop();
    }
}
