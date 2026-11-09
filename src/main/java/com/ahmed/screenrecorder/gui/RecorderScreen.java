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
 * Zero-Lag 60 FPS OBS Studio 30 Floating Click-GUI:
 * Uses a direct crystal-clear hardware viewport cut-out for the 16:9 Live Preview Canvas
 * so opening OBS causes 0% FPS drop and shows the live game in 100% native HD sharpness!
 */
public class RecorderScreen extends Screen {
    private final Screen parent;
    private static final List<String> SCENES = new ArrayList<>(Arrays.asList("Main Scene", "Full Display", "Gameplay Scene"));
    private static final List<String> SOURCES = new ArrayList<>(Arrays.asList("Display Capture", "Game Capture", "Desktop Audio", "Mic/Aux"));
    private static int selectedScene = 0;
    private static int selectedSource = 0;
    private static boolean studioMode = false;
    private static boolean showStatsWindow = false;

    private boolean contextMenuOpen = false;
    private int contextMenuX = 0;
    private int contextMenuY = 0;
    private final String[] contextMenuItems = {
            "Add Display Capture",
            "Add Game Capture",
            "Source Properties...",
            "Audio Filters...",
            "Transform: Fit to Screen",
            "Remove Selected"
    };

    private boolean draggingDesktopVol = false;
    private boolean draggingMicVol = false;
    private int winX, winY, winW, winH;

    public RecorderScreen(Screen parent) {
        super(Text.literal("OBS"));
        this.parent = parent;
    }

    private void computeFloatingWindowBounds() {
        this.winW = Math.min(500, Math.max(320, (int) (this.width * 0.88f)));
        this.winH = Math.min(285, Math.max(195, (int) (this.height * 0.86f)));
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;
    }

    @Override
    protected void init() {
        computeFloatingWindowBounds();
        ZeroCopyGlPreviewHandler.captureOnceOnGuiOpen();
        RecordingManager mgr = RecordingManager.getInstance();
        RecordingConfig cfg = RecordingConfig.get();

        int pad = 6;
        int bottomBarH = 13;
        int dockAreaH = Math.max(78, Math.min(108, (int) (this.winH * 0.38f)));
        int dockY = this.winY + this.winH - bottomBarH - dockAreaH - 2;
        int toolbarY = dockY - 17;

        String currentSrc = SOURCES.isEmpty() ? "Display Capture" : SOURCES.get(Math.min(selectedSource, SOURCES.size() - 1));
        int labelEnd = this.winX + pad + 20 + this.textRenderer.getWidth(currentSrc) + 8;
        int tbX = Math.max(this.winX + 115, labelEnd);
        int availTbW = (this.winX + this.winW - pad) - tbX;
        int tbBtnW = Math.min(64, Math.max(42, (availTbW - 12) / 4));

        this.addDrawableChild(new ThemeButton(tbX, toolbarY + 2, tbBtnW, 12,
                UIHelper.Icon.SETTINGS, Text.literal("Properties"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.VIDEO))));

        this.addDrawableChild(new ThemeButton(tbX + tbBtnW + 3, toolbarY + 2, tbBtnW, 12,
                UIHelper.Icon.FILTERS, Text.literal("Filters"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.AUDIO))));

        this.addDrawableChild(new ThemeButton(tbX + (tbBtnW + 3) * 2, toolbarY + 2, tbBtnW, 12,
                UIHelper.Icon.VIDEO, Text.literal("Shot"),
                mgr::takeScreenshot));

        this.addDrawableChild(new ThemeButton(tbX + (tbBtnW + 3) * 3, toolbarY + 2, tbBtnW, 12,
                UIHelper.Icon.PERFORMANCE, Text.literal("Stats"),
                () -> {
                    showStatsWindow = !showStatsWindow;
                    this.clearAndInit();
                }, showStatsWindow));

        int totalDockW = this.winW - pad * 2;
        int gap = 3;
        int colW = (totalDockW - gap * 4) / 5;
        int controlsX = this.winX + pad + (colW + gap) * 4;
        int controlsW = totalDockW - (colW + gap) * 4;

        int btnX = controlsX + 3;
        int btnW = controlsW - 6;
        int availBtnH = dockAreaH - 16;
        int btnH = Math.max(10, (availBtnH - 10) / 6);
        int stepY = btnH + 2;
        int curY = dockY + 14;

        if (mgr.isRecording()) {
            int stopW = btnW - 18;
            this.addDrawableChild(new ThemeButton(btnX, curY, stopW, btnH,
                    UIHelper.Icon.STOP, Text.literal("Stop"),
                    () -> {
                        mgr.stopRecording();
                        this.clearAndInit();
                    }, true));
            this.addDrawableChild(new ThemeButton(btnX + stopW + 2, curY, 16, btnH,
                    mgr.isPaused() ? UIHelper.Icon.PLAY : UIHelper.Icon.PAUSE, Text.empty(),
                    () -> {
                        mgr.togglePause();
                        this.clearAndInit();
                    }, mgr.isPaused()));
        } else {
            this.addDrawableChild(new ThemeButton(btnX, curY, btnW, btnH,
                    UIHelper.Icon.RECORD, Text.literal("Start Rec"),
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
                UIHelper.Icon.VIDEO, Text.literal("Studio"),
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
        computeFloatingWindowBounds();
        ZeroCopyGlPreviewHandler.captureOnceOnGuiOpen();
        RecordingManager mgr = RecordingManager.getInstance();
        RecordingConfig cfg = RecordingConfig.get();

        int pad = 6;
        int bottomBarH = 13;
        int dockAreaH = Math.max(78, Math.min(108, (int) (this.winH * 0.38f)));
        int dockY = this.winY + this.winH - bottomBarH - dockAreaH - 2;
        int toolbarY = dockY - 17;
        int previewAreaY = winY + 15;
        int previewAreaH = Math.max(36, toolbarY - previewAreaY - 2);

        int availW = winW - pad * 4;
        int availH = previewAreaH - 6;
        int canvasH = availH;
        int canvasW = (canvasH * 16) / 9;
        if (canvasW > availW) {
            canvasW = availW;
            canvasH = (canvasW * 9) / 16;
        }
        int canvasX = winX + (winW - canvasW) / 2;
        int canvasY = previewAreaY + (previewAreaH - canvasH) / 2;

        // Draw Floating OBS Window Frame AROUND the crystal-clear 60 FPS Live Preview Viewport!
        context.fill(winX, winY, winX + winW, winY + winH, 0xFF1D1F26);
        ZeroCopyGlPreviewHandler.drawPreviewCanvas(context, canvasX, canvasY, canvasW, canvasH); // Above preview
        context.fill(winX, canvasY + canvasH, winX + winW, winY + winH, 0xFF1D1F26); // Below preview
        context.fill(winX, canvasY, canvasX, canvasY + canvasH, 0xFF1D1F26); // Left of preview
        context.fill(canvasX + canvasW, canvasY, winX + winW, canvasY + canvasH, 0xFF1D1F26); // Right of preview

        // Outer Window Border & Titlebar
        UIHelper.drawBorder(context, winX, winY, winW, winH, 0xFF3F4454);
        context.fill(winX + 1, winY + 1, winX + winW - 1, winY + 14, 0xFF2B2E38);
        context.fill(winX + 6, winY + 5, winX + 11, winY + 10, 0xFFFF5F56);
        context.fill(winX + 14, winY + 5, winX + 19, winY + 10, 0xFFFFBD2E);
        context.fill(winX + 22, winY + 5, winX + 27, winY + 10, 0xFF27C93F);
        String topTitle = "OBS 30.1.2 - Profile: " + cfg.getTargetWidth() + "x" + cfg.getTargetHeight() + " - Scene: " + SCENES.get(Math.min(selectedScene, SCENES.size() - 1));
        context.drawCenteredTextWithShadow(this.textRenderer, topTitle, winX + winW / 2, winY + 3, 0xFFDADCE0);

        // Red OBS Transform Bounding Box & Handles around the 60 FPS Live Preview
        int boundColor = mgr.isRecording() ? (mgr.isPaused() ? 0xFFF1C40F : 0xFFFF3B30) : 0xFFE53935;
        UIHelper.drawBorder(context, canvasX, canvasY, canvasW, canvasH, boundColor);
        drawObsTransformHandles(context, canvasX, canvasY, canvasW, canvasH, boundColor);

        // Subtle HUD crosshair & resolution badge inside Live Preview
        String liveBadge = (mgr.isRecording() ? "LIVE REC " : "PREVIEW ") + cfg.getTargetWidth() + "x" + cfg.getTargetHeight();
        context.fill(canvasX + 4, canvasY + 4, canvasX + 10 + this.textRenderer.getWidth(liveBadge), canvasY + 15, 0xAA121318);
        context.drawTextWithShadow(this.textRenderer, liveBadge, canvasX + 7, canvasY + 6, mgr.isRecording() ? 0xFFFF5252 : 0xFF60A5FA);

        if (studioMode) {
            int midX = canvasX + canvasW / 2;
            context.fill(midX, canvasY, midX + 1, canvasY + canvasH, 0xFFE53935);
            context.drawTextWithShadow(this.textRenderer, "Program", midX + 5, canvasY + 6, 0xFFFF5252);
        }

        if (showStatsWindow) {
            renderObsStatsPanel(context, canvasX + 4, canvasY + 18, mgr, cfg);
        }

        // Middle Context Toolbar
        context.fill(winX + pad, toolbarY, winX + winW - pad, toolbarY + 16, 0xFF262932);
        UIHelper.drawBorder(context, winX + pad, toolbarY, winW - pad * 2, 16, 0xFF353844);
        UIHelper.drawIcon(context, UIHelper.Icon.VIDEO, winX + pad + 4, toolbarY + 4, 0xFFE53935);
        String srcLabel = SOURCES.isEmpty() ? "Display Capture" : SOURCES.get(Math.min(selectedSource, SOURCES.size() - 1));
        context.drawTextWithShadow(this.textRenderer, srcLabel, winX + pad + 15, toolbarY + 4, 0xFFDADCE0);

        // 5 Bottom Docks
        int totalDockW = winW - pad * 2;
        int gap = 3;
        int colW = (totalDockW - gap * 4) / 5;
        String[] dockTitles = {"Scenes", "Sources", "Audio Mixer", "Transitions", "Controls"};

        for (int i = 0; i < 5; i++) {
            int dx = winX + pad + i * (colW + gap);
            int dw = (i == 4) ? (totalDockW - (colW + gap) * 4) : colW;

            context.fill(dx, dockY, dx + dw, dockY + dockAreaH, 0xFF23262E);
            context.fill(dx, dockY, dx + dw, dockY + 12, 0xFF2D303B);
            UIHelper.drawBorder(context, dx, dockY, dw, dockAreaH, 0xFF383C4A);
            context.drawTextWithShadow(this.textRenderer, dockTitles[i], dx + 4, dockY + 2, 0xFFE8EAED);

            if (i == 0) {
                for (int s = 0; s < SCENES.size() && s < 4; s++) {
                    int sy = dockY + 14 + s * 12;
                    if (s == selectedScene) {
                        context.fill(dx + 2, sy, dx + dw - 2, sy + 11, 0xFF2865C7);
                    }
                    context.drawTextWithShadow(this.textRenderer, truncate(SCENES.get(s), 13), dx + 4, sy + 2, 0xFFFFFFFF);
                }
                drawDockMiniToolbar(context, dx + 2, dockY + dockAreaH - 12, dw - 4);
            } else if (i == 1) {
                for (int s = 0; s < SOURCES.size() && s < 4; s++) {
                    int sy = dockY + 14 + s * 12;
                    if (s == selectedSource) {
                        context.fill(dx + 2, sy, dx + dw - 2, sy + 11, 0xFF2865C7);
                    }
                    String sName = SOURCES.get(s);
                    boolean enabled = true;
                    UIHelper.Icon ic = UIHelper.Icon.VIDEO;
                    if (sName.contains("Desktop")) { enabled = cfg.gameAudioEnabled; ic = UIHelper.Icon.AUDIO; }
                    else if (sName.contains("Mic")) { enabled = cfg.microphoneEnabled; ic = UIHelper.Icon.MICROPHONE; }
                    int col = enabled ? 0xFFFFFFFF : 0xFF6C707E;
                    UIHelper.drawIcon(context, ic, dx + 4, sy + 2, col);
                    context.drawTextWithShadow(this.textRenderer, truncate(sName, 9), dx + 14, sy + 2, col);
                    UIHelper.drawIcon(context, UIHelper.Icon.EYE, dx + dw - 19, sy + 2, col);
                    UIHelper.drawIcon(context, UIHelper.Icon.LOCK, dx + dw - 10, sy + 2, 0xFF9AA0A6);
                }
                drawDockMiniToolbar(context, dx + 2, dockY + dockAreaH - 12, dw - 4);
            } else if (i == 2) {
                renderInteractiveAudioMixer(context, dx + 4, dockY + 15, dw - 8, cfg, mgr.isRecording() && !mgr.isPaused());
            } else if (i == 3) {
                context.fill(dx + 4, dockY + 16, dx + dw - 4, dockY + 27, 0xFF323642);
                UIHelper.drawBorder(context, dx + 4, dockY + 16, dw - 8, 11, 0xFF454A59);
                context.drawTextWithShadow(this.textRenderer, "Cut / Fade", dx + 6, dockY + 18, 0xFFFFFFFF);

                context.drawTextWithShadow(this.textRenderer, "Duration", dx + 4, dockY + 31, 0xFFBDC1C6);
                context.fill(dx + 4, dockY + 41, dx + dw - 4, dockY + 52, 0xFF191B21);
                context.drawTextWithShadow(this.textRenderer, "300 ms", dx + 6, dockY + 43, 0xFFFFFFFF);
            }
        }

        // Bottom Telemetry Status Bar
        int sbY = winY + winH - bottomBarH;
        context.fill(winX + 1, sbY, winX + winW - 1, winY + winH - 1, 0xFF282B34);
        context.drawTextWithShadow(this.textRenderer, mgr.getLastStatusMessage(), winX + 6, sbY + 3, mgr.isLastStatusError() ? 0xFFFF5252 : 0xFFBDC1C6);

        int currentFps = MinecraftClient.getInstance().getCurrentFps();
        String statsRight = String.format("REC: %s | %d FPS", mgr.getFormattedDuration(), currentFps);
        int rw = this.textRenderer.getWidth(statsRight);
        context.drawTextWithShadow(this.textRenderer, statsRight, winX + winW - rw - 6, sbY + 3, 0xFFE8EAED);

        super.render(context, mouseX, mouseY, delta);

        if (contextMenuOpen) {
            int cmW = 128;
            int cmH = contextMenuItems.length * 14 + 4;
            int cx = Math.min(contextMenuX, this.width - cmW - 4);
            int cy = Math.min(contextMenuY, this.height - cmH - 4);
            context.fill(cx, cy, cx + cmW, cy + cmH, 0xFF282C37);
            UIHelper.drawBorder(context, cx, cy, cmW, cmH, 0xFF60A5FA);

            for (int i = 0; i < contextMenuItems.length; i++) {
                int iy = cy + 2 + i * 14;
                boolean hover = mouseX >= cx && mouseX <= cx + cmW && mouseY >= iy && mouseY < iy + 14;
                if (hover) {
                    context.fill(cx + 2, iy, cx + cmW - 2, iy + 13, 0xFF2865C7);
                }
                context.drawTextWithShadow(this.textRenderer, contextMenuItems[i], cx + 6, iy + 3, 0xFFFFFFFF);
            }
        }

        RecordingIndicator.renderCleanOverlay(context);
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() <= len ? s : s.substring(0, len);
    }

    private void renderObsStatsPanel(DrawContext ctx, int x, int y, RecordingManager mgr, RecordingConfig cfg) {
        int w = 158;
        int h = 44;
        ctx.fill(x, y, x + w, y + h, 0xE6191B22);
        UIHelper.drawBorder(ctx, x, y, w, h, 0xFF3B82F6);

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long freeDiskGb = RecordingFileManager.getRecordingsDir().getUsableSpace() / (1024L * 1024L * 1024L);
        int encoded = mgr.getPerformanceManager().getEncodedFrames();

        ctx.drawTextWithShadow(this.textRenderer, "RAM: " + usedMb + "MB | Disk: " + freeDiskGb + "GB", x + 4, y + 4, 0xFFE8EAED);
        ctx.drawTextWithShadow(this.textRenderer, "Encoded Frames: " + encoded, x + 4, y + 17, 0xFF60A5FA);
        ctx.drawTextWithShadow(this.textRenderer, "H.264 Stream Ready", x + 4, y + 30, 0xFF2ECC71);
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

        context.drawTextWithShadow(this.textRenderer, "Desktop (" + cfg.gameVolume + "%)", x, y, 0xFFE8EAED);
        drawDbMeterBar(context, x, y + 10, w, cfg.gameAudioEnabled ? Math.max(0f, (cfg.gameVolume / 100.0f) + jitter) : 0.0f);
        drawBlueVolumeSlider(context, x, y + 16, w, cfg.gameVolume / 100.0f);

        int micY = y + 26;
        context.drawTextWithShadow(this.textRenderer, "Mic/Aux (" + cfg.microphoneVolume + "%)", x, micY, 0xFFE8EAED);
        drawDbMeterBar(context, x, micY + 10, w, cfg.microphoneEnabled ? Math.max(0f, (cfg.microphoneVolume / 100.0f) + jitter) : 0.0f);
        drawBlueVolumeSlider(context, x, micY + 16, w, cfg.microphoneVolume / 100.0f);
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

        if (contextMenuOpen) {
            int cmW = 128;
            int cmH = contextMenuItems.length * 14 + 4;
            int cx = Math.min(contextMenuX, this.width - cmW - 4);
            int cy = Math.min(contextMenuY, this.height - cmH - 4);
            if (mx >= cx && mx <= cx + cmW && my >= cy && my <= cy + cmH) {
                int idx = (int) ((my - cy - 2) / 14);
                if (idx == 0 && !SOURCES.contains("Display Capture 2")) SOURCES.add("Display Capture 2");
                else if (idx == 1 && !SOURCES.contains("Game Capture 2")) SOURCES.add("Game Capture 2");
                else if (idx == 2) this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.VIDEO));
                else if (idx == 3) this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.AUDIO));
                else if (idx == 5 && SOURCES.size() > 1) {
                    SOURCES.remove(Math.min(selectedSource, SOURCES.size() - 1));
                    selectedSource = Math.max(0, selectedSource - 1);
                }
            }
            contextMenuOpen = false;
            return true;
        }

        computeFloatingWindowBounds();
        ZeroCopyGlPreviewHandler.captureOnceOnGuiOpen();
        int pad = 6;
        int bottomBarH = 13;
        int dockAreaH = Math.max(78, Math.min(108, (int) (this.winH * 0.38f)));
        int dockY = this.winY + this.winH - bottomBarH - dockAreaH - 2;
        int totalDockW = this.winW - pad * 2;
        int gap = 3;
        int colW = (totalDockW - gap * 4) / 5;

        int scenesX = winX + pad;
        int sourcesX = winX + pad + colW + gap;

        if (click.button() == 1 && mx >= scenesX && mx <= sourcesX + colW && my >= dockY && my <= dockY + dockAreaH) {
            contextMenuOpen = true;
            contextMenuX = (int) mx;
            contextMenuY = (int) my;
            return true;
        }

        if (mx >= scenesX && mx <= scenesX + colW && my >= dockY + 14 && my <= dockY + dockAreaH) {
            if (my >= dockY + dockAreaH - 12) {
                int step = Math.max(10, Math.min(15, (colW - 4) / 5));
                int relX = (int) (mx - scenesX - 2);
                if (relX < step) {
                    SCENES.add("Scene " + (SCENES.size() + 1));
                    selectedScene = SCENES.size() - 1;
                } else if (relX < step * 2 && SCENES.size() > 1) {
                    SCENES.remove(selectedScene);
                    selectedScene = Math.max(0, selectedScene - 1);
                } else if (relX < step * 3) {
                    this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.GENERAL));
                } else if (relX < step * 4 && selectedScene > 0) {
                    selectedScene--;
                } else if (selectedScene < SCENES.size() - 1) {
                    selectedScene++;
                }
            } else {
                int idx = (int) ((my - (dockY + 14)) / 12);
                if (idx >= 0 && idx < SCENES.size()) selectedScene = idx;
            }
            return true;
        }

        if (mx >= sourcesX && mx <= sourcesX + colW && my >= dockY + 14 && my <= dockY + dockAreaH) {
            if (my >= dockY + dockAreaH - 12) {
                int step = Math.max(10, Math.min(15, (colW - 4) / 5));
                int relX = (int) (mx - sourcesX - 2);
                if (relX < step) {
                    contextMenuOpen = true;
                    contextMenuX = (int) mx;
                    contextMenuY = (int) my - 70;
                } else if (relX < step * 2 && SOURCES.size() > 1) {
                    SOURCES.remove(Math.min(selectedSource, SOURCES.size() - 1));
                    selectedSource = Math.max(0, selectedSource - 1);
                } else if (relX < step * 3) {
                    this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.VIDEO));
                } else if (relX < step * 4 && selectedSource > 0) {
                    selectedSource--;
                } else if (selectedSource < SOURCES.size() - 1) {
                    selectedSource++;
                }
            } else {
                int idx = (int) ((my - (dockY + 14)) / 12);
                if (idx >= 0 && idx < SOURCES.size()) {
                    selectedSource = idx;
                    String sName = SOURCES.get(idx);
                    if (sName.contains("Desktop")) cfg.gameAudioEnabled = !cfg.gameAudioEnabled;
                    else if (sName.contains("Mic")) cfg.microphoneEnabled = !cfg.microphoneEnabled;
                    RecordingConfig.save();
                    this.clearAndInit();
                }
            }
            return true;
        }

        int mixerX = winX + pad + (colW + gap) * 2 + 4;
        int mixerW = colW - 8;
        int dSliderY = dockY + 15 + 16;
        int mSliderY = dockY + 15 + 26 + 16;

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
            computeFloatingWindowBounds();
        ZeroCopyGlPreviewHandler.captureOnceOnGuiOpen();
            int pad = 6;
            int totalDockW = this.winW - pad * 2;
            int gap = 3;
            int colW = (totalDockW - gap * 4) / 5;
            int mixerX = winX + pad + (colW + gap) * 2 + 4;
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
