package com.ahmed.screenrecorder.audio;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MicrophoneManager {
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100.0f, 16, 2, true, false);
    private TargetDataLine targetLine;
    private SourceDataLine monitoringLine;
    private final AudioProcessor dsp = new AudioProcessor();
    private final AtomicBoolean active = new AtomicBoolean(false);

    public static List<String> getAvailableMicrophoneDevices() {
        List<String> devices = new ArrayList<>();
        devices.add("Default");
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            if (mixer.isLineSupported(info)) {
                devices.add(mixerInfo.getName());
            }
        }
        return devices;
    }

    public boolean start() {
        RecordingConfig cfg = RecordingConfig.get();
        if (!cfg.microphoneEnabled) {
            return false;
        }

        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            TargetDataLine selectedLine = null;

            if (!"Default".equalsIgnoreCase(cfg.microphoneDevice)) {
                for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                    if (mixerInfo.getName().equalsIgnoreCase(cfg.microphoneDevice)) {
                        Mixer mixer = AudioSystem.getMixer(mixerInfo);
                        if (mixer.isLineSupported(info)) {
                            selectedLine = (TargetDataLine) mixer.getLine(info);
                            break;
                        }
                    }
                }
            }

            if (selectedLine == null) {
                if (!AudioSystem.isLineSupported(info)) {
                    ScreenRecorderClient.LOGGER.warn("Microphone format not supported by default device.");
                    return false;
                }
                selectedLine = (TargetDataLine) AudioSystem.getLine(info);
            }

            this.targetLine = selectedLine;
            this.targetLine.open(AUDIO_FORMAT, 8820);
            this.targetLine.start();

            if (cfg.micMonitoring) {
                try {
                    DataLine.Info outInfo = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
                    monitoringLine = (SourceDataLine) AudioSystem.getLine(outInfo);
                    monitoringLine.open(AUDIO_FORMAT, 8820);
                    monitoringLine.start();
                } catch (Exception e) {
                    ScreenRecorderClient.LOGGER.warn("Could not initialize microphone monitoring line.", e);
                }
            }

            active.set(true);
            ScreenRecorderClient.LOGGER.info("Microphone initialized: {}", cfg.microphoneDevice);
            return true;
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.error("Microphone device could not be initialized.", e);
            stop();
            return false;
        }
    }

    public void readProcessedBytes(byte[] buffer, int length) {
        if (!active.get() || targetLine == null) {
            Arrays.fill(buffer, 0, length, (byte) 0);
            return;
        }

        int available = targetLine.available();
        if (available >= length) {
            int read = targetLine.read(buffer, 0, length);
            if (read < length) {
                Arrays.fill(buffer, read, length, (byte) 0);
            }
        } else if (available > 0) {
            int read = targetLine.read(buffer, 0, available);
            Arrays.fill(buffer, read, length, (byte) 0);
        } else {
            Arrays.fill(buffer, 0, length, (byte) 0);
        }

        RecordingConfig cfg = RecordingConfig.get();
        dsp.processMicrophoneBuffer(
                buffer,
                length,
                cfg.microphoneVolume,
                cfg.microphoneGainDb,
                cfg.noiseSuppression,
                cfg.noiseGate,
                cfg.noiseGateThresholdDb
        );

        if (monitoringLine != null && cfg.micMonitoring) {
            monitoringLine.write(buffer, 0, length);
        }
    }

    public void stop() {
        active.set(false);
        if (targetLine != null) {
            try {
                targetLine.stop();
                targetLine.close();
            } catch (Exception ignored) {}
            targetLine = null;
        }
        if (monitoringLine != null) {
            try {
                monitoringLine.stop();
                monitoringLine.close();
            } catch (Exception ignored) {}
            monitoringLine = null;
        }
    }
}
