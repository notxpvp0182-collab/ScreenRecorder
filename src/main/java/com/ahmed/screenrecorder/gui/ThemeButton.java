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

    public ThemeButton(int x, int y, int width, int height, UIHelper.Icon icon, Text message, Runnable onPress, boolean accentBlue) {
        super(x, y, width, height, message);
        this.icon = icon;
        this.onPress = onPress;
        this.accentBlue = accentBlue;
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
        float target = (this.active && this.isHovered()) ? 1.0f : 0.0f;
        hoverAnim += (target - hoverAnim) * Math.min(1.0f, delta * 0.45f);

        int bgColor;
        int borderColor;
        int textColor;

        if (!this.active) {
            bgColor = 0xFF22252E;
            borderColor = 0xFF303440;
            textColor = 0xFF6C707E;
        } else if (this.isPressedState && this.isHovered()) {
            bgColor = 0xFF1F4E9E;
            borderColor = 0xFF60A5FA;
            textColor = 0xFFFFFFFF;
        } else if (accentBlue) {
            bgColor = blendColor(0xFF2865C7, 0xFF3476E0, hoverAnim);
            borderColor = blendColor(0xFF3B82F6, 0xFF93C5FD, hoverAnim);
            textColor = 0xFFFFFFFF;
        } else {
            bgColor = blendColor(0xFF363A47, 0xFF454A5A, hoverAnim);
            borderColor = blendColor(0xFF494E5F, 0xFF646B82, hoverAnim);
            textColor = 0xFFE8EAED;
        }

        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        UIHelper.drawBorder(context, getX(), getY(), getWidth(), getHeight(), borderColor);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
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
