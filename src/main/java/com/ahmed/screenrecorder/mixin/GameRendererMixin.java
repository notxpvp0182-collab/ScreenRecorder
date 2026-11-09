package com.ahmed.screenrecorder.mixin;

import com.ahmed.screenrecorder.capture.RecordingManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onPostRenderFrame(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        RecordingManager.getInstance().onFrameRendered();
    }
}
