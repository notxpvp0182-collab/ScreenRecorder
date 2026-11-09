package com.ahmed.screenrecorder.capture;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Captures the rendered Minecraft framebuffer directly on the Render Thread
 * and transfers the pixel data into a pooled off-heap ByteBuffer.
 */
public class VideoCaptureManager {
    private int lastCapturedWidth = 0;
    private int lastCapturedHeight = 0;

    public boolean captureFramebuffer(ByteBuffer targetBuffer, int expectedWidth, int expectedHeight) {
        RenderSystem.assertOnRenderThread();
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null) return false;

        int fbWidth = framebuffer.textureWidth;
        int fbHeight = framebuffer.textureHeight;
        if (fbWidth <= 0 || fbHeight <= 0) return false;

        int requiredBytes = fbWidth * fbHeight * 4;
        if (targetBuffer.capacity() < requiredBytes) {
            return false;
        }

        this.lastCapturedWidth = fbWidth;
        this.lastCapturedHeight = fbHeight;

        targetBuffer.clear();

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.fbo);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, 0);

        GL11.glReadPixels(0, 0, fbWidth, fbHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, targetBuffer);

        targetBuffer.limit(requiredBytes);
        return true;
    }

    public int getLastCapturedWidth() {
        return lastCapturedWidth;
    }

    public int getLastCapturedHeight() {
        return lastCapturedHeight;
    }
}
