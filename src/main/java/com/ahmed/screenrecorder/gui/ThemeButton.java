package com.ahmed.screenrecorder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public class ThemeButton extends ClickableWidget {
    private final Runnable onPress;
    private boolean accentBlue;
    private UIHelper.Icon icon;
    private boolean isPressedState = false;
    private float hoverAnim = 0.0f;
    private float toggleAnim = 0.0f;

    public ThemeButton(int x, int y, int width, int height, UIHelper.Icon icon, Text message, Runnable onPress, boolean accentBlue) {
        super(x, y, width, height, message);
        this.icon = icon;
        this.onPress = onPress;
        this.accentBlue = accentBlue;
        this.toggleAnim = accentBlue ? 1.0f : 0.0f;
    }

    public ThemeButton(int x, int y, int width, int height, UIHelper.Icon icon, Text message, Runnable onPress) {
        this(x, y, width, height, icon, message, onPress, false);
    }

    public ThemeButton(int x, int y, int width, int height, Text message, Runnable onPress, boolean accentBlue) {
        this(x, y, width, height, UIHelper.Icon.NONE, message, onPress, accentBlue);
    }

    public ThemeButton(int x, int y, int width, int height, Text message, Runnable onPress) {
        this(x, y, width, height, UIHelper.Icon.NONE, message, onPress, false);
    }

    public void setIcon(UIHelper.Icon icon) {
        this.icon = icon;
    }

    public void setAccentBlue(boolean accentBlue) {
        this.accentBlue = accentBlue;
    }

    @Override
    public void onClick(Click click, boolean bl) {
        if (this.active && this.visible && this.onPress != null) {
            this.onPress.run();
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (this.active && this.visible && this.isMouseOver(click.x(), click.y())) {
            this.isPressedState = true;
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(Click click) {
        this.isPressedState = false;
        return super.mouseReleased(click);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        float hoverTarget = (this.active && this.isHovered()) ? 1.0f : 0.0f;
        hoverAnim += (hoverTarget - hoverAnim) * Math.min(1.0f, delta * 0.4f);

        String msgStr = getMessage().getString();
        boolean isOnOffToggle = msgStr.endsWith(": ON") || msgStr.endsWith(": OFF");
        boolean isOnState = msgStr.endsWith(": ON") || accentBlue;
        float toggleTarget = isOnState ? 1.0f : 0.0f;
        toggleAnim += (toggleTarget - toggleAnim) * Math.min(1.0f, delta * 0.35f);

        int bgColor;
        int borderColor;
        int textColor;

        if (!this.active) {
            bgColor = 0xFF20232B;
            borderColor = 0xFF2E323D;
            textColor = 0xFF686C7A;
        } else if (this.isPressedState && this.isHovered()) {
            bgColor = 0xFF1E4C9A;
            borderColor = 0xFF60A5FA;
            textColor = 0xFFFFFFFF;
        } else if (accentBlue) {
            bgColor = blendColor(0xFF255EC0, 0xFF3373DF, hoverAnim);
            borderColor = blendColor(0xFF3B82F6, 0xFF93C5FD, hoverAnim);
            textColor = 0xFFFFFFFF;
        } else {
            bgColor = blendColor(0xFF323643, 0xFF424758, hoverAnim);
            borderColor = blendColor(0xFF454A5C, 0xFF606780, hoverAnim);
            textColor = 0xFFE8EAED;
        }

        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        UIHelper.drawBorder(context, getX(), getY(), getWidth(), getHeight(), borderColor);

        // Top subtle highlight bar on hover
        if (hoverAnim > 0.05f) {
            int hlAlpha = (int) (hoverAnim * 70) << 24;
            context.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + 2, hlAlpha | 0x00FFFFFF);
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        if (isOnOffToggle && getWidth() >= 70) {
            // Render Animated Pill Toggle Switch on the right side of the button
            String cleanLabel = msgStr.substring(0, msgStr.lastIndexOf(':'));
            int startX = getX() + 5;
            int textY = getY() + (getHeight() - tr.fontHeight) / 2 + 1;

            if (icon != null && icon != UIHelper.Icon.NONE) {
                int iconY = getY() + (getHeight() - 8) / 2;
                UIHelper.drawIcon(context, icon, startX, iconY, textColor);
                startX += 11;
            }
            context.drawTextWithShadow(tr, cleanLabel, startX, textY, textColor);

            int pillW = 18;
            int pillH = 8;
            int pillX = getX() + getWidth() - pillW - 4;
            int pillY = getY() + (getHeight() - pillH) / 2;
            int pillBg = blendColor(0xFF181A20, 0xFF2ECC71, toggleAnim);
            context.fill(pillX, pillY, pillX + pillW, pillY + pillH, pillBg);
            UIHelper.drawBorder(context, pillX, pillY, pillW, pillH, 0xFF4A5062);

            int knobX = pillX + 1 + (int) (toggleAnim * (pillW - 7));
            context.fill(knobX, pillY + 1, knobX + 5, pillY + pillH - 1, 0xFFFFFFFF);
            return;
        }

        int textWidth = tr.getWidth(getMessage());
        int iconSpace = (icon != null && icon != UIHelper.Icon.NONE) ? (textWidth > 0 ? 11 : 8) : 0;
        int totalContentW = textWidth + iconSpace;

        int startX = getX() + (getWidth() - totalContentW) / 2;
        int textY = getY() + (getHeight() - tr.fontHeight) / 2 + 1;

        if (icon != null && icon != UIHelper.Icon.NONE) {
            int iconY = getY() + (getHeight() - 8) / 2;
            UIHelper.drawIcon(context, icon, startX, iconY, textColor);
            startX += iconSpace;
        }

        if (textWidth > 0) {
            context.drawTextWithShadow(tr, getMessage(), startX, textY, textColor);
        }
    }

    private int blendColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
