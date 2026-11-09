package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.capture.RecordingManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Complete OBS Studio 30 Interface:
 * - Real-time Live Display Capture Preview
 * - Pause/Resume Recording Button next to Start/Stop Recording
 * - Instant Screenshot & Live Stats Overlay (CPU/RAM, Disk Space, FPS, Dropped Frames)
 * - Interactive Scenes, Sources, Audio Mixer Sliders, and Transitions
 */
public class RecorderScreen extends Screen {
    private final Screen parent;
    private static final List<String> SCENES = new ArrayList<>(Arrays.asList("Main Scene", "Display Capture", "Gameplay Only"));
    private static int selectedScene = 0;
    private static int selectedSource = 0;
    private static boolean studioMode = false;
    private static boolean showStatsWindow = false;

    private boolean draggingDesktopVol = false;
    private boolean draggingMicVol = false;

    public RecorderScreen(Screen parent) {
        super(Text.literal("OBS"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RecordingManager mgr = RecordingManager.getInstance();
        RecordingConfig cfg = RecordingConfig.get();

        int margin = Math.max(4, this.width / 90);
        int bottomBarH = 14;
        int dockAreaH = Math.max(86, Math.min(128, (int) (this.height * 0.36f)));
        int dockY = this.height - bottomBarH - dockAreaH - 2;
        int toolbarY = dockY - 18;

        // Context Toolbar: Properties | Filters | Screenshot | Stats
        int tbX = margin + 92;
        this.addDrawableChild(new ThemeButton(tbX, toolbarY + 2, 62, 12,
                UIHelper.Icon.SETTINGS, Text.literal("Properties"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.VIDEO))));

        this.addDrawableChild(new ThemeButton(tbX + 65, toolbarY + 2, 52, 12,
                UIHelper.Icon.FILTERS, Text.literal("Filters"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.AUDIO))));

        this.addDrawableChild(new ThemeButton(tbX + 120, toolbarY + 2, 62, 12,
                UIHelper.Icon.VIDEO, Text.literal("Screenshot"),
                mgr::takeScreenshot));

        this.addDrawableChild(new ThemeButton(tbX + 185, toolbarY + 2, 48, 12,
                UIHelper.Icon.PERFORMANCE, Text.literal("Stats"),
                () -> {
                    showStatsWindow = !showStatsWindow;
                    this.clearAndInit();
                }, showStatsWindow));

        int totalDockW = this.width - margin * 2;
        int gap = 3;
        int colW = (totalDockW - gap * 4) / 5;
        int controlsX = margin + (colW + gap) * 4;
        int controlsW = totalDockW - (colW + gap) * 4;

        // Controls Dock (6 Rows, Row 1 splits into Stop + Pause when recording!)
        int btnX = controlsX + 4;
        int btnW = controlsW - 8;
        int availBtnH = dockAreaH - 17;
        int btnH = Math.max(11, (availBtnH - 10) / 6);
        int stepY = btnH + 2;
        int curY = dockY + 15;

        if (mgr.isRecording()) {
            int stopW = btnW - 20;
            this.addDrawableChild(new ThemeButton(btnX, curY, stopW, btnH,
                    UIHelper.Icon.STOP, Text.literal("Stop Rec"),
                    () -> {
                        mgr.stopRecording();
                        this.clearAndInit();
                    }, true));
            this.addDrawableChild(new ThemeButton(btnX + stopW + 2, curY, 18, btnH,
                    mgr.isPaused() ? UIHelper.Icon.PLAY : UIHelper.Icon.PAUSE, Text.empty(),
                    () -> {
                        mgr.togglePause();
                        this.clearAndInit();
                    }, mgr.isPaused()));
        } else {
            this.addDrawableChild(new ThemeButton(btnX, curY, btnW, btnH,
                    UIHelper.Icon.RECORD, Text.literal("Start Recording"),
                    () -> {
                        mgr.startRecording();
                        this.clearAndInit();
                    }, false));
        }

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY, btnW, btnH,
                UIHelper.Icon.FOLDER, Text.literal("Recordings"),
                () -> this.client.setScreen(new RecordingsScreen(this))));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 2, btnW, btnH,
                UIHelper.Icon.MICROPHONE, Text.literal("Mic: " + (cfg.microphoneEnabled ? "ON" : "OFF")),
                () -> {
                    cfg.microphoneEnabled = !cfg.microphoneEnabled;
                    RecordingConfig.save();
                    this.clearAndInit();
                }, cfg.microphoneEnabled));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 3, btnW, btnH,
                UIHelper.Icon.VIDEO, Text.literal("Studio Mode"),
                () -> {
                    studioMode = !studioMode;
                    this.clearAndInit();
                }, studioMode));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 4, btnW, btnH,
                UIHelper.Icon.SETTINGS, Text.literal("Settings"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.GENERAL))));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 5, btnW, btnH,
                UIHelper.Icon.CLOSE, Text.literal("Exit"),
                this::close));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RecordingManager mgr = RecordingManager.getInstance();
        RecordingConfig cfg = RecordingConfig.get();

        int margin = Math.max(4, this.width / 90);
        int bottomBarH = 14;
        int dockAreaH = Math.max(86, Math.min(128, (int) (this.height * 0.36f)));
        int dockY = this.height - bottomBarH - dockAreaH - 2;
        int toolbarY = dockY - 18;
        int previewAreaY = 14;
        int previewAreaH = Math.max(40, toolbarY - previewAreaY);

        int pad = 6;
        int availW = this.width - margin * 2 - pad * 2;
        int availH = previewAreaH - pad * 2;
        int canvasH = availH;
        int canvasW = (canvasH * 16) / 9;
        if (canvasW > availW) {
            canvasW = availW;
            canvasH = (canvasW * 9) / 16;
        }
        int canvasX = (this.width - canvasW) / 2;
        int canvasY = previewAreaY + (previewAreaH - canvasH) / 2;

        // Fill OBS Dark Slate around the transparent Live Display Viewport
        context.fill(0, 0, this.width, canvasY, 0xFF1D1F26);
        context.fill(0, canvasY + canvasH, this.width, this.height, 0xFF1D1F26);
        context.fill(0, canvasY, canvasX, canvasY + canvasH, 0xFF1D1F26);
        context.fill(canvasX + canvasW, canvasY, this.width, canvasY + canvasH, 0xFF1D1F26);

        int boundColor = mgr.isRecording() ? (mgr.isPaused() ? 0xFFF1C40F : 0xFFFF3B30) : 0xFFE53935;
        UIHelper.drawBorder(context, canvasX, canvasY, canvasW, canvasH, boundColor);
        drawObsTransformHandles(context, canvasX, canvasY, canvasW, canvasH, boundColor);

        if (studioMode) {
            int midX = canvasX + canvasW / 2;
            context.fill(midX, canvasY, midX + 1, canvasY + canvasH, 0xFFE53935);
            context.drawTextWithShadow(this.textRenderer, "Preview", canvasX + 6, canvasY + 4, 0xFFDADCE0);
            context.drawTextWithShadow(this.textRenderer, "Program (Live)", midX + 6, canvasY + 4, 0xFFFF5252);
        }

        // Optional Live OBS Stats Floating Panel
        if (showStatsWindow) {
            renderObsStatsPanel(context, canvasX + 6, canvasY + 6, mgr, cfg);
        }

        // Top Window Titlebar
        context.fill(0, 0, this.width, 13, 0xFF2B2E38);
        context.fill(6, 4, 11, 9, 0xFFFF5F56);
        context.fill(14, 4, 19, 9, 0xFFFFBD2E);
        context.fill(22, 4, 27, 9, 0xFF27C93F);
        String topTitle = "OBS 30.1.2 - Profile: " + cfg.getTargetWidth() + "x" + cfg.getTargetHeight() + " - Scenes: " + SCENES.get(selectedScene);
        context.drawCenteredTextWithShadow(this.textRenderer, topTitle, this.width / 2, 2, 0xFFDADCE0);

        // Middle Context Toolbar
        context.fill(margin, toolbarY, this.width - margin, toolbarY + 16, 0xFF262932);
        UIHelper.drawBorder(context, margin, toolbarY, this.width - margin * 2, 16, 0xFF353844);
        UIHelper.drawIcon(context, UIHelper.Icon.VIDEO, margin + 5, toolbarY + 4, 0xFFE53935);
        String srcLabel = selectedSource == 0 ? "Display Capture" : (selectedSource == 1 ? "Desktop Audio" : "Mic/Aux");
        context.drawTextWithShadow(this.textRenderer, srcLabel, margin + 16, toolbarY + 4, 0xFFDADCE0);

        // 5 Bottom Docks
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
                for (int s = 0; s < SCENES.size() && s < 4; s++) {
                    int sy = dockY + 15 + s * 13;
                    if (s == selectedScene) {
                        context.fill(dx + 2, sy, dx + dw - 2, sy + 12, 0xFF2865C7);
                    }
                    context.drawTextWithShadow(this.textRenderer, SCENES.get(s), dx + 5, sy + 2, 0xFFFFFFFF);
                }
                drawDockMiniToolbar(context, dx + 2, dockY + dockAreaH - 13, dw - 4);
            } else if (i == 1) {
                String[] srcs = {"Display Capture", "Desktop Audio", "Mic/Aux"};
                boolean[] states = {true, cfg.gameAudioEnabled, cfg.microphoneEnabled};
                UIHelper.Icon[] icons = {UIHelper.Icon.VIDEO, UIHelper.Icon.AUDIO, UIHelper.Icon.MICROPHONE};
                for (int s = 0; s < srcs.length; s++) {
                    int sy = dockY + 15 + s * 13;
                    if (s == selectedSource) {
                        context.fill(dx + 2, sy, dx + dw - 2, sy + 12, 0xFF2865C7);
                    }
                    int col = states[s] ? 0xFFFFFFFF : 0xFF6C707E;
                    UIHelper.drawIcon(context, icons[s], dx + 4, sy + 2, col);
                    context.drawTextWithShadow(this.textRenderer, srcs[s], dx + 14, sy + 2, col);
                }
                drawDockMiniToolbar(context, dx + 2, dockY + dockAreaH - 13, dw - 4);
            } else if (i == 2) {
                renderInteractiveAudioMixer(context, dx + 4, dockY + 16, dw - 8, cfg, mgr.isRecording() && !mgr.isPaused());
            } else if (i == 3) {
                context.fill(dx + 4, dockY + 17, dx + dw - 4, dockY + 29, 0xFF323642);
                UIHelper.drawBorder(context, dx + 4, dockY + 17, dw - 8, 12, 0xFF454A59);
                context.drawTextWithShadow(this.textRenderer, "Cut / Fade", dx + 7, dockY + 19, 0xFFFFFFFF);

                context.drawTextWithShadow(this.textRenderer, "Duration", dx + 4, dockY + 34, 0xFFBDC1C6);
                context.fill(dx + 4, dockY + 45, dx + dw - 4, dockY + 57, 0xFF191B21);
                context.drawTextWithShadow(this.textRenderer, "300 ms", dx + 7, dockY + 47, 0xFFFFFFFF);
            }
        }

        // Bottom Telemetry Bar
        int sbY = this.height - bottomBarH;
        context.fill(0, sbY, this.width, this.height, 0xFF282B34);
        context.drawTextWithShadow(this.textRenderer, mgr.getLastStatusMessage(), 6, sbY + 3, mgr.isLastStatusError() ? 0xFFFF5252 : 0xFFBDC1C6);

        int currentFps = MinecraftClient.getInstance().getCurrentFps();
        String statsRight = String.format("REC: %s | %d / %d.00 FPS", mgr.getFormattedDuration(), currentFps, cfg.getTargetFps());
        int rw = this.textRenderer.getWidth(statsRight);
        context.drawTextWithShadow(this.textRenderer, statsRight, this.width - rw - 6, sbY + 3, 0xFFE8EAED);

        super.render(context, mouseX, mouseY, delta);
        RecordingIndicator.renderCleanOverlay(context);
    }

    private void renderObsStatsPanel(DrawContext ctx, int x, int y, RecordingManager mgr, RecordingConfig cfg) {
        int w = 165;
        int h = 58;
        ctx.fill(x, y, x + w, y + h, 0xD9191B22);
        UIHelper.drawBorder(ctx, x, y, w, h, 0xFF3B82F6);

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long freeDiskGb = RecordingFileManager.getRecordingsDir().getUsableSpace() / (1024L * 1024L * 1024L);
        int dropped = mgr.getPerformanceManager().getDroppedFrames();
        int encoded = mgr.getPerformanceManager().getEncodedFrames();

        ctx.drawTextWithShadow(this.textRenderer, "OBS Telemetry Stats", x + 5, y + 4, 0xFF60A5FA);
        ctx.drawTextWithShadow(this.textRenderer, "Memory: " + usedMb + " MB | Disk: " + freeDiskGb + " GB", x + 5, y + 16, 0xFFE8EAED);
        ctx.drawTextWithShadow(this.textRenderer, "Encoded: " + encoded + " | Dropped: " + dropped, x + 5, y + 28, 0xFFBDC1C6);
        ctx.drawTextWithShadow(this.textRenderer, "Encoder: H.264 (" + cfg.getTargetBitrateKbps() + " kbps)", x + 5, y + 40, 0xFF2ECC71);
    }

    private void drawObsTransformHandles(DrawContext ctx, int x, int y, int w, int h, int color) {
        int s = 3;
        ctx.fill(x - 1, y - 1, x + s, y + s, color);
        ctx.fill(x + w - s, y - 1, x + w + 1, y + s, color);
        ctx.fill(x - 1, y + h - s, x + s, y + h + 1, color);
        ctx.fill(x + w - s, y + h - s, x + w + 1, y + h + 1, color);
    }

    private void drawDockMiniToolbar(DrawContext context, int x, int y, int w) {
        context.fill(x, y, x + w, y + 11, 0xFF292C36);
        int step = Math.max(10, Math.min(15, w / 5));
        UIHelper.drawIcon(context, UIHelper.Icon.PLUS, x + 3, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.MINUS, x + 3 + step, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.SETTINGS, x + 3 + step * 2, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.UP, x + 3 + step * 3, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.DOWN, x + 3 + step * 4, y + 2, 0xFFBDC1C6);
    }

    private void renderInteractiveAudioMixer(DrawContext context, int x, int y, int w, RecordingConfig cfg, boolean live) {
        float jitter = live ? (float) (Math.sin(System.currentTimeMillis() * 0.025) * 0.06) : 0.0f;

        context.drawTextWithShadow(this.textRenderer, "Desktop Audio (" + cfg.gameVolume + "%)", x, y, 0xFFE8EAED);
        drawDbMeterBar(context, x, y + 10, w, cfg.gameAudioEnabled ? Math.max(0f, (cfg.gameVolume / 100.0f) + jitter) : 0.0f);
        drawBlueVolumeSlider(context, x, y + 17, w, cfg.gameVolume / 100.0f);

        int micY = y + 29;
        context.drawTextWithShadow(this.textRenderer, "Mic/Aux (" + cfg.microphoneVolume + "%)", x, micY, 0xFFE8EAED);
        drawDbMeterBar(context, x, micY + 10, w, cfg.microphoneEnabled ? Math.max(0f, (cfg.microphoneVolume / 100.0f) + jitter) : 0.0f);
        drawBlueVolumeSlider(context, x, micY + 17, w, cfg.microphoneVolume / 100.0f);
    }

    private void drawDbMeterBar(DrawContext context, int x, int y, int w, float activeRatio) {
        context.fill(x, y, x + w, y + 4, 0xFF14161B);
        int activeW = (int) (w * Math.min(1.0f, activeRatio));
        int greenEnd = (int) (w * 0.70f);
        int yellowEnd = (int) (w * 0.88f);

        if (activeW > 0) context.fill(x, y, x + Math.min(activeW, greenEnd), y + 4, 0xFF2ECC71);
        if (activeW > greenEnd) context.fill(x + greenEnd, y, x + Math.min(activeW, yellowEnd), y + 4, 0xFFF1C40F);
        if (activeW > yellowEnd) context.fill(x + yellowEnd, y, x + activeW, y + 4, 0xFFE74C3C);
    }

    private void drawBlueVolumeSlider(DrawContext context, int x, int y, int w, float percent) {
        context.fill(x, y + 1, x + w, y + 3, 0xFF3A3F4D);
        int fillW = (int) (w * Math.max(0.0f, Math.min(1.0f, percent)));
        context.fill(x, y + 1, x + fillW, y + 3, 0xFF3B82F6);
        context.fill(x + Math.max(0, fillW - 2), y - 1, x + Math.min(w, fillW + 2), y + 5, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        double mx = click.x();
        double my = click.y();
        RecordingConfig cfg = RecordingConfig.get();

        int margin = Math.max(4, this.width / 90);
        int bottomBarH = 14;
        int dockAreaH = Math.max(86, Math.min(128, (int) (this.height * 0.36f)));
        int dockY = this.height - bottomBarH - dockAreaH - 2;
        int totalDockW = this.width - margin * 2;
        int gap = 3;
        int colW = (totalDockW - gap * 4) / 5;

        int scenesX = margin;
        if (mx >= scenesX && mx <= scenesX + colW && my >= dockY + 15 && my <= dockY + dockAreaH) {
            if (my >= dockY + dockAreaH - 13) {
                int step = Math.max(10, Math.min(15, (colW - 4) / 5));
                if (mx < scenesX + 3 + step) {
                    SCENES.add("Scene " + (SCENES.size() + 1));
                    selectedScene = SCENES.size() - 1;
                } else if (mx < scenesX + 3 + step * 2 && SCENES.size() > 1) {
                    SCENES.remove(selectedScene);
                    selectedScene = Math.max(0, selectedScene - 1);
                }
            } else {
                int idx = (int) ((my - (dockY + 15)) / 13);
                if (idx >= 0 && idx < SCENES.size()) selectedScene = idx;
            }
            return true;
        }

        int sourcesX = margin + colW + gap;
        if (mx >= sourcesX && mx <= sourcesX + colW && my >= dockY + 15 && my <= dockY + dockAreaH) {
            int idx = (int) ((my - (dockY + 15)) / 13);
            if (idx >= 0 && idx < 3) {
                selectedSource = idx;
                if (idx == 1) cfg.gameAudioEnabled = !cfg.gameAudioEnabled;
                if (idx == 2) cfg.microphoneEnabled = !cfg.microphoneEnabled;
                RecordingConfig.save();
                this.clearAndInit();
            }
            return true;
        }

        int mixerX = margin + (colW + gap) * 2 + 4;
        int mixerW = colW - 8;
        int dSliderY = dockY + 16 + 17;
        int mSliderY = dockY + 16 + 29 + 17;

        if (mx >= mixerX && mx <= mixerX + mixerW) {
            if (my >= dSliderY - 4 && my <= dSliderY + 7) {
                draggingDesktopVol = true;
                updateVolumeFromMouse(mx, mixerX, mixerW, true);
                return true;
            }
            if (my >= mSliderY - 4 && my <= mSliderY + 7) {
                draggingMicVol = true;
                updateVolumeFromMouse(mx, mixerX, mixerW, false);
                return true;
            }
        }

        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingDesktopVol || draggingMicVol) {
            int margin = Math.max(4, this.width / 90);
            int totalDockW = this.width - margin * 2;
            int gap = 3;
            int colW = (totalDockW - gap * 4) / 5;
            int mixerX = margin + (colW + gap) * 2 + 4;
            int mixerW = colW - 8;
            updateVolumeFromMouse(click.x(), mixerX, mixerW, draggingDesktopVol);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingDesktopVol = false;
        draggingMicVol = false;
        return super.mouseReleased(click);
    }

    private void updateVolumeFromMouse(double mx, int sliderX, int sliderW, boolean isDesktop) {
        int pct = (int) Math.round(((mx - sliderX) / (double) sliderW) * 100.0);
        pct = Math.max(0, Math.min(100, pct));
        RecordingConfig cfg = RecordingConfig.get();
        if (isDesktop) {
            cfg.gameVolume = pct;
        } else {
            cfg.microphoneVolume = pct;
        }
        RecordingConfig.save();
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
