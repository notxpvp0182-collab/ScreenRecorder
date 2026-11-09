package com.ahmed.screenrecorder.capture;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.Codec;
import org.jcodec.common.Format;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;

import java.io.*;

/**
 * High-Quality Single-Pass H.264 MP4 Muxer with Post-Finalize Exact Wall-Clock Duration Patching.
 * Never duplicates frames, so there are NO frozen 8-second tails at the end of the video!
 * And because VariableDurationMp4Patcher calibrates the MP4 header upon stop,
 * a 50-second recording ALWAYS has a 50.00-second duration!
 */
public class NativeMjpegMp4Muxer {
    public static final int PLAYER_CACHE_W = 240;
    public static final int PLAYER_CACHE_H = 135;

    private final File outputFile;
    private final int width;
    private final int height;
    private final int nominalFps;

    private SeekableByteChannel channel;
    private SequenceEncoder seqEncoder;
    private Picture rgbPic;
    private DataOutputStream rawReplayOut;
    private int encodedFrames = 0;

    public NativeMjpegMp4Muxer(File outputFile, int width, int height, int fps) throws IOException {
        this.outputFile = outputFile;
        this.width = Math.max(128, (width / 2) * 2);
        this.height = Math.max(128, (height / 2) * 2);
        this.nominalFps = Math.max(10, Math.min(60, fps));

        this.channel = NIOUtils.writableFileChannel(outputFile.getAbsolutePath());
        this.seqEncoder = new SequenceEncoder(channel, Rational.R(this.nominalFps, 1), Format.MOV, Codec.H264, null);
        this.rgbPic = Picture.create(this.width, this.height, ColorSpace.RGB);

        File cacheFile = getReplayCacheFile(outputFile);
        this.rawReplayOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile), 131072));
        this.rawReplayOut.writeInt(0x4F425332); // Magic 'OBS2'
        this.rawReplayOut.writeInt(PLAYER_CACHE_W);
        this.rawReplayOut.writeInt(PLAYER_CACHE_H);
        this.rawReplayOut.writeLong(0L); // Placeholder for actual duration in ms
    }

    public static File getReplayCacheFile(File mp4File) {
        return new File(mp4File.getParentFile(), "." + mp4File.getName() + ".obsstream");
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getEncodedFrames() { return encodedFrames; }

    public synchronized void encodeSingleFrame(byte[] rawRgb) throws IOException {
        if (seqEncoder == null || rgbPic == null) return;

        byte[] dst = rgbPic.getPlaneData(0);
        int len = Math.min(rawRgb.length, dst.length);
        for (int i = 0; i < len; i++) {
            dst[i] = (byte) ((rawRgb[i] & 0xFF) - 128);
        }

        seqEncoder.encodeNativeFrame(rgbPic);
        encodedFrames++;

        if (rawReplayOut != null) {
            for (int y = 0; y < PLAYER_CACHE_H; y++) {
                int sy = (y * height) / PLAYER_CACHE_H;
                int rowOff = sy * width * 3;
                for (int x = 0; x < PLAYER_CACHE_W; x++) {
                    int sx = (x * width) / PLAYER_CACHE_W;
                    int idx = rowOff + sx * 3;
                    rawReplayOut.writeByte(rawRgb[idx]);
                    rawReplayOut.writeByte(rawRgb[idx + 1]);
                    rawReplayOut.writeByte(rawRgb[idx + 2]);
                }
            }
        }
    }

    public synchronized void finishAndCalibrate(long actualElapsedMs) throws IOException {
        if (rawReplayOut != null) {
            try {
                rawReplayOut.flush();
                rawReplayOut.close();
            } catch (Exception ignored) {}
            rawReplayOut = null;
        }

        File cacheFile = getReplayCacheFile(outputFile);
        if (cacheFile.exists() && cacheFile.length() >= 20) {
            try (RandomAccessFile cRaf = new RandomAccessFile(cacheFile, "rw")) {
                cRaf.seek(12);
                cRaf.writeLong(actualElapsedMs);
            } catch (Exception ignored) {}
        }

        if (seqEncoder != null) {
            try {
                seqEncoder.finish();
            } finally {
                seqEncoder = null;
                NIOUtils.closeQuietly(channel);
                channel = null;
            }
        }

        // Patch the MP4 container header so its duration equals the exact wall-clock recording duration!
        VariableDurationMp4Patcher.calibrateExactDuration(outputFile, actualElapsedMs, encodedFrames);
    }
}
