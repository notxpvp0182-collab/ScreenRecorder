package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.capture.RecordingManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Main Screen Recorder Dashboard with OBS-inspired Dark + Red + White layout.
 */
public class RecorderScreen extends Screen {
    private final Screen parent;
    private ThemeButton startButton;
    private ThemeButton stopButton;

    public RecorderScreen(Screen parent) {
        super(Text.literal("SCREEN RECORDER"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int panelWidth = 340;
        int startY = Math.max(36, (this.height - 270) / 2);

        int btnW = 154;
        int btnH = 24;
        int gap = 8;

        // Primary Start / Stop Row
        startButton = new ThemeButton(
                centerX - btnW - gap / 2, startY + 42, btnW, 28,
                Text.literal("● START RECORDING"),
                () -> {
                    RecordingManager.getInstance().startRecording();
                    updateButtonStates();
                },
                true
        );

        stopButton = new ThemeButton(
                centerX + gap / 2, startY + 42, btnW, 28,
                Text.literal("■ STOP RECORDING"),
                () -> {
                    RecordingManager.getInstance().stopRecording();
                    updateButtonStates();
                },
                false
        );

        this.addDrawableChild(startButton);
        this.addDrawableChild(stopButton);

        // Navigation Grid (2 Columns x 4 Rows)
        int row1Y = startY + 84;
        this.addDrawableChild(new ThemeButton(
                centerX - btnW - gap / 2, row1Y, btnW, btnH,
                Text.literal("📁 RECORDINGS"),
                () -> this.client.setScreen(new RecordingsScreen(this))
        ));
        this.addDrawableChild(new ThemeButton(
                centerX + gap / 2, row1Y, btnW, btnH,
                Text.literal("⚙ SETTINGS"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.RECORDING))
        ));

        int row2Y = row1Y + btnH + gap;
        this.addDrawableChild(new ThemeButton(
                centerX - btnW - gap / 2, row2Y, btnW, btnH,
                Text.literal("🎙 MICROPHONE"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.MICROPHONE))
        ));
        this.addDrawableChild(new ThemeButton(
                centerX + gap / 2, row2Y, btnW, btnH,
                Text.literal("🎞 VIDEO"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.VIDEO))
        ));

        int row3Y = row2Y + btnH + gap;
        this.addDrawableChild(new ThemeButton(
                centerX - btnW - gap / 2, row3Y, btnW, btnH,
                Text.literal("🔊 AUDIO"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.AUDIO))
        ));
        this.addDrawableChild(new ThemeButton(
                centerX + gap / 2, row3Y, btnW, btnH,
                Text.literal("⌨ CONTROLS"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.CONTROLS))
        ));

        int row4Y = row3Y + btnH + gap;
        this.addDrawableChild(new ThemeButton(
                centerX - btnW - gap / 2, row4Y, btnW, btnH,
                Text.literal("⚡ PERFORMANCE"),
                () -> this.client.setScreen(new RecorderSettingsScreen(this, RecorderSettingsScreen.Category.PERFORMANCE))
        ));
        this.addDrawableChild(new ThemeButton(
                centerX + gap / 2, row4Y, btnW, btnH,
                Text.literal("X CLOSE"),
                this::close
        ));

        updateButtonStates();
    }

    private void updateButtonStates() {
        RecordingManager mgr = RecordingManager.getInstance();
        if (startButton != null && stopButton != null) {
            startButton.active = !mgr.isRecording() && !mgr.isStopping();
            stopButton.active = mgr.isRecording() && !mgr.isStopping();
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonStates();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Solid Dark Overlay
        context.fill(0, 0, this.width, this.height, 0xD908080A);

        int panelW = 350;
        int panelH = 265;
        int px = (this.width - panelW) / 2;
        int py = Math.max(28, (this.height - panelH) / 2);

        // Main Studio Panel
        context.fill(px, py, px + panelW, py + panelH, 0xFF101014);
        context.drawBorder(px, py, panelW, panelH, 0xFF8E1616);

        // Header Bar
        context.fill(px + 1, py + 1, px + panelW - 1, py + 30, 0xFF1A0D0E);
        context.fill(px + 1, py + 30, px + panelW - 1, py + 31, 0xFFB71C1C);

        context.drawCenteredTextWithShadow(this.textRenderer, "SCREEN RECORDER", this.width / 2, py + 11, 0xFFFFFFFF);

        super.render(context, mouseX, mouseY, delta);

        // Status Telemetry Footer Box
        int footerY = py + panelH - 46;
        context.fill(px + 14, footerY, px + panelW - 14, py + panelH - 10, 0xFF0B0B0D);
        context.drawBorder(px + 14, footerY, panelW - 28, 36, 0xFF3A1818);

        RecordingManager mgr = RecordingManager.getInstance();
        RecordingConfig cfg = RecordingConfig.get();

        String profileText = String.format(
                "Profile: %dx%d @ %d FPS | %s | Mic: %s",
                cfg.getTargetWidth(), cfg.getTargetHeight(), cfg.getTargetFps(),
                cfg.videoQuality.name(), cfg.microphoneEnabled ? "ON" : "OFF"
        );
        context.drawTextWithShadow(this.textRenderer, profileText, px + 22, footerY + 7, 0xFFB0B0B5);

        String status = mgr.isRecording()
                ? "● LIVE [" + mgr.getFormattedDuration() + "] - Encoded: " + mgr.getPerformanceManager().getEncodedFrames() + " frames"
                : mgr.getLastStatusMessage();
        int statusColor = mgr.isLastStatusError() ? 0xFFFF5252 : (mgr.isRecording() ? 0xFFE53935 : 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, status, px + 22, footerY + 20, statusColor);
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
