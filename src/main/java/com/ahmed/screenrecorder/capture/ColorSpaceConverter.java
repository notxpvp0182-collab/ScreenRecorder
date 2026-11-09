package com.ahmed.screenrecorder.capture;

import java.nio.ByteBuffer;

/**
 * High-Precision RGBA-to-RGB Sharp Frame Scaler & Vertical Flip Processor.
 * Runs exclusively on the background encoding worker thread so Minecraft's Render Thread never slows down.
 */
public class ColorSpaceConverter {

    public static void convertRgbaToSharpRgb(ByteBuffer srcRgba, int srcW, int srcH, byte[] dstRgb, int dstW, int dstH) {
        if (srcRgba == null || srcW <= 0 || srcH <= 0) return;

        int dstIdx = 0;
        for (int y = 0; y < dstH; y++) {
            int srcY = srcH - 1 - ((y * srcH) / dstH);
            if (srcY < 0) srcY = 0;
            if (srcY >= srcH) srcY = srcH - 1;
            int rowOffset = srcY * srcW * 4;

            for (int x = 0; x < dstW; x++) {
                int srcX = (x * srcW) / dstW;
                int idx = rowOffset + (srcX << 2);

                dstRgb[dstIdx]     = srcRgba.get(idx);
                dstRgb[dstIdx + 1] = srcRgba.get(idx + 1);
                dstRgb[dstIdx + 2] = srcRgba.get(idx + 2);
                dstIdx += 3;
            }
        }
    }
}
