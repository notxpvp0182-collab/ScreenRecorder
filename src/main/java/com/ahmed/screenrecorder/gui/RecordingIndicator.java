package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.capture.RecordingManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Renders the in-game ● REC indicator.
 * Note: Because frame capture executes in GameRendererMixin, and this indicator
 * renders only when allowed or outside the raw capture buffer, it never pollutes the MP4.
 */
public class RecordingIndicator {

    /**
     * Note on MP4 exclusion:
     * To guarantee the REC indicator is strictly visible to the player on screen
     * while NEVER appearing in the captured MP4 frames, we render it via GLFW/SwapBuffers
     * overlay or immediately after capture. Since HudRenderCallback runs during GameRenderer,
     * we suppress HudRenderCallback drawing and instead invoke renderPostCapture from
     * RecorderScreen / Post-capture hook.
     */
    public static void render(DrawContext context) {
        // Intentionally left empty during standard HUD pass so GameRendererMixin
        // captures a 100% clean game frame without the REC indicator.
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

        // Solid dark background + dark red border
        context.fill(x, y, x + boxWidth, y + boxHeight, 0xE60D0D0F);
        context.drawBorder(x, y, boxWidth, boxHeight, 0xFFB71C1C);

        // Red dot + white timer text
        context.drawTextWithShadow(tr, "●", x + padX, y + padY, 0xFFE53935);
        int dotWidth = tr.getWidth("● ");
        context.drawTextWithShadow(tr, "REC " + mgr.getFormattedDuration(), x + padX + dotWidth, y + padY, 0xFFFFFFFF);
    }
}
