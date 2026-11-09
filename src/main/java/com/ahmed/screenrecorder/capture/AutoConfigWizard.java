package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.config.RecordingConfig;
import net.minecraft.client.MinecraftClient;

/**
 * OBS Studio Auto-Configuration Wizard:
 * Benchmarks device RAM, CPU cores, and current live FPS to automatically configure
 * optimal zero-lag HD recording settings.
 */
public class AutoConfigWizard {

    public static String runOptimalBenchmark() {
        RecordingConfig cfg = RecordingConfig.get();
        int cores = Runtime.getRuntime().availableProcessors();
        long maxRamMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        int currentFps = Math.max(20, MinecraftClient.getInstance().getCurrentFps());

        if (cores >= 8 && maxRamMb >= 3072 && currentFps >= 55) {
            cfg.resolutionPreset = RecordingConfig.ResolutionPreset.RES_1080P;
            cfg.fpsPreset = RecordingConfig.FpsPreset.FPS_60;
            cfg.videoQuality = RecordingConfig.VideoQuality.VERY_HIGH;
            cfg.encoderPreset = "veryfast";
        } else if (cores >= 6 && maxRamMb >= 1800) {
            cfg.resolutionPreset = RecordingConfig.ResolutionPreset.RES_720P;
            cfg.fpsPreset = RecordingConfig.FpsPreset.FPS_60;
            cfg.videoQuality = RecordingConfig.VideoQuality.HIGH;
            cfg.encoderPreset = "superfast";
        } else {
            cfg.resolutionPreset = RecordingConfig.ResolutionPreset.RES_720P;
            cfg.fpsPreset = RecordingConfig.FpsPreset.FPS_30;
            cfg.videoQuality = RecordingConfig.VideoQuality.HIGH;
            cfg.encoderPreset = "ultrafast";
        }

        cfg.gameAudioEnabled = true;
        cfg.noiseSuppression = true;
        cfg.noiseGate = true;
        RecordingConfig.save();

        return String.format("Auto-Config Applied: %s @ %d FPS (%d Cores, %dMB RAM)",
                cfg.resolutionPreset.label, cfg.getTargetFps(), cores, maxRamMb);
    }
}
