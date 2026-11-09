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
 * Authentic OBS Studio 30 Settings Dialog with Left Vertical Category Sidebar,
 * Real-Time Connected Microphone Detection, and Responsive Mobile/PC Scaling.
 */
public class RecorderSettingsScreen extends Screen {
    public enum Category {
        GENERAL("General", UIHelper.Icon.SETTINGS),
        OUTPUT("Output", UIHelper.Icon.RECORD),
        AUDIO("Audio", UIHelper.Icon.AUDIO),
        VIDEO("Video", UIHelper.Icon.VIDEO),
        HOTKEYS("Hotkeys", UIHelper.Icon.FILTERS),
        ADVANCED("Advanced", UIHelper.Icon.PERFORMANCE),
        // Aliases for compatibility
        RECORDING("Output", UIHelper.Icon.RECORD),
        MICROPHONE("Audio", UIHelper.Icon.MICROPHONE),
        CONTROLS("Hotkeys", UIHelper.Icon.FILTERS),
        PERFORMANCE("Advanced", UIHelper.Icon.PERFORMANCE);

        public final String title;
        public final UIHelper.Icon icon;
        Category(String title, UIHelper.Icon icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private static final Category[] SIDEBAR_TABS = {
            Category.GENERAL, Category.OUTPUT, Category.AUDIO, Category.VIDEO, Category.HOTKEYS, Category.ADVANCED
    };

    private final Screen parent;
    private Category activeCategory;

    private TextFieldWidget customWidthField;
    private TextFieldWidget customHeightField;
    private TextFieldWidget customFpsField;
    private TextFieldWidget customBitrateField;
    private TextFieldWidget filePrefixField;

    public RecorderSettingsScreen(Screen parent, Category initialCategory) {
        super(Text.literal("Settings"));
        this.parent = parent;
        this.activeCategory = normalizeCategory(initialCategory);
    }

    private Category normalizeCategory(Category c) {
        return switch (c) {
            case RECORDING -> Category.OUTPUT;
            case MICROPHONE -> Category.AUDIO;
            case CONTROLS -> Category.HOTKEYS;
            case PERFORMANCE -> Category.ADVANCED;
            default -> c;
        };
    }

    @Override
    protected void init() {
        RecordingConfig cfg = RecordingConfig.get();

        int winW = Math.min(420, this.width - 12);
        int winH = Math.min(250, this.height - 12);
        int wx = (this.width - winW) / 2;
        int wy = (this.height - winH) / 2;

        int sidebarW = Math.min(100, Math.max(76, winW / 4));
        int sideX = wx + 6;
        int sideY = wy + 20;

        // Left Sidebar Category Buttons (OBS Yami Style)
        for (int i = 0; i < SIDEBAR_TABS.length; i++) {
            Category tab = SIDEBAR_TABS[i];
            boolean selected = (tab == activeCategory);
            this.addDrawableChild(new ThemeButton(
                    sideX, sideY + i * 24, sidebarW, 21,
                    tab.icon, Text.literal(tab.title),
                    () -> {
                        saveTextFields();
                        this.activeCategory = tab;
                        this.clearAndInit();
                    },
                    selected
            ));
        }

        // Right Content Pane Coordinates
        int contentX = sideX + sidebarW + 10;
        int contentW = winW - sidebarW - 22;
        int colW = (contentW - 6) / 2;
        int rightColX = contentX + colW + 6;
        int startY = wy + 24;
        int stepY = 26;

        switch (activeCategory) {
            case GENERAL -> buildGeneralPane(cfg, contentX, rightColX, colW, startY, stepY);
            case OUTPUT -> buildOutputPane(cfg, contentX, rightColX, colW, startY, stepY);
            case AUDIO -> buildAudioPane(cfg, contentX, rightColX, colW, startY, stepY);
            case VIDEO -> buildVideoPane(cfg, contentX, rightColX, colW, startY, stepY);
            case HOTKEYS -> buildHotkeysPane(contentX, contentW, startY);
            case ADVANCED -> buildAdvancedPane(cfg, contentX, rightColX, colW, startY, stepY);
            default -> buildGeneralPane(cfg, contentX, rightColX, colW, startY, stepY);
        }

        // Bottom Right OK / Cancel / Apply Buttons
        int btnY = wy + winH - 24;
        this.addDrawableChild(new ThemeButton(wx + winW - 140, btnY, 64, 18,
                Text.literal("OK"), this::close, true));
        this.addDrawableChild(new ThemeButton(wx + winW - 72, btnY, 64, 18,
                Text.literal("Apply"), this::saveTextFields));
    }

    private void buildGeneralPane(RecordingConfig cfg, int lx, int rx, int w, int y, int stepY) {
        this.addDrawableChild(new ThemeButton(lx, y, w, 20,
                Text.literal("Theme: Yami Dark"), () -> {}));

        this.addDrawableChild(new ThemeButton(rx, y, w, 20,
                Text.literal("HUD REC: " + (cfg.showIndicator ? "ON" : "OFF")),
                () -> {
                    cfg.showIndicator = !cfg.showIndicator;
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(lx, y + stepY, w, 20,
                Text.literal("Corner: " + cfg.indicatorPosition.label),
                () -> {
                    RecordingConfig.IndicatorPosition[] vals = RecordingConfig.IndicatorPosition.values();
                    cfg.indicatorPosition = vals[(cfg.indicatorPosition.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }));
    }

    private void buildOutputPane(RecordingConfig cfg, int lx, int rx, int w, int y, int stepY) {
        this.addDrawableChild(new ThemeButton(lx, y, w, 20,
                Text.literal("Quality: " + cfg.videoQuality.name()),
                () -> {
                    RecordingConfig.VideoQuality[] vals = RecordingConfig.VideoQuality.values();
                    cfg.videoQuality = vals[(cfg.videoQuality.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(rx, y, w, 20,
                Text.literal("Encoder: H.264 MP4"), () -> {}));

        List<String> presets = List.of("ultrafast", "superfast", "veryfast", "faster", "fast");
        this.addDrawableChild(new ThemeButton(lx, y + stepY, w, 20,
                Text.literal("Preset: " + cfg.encoderPreset),
                () -> {
                    int idx = (presets.indexOf(cfg.encoderPreset) + 1) % presets.size();
                    cfg.encoderPreset = presets.get(idx);
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(rx, y + stepY, w, 20,
                Text.literal("Format: .mp4 (AVC1)"), () -> {}));

        customBitrateField = new TextFieldWidget(this.textRenderer, lx, y + stepY * 2 + 12, w, 18, Text.literal("Bitrate"));
        customBitrateField.setText(String.valueOf(cfg.customBitrateKbps));
        this.addDrawableChild(customBitrateField);

        filePrefixField = new TextFieldWidget(this.textRenderer, rx, y + stepY * 2 + 12, w, 18, Text.literal("Prefix"));
        filePrefixField.setText(cfg.fileNamingPrefix);
        this.addDrawableChild(filePrefixField);
    }

    private void buildAudioPane(RecordingConfig cfg, int lx, int rx, int w, int y, int stepY) {
        this.addDrawableChild(new ThemeButton(lx, y, w, 20,
                Text.literal("Desktop Audio: " + (cfg.gameAudioEnabled ? "ON" : "OFF")),
                () -> {
                    cfg.gameAudioEnabled = !cfg.gameAudioEnabled;
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(rx, y, w, 20,
                Text.literal("Desktop Vol: " + cfg.gameVolume + "%"),
                () -> {
                    cfg.gameVolume = (cfg.gameVolume + 10) > 100 ? 0 : cfg.gameVolume + 10;
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        // Live Connected Microphone Hardware Selector
        List<String> micDevices = MicrophoneManager.getAvailableMicrophoneDevices();
        this.addDrawableChild(new ThemeButton(lx, y + stepY, w, 20,
                UIHelper.Icon.MICROPHONE, Text.literal("Mic: " + (cfg.microphoneEnabled ? "ON" : "OFF")),
                () -> {
                    cfg.microphoneEnabled = !cfg.microphoneEnabled;
                    RecordingConfig.save();
                    this.clearAndInit();
                }, cfg.microphoneEnabled));

        this.addDrawableChild(new ThemeButton(rx, y + stepY, w, 20,
                Text.literal("Input: " + truncate(cfg.microphoneDevice, 12)),
                () -> {
                    List<String> liveMics = MicrophoneManager.getAvailableMicrophoneDevices();
                    int idx = (liveMics.indexOf(cfg.microphoneDevice) + 1) % liveMics.size();
                    cfg.microphoneDevice = liveMics.get(idx);
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(lx, y + stepY * 2, w, 20,
                Text.literal("Noise Supp: " + (cfg.noiseSuppression ? "ON" : "OFF")),
                () -> {
                    cfg.noiseSuppression = !cfg.noiseSuppression;
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(rx, y + stepY * 2, w, 20,
                Text.literal("Noise Gate: " + (cfg.noiseGate ? "ON" : "OFF")),
                () -> {
                    cfg.noiseGate = !cfg.noiseGate;
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(lx, y + stepY * 3, w, 20,
                Text.literal(String.format("Mic Gain: +%.0f dB", cfg.microphoneGainDb)),
                () -> {
                    cfg.microphoneGainDb = (cfg.microphoneGainDb + 3.0f) > 18.0f ? 0.0f : cfg.microphoneGainDb + 3.0f;
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(rx, y + stepY * 3, w, 20,
                Text.literal("Bitrate: " + cfg.audioQuality.bitrateKbps + "k"),
                () -> {
                    RecordingConfig.AudioQuality[] vals = RecordingConfig.AudioQuality.values();
                    cfg.audioQuality = vals[(cfg.audioQuality.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }));
    }

    private void buildVideoPane(RecordingConfig cfg, int lx, int rx, int w, int y, int stepY) {
        this.addDrawableChild(new ThemeButton(lx, y, w, 20,
                Text.literal("Output Res: " + cfg.resolutionPreset.label),
                () -> {
                    RecordingConfig.ResolutionPreset[] vals = RecordingConfig.ResolutionPreset.values();
                    cfg.resolutionPreset = vals[(cfg.resolutionPreset.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(rx, y, w, 20,
                Text.literal("FPS Values: " + cfg.fpsPreset.label),
                () -> {
                    RecordingConfig.FpsPreset[] vals = RecordingConfig.FpsPreset.values();
                    cfg.fpsPreset = vals[(cfg.fpsPreset.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        customWidthField = new TextFieldWidget(this.textRenderer, lx, y + stepY + 12, w / 2 - 2, 18, Text.literal("Width"));
        customWidthField.setText(String.valueOf(cfg.customWidth));
        this.addDrawableChild(customWidthField);

        customHeightField = new TextFieldWidget(this.textRenderer, lx + w / 2 + 2, y + stepY + 12, w / 2 - 2, 18, Text.literal("Height"));
        customHeightField.setText(String.valueOf(cfg.customHeight));
        this.addDrawableChild(customHeightField);

        customFpsField = new TextFieldWidget(this.textRenderer, rx, y + stepY + 12, w, 18, Text.literal("Custom FPS"));
        customFpsField.setText(String.valueOf(cfg.customFps));
        this.addDrawableChild(customFpsField);
    }

    private void buildHotkeysPane(int lx, int contentW, int y) {
        this.addDrawableChild(new ThemeButton(lx, y + 10, contentW, 24,
                UIHelper.Icon.SETTINGS, Text.literal("Configure OBS Keybinds in Minecraft Controls"),
                () -> this.client.setScreen(new KeybindsScreen(this, this.client.options)),
                true));
    }

    private void buildAdvancedPane(RecordingConfig cfg, int lx, int rx, int w, int y, int stepY) {
        this.addDrawableChild(new ThemeButton(lx, y, w, 20,
                Text.literal("Priority: " + cfg.encoderPriority.label),
                () -> {
                    RecordingConfig.EncoderPriority[] vals = RecordingConfig.EncoderPriority.values();
                    cfg.encoderPriority = vals[(cfg.encoderPriority.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(rx, y, w, 20,
                Text.literal("Buffer Queue: " + cfg.maxFrameQueueSize),
                () -> {
                    cfg.maxFrameQueueSize = (cfg.maxFrameQueueSize >= 24) ? 6 : cfg.maxFrameQueueSize + 6;
                    RecordingConfig.save();
                    this.clearAndInit();
                }));
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
        context.fill(0, 0, this.width, this.height, 0xB3090A0D);

        int winW = Math.min(420, this.width - 12);
        int winH = Math.min(250, this.height - 12);
        int wx = (this.width - winW) / 2;
        int wy = (this.height - winH) / 2;
        int sidebarW = Math.min(100, Math.max(76, winW / 4));

        // OBS Settings Window Background
        context.fill(wx, wy, wx + winW, wy + winH, 0xFF1D1F26);
        UIHelper.drawBorder(context, wx, wy, winW, winH, 0xFF3A3E4B);

        // Top Window Header
        context.fill(wx + 1, wy + 1, wx + winW - 1, wy + 16, 0xFF2B2E38);
        context.drawTextWithShadow(this.textRenderer, "Settings — " + activeCategory.title, wx + 8, wy + 4, 0xFFFFFFFF);

        // Left Sidebar Background Box
        context.fill(wx + 4, wy + 18, wx + sidebarW + 8, wy + winH - 6, 0xFF15171C);
        UIHelper.drawBorder(context, wx + 4, wy + 18, sidebarW + 4, winH - 24, 0xFF2D313D);

        // Right Content Area Box
        int cx = wx + sidebarW + 12;
        int cw = winW - sidebarW - 16;
        context.fill(cx, wy + 18, wx + winW - 4, wy + winH - 28, 0xFF23262E);
        UIHelper.drawBorder(context, cx, wy + 18, cw, winH - 46, 0xFF323644);

        if (activeCategory == Category.VIDEO) {
            context.drawTextWithShadow(this.textRenderer, "Custom Width / Height", cx + 4, wy + 24 + 26 + 2, 0xFFBDC1C6);
            context.drawTextWithShadow(this.textRenderer, "Custom FPS", cx + cw / 2 + 4, wy + 24 + 26 + 2, 0xFFBDC1C6);
        } else if (activeCategory == Category.OUTPUT) {
            context.drawTextWithShadow(this.textRenderer, "Bitrate (kbps)", cx + 4, wy + 24 + 52 + 2, 0xFFBDC1C6);
            context.drawTextWithShadow(this.textRenderer, "Filename Prefix", cx + cw / 2 + 4, wy + 24 + 52 + 2, 0xFFBDC1C6);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        saveTextFields();
        this.client.setScreen(parent);
    }
}
