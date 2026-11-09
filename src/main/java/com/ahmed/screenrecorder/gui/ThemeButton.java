package com.ahmed.screenrecorder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

/**
 * Custom solid Dark + Red + White button component with Normal, Hover, Pressed, and Disabled states.
 * Uses zero gradients or heavy animations for maximum FPS stability.
 */
public class ThemeButton extends PressableWidget {
    private final Runnable onPress;
    private final boolean primaryRed;
    private boolean isPressedState = false;

    public ThemeButton(int x, int y, int width, int height, Text message, Runnable onPress, boolean primaryRed) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.primaryRed = primaryRed;
    }

    public ThemeButton(int x, int y, int width, int height, Text message, Runnable onPress) {
        this(x, y, width, height, message, onPress, false);
    }

    @Override
    public void onPress() {
        if (this.active && this.onPress != null) {
            this.onPress.run();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && this.visible && this.isValidClickButton(button) && this.isMouseOver(mouseX, mouseY)) {
            this.isPressedState = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isPressedState = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int bgColor;
        int borderColor;
        int textColor;

        if (!this.active) {
            bgColor = 0xFF1A1A1D;
            borderColor = 0xFF2C2C30;
            textColor = 0xFF6E6E73;
        } else if (this.isPressedState && this.isHovered()) {
            bgColor = 0xFFD32F2F;
            borderColor = 0xFFFFFFFF;
            textColor = 0xFFFFFFFF;
        } else if (this.isHovered()) {
            bgColor = primaryRed ? 0xFFC62828 : 0xFF2A1215;
            borderColor = 0xFFE53935;
            textColor = 0xFFFFFFFF;
        } else {
            bgColor = primaryRed ? 0xFF8E1616 : 0xFF16161A;
            borderColor = primaryRed ? 0xFFD32F2F : 0xFF5C1919;
            textColor = 0xFFFFFFFF;
        }

        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        context.drawBorder(getX(), getY(), getWidth(), getHeight(), borderColor);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int textWidth = tr.getWidth(getMessage());
        int textX = getX() + (getWidth() - textWidth) / 2;
        int textY = getY() + (getHeight() - tr.fontHeight) / 2 + 1;

        context.drawTextWithShadow(tr, getMessage(), textX, textY, textColor);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
