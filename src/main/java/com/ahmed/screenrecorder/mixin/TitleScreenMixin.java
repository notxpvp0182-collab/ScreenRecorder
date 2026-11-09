package com.ahmed.screenrecorder.mixin;

import com.ahmed.screenrecorder.gui.RecordingsScreen;
import com.ahmed.screenrecorder.gui.ThemeButton;
import com.ahmed.screenrecorder.gui.UIHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
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
    private void addCleanObsRecordingsButton(CallbackInfo ci) {
        int btnX = this.width / 2 + 104;
        int btnY = this.height / 4 + 48;

        this.addDrawableChild(new ThemeButton(
                btnX, btnY, 20, 20,
                UIHelper.Icon.FOLDER, Text.empty(),
                () -> {
                    if (this.client != null) {
                        this.client.setScreen(new RecordingsScreen(this));
                    }
                },
                true
        ));
    }
}
