package com.ahmed.screenrecorder.capture;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.Codec;
import org.jcodec.common.Format;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * 100% Android & PC Hardware-Compatible H.264 (YUV420J + ISO 'mp42'/'isom') MP4 Muxer.
 * Fixes both the JCodec Format.MOV compile requirement AND Android MediaCodec's strict
 * requirement for YUV420 planar frames and ISO Base Media 'mp42' ftyp headers!
 */
public class NativeMjpegMp4Muxer {
    private final File outputFile;
    private final int width;
    private final int height;
    private final int fps;

    private SeekableByteChannel channel;
    private SequenceEncoder seqEncoder;
    private Picture yuvPic;
    private int encodedFrames = 0;

    public NativeMjpegMp4Muxer(File outputFile, int width, int height, int fps) throws IOException {
        this.outputFile = outputFile;
        this.width = Math.max(256, (width / 16) * 16);
        this.height = Math.max(144, (height / 16) * 16);
        this.fps = Math.max(15, Math.min(30, fps));

        this.channel = NIOUtils.writableFileChannel(outputFile.getAbsolutePath());
        this.seqEncoder = new SequenceEncoder(channel, Rational.R(this.fps, 1), Format.MOV, Codec.H264, null);
        // Hardware Android/Windows players strictly require YUV420J (4:2:0 planar) for H.264!
        this.yuvPic = Picture.create(this.width, this.height, ColorSpace.YUV420J);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getFps() { return fps; }
    public int getEncodedFrames() { return encodedFrames; }

    public synchronized void loadRgbAndEncode(byte[] rawRgb, int count) throws IOException {
        if (seqEncoder == null || yuvPic == null || count <= 0) return;

        byte[] yPlane = yuvPic.getPlaneData(0);
        byte[] uPlane = yuvPic.getPlaneData(1);
        byte[] vPlane = yuvPic.getPlaneData(2);

        int w = this.width;
        int h = this.height;

        // Convert RGB24 to Hardware-Compliant YUV420 Planar
        for (int y = 0; y < h; y++) {
            int rowOff = y * w * 3;
            int yRow = y * w;
            int uvRow = (y >> 1) * (w >> 1);
            boolean evenY = (y & 1) == 0;

            for (int x = 0; x < w; x++) {
                int idx = rowOff + x * 3;
                int r = rawRgb[idx] & 0xFF;
                int g = rawRgb[idx + 1] & 0xFF;
                int b = rawRgb[idx + 2] & 0xFF;

                int yVal = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                yPlane[yRow + x] = (byte) (Math.max(0, Math.min(255, yVal)) - 128);

                if (evenY && (x & 1) == 0) {
                    int uVal = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int vVal = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                    int uvIdx = uvRow + (x >> 1);
                    uPlane[uvIdx] = (byte) (Math.max(0, Math.min(255, uVal)) - 128);
                    vPlane[uvIdx] = (byte) (Math.max(0, Math.min(255, vVal)) - 128);
                }
            }
        }

        for (int c = 0; c < count; c++) {
            seqEncoder.encodeNativeFrame(yuvPic);
            encodedFrames++;
        }
    }

    public synchronized void finishCleanly() throws IOException {
        if (seqEncoder != null) {
            try {
                seqEncoder.finish();
            } finally {
                seqEncoder = null;
                NIOUtils.closeQuietly(channel);
                channel = null;
            }
        }

        // Stamp standard ISO MP4 ('isom' / 'mp42') brand in the first 24 bytes so Android Gallery/File Manager plays it!
        stampIsoMp4BrandHeader(outputFile);
    }

    private static void stampIsoMp4BrandHeader(File file) {
        if (file == null || !file.exists() || file.length() < 32) return;
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] header = new byte[24];
            raf.seek(0);
            raf.readFully(header);
            // Check if box at offset 4 is 'ftyp'
            if (header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
                raf.seek(8);
                raf.write("isom".getBytes(StandardCharsets.US_ASCII)); // Major brand: isom
                raf.writeInt(512); // Minor version
                raf.write("isom".getBytes(StandardCharsets.US_ASCII)); // Compatible brand 1
                if ((header[3] & 0xFF) >= 24) {
                    raf.write("mp42".getBytes(StandardCharsets.US_ASCII)); // Compatible brand 2
                }
            }
        } catch (Exception ignored) {}
    }
}
