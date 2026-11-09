package com.ahmed.screenrecorder.mixin;

import com.ahmed.screenrecorder.gui.RecorderScreen;
import com.ahmed.screenrecorder.gui.UIHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addDefaultSmallObsButton(CallbackInfo ci) {
        int btnX = this.width / 2 + 104;
        int btnY = this.height / 4 + 48;

        // Default Minecraft 20x20 square button with custom OBS icon rendered on top
        ButtonWidget obsBtn = ButtonWidget.builder(Text.empty(), button -> {
            if (this.client != null) {
                this.client.setScreen(new RecorderScreen(this));
            }
        }).dimensions(btnX, btnY, 20, 20).build();

        this.addDrawableChild(obsBtn);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderObsIconOnDefaultButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int btnX = this.width / 2 + 104;
        int btnY = this.height / 4 + 48;
        UIHelper.drawIcon(context, UIHelper.Icon.RECORD, btnX + 6, btnY + 6, 0xFFFFFFFF);
    }
}
