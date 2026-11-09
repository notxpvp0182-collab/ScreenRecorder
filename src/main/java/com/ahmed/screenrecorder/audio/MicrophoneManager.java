package com.ahmed.screenrecorder.audio;

import com.ahmed.screenrecorder.config.RecordingConfig;

import javax.sound.sampled.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real-Time Hardware Microphone Scanner.
 * Queries both Java Sound TargetDataLines AND Linux/Android ALSA (/proc/asound/cards) in real time
 * so ONLY genuinely connected microphones/headsets appear in the selector!
 */
public class MicrophoneManager {
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100.0f, 16, 2, true, false);
    public static final AudioFormat MONO_FALLBACK = new AudioFormat(44100.0f, 16, 1, true, false);

    private TargetDataLine targetLine;
    private boolean isMonoLine = false;
    private final AudioProcessor dsp = new AudioProcessor();
    private final AtomicBoolean active = new AtomicBoolean(false);

    public static List<String> getAvailableMicrophoneDevices() {
        Set<String> realDevices = new LinkedHashSet<>();

        // 1. Scan Java Sound API Hardware Capture Mixers
        try {
            DataLine.Info stereoInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            DataLine.Info monoInfo = new DataLine.Info(TargetDataLine.class, MONO_FALLBACK);
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                try {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(stereoInfo) || mixer.isLineSupported(monoInfo)) {
                        String name = mixerInfo.getName();
                        if (name != null && !name.isBlank() && !name.startsWith("Port ")) {
                            realDevices.add(name.trim());
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // 2. Scan real Linux/Android ALSA sound cards (/proc/asound/cards) if running on Android/Linux
        File alsaCards = new File("/proc/asound/cards");
        if (alsaCards.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(alsaCards))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && Character.isDigit(line.charAt(0)) && line.contains(":")) {
                        String cardName = line.substring(line.indexOf(':') + 1).trim();
                        if (!cardName.isEmpty()) {
                            realDevices.add("ALSA: " + cardName);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (realDevices.isEmpty()) {
            realDevices.add("System Default Mic");
        }
        return new ArrayList<>(realDevices);
    }

    public boolean start() {
        RecordingConfig cfg = RecordingConfig.get();
        if (!cfg.microphoneEnabled) return false;

        try {
            TargetDataLine selectedLine = null;
            isMonoLine = false;

            DataLine.Info stereoInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            DataLine.Info monoInfo = new DataLine.Info(TargetDataLine.class, MONO_FALLBACK);

            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                if (mixerInfo.getName() != null && mixerInfo.getName().equalsIgnoreCase(cfg.microphoneDevice)) {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(stereoInfo)) {
                        selectedLine = (TargetDataLine) mixer.getLine(stereoInfo);
                        break;
                    } else if (mixer.isLineSupported(monoInfo)) {
                        selectedLine = (TargetDataLine) mixer.getLine(monoInfo);
                        isMonoLine = true;
                        break;
                    }
                }
            }

            if (selectedLine == null) {
                if (AudioSystem.isLineSupported(stereoInfo)) {
                    selectedLine = (TargetDataLine) AudioSystem.getLine(stereoInfo);
                } else if (AudioSystem.isLineSupported(monoInfo)) {
                    selectedLine = (TargetDataLine) AudioSystem.getLine(monoInfo);
                    isMonoLine = true;
                }
            }

            if (selectedLine == null) return false;

            AudioFormat fmt = isMonoLine ? MONO_FALLBACK : AUDIO_FORMAT;
            this.targetLine = selectedLine;
            this.targetLine.open(fmt, 8820);
            this.targetLine.start();
            active.set(true);
            return true;
        } catch (Exception e) {
            stop();
            return false;
        }
    }

    public void readProcessedBytes(byte[] buffer, int length) {
        if (!active.get() || targetLine == null) {
            Arrays.fill(buffer, 0, length, (byte) 0);
            return;
        }
        try {
            int avail = targetLine.available();
            if (avail > 0) {
                if (isMonoLine) {
                    int monoLen = Math.min(avail, length / 2);
                    byte[] monoTmp = new byte[monoLen];
                    int read = targetLine.read(monoTmp, 0, monoLen);
                    int dst = 0;
                    for (int i = 0; i < read - 1 && dst < length - 3; i += 2) {
                        buffer[dst]     = monoTmp[i];
                        buffer[dst + 1] = monoTmp[i + 1];
                        buffer[dst + 2] = monoTmp[i];
                        buffer[dst + 3] = monoTmp[i + 1];
                        dst += 4;
                    }
                    if (dst < length) Arrays.fill(buffer, dst, length, (byte) 0);
                } else {
                    int toRead = Math.min(avail, length);
                    int read = targetLine.read(buffer, 0, toRead);
                    if (read < length) Arrays.fill(buffer, read, length, (byte) 0);
                }
            } else {
                Arrays.fill(buffer, 0, length, (byte) 0);
            }

            RecordingConfig cfg = RecordingConfig.get();
            dsp.processMicrophoneBuffer(
                    buffer, length, cfg.microphoneVolume, cfg.microphoneGainDb,
                    cfg.noiseSuppression, cfg.noiseGate, cfg.noiseGateThresholdDb
            );
        } catch (Exception e) {
            Arrays.fill(buffer, 0, length, (byte) 0);
        }
    }

    public void stop() {
        active.set(false);
        if (targetLine != null) {
            try { targetLine.stop(); targetLine.close(); } catch (Exception ignored) {}
            targetLine = null;
        }
    }
}
