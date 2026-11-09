package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.audio.AudioCaptureManager;
import com.ahmed.screenrecorder.audio.MicrophoneManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Categorized Settings Screen (VIDEO, AUDIO, MICROPHONE, RECORDING, CONTROLS, PERFORMANCE)
 * with full Dark/Red/White styling and persistent configuration saving.
 */
public class RecorderSettingsScreen extends Screen {
    public enum Category {
        VIDEO("VIDEO"),
        AUDIO("AUDIO"),
        MICROPHONE("MICROPHONE"),
        RECORDING("RECORDING"),
        CONTROLS("CONTROLS"),
        PERFORMANCE("PERFORMANCE");

        public final String title;
        Category(String title) { this.title = title; }
    }

    private final Screen parent;
    private Category activeCategory;

    private TextFieldWidget customWidthField;
    private TextFieldWidget customHeightField;
    private TextFieldWidget customFpsField;
    private TextFieldWidget customBitrateField;
    private TextFieldWidget filePrefixField;

    public RecorderSettingsScreen(Screen parent, Category initialCategory) {
        super(Text.literal("Recorder Settings"));
        this.parent = parent;
        this.activeCategory = initialCategory;
    }

    @Override
    protected void init() {
        RecordingConfig cfg = RecordingConfig.get();

        // Top Category Tabs
        Category[] categories = Category.values();
        int tabW = 72;
        int totalTabW = categories.length * (tabW + 4);
        int startTabX = (this.width - totalTabW) / 2;

        for (int i = 0; i < categories.length; i++) {
            Category cat = categories[i];
            boolean isSelected = (cat == activeCategory);
            this.addDrawableChild(new ThemeButton(
                    startTabX + i * (tabW + 4), 28, tabW, 20,
                    Text.literal(cat.title),
                    () -> {
                        saveTextFields();
                        this.activeCategory = cat;
                        this.clearAndInit();
                    },
                    isSelected
            ));
        }

        int leftColX = this.width / 2 - 155;
        int rightColX = this.width / 2 + 5;
        int colW = 150;
        int rowY = 66;
        int stepY = 28;

        switch (activeCategory) {
            case VIDEO -> buildVideoTab(cfg, leftColX, rightColX, colW, rowY, stepY);
            case AUDIO -> buildAudioTab(cfg, leftColX, rightColX, colW, rowY, stepY);
            case MICROPHONE -> buildMicrophoneTab(cfg, leftColX, rightColX, colW, rowY, stepY);
            case RECORDING -> buildRecordingTab(cfg, leftColX, rightColX, colW, rowY, stepY);
            case CONTROLS -> buildControlsTab(leftColX, colW, rowY);
            case PERFORMANCE -> buildPerformanceTab(cfg, leftColX, rightColX, colW, rowY, stepY);
        }

        // Save & Back Button
        this.addDrawableChild(new ThemeButton(
                this.width / 2 - 80, this.height - 32, 160, 22,
                Text.literal("SAVE & RETURN"),
                this::close,
                true
        ));
    }

    private void buildVideoTab(RecordingConfig cfg, int leftX, int rightX, int w, int y, int stepY) {
        this.addDrawableChild(new ThemeButton(leftX, y, w, 22,
                Text.literal("Resolution: " + cfg.resolutionPreset.label),
                () -> {
                    RecordingConfig.ResolutionPreset[] vals = RecordingConfig.ResolutionPreset.values();
                    cfg.resolutionPreset = vals[(cfg.resolutionPreset.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(rightX, y, w, 22,
                Text.literal("FPS: " + cfg.fpsPreset.label),
                () -> {
                    RecordingConfig.FpsPreset[] vals = RecordingConfig.FpsPreset.values();
                    cfg.fpsPreset = vals[(cfg.fpsPreset.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(leftX, y + stepY, w, 22,
                Text.literal("Quality: " + cfg.videoQuality.name()),
                () -> {
                    RecordingConfig.VideoQuality[] vals = RecordingConfig.VideoQuality.values();
                    cfg.videoQuality = vals[(cfg.videoQuality.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        List<String> encoders = List.of("libx264", "h264_nvenc", "h264_amf", "h264_qsv");
        this.addDrawableChild(new ThemeButton(rightX, y + stepY, w, 22,
                Text.literal("Encoder: " + cfg.encoder),
                () -> {
                    int idx = (encoders.indexOf(cfg.encoder) + 1) % encoders.size();
                    cfg.encoder = encoders.get(idx);
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        List<String> presets = List.of("ultrafast", "superfast", "veryfast", "faster", "fast", "medium");
        this.addDrawableChild(new ThemeButton(leftX, y + stepY * 2, w, 22,
                Text.literal("Preset: " + cfg.encoderPreset),
                () -> {
                    int idx = (presets.indexOf(cfg.encoderPreset) + 1) % presets.size();
                    cfg.encoderPreset = presets.get(idx);
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(rightX, y + stepY * 2, w, 22,
                Text.literal("Keyframe Int: " + cfg.keyframeInterval + "s"),
                () -> {
                    cfg.keyframeInterval = (cfg.keyframeInterval % 5) + 1;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        // Custom numeric fields
        customWidthField = new TextFieldWidget(this.textRenderer, leftX, y + stepY * 3 + 10, 72, 18, Text.literal("Width"));
        customWidthField.setText(String.valueOf(cfg.customWidth));
        this.addDrawableChild(customWidthField);

        customHeightField = new TextFieldWidget(this.textRenderer, leftX + 78, y + stepY * 3 + 10, 72, 18, Text.literal("Height"));
        customHeightField.setText(String.valueOf(cfg.customHeight));
        this.addDrawableChild(customHeightField);

        customFpsField = new TextFieldWidget(this.textRenderer, rightX, y + stepY * 3 + 10, 72, 18, Text.literal("Custom FPS"));
        customFpsField.setText(String.valueOf(cfg.customFps));
        this.addDrawableChild(customFpsField);

        customBitrateField = new TextFieldWidget(this.textRenderer, rightX + 78, y + stepY * 3 + 10, 72, 18, Text.literal("Bitrate kbps"));
        customBitrateField.setText(String.valueOf(cfg.customBitrateKbps));
        this.addDrawableChild(customBitrateField);
    }

    private void buildAudioTab(RecordingConfig cfg, int leftX, int rightX, int w, int y, int stepY) {
        this.addDrawableChild(new ThemeButton(leftX, y, w, 22,
                Text.literal("Game Audio: " + (cfg.gameAudioEnabled ? "ON" : "OFF")),
                () -> {
                    cfg.gameAudioEnabled = !cfg.gameAudioEnabled;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(rightX, y, w, 22,
                Text.literal("Game Volume: " + cfg.gameVolume + "%"),
                () -> {
                    cfg.gameVolume = (cfg.gameVolume + 10) > 100 ? 0 : cfg.gameVolume + 10;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(leftX, y + stepY, w, 22,
                Text.literal("Audio Quality: " + cfg.audioQuality.label),
                () -> {
                    RecordingConfig.AudioQuality[] vals = RecordingConfig.AudioQuality.values();
                    cfg.audioQuality = vals[(cfg.audioQuality.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        List<String> gameDevices = AudioCaptureManager.getAvailableGameAudioDevices();
        this.addDrawableChild(new ThemeButton(rightX, y + stepY, w, 22,
                Text.literal("Loopback: " + truncate(cfg.gameAudioDevice, 14)),
                () -> {
                    int idx = (gameDevices.indexOf(cfg.gameAudioDevice) + 1) % gameDevices.size();
                    cfg.gameAudioDevice = gameDevices.get(idx);
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));
    }

    private void buildMicrophoneTab(RecordingConfig cfg, int leftX, int rightX, int w, int y, int stepY) {
        this.addDrawableChild(new ThemeButton(leftX, y, w, 22,
                Text.literal("Microphone: " + (cfg.microphoneEnabled ? "ON" : "OFF")),
                () -> {
                    cfg.microphoneEnabled = !cfg.microphoneEnabled;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        List<String> micDevices = MicrophoneManager.getAvailableMicrophoneDevices();
        this.addDrawableChild(new ThemeButton(rightX, y, w, 22,
                Text.literal("Device: " + truncate(cfg.microphoneDevice, 15)),
                () -> {
                    int idx = (micDevices.indexOf(cfg.microphoneDevice) + 1) % micDevices.size();
                    cfg.microphoneDevice = micDevices.get(idx);
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(leftX, y + stepY, w, 22,
                Text.literal("Mic Volume: " + cfg.microphoneVolume + "%"),
                () -> {
                    cfg.microphoneVolume = (cfg.microphoneVolume + 10) > 100 ? 0 : cfg.microphoneVolume + 10;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(rightX, y + stepY, w, 22,
                Text.literal(String.format("Mic Gain: +%.0f dB", cfg.microphoneGainDb)),
                () -> {
                    cfg.microphoneGainDb = (cfg.microphoneGainDb + 3.0f) > 18.0f ? 0.0f : cfg.microphoneGainDb + 3.0f;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(leftX, y + stepY * 2, w, 22,
                Text.literal("Noise Suppression: " + (cfg.noiseSuppression ? "ON" : "OFF")),
                () -> {
                    cfg.noiseSuppression = !cfg.noiseSuppression;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(rightX, y + stepY * 2, w, 22,
                Text.literal("Noise Gate: " + (cfg.noiseGate ? "ON" : "OFF")),
                () -> {
                    cfg.noiseGate = !cfg.noiseGate;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(leftX, y + stepY * 3, w, 22,
                Text.literal("Mic Monitoring: " + (cfg.micMonitoring ? "ON" : "OFF")),
                () -> {
                    cfg.micMonitoring = !cfg.micMonitoring;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));
    }

    private void buildRecordingTab(RecordingConfig cfg, int leftX, int rightX, int w, int y, int stepY) {
        this.addDrawableChild(new ThemeButton(leftX, y, w, 22,
                Text.literal("REC Indicator: " + (cfg.showIndicator ? "ON" : "OFF")),
                () -> {
                    cfg.showIndicator = !cfg.showIndicator;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(rightX, y, w, 22,
                Text.literal("Position: " + cfg.indicatorPosition.label),
                () -> {
                    RecordingConfig.IndicatorPosition[] vals = RecordingConfig.IndicatorPosition.values();
                    cfg.indicatorPosition = vals[(cfg.indicatorPosition.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        filePrefixField = new TextFieldWidget(this.textRenderer, leftX, y + stepY + 14, w, 18, Text.literal("File Prefix"));
        filePrefixField.setText(cfg.fileNamingPrefix);
        this.addDrawableChild(filePrefixField);
    }

    private void buildControlsTab(int leftX, int w, int y) {
        this.addDrawableChild(new ThemeButton(this.width / 2 - 110, y + 12, 220, 26,
                Text.literal("OPEN MINECRAFT KEYBINDS MENU"),
                () -> this.client.setScreen(new KeybindsScreen(this, this.client.options)),
                true
        ));
    }

    private void buildPerformanceTab(RecordingConfig cfg, int leftX, int rightX, int w, int y, int stepY) {
        this.addDrawableChild(new ThemeButton(leftX, y, w, 22,
                Text.literal("Encoder Priority: " + cfg.encoderPriority.label),
                () -> {
                    RecordingConfig.EncoderPriority[] vals = RecordingConfig.EncoderPriority.values();
                    cfg.encoderPriority = vals[(cfg.encoderPriority.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(rightX, y, w, 22,
                Text.literal("Frame Buffer Queue: " + cfg.maxFrameQueueSize),
                () -> {
                    cfg.maxFrameQueueSize = (cfg.maxFrameQueueSize >= 24) ? 6 : cfg.maxFrameQueueSize + 6;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(leftX, y + stepY, w, 22,
                Text.literal("Overload Frame Drop: " + (cfg.dropFramesWhenFull ? "ON" : "OFF")),
                () -> {
                    cfg.dropFramesWhenFull = !cfg.dropFramesWhenFull;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));
    }

    private void saveTextFields() {
        RecordingConfig cfg = RecordingConfig.get();
        try {
            if (customWidthField != null) cfg.customWidth = Integer.parseInt(customWidthField.getText().trim());
            if (customHeightField != null) cfg.customHeight = Integer.parseInt(customHeightField.getText().trim());
            if (customFpsField != null) cfg.customFps = Integer.parseInt(customFpsField.getText().trim());
            if (customBitrateField != null) cfg.customBitrateKbps = Integer.parseInt(customBitrateField.getText().trim());
            if (filePrefixField != null && !filePrefixField.getText().isBlank()) {
                cfg.fileNamingPrefix = filePrefixField.getText().trim();
            }
        } catch (NumberFormatException ignored) {}
        RecordingConfig.save();
    }

    private String truncate(String s, int max) {
        if (s == null) return "Default";
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xE608080A);

        int panelW = 350;
        int panelH = this.height - 44;
        int px = (this.width - panelW) / 2;
        int py = 18;

        context.fill(px, py, px + panelW, py + panelH, 0xFF101014);
        context.drawBorder(px, py, panelW, panelH, 0xFF8E1616);

        if (activeCategory == Category.VIDEO) {
            int labelY = 66 + 28 * 3;
            context.drawTextWithShadow(this.textRenderer, "Custom Width / Height:", this.width / 2 - 155, labelY, 0xFFB0B0B5);
            context.drawTextWithShadow(this.textRenderer, "Custom FPS / Bitrate(kbps):", this.width / 2 + 5, labelY, 0xFFB0B0B5);
        } else if (activeCategory == Category.RECORDING) {
            context.drawTextWithShadow(this.textRenderer, "File Naming Prefix:", this.width / 2 - 155, 66 + 28 + 3, 0xFFB0B0B5);
            context.drawTextWithShadow(this.textRenderer, "Output Folder: .minecraft/" + RecordingConfig.get().outputFolderName + "/", this.width / 2 - 155, 66 + 28 * 3, 0xFFB0B0B5);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        saveTextFields();
        this.client.setScreen(parent);
    }
}
