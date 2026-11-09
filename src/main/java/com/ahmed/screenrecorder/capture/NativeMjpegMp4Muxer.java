package com.ahmed.screenrecorder.capture;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Official JCodec 0.2.5 SequenceEncoder H.264 (AVC1) MP4 Muxer.
 * Writes universal H.264 MP4 files playable in Android Gallery, Windows Media Player, VLC, and In-Game Player.
 */
public class NativeMjpegMp4Muxer {
    private final int width;
    private final int height;
    private final int fps;
    private SeekableByteChannel channel;
    private SequenceEncoder encoder;
    private Picture rgbPic;

    public NativeMjpegMp4Muxer(File outputFile, int width, int height, int fps) throws IOException {
        this.width = Math.max(128, (width / 2) * 2);
        this.height = Math.max(128, (height / 2) * 2);
        this.fps = Math.max(15, Math.min(120, fps));

        this.channel = NIOUtils.writableFileChannel(outputFile.getAbsolutePath());
        this.encoder = new SequenceEncoder(channel, Rational.R(this.fps, 1));
        this.rgbPic = Picture.create(this.width, this.height, ColorSpace.RGB);
    }

    public synchronized void writeRgbaFrame(ByteBuffer rgbaBuf, int srcW, int srcH) throws IOException {
        if (encoder == null) return;
        rgbaBuf.rewind();

        byte[] dstData = rgbPic.getPlaneData(0);
        int dstW = this.width;
        int dstH = this.height;
        int dstIdx = 0;

        // High-speed OpenGL RGBA (bottom-up) to JCodec RGB (top-down) conversion
        for (int y = 0; y < dstH; y++) {
            int srcY = srcH - 1 - ((y * srcH) / dstH);
            if (srcY < 0) srcY = 0;
            if (srcY >= srcH) srcY = srcH - 1;
            int srcRowOffset = srcY * srcW * 4;

            for (int x = 0; x < dstW; x++) {
                int srcX = (x * srcW) / dstW;
                int idx = srcRowOffset + (srcX << 2);

                int r = rgbaBuf.get(idx) & 0xFF;
                int g = rgbaBuf.get(idx + 1) & 0xFF;
                int b = rgbaBuf.get(idx + 2) & 0xFF;

                dstData[dstIdx]     = (byte) (r - 128);
                dstData[dstIdx + 1] = (byte) (g - 128);
                dstData[dstIdx + 2] = (byte) (b - 128);
                dstIdx += 3;
            }
        }

        encoder.encodeNativeFrame(rgbPic);
    }

    public synchronized void finish() throws IOException {
        if (encoder != null) {
            try {
                encoder.finish();
            } finally {
                encoder = null;
                NIOUtils.closeQuietly(channel);
                channel = null;
            }
        }
    }
}
