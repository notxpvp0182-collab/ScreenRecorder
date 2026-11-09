package com.ahmed.screenrecorder.mixin;

import com.ahmed.screenrecorder.audio.InGameAudioTap;
import com.ahmed.screenrecorder.capture.RecordingManager;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"))
    private void onPlaySound(SoundInstance sound, CallbackInfo ci) {
        if (RecordingManager.getInstance().isRecording() && !RecordingManager.getInstance().isPaused() && sound != null && sound.getId() != null) {
            try {
                InGameAudioTap.onMinecraftSoundPlayed(sound.getId().toString(), 0.85f, 1.0f);
            } catch (Exception ignored) {}
        }
    }
}
