package com.ahmed.screenrecorder.capture;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.MP4TrackType;
import org.jcodec.containers.mp4.muxer.FramesMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Pure-Java Standard H.264 (AVC1 / YUV420J) MP4 Encoder.
 * Produces 100% compliant .mp4 files playable in Android Gallery, VLC, Windows Media Player, and In-Game Player.
 * Uses zero java.awt classes so it works on all mobile launchers (Pojav, Zalith, Fold Craft) without lag.
 */
public class NativeMjpegMp4Muxer {
    private final int width;
    private final int height;
    private final int fps;
    private SeekableByteChannel channel;
    private MP4Muxer muxer;
    private FramesMP4MuxerTrack videoTrack;
    private H264Encoder encoder;
    private Picture yuvPic;
    private ByteBuffer encodeOutBuf;
    private final ArrayList<ByteBuffer> spsList = new ArrayList<>();
    private final ArrayList<ByteBuffer> ppsList = new ArrayList<>();
    private long frameNo = 0;

    public NativeMjpegMp4Muxer(File outputFile, int width, int height, int fps) throws IOException {
        // Ensure dimensions are multiples of 2 for YUV420
        this.width = Math.max(128, (width / 2) * 2);
        this.height = Math.max(128, (height / 2) * 2);
        this.fps = Math.max(15, Math.min(120, fps));

        this.channel = NIOUtils.writableFileChannel(outputFile.getAbsolutePath());
        this.muxer = MP4Muxer.createMP4Muxer(channel, Brand.MP4);
        this.videoTrack = muxer.addTrack(MP4TrackType.VIDEO, this.fps);
        this.encoder = H264Encoder.createH264Encoder();
        this.yuvPic = Picture.create(this.width, this.height, ColorSpace.YUV420J);
        this.encodeOutBuf = ByteBuffer.allocate(this.width * this.height * 2);
    }

    public synchronized void writeRgbaFrame(ByteBuffer rgbaBuf, int srcW, int srcH) throws IOException {
        if (muxer == null || channel == null) return;
        rgbaBuf.rewind();

        byte[] yPlane = yuvPic.getPlaneData(0);
        byte[] uPlane = yuvPic.getPlaneData(1);
        byte[] vPlane = yuvPic.getPlaneData(2);

        // Fast integer RGB-to-YUV420 conversion with OpenGL vertical flip & scaling
        int dstW = this.width;
        int dstH = this.height;

        for (int y = 0; y < dstH; y++) {
            int srcY = srcH - 1 - ((y * srcH) / dstH);
            if (srcY < 0) srcY = 0;
            if (srcY >= srcH) srcY = srcH - 1;
            int srcRowOffset = srcY * srcW * 4;
            int yRowOffset = y * dstW;
            int uvRowOffset = (y >> 1) * (dstW >> 1);
            boolean evenY = (y & 1) == 0;

            for (int x = 0; x < dstW; x++) {
                int srcX = (x * srcW) / dstW;
                int idx = srcRowOffset + (srcX << 2);

                int r = rgbaBuf.get(idx) & 0xFF;
                int g = rgbaBuf.get(idx + 1) & 0xFF;
                int b = rgbaBuf.get(idx + 2) & 0xFF;

                int yVal = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                yPlane[yRowOffset + x] = (byte) (Math.max(0, Math.min(255, yVal)) - 128);

                if (evenY && (x & 1) == 0) {
                    int uVal = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int vVal = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                    int uvIdx = uvRowOffset + (x >> 1);
                    uPlane[uvIdx] = (byte) (Math.max(0, Math.min(255, uVal)) - 128);
                    vPlane[uvIdx] = (byte) (Math.max(0, Math.min(255, vVal)) - 128);
                }
            }
        }

        encodeOutBuf.clear();
        ByteBuffer result = encoder.encodeFrame(yuvPic, encodeOutBuf);

        spsList.clear();
        ppsList.clear();
        H264Encoder.wipePS(result, spsList, ppsList);
        H264Encoder.encodeSlice(result);

        MP4Packet pkt = new MP4Packet(
                result,
                frameNo,
                this.fps,
                1,
                frameNo,
                true,
                null,
                frameNo,
                0
        );
        videoTrack.addFrame(pkt);
        frameNo++;
    }

    public synchronized void finish() throws IOException {
        if (muxer == null || channel == null) return;
        try {
            if (!spsList.isEmpty() && !ppsList.isEmpty()) {
                videoTrack.addSampleEntry(H264Encoder.createSampleEntry(spsList, ppsList, 4));
            }
            muxer.writeHeader();
        } finally {
            NIOUtils.closeQuietly(channel);
            muxer = null;
            channel = null;
        }
    }
}
