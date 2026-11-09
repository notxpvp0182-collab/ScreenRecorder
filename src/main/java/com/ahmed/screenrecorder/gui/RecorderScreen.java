package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.audio.MicrophoneManager;
import com.ahmed.screenrecorder.capture.RecordingManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Clean, Uncluttered, Zero-Overlap OBS Studio Click-GUI:
 * - Zero extra text descriptions or cramped top ribbons
 * - Clean 4-Dock Studio Layout (Scenes | Sources | Audio & Mic Selector | Controls)
 * - Live Rendering Progress Bar when finalizing MP4
 */
public class RecorderScreen extends Screen {
    private final Screen parent;

    private boolean contextMenuOpen = false;
    private int contextMenuX = 0;
    private int contextMenuY = 0;
    private final String[] contextMenuItems = {
            "Add Display Capture",
            "Add Game Capture",
            "Switch Microphone Device",
            "Cycle Resolution",
            "Open Settings...",
            "Remove Source"
    };

    private boolean draggingDesktopVol = false;
    private boolean draggingMicVol = false;
    private int winX, winY, winW, winH;

    public RecorderScreen(Screen parent) {
        super(Text.literal("OBS"));
        this.parent = parent;
    }

    private void computeFloatingWindowBounds() {
        this.winW = Math.min(460, Math.max(310, (int) (this.width * 0.86f)));
        this.winH = Math.min(230, Math.max(175, (int) (this.height * 0.78f)));
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;
    }

    @Override
    protected void init() {
        computeFloatingWindowBounds();
        RecordingManager mgr = RecordingManager.getInstance();
        RecordingConfig cfg = RecordingConfig.get();

        // Top-Right [ X ] Close Button
        this.addDrawableChild(new ThemeButton(winX + winW - 18, winY + 2, 16, 12,
                UIHelper.Icon.CLOSE, Text.empty(), this::close, true));

        int pad = 6;
        int dockY = winY + 20;
        int bottomBarH = 15;
        int dockAreaH = (winY + winH - bottomBarH - 3) - dockY;
        int totalDockW = winW - pad * 2;
        int gap = 4;
        int colW = (totalDockW - gap * 3) / 4;

        // Audio Mixer Dock Mic Device Selector Button (Inside Column 3)
        int mixerDockX = winX + pad + (colW + gap) * 2;
        List<String> mics = MicrophoneManager.getAvailableMicrophoneDevices();
        if (cfg.microphoneDevice == null || cfg.microphoneDevice.equals("Default")) {
            cfg.microphoneDevice = mics.get(0);
        }
        this.addDrawableChild(new ThemeButton(mixerDockX + 4, dockY + dockAreaH - 22, colW - 8, 18,
                UIHelper.Icon.MICROPHONE, Text.literal(truncate(cfg.microphoneDevice, 11)),
                () -> {
                    List<String> list = MicrophoneManager.getAvailableMicrophoneDevices();
                    int next = (list.indexOf(cfg.microphoneDevice) + 1) % list.size();
                    cfg.microphoneDevice = list.get(next);
                    cfg.microphoneEnabled = true;
                    RecordingConfig.save();
                    mgr.setCustomStatusMessage("Mic Selected: " + cfg.microphoneDevice);
                    this.clearAndInit();
                }, cfg.microphoneEnabled));

        // Controls Dock Buttons (Column 4 - Rightmost)
        int controlsX = winX + pad + (colW + gap) * 3;
        int controlsW = totalDockW - (colW + gap) * 3;
        int btnX = controlsX + 4;
        int btnW = controlsW - 8;
        int availBtnH = dockAreaH - 18;
        int btnH = Math.max(15, (availBtnH - 10) / 5);
        int stepY = btnH + 3;
        int curY = dockY + 16;

        // 1. Start / Stop / Pause Recording
        if (mgr.isRecording()) {
            int stopW = btnW - 22;
            this.addDrawableChild(new ThemeButton(btnX, curY, stopW, btnH,
                    UIHelper.Icon.STOP, Text.literal("Stop"),
                    () -> {
                        mgr.stopRecording();
                        this.clearAndInit();
                    }, true));
            this.addDrawableChild(new ThemeButton(btnX + stopW + 2, curY, 20, btnH,
                    mgr.isPaused() ? UIHelper.Icon.PLAY : UIHelper.Icon.PAUSE, Text.empty(),
                    () -> {
                        mgr.togglePause();
                        this.clearAndInit();
                    }, mgr.isPaused()));
        } else {
            ThemeButton startBtn = new ThemeButton(btnX, curY, btnW, btnH,
                    UIHelper.Icon.RECORD, Text.literal(mgr.isStopping() ? "Rendering..." : "Start Rec"),
                    () -> {
                        mgr.startRecording();
                        this.clearAndInit();
                    }, true);
            startBtn.active = !mgr.isStopping();
            this.addDrawableChild(startBtn);
        }

        // 2. Recordings Folder & In-Game Player
        this.addDrawableChild(new ThemeButton(btnX, curY + stepY, btnW, btnH,
                UIHelper.Icon.FOLDER, Text.literal("Recordings"),
                () -> this.client.setScreen(new RecordingsScreen(this))));

        // 3. Microphone ON/OFF Toggle
        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 2, btnW, btnH,
                UIHelper.Icon.MICROPHONE, Text.literal("Mic: " + (cfg.microphoneEnabled ? "ON" : "OFF")),
                () -> {
                    cfg.microphoneEnabled = !cfg.microphoneEnabled;
                    RecordingConfig.save();
                    this.clearAndInit();
                }, cfg.microphoneEnabled));

        // 4. Settings
        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 3, btnW, btnH,
                UIHelper.Icon.SETTINGS, Text.literal("Settings"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.AUDIO))));

        // 5. Exit
        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 4, btnW, btnH,
                UIHelper.Icon.CLOSE, Text.literal("Exit"),
                this::close));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        computeFloatingWindowBounds();
        RecordingManager mgr = RecordingManager.getInstance();
        RecordingConfig cfg = RecordingConfig.get();

        context.fill(0, 0, this.width, this.height, 0x55000000);

        // Main Floating OBS Studio Window
        context.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x99000000);
        context.fill(winX, winY, winX + winW, winY + winH, 0xFF1D1F26);
        UIHelper.drawBorder(context, winX, winY, winW, winH, 0xFF3F4454);

        // Clean Top Header Bar
        context.fill(winX + 1, winY + 1, winX + winW - 1, winY + 16, 0xFF282B34);
        UIHelper.drawIcon(context, UIHelper.Icon.RECORD, winX + 6, winY + 4, mgr.isRecording() ? 0xFFFF3B30 : 0xFF60A5FA);
        context.drawTextWithShadow(this.textRenderer, "OBS Studio", winX + 18, winY + 4, 0xFFFFFFFF);

        int pad = 6;
        int dockY = winY + 20;
        int bottomBarH = 15;
        int dockAreaH = (winY + winH - bottomBarH - 3) - dockY;
        int totalDockW = winW - pad * 2;
        int gap = 4;
        int colW = (totalDockW - gap * 3) / 4;
        String[] dockTitles = {"Scenes", "Sources", "Audio Mixer", "Controls"};

        List<String> scenes = SceneCollectionManager.getScenes();

        for (int i = 0; i < 4; i++) {
            int dx = winX + pad + i * (colW + gap);
            int dw = (i == 3) ? (totalDockW - (colW + gap) * 3) : colW;

            context.fill(dx, dockY, dx + dw, dockY + dockAreaH, 0xFF23262E);
            context.fill(dx, dockY, dx + dw, dockY + 13, 0xFF2D303B);
            UIHelper.drawBorder(context, dx, dockY, dw, dockAreaH, 0xFF383C4A);
            context.drawTextWithShadow(this.textRenderer, dockTitles[i], dx + 5, dockY + 3, 0xFFE8EAED);

            if (i == 0) {
                for (int s = 0; s < scenes.size() && s < 7; s++) {
                    int sy = dockY + 16 + s * 15;
                    if (s == SceneCollectionManager.getSelectedSceneIdx()) {
                        context.fill(dx + 2, sy, dx + dw - 2, sy + 13, 0xFF2865C7);
                    }
                    context.drawTextWithShadow(this.textRenderer, truncate(scenes.get(s), 14), dx + 5, sy + 3, 0xFFFFFFFF);
                }
                drawDockMiniToolbar(context, dx + 2, dockY + dockAreaH - 13, dw - 4);
            } else if (i == 1) {
                List<SceneCollectionManager.SourceItem> sources = SceneCollectionManager.getSources();
                for (int s = 0; s < sources.size() && s < 7; s++) {
                    int sy = dockY + 16 + s * 15;
                    SceneCollectionManager.SourceItem item = sources.get(s);
                    if (s == SceneCollectionManager.getSelectedSourceIdx()) {
                        context.fill(dx + 2, sy, dx + dw - 2, sy + 13, 0xFF2865C7);
                    }
                    int col = item.visible ? 0xFFFFFFFF : 0xFF6C707E;
                    UIHelper.drawIcon(context, item.icon, dx + 4, sy + 3, col);
                    context.drawTextWithShadow(this.textRenderer, truncate(item.name, 10), dx + 14, sy + 3, col);
                    UIHelper.drawIcon(context, UIHelper.Icon.EYE, dx + dw - 12, sy + 3, item.visible ? 0xFF60A5FA : 0xFF555555);
                }
                drawDockMiniToolbar(context, dx + 2, dockY + dockAreaH - 13, dw - 4);
            } else if (i == 2) {
                renderCleanAudioMixer(context, dx + 5, dockY + 18, dw - 10, cfg, mgr.isRecording() && !mgr.isPaused());
            }
        }

        // Bottom OBS Status Bar (With Live Rendering Progress Bar when stopping!)
        int sbY = winY + winH - bottomBarH;
        context.fill(winX + 1, sbY, winX + winW - 1, winY + winH - 1, 0xFF282B34);

        if (mgr.isStopping()) {
            int pct = mgr.getRenderProgressPct();
            int barW = 100;
            context.fill(winX + 6, sbY + 4, winX + 6 + barW, sbY + 11, 0xFF14161B);
            context.fill(winX + 6, sbY + 4, winX + 6 + (barW * pct) / 100, sbY + 11, 0xFF2ECC71);
            context.drawTextWithShadow(this.textRenderer, mgr.getLastStatusMessage(), winX + 112, sbY + 3, 0xFF2ECC71);
        } else {
            context.drawTextWithShadow(this.textRenderer, mgr.getLastStatusMessage(), winX + 6, sbY + 3, mgr.isLastStatusError() ? 0xFFFF5252 : 0xFFBDC1C6);
        }

        int gameFps = MinecraftClient.getInstance().getCurrentFps();
        String statsRight = String.format("REC: %s | %d FPS", mgr.getFormattedDuration(), gameFps);
        int rw = this.textRenderer.getWidth(statsRight);
        context.drawTextWithShadow(this.textRenderer, statsRight, winX + winW - rw - 6, sbY + 3, 0xFFE8EAED);

        super.render(context, mouseX, mouseY, delta);

        if (contextMenuOpen) {
            int cmW = 145;
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

    private void renderCleanAudioMixer(DrawContext context, int x, int y, int w, RecordingConfig cfg, boolean live) {
        float jitter = live ? (float) (Math.sin(System.currentTimeMillis() * 0.025) * 0.07) : 0.0f;

        context.drawTextWithShadow(this.textRenderer, "Game Audio " + cfg.gameVolume + "%", x, y, 0xFFE8EAED);
        drawDbMeterBar(context, x, y + 11, w, cfg.gameAudioEnabled ? Math.max(0.05f, (cfg.gameVolume / 100.0f) + jitter) : 0.0f);
        drawBlueVolumeSlider(context, x, y + 19, w, cfg.gameVolume / 100.0f);

        int micY = y + 32;
        context.drawTextWithShadow(this.textRenderer, "Mic/Aux " + cfg.microphoneVolume + "%", x, micY, 0xFFE8EAED);
        drawDbMeterBar(context, x, micY + 11, w, cfg.microphoneEnabled ? Math.max(0.05f, (cfg.microphoneVolume / 100.0f) + jitter) : 0.0f);
        drawBlueVolumeSlider(context, x, micY + 19, w, cfg.microphoneVolume / 100.0f);
    }

    private void drawDockMiniToolbar(DrawContext context, int x, int y, int w) {
        context.fill(x, y, x + w, y + 11, 0xFF292C36);
        int step = Math.max(11, Math.min(18, w / 5));
        UIHelper.drawIcon(context, UIHelper.Icon.PLUS, x + 3, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.MINUS, x + 3 + step, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.SETTINGS, x + 3 + step * 2, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.UP, x + 3 + step * 3, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.DOWN, x + 3 + step * 4, y + 2, 0xFFBDC1C6);
    }

    private void drawDbMeterBar(DrawContext context, int x, int y, int w, float activeRatio) {
        context.fill(x, y, x + w, y + 5, 0xFF14161B);
        int activeW = (int) (w * Math.min(1.0f, activeRatio));
        int greenEnd = (int) (w * 0.70f);
        int yellowEnd = (int) (w * 0.88f);

        if (activeW > 0) context.fill(x, y, x + Math.min(activeW, greenEnd), y + 5, 0xFF2ECC71);
        if (activeW > greenEnd) context.fill(x + greenEnd, y, x + Math.min(activeW, yellowEnd), y + 5, 0xFFF1C40F);
        if (activeW > yellowEnd) context.fill(x + yellowEnd, y, x + activeW, y + 5, 0xFFE74C3C);
    }

    private void drawBlueVolumeSlider(DrawContext context, int x, int y, int w, float percent) {
        context.fill(x, y + 1, x + w, y + 4, 0xFF3A3F4D);
        int fillW = (int) (w * Math.max(0.0f, Math.min(1.0f, percent)));
        context.fill(x, y + 1, x + fillW, y + 4, 0xFF3B82F6);
        context.fill(x + Math.max(0, fillW - 2), y - 1, x + Math.min(w, fillW + 2), y + 6, 0xFFFFFFFF);
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() <= len ? s : s.substring(0, len);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        double mx = click.x();
        double my = click.y();
        RecordingConfig cfg = RecordingConfig.get();

        if (contextMenuOpen) {
            int cmW = 145;
            int cmH = contextMenuItems.length * 14 + 4;
            int cx = Math.min(contextMenuX, this.width - cmW - 4);
            int cy = Math.min(contextMenuY, this.height - cmH - 4);
            if (mx >= cx && mx <= cx + cmW && my >= cy && my <= cy + cmH) {
                int idx = (int) ((my - cy - 2) / 14);
                if (idx == 0) SceneCollectionManager.addSource("Display Capture", UIHelper.Icon.VIDEO);
                else if (idx == 1) SceneCollectionManager.addSource("Game Capture", UIHelper.Icon.VIDEO);
                else if (idx == 2) {
                    List<String> mics = MicrophoneManager.getAvailableMicrophoneDevices();
                    cfg.microphoneDevice = mics.get((mics.indexOf(cfg.microphoneDevice) + 1) % mics.size());
                    cfg.microphoneEnabled = true;
                    RecordingConfig.save();
                } else if (idx == 3) {
                    RecordingConfig.ResolutionPreset[] vals = RecordingConfig.ResolutionPreset.values();
                    cfg.resolutionPreset = vals[(cfg.resolutionPreset.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                } else if (idx == 4) {
                    this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.AUDIO));
                } else if (idx == 5) {
                    SceneCollectionManager.removeSelectedSource();
                }
                this.clearAndInit();
            }
            contextMenuOpen = false;
            return true;
        }

        computeFloatingWindowBounds();
        int pad = 6;
        int dockY = winY + 20;
        int bottomBarH = 15;
        int dockAreaH = (winY + winH - bottomBarH - 3) - dockY;
        int totalDockW = winW - pad * 2;
        int gap = 4;
        int colW = (totalDockW - gap * 3) / 4;

        int scenesX = winX + pad;
        int sourcesX = winX + pad + colW + gap;
        int mixerX = winX + pad + (colW + gap) * 2 + 5;
        int mixerW = colW - 10;

        if (click.button() == 1 && mx >= winX && mx <= winX + winW && my >= winY && my <= winY + winH) {
            contextMenuOpen = true;
            contextMenuX = (int) mx;
            contextMenuY = (int) my;
            return true;
        }

        if (mx >= scenesX && mx <= scenesX + colW && my >= dockY + 15 && my <= dockY + dockAreaH) {
            if (my >= dockY + dockAreaH - 13) {
                int step = Math.max(11, Math.min(18, (colW - 4) / 5));
                int relX = (int) (mx - scenesX - 2);
                if (relX < step) SceneCollectionManager.addScene();
                else if (relX < step * 2) SceneCollectionManager.removeSelectedScene();
                else if (relX < step * 3) this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.GENERAL));
                else if (relX < step * 4) SceneCollectionManager.moveSceneUp();
                else SceneCollectionManager.moveSceneDown();
            } else {
                int idx = (int) ((my - (dockY + 16)) / 15);
                SceneCollectionManager.setSelectedSceneIdx(idx);
            }
            return true;
        }

        if (mx >= sourcesX && mx <= sourcesX + colW && my >= dockY + 15 && my <= dockY + dockAreaH) {
            if (my >= dockY + dockAreaH - 13) {
                int step = Math.max(11, Math.min(18, (colW - 4) / 5));
                int relX = (int) (mx - sourcesX - 2);
                if (relX < step) {
                    contextMenuOpen = true;
                    contextMenuX = (int) mx;
                    contextMenuY = (int) my - 85;
                } else if (relX < step * 2) {
                    SceneCollectionManager.removeSelectedSource();
                } else if (relX < step * 3) {
                    this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.VIDEO));
                }
            } else {
                int idx = (int) ((my - (dockY + 16)) / 15);
                List<SceneCollectionManager.SourceItem> sources = SceneCollectionManager.getSources();
                if (idx >= 0 && idx < sources.size()) {
                    SceneCollectionManager.setSelectedSourceIdx(idx);
                    SceneCollectionManager.toggleSourceVisibility(idx);
                    this.clearAndInit();
                }
            }
            return true;
        }

        int dSliderY = dockY + 18 + 19;
        int mSliderY = dockY + 18 + 32 + 19;

        if (mx >= mixerX && mx <= mixerX + mixerW) {
            if (my >= dSliderY - 4 && my <= dSliderY + 8) {
                draggingDesktopVol = true;
                updateVolumeFromMouse(mx, mixerX, mixerW, true);
                return true;
            }
            if (my >= mSliderY - 4 && my <= mSliderY + 8) {
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
            int pad = 6;
            int totalDockW = winW - pad * 2;
            int gap = 4;
            int colW = (totalDockW - gap * 3) / 4;
            int mixerX = winX + pad + (colW + gap) * 2 + 5;
            int mixerW = colW - 10;
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
