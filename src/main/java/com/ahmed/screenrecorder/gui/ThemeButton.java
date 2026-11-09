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
    private final boolean primaryRed;
    private UIHelper.Icon icon;
    private boolean isPressedState = false;

    public ThemeButton(int x, int y, int width, int height, UIHelper.Icon icon, Text message, Runnable onPress, boolean primaryRed) {
        super(x, y, width, height, message);
        this.icon = icon;
        this.onPress = onPress;
        this.primaryRed = primaryRed;
    }

    public ThemeButton(int x, int y, int width, int height, UIHelper.Icon icon, Text message, Runnable onPress) {
        this(x, y, width, height, icon, message, onPress, false);
    }

    public ThemeButton(int x, int y, int width, int height, Text message, Runnable onPress, boolean primaryRed) {
        this(x, y, width, height, UIHelper.Icon.NONE, message, onPress, primaryRed);
    }

    public ThemeButton(int x, int y, int width, int height, Text message, Runnable onPress) {
        this(x, y, width, height, UIHelper.Icon.NONE, message, onPress, false);
    }

    public void setIcon(UIHelper.Icon icon) {
        this.icon = icon;
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
        int bgColor;
        int borderColor;
        int textColor;

        if (!this.active) {
            bgColor = 0xFF1F2128;
            borderColor = 0xFF2E313B;
            textColor = 0xFF6E727D;
        } else if (this.isPressedState && this.isHovered()) {
            bgColor = 0xFFD32F2F;
            borderColor = 0xFFFFFFFF;
            textColor = 0xFFFFFFFF;
        } else if (this.isHovered()) {
            bgColor = primaryRed ? 0xFFC62828 : 0xFF393D4B;
            borderColor = primaryRed ? 0xFFFF5252 : 0xFF545A6E;
            textColor = 0xFFFFFFFF;
        } else {
            bgColor = primaryRed ? 0xFF9E1B1B : 0xFF2E323E;
            borderColor = primaryRed ? 0xFFD32F2F : 0xFF424756;
            textColor = 0xFFE8EAED;
        }

        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        UIHelper.drawBorder(context, getX(), getY(), getWidth(), getHeight(), borderColor);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int textWidth = tr.getWidth(getMessage());
        int iconSpace = (icon != null && icon != UIHelper.Icon.NONE) ? 11 : 0;
        int totalContentW = textWidth + iconSpace;

        int startX = getX() + (getWidth() - totalContentW) / 2;
        int textY = getY() + (getHeight() - tr.fontHeight) / 2 + 1;

        if (icon != null && icon != UIHelper.Icon.NONE) {
            int iconY = getY() + (getHeight() - 8) / 2;
            UIHelper.drawIcon(context, icon, startX, iconY, textColor);
            startX += iconSpace;
        }

        context.drawTextWithShadow(tr, getMessage(), startX, textY, textColor);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
