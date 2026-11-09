package com.ahmed.screenrecorder.capture;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * Single-Shot Lightweight Framebuffer Grabber.
 * Performs ONE single glReadPixels call and downsamples into the target 640x360 RGB buffer.
 */
public class FastFrameCaptureHandler {
    private ByteBuffer fullReadBuffer;

    public boolean captureScaledRgbFrame(byte[] targetRgbData, int outW, int outH) {
        RenderSystem.assertOnRenderThread();
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.textureWidth <= 0 || fb.textureHeight <= 0) return false;

        int srcW = fb.textureWidth;
        int srcH = fb.textureHeight;
        int totalBytes = srcW * srcH * 4;

        if (fullReadBuffer == null || fullReadBuffer.capacity() < totalBytes) {
            fullReadBuffer = BufferUtils.createByteBuffer(totalBytes);
        }

        fullReadBuffer.clear();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, srcW, srcH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, fullReadBuffer);

        int dstIdx = 0;
        for (int y = 0; y < outH; y++) {
            int glY = srcH - 1 - ((y * srcH) / outH);
            if (glY < 0) glY = 0;
            if (glY >= srcH) glY = srcH - 1;
            int rowOff = glY * srcW * 4;

            for (int x = 0; x < outW; x++) {
                int srcX = (x * srcW) / outW;
                int idx = rowOff + (srcX << 2);
                targetRgbData[dstIdx]     = fullReadBuffer.get(idx);
                targetRgbData[dstIdx + 1] = fullReadBuffer.get(idx + 1);
                targetRgbData[dstIdx + 2] = fullReadBuffer.get(idx + 2);
                dstIdx += 3;
            }
        }
        return true;
    }
}
