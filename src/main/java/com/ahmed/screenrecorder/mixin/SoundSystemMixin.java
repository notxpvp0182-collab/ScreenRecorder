package com.ahmed.screenrecorder.mixin;

import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Safe Mixin placeholder for SoundSystem (prevents any class_1140 descriptor mismatch crash on 1.21.11).
 * In-game sound events (block place/break/footsteps/combat) are captured safely via InGameSoundTracker!
 */
@Mixin(SoundSystem.class)
public class SoundSystemMixin {
}
