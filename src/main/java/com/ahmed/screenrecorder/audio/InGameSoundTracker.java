package com.ahmed.screenrecorder.audio;

import com.ahmed.screenrecorder.capture.RecordingManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;

/**
 * Crash-Proof Fabric API In-Game Sound Event Tracker.
 * Detects block placement, block breaking, footsteps, jumping, and combat hits using official Fabric events
 * and feeds them directly into InGameAudioTap!
 */
public class InGameSoundTracker {
    private static double lastX = 0, lastY = 0, lastZ = 0;
    private static int stepCooldown = 0;
    private static boolean wasOnGround = true;

    public static void register() {
        // 1. Block Place / Interact Sound
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (RecordingManager.getInstance().isRecording() && !RecordingManager.getInstance().isPaused()) {
                InGameAudioTap.onMinecraftSoundPlayed("block.place", 0.85f, 1.0f);
            }
            return ActionResult.PASS;
        });

        // 2. Block Hit / Dig / Break Sound
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (RecordingManager.getInstance().isRecording() && !RecordingManager.getInstance().isPaused()) {
                InGameAudioTap.onMinecraftSoundPlayed("block.break", 0.80f, 1.1f);
            }
            return ActionResult.PASS;
        });

        // 3. Entity Combat Hit Sound
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (RecordingManager.getInstance().isRecording() && !RecordingManager.getInstance().isPaused()) {
                InGameAudioTap.onMinecraftSoundPlayed("entity.hit", 0.90f, 0.95f);
            }
            return ActionResult.PASS;
        });

        // 4. Footsteps & Landing Sounds on Client Tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!RecordingManager.getInstance().isRecording() || RecordingManager.getInstance().isPaused()) return;
            PlayerEntity p = client.player;
            if (p == null) return;

            double dx = p.getX() - lastX;
            double dz = p.getZ() - lastZ;
            double distSq = dx * dx + dz * dz;
            lastX = p.getX();
            lastY = p.getY();
            lastZ = p.getZ();

            if (!wasOnGround && p.isOnGround()) {
                InGameAudioTap.onMinecraftSoundPlayed("player.land", 0.75f, 0.9f);
            }
            wasOnGround = p.isOnGround();

            if (stepCooldown > 0) stepCooldown--;
            if (p.isOnGround() && distSq > 0.015 && stepCooldown == 0) {
                InGameAudioTap.onMinecraftSoundPlayed("player.step", 0.55f, 1.0f);
                stepCooldown = p.isSprinting() ? 5 : 8;
            }
        });
    }
}
