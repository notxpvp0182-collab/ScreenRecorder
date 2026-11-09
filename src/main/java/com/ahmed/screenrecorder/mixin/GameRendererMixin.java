package com.ahmed.screenrecorder.mixin;

import com.ahmed.screenrecorder.capture.RecordingManager;
import com.ahmed.screenrecorder.gui.RecordingIndicator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private BufferBuilderStorage buffers;

    /**
     * 1. First captures the exact rendered Minecraft screen (World, HUD, Chat, Menus).
     * 2. Immediately afterwards renders the ● REC indicator onto the screen so the player
     *    sees it live in-game, but the recorded MP4 never contains it.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void onPostRenderFrame(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        RecordingManager.getInstance().onFrameRendered();

        if (RecordingManager.getInstance().isRecording()) {
            DrawContext context = new DrawContext(this.client, this.buffers.getEntityVertexConsumers());
            RecordingIndicator.renderCleanOverlay(context);
            context.draw();
        }
    }
}
