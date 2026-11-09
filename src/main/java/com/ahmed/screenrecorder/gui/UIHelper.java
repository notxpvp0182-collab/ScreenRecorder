package com.ahmed.screenrecorder.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Renders crisp, pixel-perfect custom studio icons and borders directly via DrawContext.
 * Zero unicode emojis used.
 */
public class UIHelper {

    public enum Icon {
        NONE,
        RECORD,
        STOP,
        PLAY,
        PAUSE,
        RESTART,
        FOLDER,
        SETTINGS,
        MICROPHONE,
        AUDIO,
        VIDEO,
        FILTERS,
        PERFORMANCE,
        DELETE,
        REFRESH,
        CLOSE,
        PLUS,
        MINUS,
        UP,
        DOWN
    }

    public static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /**
     * Draws a crisp 8x8 custom vector-style icon at (x, y).
     */
    public static void drawIcon(DrawContext ctx, Icon icon, int x, int y, int color) {
        if (icon == null || icon == Icon.NONE) return;

        switch (icon) {
            case RECORD -> {
                // Crisp circular recording indicator with inner highlight
                ctx.fill(x + 2, y + 1, x + 6, y + 7, 0xFFE53935);
                ctx.fill(x + 1, y + 2, x + 7, y + 6, 0xFFE53935);
                ctx.fill(x + 2, y + 2, x + 4, y + 4, 0xFFFF8A80);
            }
            case STOP -> {
                ctx.fill(x + 1, y + 1, x + 7, y + 7, color);
            }
            case PLAY -> {
                ctx.fill(x + 2, y + 1, x + 3, y + 7, color);
                ctx.fill(x + 3, y + 2, x + 5, y + 6, color);
                ctx.fill(x + 5, y + 3, x + 7, y + 5, color);
            }
            case PAUSE -> {
                ctx.fill(x + 1, y + 1, x + 3, y + 7, color);
                ctx.fill(x + 5, y + 1, x + 7, y + 7, color);
            }
            case RESTART -> {
                ctx.fill(x + 1, y + 1, x + 2, y + 7, color);
                ctx.fill(x + 3, y + 3, x + 5, y + 5, color);
                ctx.fill(x + 5, y + 2, x + 7, y + 6, color);
            }
            case FOLDER -> {
                // Tabbed studio folder icon
                ctx.fill(x, y + 1, x + 3, y + 2, 0xFFE53935);
                ctx.fill(x, y + 2, x + 8, y + 7, color);
                ctx.fill(x + 1, y + 3, x + 7, y + 6, 0xFF1A1C22);
            }
            case SETTINGS -> {
                // Precision gear cog icon
                ctx.fill(x + 3, y, x + 5, y + 8, color);
                ctx.fill(x, y + 3, x + 8, y + 5, color);
                ctx.fill(x + 1, y + 1, x + 7, y + 7, color);
                ctx.fill(x + 3, y + 3, x + 5, y + 5, 0xFF16181E);
            }
            case MICROPHONE -> {
                // Studio condenser microphone icon
                ctx.fill(x + 3, y, x + 5, y + 4, color);
                ctx.fill(x + 1, y + 3, x + 2, y + 5, color);
                ctx.fill(x + 6, y + 3, x + 7, y + 5, color);
                ctx.fill(x + 2, y + 5, x + 6, y + 6, color);
                ctx.fill(x + 3, y + 6, x + 5, y + 7, color);
                ctx.fill(x + 2, y + 7, x + 6, y + 8, color);
            }
            case AUDIO -> {
                // Speaker cone + sound waves
                ctx.fill(x + 1, y + 3, x + 3, y + 5, color);
                ctx.fill(x + 3, y + 2, x + 4, y + 6, color);
                ctx.fill(x + 4, y + 1, x + 5, y + 7, color);
                ctx.fill(x + 6, y + 2, x + 7, y + 3, 0xFFE53935);
                ctx.fill(x + 6, y + 5, x + 7, y + 6, 0xFFE53935);
            }
            case VIDEO -> {
                // Camera / Film frame icon
                ctx.fill(x, y + 1, x + 6, y + 7, color);
                ctx.fill(x + 1, y + 2, x + 5, y + 6, 0xFF16181E);
                ctx.fill(x + 6, y + 3, x + 7, y + 5, color);
                ctx.fill(x + 7, y + 2, x + 8, y + 6, color);
            }
            case FILTERS -> {
                // 3-band EQ slider icon
                ctx.fill(x + 1, y + 1, x + 7, y + 2, color);
                ctx.fill(x + 5, y, x + 6, y + 3, 0xFFE53935);
                ctx.fill(x + 1, y + 4, x + 7, y + 5, color);
                ctx.fill(x + 2, y + 3, x + 3, y + 6, 0xFFE53935);
                ctx.fill(x + 1, y + 6, x + 7, y + 7, color);
                ctx.fill(x + 4, y + 5, x + 5, y + 8, 0xFFE53935);
            }
            case PERFORMANCE -> {
                // High-voltage bolt icon
                ctx.fill(x + 4, y, x + 6, y + 3, 0xFFFFCA28);
                ctx.fill(x + 2, y + 3, x + 6, y + 5, 0xFFFFCA28);
                ctx.fill(x + 2, y + 5, x + 4, y + 8, 0xFFFFCA28);
            }
            case DELETE -> {
                ctx.fill(x + 2, y, x + 6, y + 1, color);
                ctx.fill(x + 1, y + 1, x + 7, y + 2, color);
                ctx.fill(x + 2, y + 3, x + 6, y + 8, color);
            }
            case REFRESH -> {
                ctx.fill(x + 2, y + 1, x + 6, y + 2, color);
                ctx.fill(x + 1, y + 2, x + 2, y + 6, color);
                ctx.fill(x + 2, y + 6, x + 6, y + 7, color);
                ctx.fill(x + 6, y + 3, x + 7, y + 6, color);
                ctx.fill(x + 5, y, x + 7, y + 3, color);
            }
            case CLOSE -> {
                ctx.fill(x + 1, y + 1, x + 3, y + 3, color);
                ctx.fill(x + 5, y + 1, x + 7, y + 3, color);
                ctx.fill(x + 3, y + 3, x + 5, y + 5, color);
                ctx.fill(x + 1, y + 5, x + 3, y + 7, color);
                ctx.fill(x + 5, y + 5, x + 7, y + 7, color);
            }
            case PLUS -> {
                ctx.fill(x + 3, y + 1, x + 5, y + 7, color);
                ctx.fill(x + 1, y + 3, x + 7, y + 5, color);
            }
            case MINUS -> {
                ctx.fill(x + 1, y + 3, x + 7, y + 5, color);
            }
            case UP -> {
                ctx.fill(x + 3, y + 2, x + 5, y + 3, color);
                ctx.fill(x + 2, y + 3, x + 6, y + 4, color);
                ctx.fill(x + 1, y + 4, x + 7, y + 6, color);
            }
            case DOWN -> {
                ctx.fill(x + 1, y + 2, x + 7, y + 4, color);
                ctx.fill(x + 2, y + 4, x + 6, y + 5, color);
                ctx.fill(x + 3, y + 5, x + 5, y + 6, color);
            }
            default -> {}
        }
    }
}
