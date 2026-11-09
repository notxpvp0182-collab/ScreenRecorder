package com.ahmed.screenrecorder.audio;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatic Hardware & Mobile Microphone Detector and Non-Blocking Audio Capture Manager.
 */
public class MicrophoneManager {
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100.0f, 16, 2, true, false);
    public static final AudioFormat MONO_FALLBACK = new AudioFormat(44100.0f, 16, 1, true, false);

    private TargetDataLine targetLine;
    private boolean isMonoLine = false;
    private final AudioProcessor dsp = new AudioProcessor();
    private final AtomicBoolean active = new AtomicBoolean(false);
    private static List<String> cachedMicList = null;
    private static long lastScanTimeMs = 0;

    /**
     * Automatically detects all connected hardware microphones on PC & Mobile and provides
     * clear selectable profiles so the user can always pick their active mic in Settings & Mixer!
     */
    public static List<String> getAvailableMicrophoneDevices() {
        long now = System.currentTimeMillis();
        if (cachedMicList != null && (now - lastScanTimeMs) < 5000) {
            return cachedMicList;
        }
        lastScanTimeMs = now;

        Set<String> devices = new LinkedHashSet<>();
        devices.add("Auto-Detect Default Mic");

        try {
            DataLine.Info stereoInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            DataLine.Info monoInfo = new DataLine.Info(TargetDataLine.class, MONO_FALLBACK);
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                try {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(stereoInfo) || mixer.isLineSupported(monoInfo)) {
                        String name = mixerInfo.getName();
                        if (name != null && !name.isBlank() && !name.contains("Port ")) {
                            devices.add(name.trim());
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // Ensure mobile & PC standard input channels are always selectable
        devices.add("Built-in Device Mic");
        devices.add("Headset / Earphone Mic");
        devices.add("Bluetooth / USB Mic");

        cachedMicList = new ArrayList<>(devices);
        return cachedMicList;
    }

    public boolean start() {
        RecordingConfig cfg = RecordingConfig.get();
        if (!cfg.microphoneEnabled) {
            return false;
        }

        try {
            TargetDataLine selectedLine = null;
            isMonoLine = false;

            DataLine.Info stereoInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            DataLine.Info monoInfo = new DataLine.Info(TargetDataLine.class, MONO_FALLBACK);

            // Try matching user-selected mixer device first
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

            // Fallback to default system microphone line (Stereo or Mono)
            if (selectedLine == null) {
                if (AudioSystem.isLineSupported(stereoInfo)) {
                    selectedLine = (TargetDataLine) AudioSystem.getLine(stereoInfo);
                } else if (AudioSystem.isLineSupported(monoInfo)) {
                    selectedLine = (TargetDataLine) AudioSystem.getLine(monoInfo);
                    isMonoLine = true;
                }
            }

            if (selectedLine == null) {
                return false;
            }

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
                    buffer,
                    length,
                    cfg.microphoneVolume,
                    cfg.microphoneGainDb,
                    cfg.noiseSuppression,
                    cfg.noiseGate,
                    cfg.noiseGateThresholdDb
            );
        } catch (Exception e) {
            Arrays.fill(buffer, 0, length, (byte) 0);
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
    }
}
