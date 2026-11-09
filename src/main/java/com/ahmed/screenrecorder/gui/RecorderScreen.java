package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.capture.RecordingManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Full Responsive OBS Studio 30 UI Layout with Custom Vector Icons (No Unicode Emojis).
 */
public class RecorderScreen extends Screen {
    private final Screen parent;
    private ThemeButton startRecBtn;

    public RecorderScreen(Screen parent) {
        super(Text.literal("OBS 30.1.2 - Profile: Minecraft - Scenes: In-Game Capture"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RecordingManager mgr = RecordingManager.getInstance();
        RecordingConfig cfg = RecordingConfig.get();

        int margin = Math.max(4, this.width / 80);
        int bottomBarH = 14;
        int dockAreaH = Math.max(84, Math.min(132, (int) (this.height * 0.38f)));
        int dockY = this.height - bottomBarH - dockAreaH - 2;
        int toolbarY = dockY - 18;

        // Middle Toolbar Buttons with Custom Vector Icons
        int tbBtnW = Math.min(78, Math.max(52, (this.width - 130) / 4));
        int tbStartX = margin + 105;
        this.addDrawableChild(new ThemeButton(tbStartX, toolbarY + 2, tbBtnW, 14,
                UIHelper.Icon.SETTINGS, Text.literal("Properties"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.VIDEO))));
        this.addDrawableChild(new ThemeButton(tbStartX + tbBtnW + 4, toolbarY + 2, tbBtnW, 14,
                UIHelper.Icon.FILTERS, Text.literal("Filters"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.MICROPHONE))));
        this.addDrawableChild(new ThemeButton(tbStartX + (tbBtnW + 4) * 2, toolbarY + 2, tbBtnW, 14,
                UIHelper.Icon.AUDIO, Text.literal("Audio"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.AUDIO))));
        this.addDrawableChild(new ThemeButton(tbStartX + (tbBtnW + 4) * 3, toolbarY + 2, tbBtnW, 14,
                UIHelper.Icon.PERFORMANCE, Text.literal("Perf"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.PERFORMANCE))));

        // 5 Bottom Docks Geometry
        int totalDockW = this.width - margin * 2;
        int gap = 3;
        int colW = (totalDockW - gap * 4) / 5;
        int controlsX = margin + (colW + gap) * 4;
        int controlsW = totalDockW - (colW + gap) * 4;

        // Controls Dock (Rightmost Column - 6 Stacked OBS Buttons with Custom Icons)
        int btnX = controlsX + 4;
        int btnW = controlsW - 8;
        int availBtnH = dockAreaH - 18;
        int btnH = Math.max(11, (availBtnH - 10) / 6);
        int stepY = btnH + 2;
        int curY = dockY + 15;

        startRecBtn = new ThemeButton(btnX, curY, btnW, btnH,
                mgr.isRecording() ? UIHelper.Icon.STOP : UIHelper.Icon.RECORD,
                Text.literal(mgr.isRecording() ? "Stop Recording" : "Start Recording"),
                () -> {
                    mgr.toggleRecording();
                    this.clearAndInit();
                },
                mgr.isRecording()
        );
        this.addDrawableChild(startRecBtn);

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY, btnW, btnH,
                UIHelper.Icon.FOLDER, Text.literal("Recordings"),
                () -> this.client.setScreen(new RecordingsScreen(this))
        ));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 2, btnW, btnH,
                UIHelper.Icon.MICROPHONE, Text.literal("Mic: " + (cfg.microphoneEnabled ? "ON" : "OFF")),
                () -> {
                    cfg.microphoneEnabled = !cfg.microphoneEnabled;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 3, btnW, btnH,
                UIHelper.Icon.VIDEO, Text.literal(cfg.resolutionPreset.label),
                () -> {
                    RecordingConfig.ResolutionPreset[] vals = RecordingConfig.ResolutionPreset.values();
                    cfg.resolutionPreset = vals[(cfg.resolutionPreset.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 4, btnW, btnH,
                UIHelper.Icon.SETTINGS, Text.literal("Settings"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.VIDEO))
        ));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 5, btnW, btnH,
                UIHelper.Icon.CLOSE, Text.literal("Exit"),
                this::close
        ));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RecordingManager mgr = RecordingManager.getInstance();
        RecordingConfig cfg = RecordingConfig.get();

        // 1. OBS Studio Dark Slate Background
        context.fill(0, 0, this.width, this.height, 0xFF1D1F26);

        // 2. Top Window Title Bar
        context.fill(0, 0, this.width, 14, 0xFF2B2E38);
        context.fill(6, 4, 11, 9, 0xFFFF5F56);
        context.fill(14, 4, 19, 9, 0xFFFFBD2E);
        context.fill(22, 4, 27, 9, 0xFF27C93F);
        String titleStr = "OBS 30.1.2 - Profile: " + cfg.getTargetWidth() + "x" + cfg.getTargetHeight() + " - Scenes: Minecraft Framebuffer";
        context.drawCenteredTextWithShadow(this.textRenderer, titleStr, this.width / 2, 3, 0xFFDADCE0);

        int margin = Math.max(4, this.width / 80);
        int bottomBarH = 14;
        int dockAreaH = Math.max(84, Math.min(132, (int) (this.height * 0.38f)));
        int dockY = this.height - bottomBarH - dockAreaH - 2;
        int toolbarY = dockY - 18;

        // 3. Upper 16:9 Live Preview Canvas Area
        int previewAreaY = 16;
        int previewAreaH = Math.max(40, toolbarY - previewAreaY - 2);
        int previewAreaW = this.width - margin * 2;
        context.fill(margin, previewAreaY, margin + previewAreaW, previewAreaY + previewAreaH, 0xFF131519);

        int canvasH = previewAreaH - 6;
        int canvasW = (canvasH * 16) / 9;
        if (canvasW > previewAreaW - 12) {
            canvasW = previewAreaW - 12;
            canvasH = (canvasW * 9) / 16;
        }
        int canvasX = (this.width - canvasW) / 2;
        int canvasY = previewAreaY + (previewAreaH - canvasH) / 2;

        context.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF000000);
        UIHelper.drawBorder(context, canvasX, canvasY, canvasW, canvasH, mgr.isRecording() ? 0xFFE53935 : 0xFF3A3E4B);

        String previewTitle = mgr.isRecording() ? "RECORDING LIVE (" + mgr.getFormattedDuration() + ")" : "LIVE GAMEPLAY CAPTURE PREVIEW";
        context.drawCenteredTextWithShadow(this.textRenderer, previewTitle, this.width / 2, canvasY + canvasH / 2 - 10, mgr.isRecording() ? 0xFFFF5252 : 0xFFFFFFFF);
        String subInfo = cfg.getTargetWidth() + "x" + cfg.getTargetHeight() + " @ " + cfg.getTargetFps() + " FPS | In-Built MP4 Engine Ready";
        context.drawCenteredTextWithShadow(this.textRenderer, subInfo, this.width / 2, canvasY + canvasH / 2 + 4, 0xFF9AA0A6);

        // 4. Middle Context Toolbar
        context.fill(margin, toolbarY, this.width - margin, toolbarY + 16, 0xFF262932);
        UIHelper.drawBorder(context, margin, toolbarY, this.width - margin * 2, 16, 0xFF353844);
        UIHelper.drawIcon(context, UIHelper.Icon.VIDEO, margin + 5, toolbarY + 4, 0xFFE53935);
        context.drawTextWithShadow(this.textRenderer, "Game Capture", margin + 16, toolbarY + 4, 0xFFDADCE0);

        // 5. Lower 5-Dock System (Scenes | Sources | Audio Mixer | Scene Transitions | Controls)
        int totalDockW = this.width - margin * 2;
        int gap = 3;
        int colW = (totalDockW - gap * 4) / 5;

        String[] dockTitles = {"Scenes", "Sources", "Audio Mixer", "Scene Transitions", "Controls"};
        for (int i = 0; i < 5; i++) {
            int dx = margin + i * (colW + gap);
            int dw = (i == 4) ? (totalDockW - (colW + gap) * 4) : colW;

            context.fill(dx, dockY, dx + dw, dockY + dockAreaH, 0xFF23262E);
            context.fill(dx, dockY, dx + dw, dockY + 13, 0xFF2D303B);
            UIHelper.drawBorder(context, dx, dockY, dw, dockAreaH, 0xFF383C4A);
            context.drawTextWithShadow(this.textRenderer, dockTitles[i], dx + 4, dockY + 3, 0xFFE8EAED);

            if (i == 0) {
                context.fill(dx + 2, dockY + 15, dx + dw - 2, dockY + 28, 0xFF2865C7);
                context.drawTextWithShadow(this.textRenderer, "Main Scene", dx + 6, dockY + 18, 0xFFFFFFFF);
                drawCustomDockToolbar(context, dx + 2, dockY + dockAreaH - 14, dw - 4);
            } else if (i == 1) {
                context.fill(dx + 2, dockY + 15, dx + dw - 2, dockY + 28, 0xFF2F3441);
                UIHelper.drawIcon(context, UIHelper.Icon.VIDEO, dx + 5, dockY + 18, 0xFFFFFFFF);
                context.drawTextWithShadow(this.textRenderer, "Game Screen", dx + 16, dockY + 18, 0xFFFFFFFF);

                UIHelper.drawIcon(context, UIHelper.Icon.AUDIO, dx + 5, dockY + 31, cfg.gameAudioEnabled ? 0xFFB0B5BA : 0xFF666666);
                context.drawTextWithShadow(this.textRenderer, "Game Audio", dx + 16, dockY + 31, cfg.gameAudioEnabled ? 0xFFB0B5BA : 0xFF666666);
                drawCustomDockToolbar(context, dx + 2, dockY + dockAreaH - 14, dw - 4);
            } else if (i == 2) {
                renderAudioMixerDock(context, dx + 4, dockY + 16, dw - 8, cfg);
            } else if (i == 3) {
                context.fill(dx + 4, dockY + 17, dx + dw - 4, dockY + 29, 0xFF323642);
                UIHelper.drawBorder(context, dx + 4, dockY + 17, dw - 8, 12, 0xFF454A59);
                context.drawTextWithShadow(this.textRenderer, "Fade", dx + 7, dockY + 19, 0xFFFFFFFF);

                context.drawTextWithShadow(this.textRenderer, "Duration", dx + 4, dockY + 34, 0xFFBDC1C6);
                context.fill(dx + 4, dockY + 45, dx + dw - 4, dockY + 57, 0xFF191B21);
                context.drawTextWithShadow(this.textRenderer, "300 ms", dx + 7, dockY + 47, 0xFFFFFFFF);
            }
        }

        // 6. Bottom OBS Status Bar
        int sbY = this.height - bottomBarH;
        context.fill(0, sbY, this.width, this.height, 0xFF282B34);
        String statusLeft = mgr.getLastStatusMessage();
        context.drawTextWithShadow(this.textRenderer, statusLeft, 6, sbY + 3, mgr.isLastStatusError() ? 0xFFFF5252 : 0xFFBDC1C6);

        int currentFps = MinecraftClient.getInstance().getCurrentFps();
        String statsRight = String.format("REC: %s | %d / %d.00 FPS", mgr.getFormattedDuration(), currentFps, cfg.getTargetFps());
        int rw = this.textRenderer.getWidth(statsRight);
        context.drawTextWithShadow(this.textRenderer, statsRight, this.width - rw - 6, sbY + 3, 0xFFE8EAED);

        super.render(context, mouseX, mouseY, delta);
        RecordingIndicator.renderCleanOverlay(context);
    }

    private void drawCustomDockToolbar(DrawContext context, int x, int y, int w) {
        context.fill(x, y, x + w, y + 12, 0xFF292C36);
        int step = Math.max(10, Math.min(16, w / 5));
        UIHelper.drawIcon(context, UIHelper.Icon.PLUS, x + 3, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.MINUS, x + 3 + step, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.SETTINGS, x + 3 + step * 2, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.UP, x + 3 + step * 3, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.DOWN, x + 3 + step * 4, y + 2, 0xFFBDC1C6);
    }

    private void renderAudioMixerDock(DrawContext context, int x, int y, int w, RecordingConfig cfg) {
        context.drawTextWithShadow(this.textRenderer, "Desktop Audio", x, y, 0xFFE8EAED);
        drawDbMeterBar(context, x, y + 10, w, cfg.gameAudioEnabled ? (cfg.gameVolume / 100.0f) : 0.0f);
        drawBlueVolumeSlider(context, x, y + 17, w, cfg.gameVolume / 100.0f);

        int micY = y + 30;
        context.drawTextWithShadow(this.textRenderer, "Mic/Aux", x, micY, 0xFFE8EAED);
        drawDbMeterBar(context, x, micY + 10, w, cfg.microphoneEnabled ? (cfg.microphoneVolume / 100.0f) : 0.0f);
        drawBlueVolumeSlider(context, x, micY + 17, w, cfg.microphoneVolume / 100.0f);
    }

    private void drawDbMeterBar(DrawContext context, int x, int y, int w, float activeRatio) {
        context.fill(x, y, x + w, y + 4, 0xFF14161B);
        int activeW = (int) (w * activeRatio);
        int greenEnd = (int) (w * 0.70f);
        int yellowEnd = (int) (w * 0.88f);

        if (activeW > 0) {
            context.fill(x, y, x + Math.min(activeW, greenEnd), y + 4, 0xFF2ECC71);
        }
        if (activeW > greenEnd) {
            context.fill(x + greenEnd, y, x + Math.min(activeW, yellowEnd), y + 4, 0xFFF1C40F);
        }
        if (activeW > yellowEnd) {
            context.fill(x + yellowEnd, y, x + activeW, y + 4, 0xFFE74C3C);
        }
    }

    private void drawBlueVolumeSlider(DrawContext context, int x, int y, int w, float percent) {
        context.fill(x, y + 1, x + w, y + 3, 0xFF3A3F4D);
        int fillW = (int) (w * Math.max(0.0f, Math.min(1.0f, percent)));
        context.fill(x, y + 1, x + fillW, y + 3, 0xFF3B82F6);
        context.fill(x + Math.max(0, fillW - 2), y - 1, x + Math.min(w, fillW + 2), y + 5, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
