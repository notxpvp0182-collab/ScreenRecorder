package com.ahmed.screenrecorder.capture;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.TapeTimecode;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.MP4TrackType;
import org.jcodec.containers.mp4.muxer.CodecMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.common.VideoEncoder.EncodedFrame;
import org.jcodec.common.model.Packet.FrameType;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * High-Speed Real-Time Timestamped H.264 (AVC1) MP4 Muxer.
 * Uses millisecond-accurate PTS durations per frame so 50 seconds of recording ALWAYS plays for 50 seconds at 1x speed
 * without ever duplicating frames or lagging the CPU!
 */
public class NativeMjpegMp4Muxer {
    private final int width;
    private final int height;
    private SeekableByteChannel channel;
    private MP4Muxer muxer;
    private CodecMP4MuxerTrack videoTrack;
    private H264Encoder encoder;
    private Picture yuvPic;
    private ByteBuffer encodeOutBuf;
    private final ArrayList<ByteBuffer> spsList = new ArrayList<>();
    private final ArrayList<ByteBuffer> ppsList = new ArrayList<>();

    private long lastPtsMs = 0;
    private long frameIndex = 0;

    public NativeMjpegMp4Muxer(File outputFile, int width, int height, int fps) throws IOException {
        this.width = Math.max(128, (width / 2) * 2);
        this.height = Math.max(128, (height / 2) * 2);

        this.channel = NIOUtils.writableFileChannel(outputFile.getAbsolutePath());
        this.muxer = MP4Muxer.createMP4Muxer(channel, Brand.MP4);
        // Timescale 1000 = 1 millisecond precision!
        this.videoTrack = muxer.addTrack(MP4TrackType.VIDEO, 1000);
        this.encoder = H264Encoder.createH264Encoder();
        this.yuvPic = Picture.create(this.width, this.height, ColorSpace.YUV420J);
        this.encodeOutBuf = ByteBuffer.allocate(this.width * this.height * 2);
    }

    public synchronized void writeRgbaFrameWithTimestamp(ByteBuffer rgbaBuf, int srcW, int srcH, long elapsedMs) throws IOException {
        if (muxer == null || channel == null) return;
        rgbaBuf.rewind();

        byte[] yPlane = yuvPic.getPlaneData(0);
        byte[] uPlane = yuvPic.getPlaneData(1);
        byte[] vPlane = yuvPic.getPlaneData(2);

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
        EncodedFrame encoded = encoder.encodeFrame(yuvPic, encodeOutBuf);
        ByteBuffer data = encoded.getData();

        spsList.clear();
        ppsList.clear();
        H264Utils.wipePSinplace(data, spsList, ppsList);
        H264Utils.encodeMOVPacketInplace(data);

        long pts = Math.max(lastPtsMs + 16, elapsedMs);
        long duration = Math.max(16, pts - lastPtsMs);
        lastPtsMs = pts;

        MP4Packet pkt = new MP4Packet(
                data,
                pts,
                1000,
                duration,
                frameIndex,
                encoded.isKeyFrame() ? FrameType.KEY : FrameType.INTER,
                TapeTimecode.ZERO_TAPE_TIMECODE,
                0,
                pts,
                0,
                0,
                0,
                true
        );
        videoTrack.addFrame(pkt);
        frameIndex++;
    }

    public synchronized void finish() throws IOException {
        if (muxer == null || channel == null) return;
        try {
            if (!spsList.isEmpty() && !ppsList.isEmpty()) {
                videoTrack.addSampleEntry(H264Utils.createMOVSampleEntryFromBytes(spsList.get(0), ppsList.get(0), 4));
            }
            muxer.finalizeHeader();
        } finally {
            NIOUtils.closeQuietly(channel);
            muxer = null;
            channel = null;
        }
    }
}
