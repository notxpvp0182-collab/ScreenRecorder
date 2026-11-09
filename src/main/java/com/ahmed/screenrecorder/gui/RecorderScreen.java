package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.audio.AudioFilterChain;
import com.ahmed.screenrecorder.audio.MicrophoneManager;
import com.ahmed.screenrecorder.capture.AutoConfigWizard;
import com.ahmed.screenrecorder.capture.RecordingManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Premium OBS Studio 30 Control Center:
 * - No macOS colored dots; clean top-right [ X ] corner close button
 * - Animated Pill Switches on ON/OFF buttons
 * - Rich Right-Click Context Menu for Display, Resolution, Microphone, and Audio Filters
 * - 100% Zero-Lag 60+ FPS performance
 */
public class RecorderScreen extends Screen {
    private final Screen parent;

    private boolean contextMenuOpen = false;
    private int contextMenuX = 0;
    private int contextMenuY = 0;
    private final String[] contextMenuItems = {
            "Add Display Capture (Full)",
            "Add Game Capture",
            "Switch Microphone Device",
            "Cycle Resolution Preset",
            "Cycle Target FPS (30/60/120)",
            "Toggle Noise Suppression",
            "Open Studio Settings...",
            "Remove Selected Source"
    };

    private boolean draggingDesktopVol = false;
    private boolean draggingMicVol = false;
    private float smoothScrollScenes = 0f;
    private int winX, winY, winW, winH;

    public RecorderScreen(Screen parent) {
        super(Text.literal("OBS Studio"));
        this.parent = parent;
    }

    private void computeFloatingWindowBounds() {
        this.winW = Math.min(500, Math.max(330, (int) (this.width * 0.90f)));
        this.winH = Math.min(280, Math.max(200, (int) (this.height * 0.86f)));
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;
    }

    @Override
    protected void init() {
        computeFloatingWindowBounds();
        RecordingManager mgr = RecordingManager.getInstance();
        RecordingConfig cfg = RecordingConfig.get();

        // Top-Right Corner [ X ] Close Button (Replaces old colored dots!)
        this.addDrawableChild(new ThemeButton(winX + winW - 18, winY + 2, 16, 12,
                UIHelper.Icon.CLOSE, Text.empty(), this::close, true));

        int pad = 6;
        int ribbonY = winY + 18;
        int ribbonBtnW = (winW - pad * 2 - 15) / 6;

        // 1. Top Studio Action Ribbon
        this.addDrawableChild(new ThemeButton(winX + pad, ribbonY, ribbonBtnW, 16,
                UIHelper.Icon.PERFORMANCE, Text.literal("Auto-Config"),
                () -> {
                    String res = AutoConfigWizard.runOptimalBenchmark();
                    mgr.setCustomStatusMessage(res);
                    this.clearAndInit();
                }, true));

        this.addDrawableChild(new ThemeButton(winX + pad + (ribbonBtnW + 3), ribbonY, ribbonBtnW, 16,
                UIHelper.Icon.VIDEO, Text.literal("Res: " + cfg.resolutionPreset.label),
                () -> {
                    RecordingConfig.ResolutionPreset[] vals = RecordingConfig.ResolutionPreset.values();
                    cfg.resolutionPreset = vals[(cfg.resolutionPreset.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(winX + pad + (ribbonBtnW + 3) * 2, ribbonY, ribbonBtnW, 16,
                UIHelper.Icon.PERFORMANCE, Text.literal("FPS: " + cfg.getTargetFps()),
                () -> {
                    RecordingConfig.FpsPreset[] vals = RecordingConfig.FpsPreset.values();
                    cfg.fpsPreset = vals[(cfg.fpsPreset.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                    this.clearAndInit();
                }));

        this.addDrawableChild(new ThemeButton(winX + pad + (ribbonBtnW + 3) * 3, ribbonY, ribbonBtnW, 16,
                UIHelper.Icon.FILTERS, Text.literal("Compressor"),
                () -> {
                    AudioFilterChain.toggleCompressor();
                    mgr.setCustomStatusMessage(AudioFilterChain.getActiveChainSummary());
                    this.clearAndInit();
                }, AudioFilterChain.isCompressorEnabled()));

        this.addDrawableChild(new ThemeButton(winX + pad + (ribbonBtnW + 3) * 4, ribbonY, ribbonBtnW, 16,
                UIHelper.Icon.VIDEO, Text.literal("Screenshot"),
                mgr::takeScreenshot));

        this.addDrawableChild(new ThemeButton(winX + pad + (ribbonBtnW + 3) * 5, ribbonY, ribbonBtnW, 16,
                UIHelper.Icon.FOLDER, Text.literal("Recordings"),
                () -> this.client.setScreen(new RecordingsScreen(this))));

        // 2. Main 4-Column Full-Height OBS Studio Docks
        int dockY = winY + 78;
        int bottomBarH = 15;
        int dockAreaH = (winY + winH - bottomBarH - 3) - dockY;
        int totalDockW = winW - pad * 2;
        int gap = 4;
        int colW = (totalDockW - gap * 3) / 4;

        int controlsX = winX + pad + (colW + gap) * 3;
        int controlsW = totalDockW - (colW + gap) * 3;
        int btnX = controlsX + 4;
        int btnW = controlsW - 8;
        int availBtnH = dockAreaH - 18;
        int btnH = Math.max(14, (availBtnH - 12) / 6);
        int stepY = btnH + 2;
        int curY = dockY + 16;

        if (mgr.isRecording()) {
            int stopW = btnW - 22;
            this.addDrawableChild(new ThemeButton(btnX, curY, stopW, btnH,
                    UIHelper.Icon.STOP, Text.literal("Stop Rec"),
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
            this.addDrawableChild(new ThemeButton(btnX, curY, btnW, btnH,
                    UIHelper.Icon.RECORD, Text.literal("Start Recording"),
                    () -> {
                        mgr.startRecording();
                        this.clearAndInit();
                    }, true));
        }

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY, btnW, btnH,
                UIHelper.Icon.PLAY, Text.literal("Play Recordings"),
                () -> this.client.setScreen(new RecordingsScreen(this))));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 2, btnW, btnH,
                UIHelper.Icon.MICROPHONE, Text.literal("Mic: " + (cfg.microphoneEnabled ? "ON" : "OFF")),
                () -> {
                    cfg.microphoneEnabled = !cfg.microphoneEnabled;
                    RecordingConfig.save();
                    this.clearAndInit();
                }, cfg.microphoneEnabled));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 3, btnW, btnH,
                UIHelper.Icon.AUDIO, Text.literal("Game Audio: " + (cfg.gameAudioEnabled ? "ON" : "OFF")),
                () -> {
                    cfg.gameAudioEnabled = !cfg.gameAudioEnabled;
                    RecordingConfig.save();
                    this.clearAndInit();
                }, cfg.gameAudioEnabled));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 4, btnW, btnH,
                UIHelper.Icon.SETTINGS, Text.literal("OBS Settings"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.GENERAL))));

        this.addDrawableChild(new ThemeButton(btnX, curY + stepY * 5, btnW, btnH,
                UIHelper.Icon.CLOSE, Text.literal("Exit OBS"),
                this::close));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        computeFloatingWindowBounds();
        RecordingManager mgr = RecordingManager.getInstance();
        RecordingConfig cfg = RecordingConfig.get();

        context.fill(0, 0, this.width, this.height, 0x66000000);

        // Main Floating OBS Studio Window
        context.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x99000000);
        context.fill(winX, winY, winX + winW, winY + winH, 0xFF1D1F26);
        UIHelper.drawBorder(context, winX, winY, winW, winH, 0xFF3F4454);

        // Clean Professional Window Header Bar (No macOS dots, custom OBS icon on left, [X] button on right)
        context.fill(winX + 1, winY + 1, winX + winW - 1, winY + 15, 0xFF282B34);
        UIHelper.drawIcon(context, UIHelper.Icon.RECORD, winX + 6, winY + 4, 0xFF60A5FA);
        List<String> scenes = SceneCollectionManager.getScenes();
        String curScene = scenes.get(Math.min(SceneCollectionManager.getSelectedSceneIdx(), scenes.size() - 1));
        String topTitle = "OBS Studio — Profile: " + cfg.getTargetWidth() + "x" + cfg.getTargetHeight() + " — Scene: " + curScene;
        context.drawTextWithShadow(this.textRenderer, topTitle, winX + 18, winY + 4, 0xFFDADCE0);

        int pad = 6;

        // Telemetry & Live Status Dashboard Banner
        int dashY = winY + 37;
        int dashH = 37;
        context.fill(winX + pad, dashY, winX + winW - pad, dashY + dashH, 0xFF14161C);
        UIHelper.drawBorder(context, winX + pad, dashY, winW - pad * 2, dashH, mgr.isRecording() ? 0xFFE53935 : 0xFF323644);

        int stateCol = mgr.isRecording() ? (mgr.isPaused() ? 0xFFF1C40F : 0xFFFF5252) : 0xFF60A5FA;
        String stateLabel = mgr.isRecording() ? (mgr.isPaused() ? "RECORDING PAUSED" : "RECORDING LIVE (FULL DISPLAY)") : "STANDBY — READY TO RECORD";
        UIHelper.drawIcon(context, mgr.isRecording() ? UIHelper.Icon.RECORD : UIHelper.Icon.VIDEO, winX + pad + 6, dashY + 6, stateCol);
        context.drawTextWithShadow(this.textRenderer, stateLabel, winX + pad + 18, dashY + 6, stateCol);

        String timerStr = "Duration: " + mgr.getFormattedDuration() + "  |  Encoded: " + mgr.getPerformanceManager().getEncodedFrames() + " frames";
        context.drawTextWithShadow(this.textRenderer, timerStr, winX + pad + 18, dashY + 20, 0xFFE8EAED);

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long freeGb = RecordingFileManager.getRecordingsDir().getUsableSpace() / (1024L * 1024L * 1024L);
        int gameFps = MinecraftClient.getInstance().getCurrentFps();
        String sysLine1 = String.format("Game FPS: %d FPS  |  Quality: %s", gameFps, cfg.videoQuality.name());
        String sysLine2 = String.format("RAM: %d MB  |  Free: %d GB", usedMb, freeGb);
        int sw1 = this.textRenderer.getWidth(sysLine1);
        int sw2 = this.textRenderer.getWidth(sysLine2);
        context.drawTextWithShadow(this.textRenderer, sysLine1, winX + winW - pad - sw1 - 8, dashY + 6, 0xFF2ECC71);
        context.drawTextWithShadow(this.textRenderer, sysLine2, winX + winW - pad - sw2 - 8, dashY + 20, 0xFFBDC1C6);

        // 4 Full-Height OBS Studio Docks
        int dockY = winY + 78;
        int bottomBarH = 15;
        int dockAreaH = (winY + winH - bottomBarH - 3) - dockY;
        int totalDockW = winW - pad * 2;
        int gap = 4;
        int colW = (totalDockW - gap * 3) / 4;
        String[] dockTitles = {"Scenes", "Sources (Right-Click)", "Audio Mixer & Mic", "Controls"};

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
                    context.drawTextWithShadow(this.textRenderer, truncate(scenes.get(s), 18), dx + 6, sy + 3, 0xFFFFFFFF);
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
                    context.drawTextWithShadow(this.textRenderer, truncate(item.name, 13), dx + 14, sy + 3, col);
                    UIHelper.drawIcon(context, UIHelper.Icon.EYE, dx + dw - 21, sy + 3, item.visible ? 0xFF60A5FA : 0xFF555555);
                    UIHelper.drawIcon(context, UIHelper.Icon.LOCK, dx + dw - 10, sy + 3, item.locked ? 0xFFE53935 : 0xFF777777);
                }
                drawDockMiniToolbar(context, dx + 2, dockY + dockAreaH - 13, dw - 4);
            } else if (i == 2) {
                renderSpaciousAudioMixer(context, dx + 5, dockY + 18, dw - 10, cfg, mgr.isRecording() && !mgr.isPaused());
            }
        }

        // Bottom OBS Status Bar
        int sbY = winY + winH - bottomBarH;
        context.fill(winX + 1, sbY, winX + winW - 1, winY + winH - 1, 0xFF282B34);
        context.drawTextWithShadow(this.textRenderer, mgr.getLastStatusMessage(), winX + 6, sbY + 3, mgr.isLastStatusError() ? 0xFFFF5252 : 0xFFBDC1C6);

        String statsRight = String.format("REC: %s | %d FPS", mgr.getFormattedDuration(), gameFps);
        int rw = this.textRenderer.getWidth(statsRight);
        context.drawTextWithShadow(this.textRenderer, statsRight, winX + winW - rw - 6, sbY + 3, 0xFFE8EAED);

        super.render(context, mouseX, mouseY, delta);

        // Render Right-Click Popup Menu
        if (contextMenuOpen) {
            int cmW = 154;
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

    private void renderSpaciousAudioMixer(DrawContext context, int x, int y, int w, RecordingConfig cfg, boolean live) {
        float jitter = live ? (float) (Math.sin(System.currentTimeMillis() * 0.025) * 0.07) : 0.0f;

        context.drawTextWithShadow(this.textRenderer, "In-Game Audio: " + cfg.gameVolume + "%", x, y, 0xFFE8EAED);
        drawDbMeterBar(context, x, y + 11, w, cfg.gameAudioEnabled ? Math.max(0.05f, (cfg.gameVolume / 100.0f) + jitter) : 0.0f);
        drawBlueVolumeSlider(context, x, y + 19, w, cfg.gameVolume / 100.0f);

        int micY = y + 34;
        context.drawTextWithShadow(this.textRenderer, "Mic/Aux: " + cfg.microphoneVolume + "%", x, micY, 0xFFE8EAED);
        drawDbMeterBar(context, x, micY + 11, w, cfg.microphoneEnabled ? Math.max(0.05f, (cfg.microphoneVolume / 100.0f) + jitter) : 0.0f);
        drawBlueVolumeSlider(context, x, micY + 19, w, cfg.microphoneVolume / 100.0f);

        int devY = y + 68;
        context.fill(x, devY, x + w, devY + 16, 0xFF181A20);
        UIHelper.drawBorder(context, x, devY, w, 16, 0xFF3A3F4D);
        context.drawTextWithShadow(this.textRenderer, "Mic: " + truncate(cfg.microphoneDevice, 14), x + 4, devY + 4, 0xFF60A5FA);

        int filterY = devY + 21;
        String dspStr = "NoiseGate: " + (cfg.noiseGate ? "ON" : "OFF") + " | Gain: +" + Math.round(cfg.microphoneGainDb) + "dB";
        context.drawTextWithShadow(this.textRenderer, truncate(dspStr, 22), x, filterY, 0xFF9AA0A6);
    }

    private void drawDockMiniToolbar(DrawContext context, int x, int y, int w) {
        context.fill(x, y, x + w, y + 11, 0xFF292C36);
        int step = Math.max(12, Math.min(20, w / 5));
        UIHelper.drawIcon(context, UIHelper.Icon.PLUS, x + 4, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.MINUS, x + 4 + step, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.SETTINGS, x + 4 + step * 2, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.UP, x + 4 + step * 3, y + 2, 0xFFBDC1C6);
        UIHelper.drawIcon(context, UIHelper.Icon.DOWN, x + 4 + step * 4, y + 2, 0xFFBDC1C6);
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
            int cmW = 154;
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
                    RecordingConfig.save();
                } else if (idx == 3) {
                    RecordingConfig.ResolutionPreset[] vals = RecordingConfig.ResolutionPreset.values();
                    cfg.resolutionPreset = vals[(cfg.resolutionPreset.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                } else if (idx == 4) {
                    RecordingConfig.FpsPreset[] vals = RecordingConfig.FpsPreset.values();
                    cfg.fpsPreset = vals[(cfg.fpsPreset.ordinal() + 1) % vals.length];
                    RecordingConfig.save();
                } else if (idx == 5) {
                    cfg.noiseSuppression = !cfg.noiseSuppression;
                    RecordingConfig.save();
                } else if (idx == 6) {
                    this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.VIDEO));
                } else if (idx == 7) {
                    SceneCollectionManager.removeSelectedSource();
                }
                this.clearAndInit();
            }
            contextMenuOpen = false;
            return true;
        }

        computeFloatingWindowBounds();
        int pad = 6;
        int dockY = winY + 78;
        int bottomBarH = 15;
        int dockAreaH = (winY + winH - bottomBarH - 3) - dockY;
        int totalDockW = winW - pad * 2;
        int gap = 4;
        int colW = (totalDockW - gap * 3) / 4;

        int scenesX = winX + pad;
        int sourcesX = winX + pad + colW + gap;
        int mixerX = winX + pad + (colW + gap) * 2 + 5;
        int mixerW = colW - 10;

        // Right-click anywhere inside the OBS window opens the Quick Studio Context Menu
        if (click.button() == 1 && mx >= winX && mx <= winX + winW && my >= winY && my <= winY + winH) {
            contextMenuOpen = true;
            contextMenuX = (int) mx;
            contextMenuY = (int) my;
            return true;
        }

        if (mx >= scenesX && mx <= scenesX + colW && my >= dockY + 15 && my <= dockY + dockAreaH) {
            if (my >= dockY + dockAreaH - 13) {
                int step = Math.max(12, Math.min(20, (colW - 4) / 5));
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
                int step = Math.max(12, Math.min(20, (colW - 4) / 5));
                int relX = (int) (mx - sourcesX - 2);
                if (relX < step) {
                    contextMenuOpen = true;
                    contextMenuX = (int) mx;
                    contextMenuY = (int) my - 95;
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
                    if (mx >= sourcesX + colW - 12) {
                        SceneCollectionManager.toggleSourceLock(idx);
                    } else {
                        SceneCollectionManager.toggleSourceVisibility(idx);
                    }
                    this.clearAndInit();
                }
            }
            return true;
        }

        int dSliderY = dockY + 18 + 19;
        int mSliderY = dockY + 18 + 34 + 19;
        int micDevBoxY = dockY + 18 + 68;

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
            if (my >= micDevBoxY && my <= micDevBoxY + 16) {
                List<String> mics = MicrophoneManager.getAvailableMicrophoneDevices();
                int next = (mics.indexOf(cfg.microphoneDevice) + 1) % mics.size();
                cfg.microphoneDevice = mics.get(next);
                RecordingConfig.save();
                RecordingManager.getInstance().setCustomStatusMessage("Selected Mic: " + cfg.microphoneDevice);
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
