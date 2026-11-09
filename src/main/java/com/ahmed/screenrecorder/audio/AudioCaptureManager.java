package com.ahmed.screenrecorder.audio;

import com.ahmed.screenrecorder.config.RecordingConfig;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 100% Non-Blocking Audio Capture Manager.
 * Initializes microphone and in-game audio on the background thread so clicking Start/Stop Recording
 * NEVER freezes the phone or game!
 */
public class AudioCaptureManager {
    private final MicrophoneManager microphoneManager = new MicrophoneManager();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread audioWorkerThread;

    public static List<String> getAvailableGameAudioDevices() {
        return List.of("In-Game Engine Audio (Auto)", "System Loopback");
    }

    public void start(OutputStream audioPipe) {
        if (running.getAndSet(true)) return;

        audioWorkerThread = new Thread(() -> runAudioLoop(audioPipe), "OBS-AudioThread");
        audioWorkerThread.setDaemon(true);
        audioWorkerThread.setPriority(Thread.MIN_PRIORITY + 1);
        audioWorkerThread.start();
    }

    private void runAudioLoop(OutputStream audioPipe) {
        RecordingConfig cfg = RecordingConfig.get();
        if (cfg.microphoneEnabled) {
            microphoneManager.start();
        }

        final int chunkSize = 3528; // 20ms stereo 44.1kHz PCM
        byte[] gameBuf = new byte[chunkSize];
        byte[] micBuf = new byte[chunkSize];
        byte[] mixBuf = new byte[chunkSize];

        try {
            while (running.get()) {
                long t0 = System.currentTimeMillis();
                Arrays.fill(gameBuf, 0, chunkSize, (byte) 0);

                if (cfg.gameAudioEnabled) {
                    InGameAudioTap.mixInGameSoundsIntoPcm(gameBuf, chunkSize, cfg.gameVolume);
                }

                if (cfg.microphoneEnabled) {
                    microphoneManager.readProcessedBytes(micBuf, chunkSize);
                } else {
                    Arrays.fill(micBuf, 0, chunkSize, (byte) 0);
                }

                AudioProcessor.mixPcmStreams(gameBuf, micBuf, mixBuf, chunkSize);

                if (audioPipe != null) {
                    try {
                        audioPipe.write(mixBuf, 0, chunkSize);
                    } catch (Exception ignored) {
                        break;
                    }
                }

                long elapsed = System.currentTimeMillis() - t0;
                Thread.sleep(Math.max(5L, 20L - elapsed));
            }
        } catch (InterruptedException ignored) {
        } finally {
            microphoneManager.stop();
        }
    }

    public void stop() {
        running.set(false);
        if (audioWorkerThread != null) {
            audioWorkerThread.interrupt();
            audioWorkerThread = null;
        }
    }
}
