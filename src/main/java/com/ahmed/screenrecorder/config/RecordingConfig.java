package com.ahmed.screenrecorder.config;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class RecordingConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "screen_recorder.json");
    private static RecordingConfig INSTANCE = new RecordingConfig();

    // Video Settings
    public ResolutionPreset resolutionPreset = ResolutionPreset.RES_1080P;
    public int customWidth = 1920;
    public int customHeight = 1080;
    public FpsPreset fpsPreset = FpsPreset.FPS_60;
    public int customFps = 60;
    public VideoQuality videoQuality = VideoQuality.HIGH;
    public int customBitrateKbps = 15000;
    public String encoder = "libx264";
    public String encoderPreset = "veryfast";
    public int keyframeInterval = 2;

    // Audio Settings
    public boolean gameAudioEnabled = true;
    public int gameVolume = 100;
    public String gameAudioDevice = "Default";
    public AudioQuality audioQuality = AudioQuality.HIGH;

    // Microphone Settings
    public boolean microphoneEnabled = false;
    public String microphoneDevice = "Default";
    public int microphoneVolume = 100;
    public float microphoneGainDb = 0.0f;
    public boolean noiseSuppression = true;
    public boolean noiseGate = true;
    public float noiseGateThresholdDb = -40.0f;
    public boolean micMonitoring = false;

    // Recording & Indicator Settings
    public String outputFolderName = "screen-recordings";
    public String fileNamingPrefix = "Recording";
    public boolean showIndicator = true;
    public IndicatorPosition indicatorPosition = IndicatorPosition.TOP_LEFT;
    public String ffmpegPath = "ffmpeg";

    // Performance Settings
    public EncoderPriority encoderPriority = EncoderPriority.NORMAL;
    public int maxFrameQueueSize = 12;
    public boolean dropFramesWhenFull = true;

    public static RecordingConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                RecordingConfig loaded = GSON.fromJson(reader, RecordingConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                }
            } catch (Exception e) {
                ScreenRecorderClient.LOGGER.error("Failed to load configuration, using defaults.", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            ScreenRecorderClient.LOGGER.error("Failed to save configuration.", e);
        }
    }

    public int getTargetWidth() {
        int w = resolutionPreset == ResolutionPreset.CUSTOM ? customWidth : resolutionPreset.width;
        return Math.max(128, (w / 2) * 2); // Ensure even dimensions for YUV420P
    }

    public int getTargetHeight() {
        int h = resolutionPreset == ResolutionPreset.CUSTOM ? customHeight : resolutionPreset.height;
        return Math.max(128, (h / 2) * 2);
    }

    public int getTargetFps() {
        int f = fpsPreset == FpsPreset.CUSTOM ? customFps : fpsPreset.fps;
        return Math.max(10, Math.min(240, f));
    }

    public int getTargetBitrateKbps() {
        return videoQuality == VideoQuality.CUSTOM ? Math.max(1000, customBitrateKbps) : videoQuality.bitrateKbps;
    }

    public enum ResolutionPreset {
        RES_720P("1280x720", 1280, 720),
        RES_1080P("1920x1080", 1920, 1080),
        RES_1440P("2560x1440", 2560, 1440),
        RES_2160P("3840x2160", 3840, 2160),
        CUSTOM("Custom", 1920, 1080);

        public final String label;
        public final int width;
        public final int height;

        ResolutionPreset(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }
    }

    public enum FpsPreset {
        FPS_30("30 FPS", 30),
        FPS_60("60 FPS", 60),
        FPS_90("90 FPS", 90),
        FPS_120("120 FPS", 120),
        CUSTOM("Custom", 60);

        public final String label;
        public final int fps;

        FpsPreset(String label, int fps) {
            this.label = label;
            this.fps = fps;
        }
    }

    public enum VideoQuality {
        LOW("Low (4 Mbps)", 4000, 28),
        MEDIUM("Medium (8 Mbps)", 8000, 24),
        HIGH("High (15 Mbps)", 15000, 20),
        VERY_HIGH("Very High (25 Mbps)", 25000, 18),
        ULTRA("Ultra (45 Mbps)", 45000, 15),
        CUSTOM("Custom", 15000, 20);

        public final String label;
        public final int bitrateKbps;
        public final int crf;

        VideoQuality(String label, int bitrateKbps, int crf) {
            this.label = label;
            this.bitrateKbps = bitrateKbps;
            this.crf = crf;
        }
    }

    public enum AudioQuality {
        LOW("Low (96 kbps)", 96),
        MEDIUM("Medium (128 kbps)", 128),
        HIGH("High (192 kbps)", 192),
        ULTRA("Ultra (320 kbps)", 320);

        public final String label;
        public final int bitrateKbps;

        AudioQuality(String label, int bitrateKbps) {
            this.label = label;
            this.bitrateKbps = bitrateKbps;
        }
    }

    public enum IndicatorPosition {
        TOP_LEFT("Top Left"),
        TOP_RIGHT("Top Right"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_RIGHT("Bottom Right");

        public final String label;

        IndicatorPosition(String label) {
            this.label = label;
        }
    }

    public enum EncoderPriority {
        LOW("Low"),
        NORMAL("Normal"),
        HIGH("High");

        public final String label;

        EncoderPriority(String label) {
            this.label = label;
        }
    }
}
