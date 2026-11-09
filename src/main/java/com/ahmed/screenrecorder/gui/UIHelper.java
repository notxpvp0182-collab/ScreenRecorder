package com.ahmed.screenrecorder.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * High-Precision Custom Studio Icon Renderer (Zero Emojis, Zero Lag).
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
        DOWN,
        EYE,
        LOCK
    }

    public static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void drawIcon(DrawContext ctx, Icon icon, int x, int y, int color) {
        if (icon == null || icon == Icon.NONE) return;

        switch (icon) {
            case RECORD -> {
                ctx.fill(x + 1, y + 1, x + 7, y + 7, 0xFF8E1616);
                ctx.fill(x + 2, y + 1, x + 6, y + 7, 0xFFFF2A2A);
                ctx.fill(x + 1, y + 2, x + 7, y + 6, 0xFFFF2A2A);
                ctx.fill(x + 2, y + 2, x + 4, y + 4, 0xFFFF9E9E);
            }
            case STOP -> {
                ctx.fill(x + 1, y + 1, x + 7, y + 7, 0xFFFF4444);
                ctx.fill(x + 2, y + 2, x + 6, y + 6, color);
            }
            case PLAY -> {
                ctx.fill(x + 2, y + 1, x + 4, y + 7, color);
                ctx.fill(x + 4, y + 2, x + 6, y + 6, color);
                ctx.fill(x + 6, y + 3, x + 7, y + 5, color);
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
                ctx.fill(x, y + 1, x + 4, y + 2, 0xFF60A5FA);
                ctx.fill(x, y + 2, x + 8, y + 7, 0xFF3B82F6);
                ctx.fill(x + 1, y + 3, x + 7, y + 6, color);
            }
            case SETTINGS -> {
                ctx.fill(x + 3, y, x + 5, y + 8, color);
                ctx.fill(x, y + 3, x + 8, y + 5, color);
                ctx.fill(x + 1, y + 1, x + 7, y + 7, color);
                ctx.fill(x + 2, y + 2, x + 6, y + 6, 0xFF1F2229);
                ctx.fill(x + 3, y + 3, x + 5, y + 5, 0xFF60A5FA);
            }
            case MICROPHONE -> {
                ctx.fill(x + 3, y, x + 5, y + 5, color);
                ctx.fill(x + 1, y + 3, x + 2, y + 6, 0xFF60A5FA);
                ctx.fill(x + 6, y + 3, x + 7, y + 6, 0xFF60A5FA);
                ctx.fill(x + 2, y + 5, x + 6, y + 6, 0xFF60A5FA);
                ctx.fill(x + 3, y + 6, x + 5, y + 8, color);
            }
            case AUDIO -> {
                ctx.fill(x, y + 3, x + 2, y + 5, color);
                ctx.fill(x + 2, y + 2, x + 3, y + 6, color);
                ctx.fill(x + 3, y + 1, x + 4, y + 7, color);
                ctx.fill(x + 5, y + 2, x + 6, y + 3, 0xFF2ECC71);
                ctx.fill(x + 5, y + 5, x + 6, y + 6, 0xFF2ECC71);
                ctx.fill(x + 7, y + 1, x + 8, y + 7, 0xFF3B82F6);
            }
            case VIDEO -> {
                ctx.fill(x, y + 1, x + 6, y + 7, 0xFF3B82F6);
                ctx.fill(x + 1, y + 2, x + 5, y + 6, color);
                ctx.fill(x + 6, y + 3, x + 7, y + 5, color);
                ctx.fill(x + 7, y + 2, x + 8, y + 6, color);
            }
            case FILTERS -> {
                ctx.fill(x, y + 1, x + 8, y + 2, color);
                ctx.fill(x + 5, y, x + 7, y + 3, 0xFF3B82F6);
                ctx.fill(x, y + 4, x + 8, y + 5, color);
                ctx.fill(x + 2, y + 3, x + 4, y + 6, 0xFF2ECC71);
                ctx.fill(x, y + 6, x + 8, y + 7, color);
                ctx.fill(x + 4, y + 5, x + 6, y + 8, 0xFFF1C40F);
            }
            case PERFORMANCE -> {
                ctx.fill(x + 4, y, x + 6, y + 3, 0xFFFFCA28);
                ctx.fill(x + 2, y + 3, x + 6, y + 5, 0xFFFFCA28);
                ctx.fill(x + 2, y + 5, x + 4, y + 8, 0xFFFFCA28);
            }
            case DELETE -> {
                ctx.fill(x + 2, y, x + 6, y + 1, 0xFFFF5252);
                ctx.fill(x + 1, y + 1, x + 7, y + 2, color);
                ctx.fill(x + 2, y + 3, x + 6, y + 8, color);
            }
            case REFRESH -> {
                ctx.fill(x + 2, y + 1, x + 6, y + 2, color);
                ctx.fill(x + 1, y + 2, x + 2, y + 6, color);
                ctx.fill(x + 2, y + 6, x + 6, y + 7, color);
                ctx.fill(x + 6, y + 3, x + 7, y + 6, color);
                ctx.fill(x + 5, y, x + 7, y + 3, 0xFF3B82F6);
            }
            case CLOSE -> {
                ctx.fill(x + 1, y + 1, x + 3, y + 3, color);
                ctx.fill(x + 5, y + 1, x + 7, y + 3, color);
                ctx.fill(x + 3, y + 3, x + 5, y + 5, 0xFFFF5252);
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
            case EYE -> {
                ctx.fill(x + 2, y + 2, x + 6, y + 6, color);
                ctx.fill(x + 1, y + 3, x + 7, y + 5, color);
                ctx.fill(x + 3, y + 3, x + 5, y + 5, 0xFF3B82F6);
            }
            case LOCK -> {
                ctx.fill(x + 2, y + 1, x + 6, y + 3, color);
                ctx.fill(x + 1, y + 3, x + 7, y + 7, color);
                ctx.fill(x + 3, y + 4, x + 5, y + 6, 0xFF16181E);
            }
        }
    }
}
