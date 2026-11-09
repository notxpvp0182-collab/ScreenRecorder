package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.gui.ZeroCopyGlPreviewHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Single-Pass DMA Framebuffer Grabber.
 * Eliminates the 360-call glReadPixels bottleneck by performing ONE contiguous read on the Render Thread
 * and delegating all RGB scaling/flipping to the background encoder thread!
 */
public class SinglePassFrameGrabber {
    private ByteBuffer rawGlBuffer;
    private int lastW = 0;
    private int lastH = 0;

    public synchronized boolean grabFullFramebufferFast() {
        RenderSystem.assertOnRenderThread();
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.textureWidth <= 0 || fb.textureHeight <= 0) return false;

        int w = fb.textureWidth;
        int h = fb.textureHeight;
        int needed = w * h * 4;

        if (rawGlBuffer == null || rawGlBuffer.capacity() < needed) {
            rawGlBuffer = ByteBuffer.allocateDirect(needed).order(ByteOrder.nativeOrder());
        }

        rawGlBuffer.clear();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rawGlBuffer);
        rawGlBuffer.limit(needed);

        this.lastW = w;
        this.lastH = h;
        return true;
    }

    /**
     * Runs on the Background Worker Thread (0% impact on Minecraft Render Thread!).
     * Converts the raw OpenGL RGBA buffer into scaled top-down RGB bytes and updates the live preview snapshot.
     */
    public synchronized void convertOnBackgroundThread(byte[] dstRgb, int outW, int outH) {
        if (rawGlBuffer == null || lastW <= 0 || lastH <= 0) return;

        // Also feed the live preview handler for free on the background thread!
        ZeroCopyGlPreviewHandler.updateSnapshotFromGame(rawGlBuffer, lastW, lastH);

        int srcW = lastW;
        int srcH = lastH;
        int dstIdx = 0;

        for (int y = 0; y < outH; y++) {
            int srcY = srcH - 1 - ((y * srcH) / outH);
            if (srcY < 0) srcY = 0;
            if (srcY >= srcH) srcY = srcH - 1;
            int rowOff = srcY * srcW * 4;

            for (int x = 0; x < outW; x++) {
                int srcX = (x * srcW) / outW;
                int idx = rowOff + (srcX << 2);
                dstRgb[dstIdx]     = rawGlBuffer.get(idx);
                dstRgb[dstIdx + 1] = rawGlBuffer.get(idx + 1);
                dstRgb[dstIdx + 2] = rawGlBuffer.get(idx + 2);
                dstIdx += 3;
            }
        }
    }
}
