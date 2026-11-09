package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Post-Finalize MP4 / MOV Container Timestamp Calibrator.
 * When JCodec SequenceEncoder finishes writing N frames, its default header assumes a fixed frame rate.
 * This patcher inspects the finalized MP4 atoms ('moov' -> 'mvhd', 'tkhd', 'mdhd', 'stts') and calibrates
 * the sample delta and track duration to match the EXACT wall-clock recording time (e.g., 50.00 seconds)!
 */
public class VariableDurationMp4Patcher {

    public static void calibrateExactDuration(File mp4File, long actualElapsedMs, int totalEncodedFrames) {
        if (mp4File == null || !mp4File.exists() || totalEncodedFrames <= 0 || actualElapsedMs <= 500) {
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(mp4File, "rw")) {
            long fileLen = raf.length();
            if (fileLen < 64) return;

            // Read tail of file where JCodec writes the 'moov' atom
            int searchSize = (int) Math.min(fileLen, 2 * 1024 * 1024);
            long searchStart = fileLen - searchSize;
            byte[] buf = new byte[searchSize];
            raf.seek(searchStart);
            raf.readFully(buf);

            // Calculate exact sample duration in a 1000-Hz timescale
            // Total duration in 1000-Hz units = actualElapsedMs
            int timescale = 1000;
            int sampleDelta = Math.max(1, Math.round((float) actualElapsedMs / totalEncodedFrames));
            int exactTotalDuration = sampleDelta * totalEncodedFrames;

            // 1. Patch 'mvhd' (Movie Header Box) timescale & duration
            patchAtomTimescaleAndDuration(buf, "mvhd", timescale, exactTotalDuration, false);

            // 2. Patch 'mdhd' (Media Header Box) timescale & duration
            patchAtomTimescaleAndDuration(buf, "mdhd", timescale, exactTotalDuration, false);

            // 3. Patch 'tkhd' (Track Header Box) duration
            patchTkhdDuration(buf, exactTotalDuration);

            // 4. Patch 'stts' (Decoding Time-to-Sample Box) sampleCount & sampleDelta
            patchSttsSampleDelta(buf, totalEncodedFrames, sampleDelta);

            raf.seek(searchStart);
            raf.write(buf);

            ScreenRecorderClient.LOGGER.info("Calibrated MP4 duration to exact {}s ({} frames, delta={}ms)",
                    String.format("%.2f", exactTotalDuration / 1000.0), totalEncodedFrames, sampleDelta);
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.warn("MP4 duration calibration skipped: {}", e.getMessage());
        }
    }

    private static void patchAtomTimescaleAndDuration(byte[] buf, String fourcc, int timescale, int duration, boolean is64) {
        byte[] pat = fourcc.getBytes(StandardCharsets.US_ASCII);
        for (int i = 4; i < buf.length - 32; i++) {
            if (buf[i] == pat[0] && buf[i + 1] == pat[1] && buf[i + 2] == pat[2] && buf[i + 3] == pat[3]) {
                int version = buf[i + 4] & 0xFF;
                if (version == 0) {
                    // version(1) + flags(3) + creation(4) + modification(4) = +12 -> timescale(4), duration(4)
                    writeIntBE(buf, i + 16, timescale);
                    writeIntBE(buf, i + 20, duration);
                }
            }
        }
    }

    private static void patchTkhdDuration(byte[] buf, int duration) {
        byte[] pat = "tkhd".getBytes(StandardCharsets.US_ASCII);
        for (int i = 4; i < buf.length - 36; i++) {
            if (buf[i] == pat[0] && buf[i + 1] == pat[1] && buf[i + 2] == pat[2] && buf[i + 3] == pat[3]) {
                int version = buf[i + 4] & 0xFF;
                if (version == 0) {
                    // version(1)+flags(3)+creation(4)+mod(4)+trackId(4)+reserved(4) = +20 -> duration(4)
                    writeIntBE(buf, i + 24, duration);
                }
            }
        }
    }

    private static void patchSttsSampleDelta(byte[] buf, int totalFrames, int sampleDelta) {
        byte[] pat = "stts".getBytes(StandardCharsets.US_ASCII);
        for (int i = 4; i < buf.length - 20; i++) {
            if (buf[i] == pat[0] && buf[i + 1] == pat[1] && buf[i + 2] == pat[2] && buf[i + 3] == pat[3]) {
                int entryCount = readIntBE(buf, i + 8);
                if (entryCount == 1) {
                    writeIntBE(buf, i + 12, totalFrames);
                    writeIntBE(buf, i + 16, sampleDelta);
                }
            }
        }
    }

    private static int readIntBE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static void writeIntBE(byte[] b, int off, int val) {
        b[off]     = (byte) ((val >> 24) & 0xFF);
        b[off + 1] = (byte) ((val >> 16) & 0xFF);
        b[off + 2] = (byte) ((val >> 8) & 0xFF);
        b[off + 3] = (byte) (val & 0xFF);
    }
}
