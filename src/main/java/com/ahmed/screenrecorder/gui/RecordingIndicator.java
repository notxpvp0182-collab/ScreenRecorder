package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.capture.RecordingManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class RecordingIndicator {

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof RecorderScreen) {
            renderCleanOverlay(context);
        }
    }

    public static void renderCleanOverlay(DrawContext context) {
        RecordingConfig cfg = RecordingConfig.get();
        RecordingManager mgr = RecordingManager.getInstance();
        if (!cfg.showIndicator || !mgr.isRecording()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        String text = "● REC " + mgr.getFormattedDuration();

        int padX = 6;
        int padY = 4;
        int boxWidth = tr.getWidth(text) + padX * 2;
        int boxHeight = tr.fontHeight + padY * 2;

        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        int margin = 8;

        int x = switch (cfg.indicatorPosition) {
            case TOP_LEFT, BOTTOM_LEFT -> margin;
            case TOP_RIGHT, BOTTOM_RIGHT -> sw - boxWidth - margin;
        };

        int y = switch (cfg.indicatorPosition) {
            case TOP_LEFT, TOP_RIGHT -> margin;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - boxHeight - margin;
        };

        context.fill(x, y, x + boxWidth, y + boxHeight, 0xE60D0D0F);
        UIHelper.drawBorder(context, x, y, boxWidth, boxHeight, 0xFFB71C1C);

        context.drawTextWithShadow(tr, "●", x + padX, y + padY, 0xFFE53935);
        int dotWidth = tr.getWidth("● ");
        context.drawTextWithShadow(tr, "REC " + mgr.getFormattedDuration(), x + padX + dotWidth, y + padY, 0xFFFFFFFF);
    }
}
