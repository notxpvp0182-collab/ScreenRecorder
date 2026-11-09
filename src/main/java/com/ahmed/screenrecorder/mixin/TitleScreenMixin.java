package com.ahmed.screenrecorder.mixin;

import com.ahmed.screenrecorder.gui.RecordingsScreen;
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
    private void addDefaultMinecraftThemeButton(CallbackInfo ci) {
        int btnX = this.width / 2 + 104;
        int btnY = this.height / 4 + 48;

        // Pure Default Minecraft Vanilla Button Style
        this.addDrawableChild(ButtonWidget.builder(Text.literal("OBS"), button -> {
            if (this.client != null) {
                this.client.setScreen(new RecordingsScreen(this));
            }
        }).dimensions(btnX, btnY, 32, 20).build());
    }
}
