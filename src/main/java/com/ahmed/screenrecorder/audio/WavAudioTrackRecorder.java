package com.ahmed.screenrecorder.audio;

import java.io.*;

/**
 * Records synchronized 44.1kHz 16-bit Stereo Audio (In-Game Block/Step/Combat Sounds + Microphone Voice)
 * into a companion high-fidelity audio track for the recording session.
 */
public class WavAudioTrackRecorder {
    private RandomAccessFile wavRaf;
    private long dataBytesWritten = 0;

    public synchronized void start(File mp4File) {
        try {
            File wavFile = getCompanionWavFile(mp4File);
            wavRaf = new RandomAccessFile(wavFile, "rw");
            wavRaf.setLength(0);
            writeWavHeader(0);
            dataBytesWritten = 0;
        } catch (Exception ignored) {}
    }

    public static File getCompanionWavFile(File mp4File) {
        return new File(mp4File.getParentFile(), "." + mp4File.getName() + ".wav");
    }

    public synchronized void writePcmChunk(byte[] pcm, int length) {
        if (wavRaf == null) return;
        try {
            wavRaf.write(pcm, 0, length);
            dataBytesWritten += length;
        } catch (IOException ignored) {}
    }

    public synchronized void finish() {
        if (wavRaf == null) return;
        try {
            wavRaf.seek(0);
            writeWavHeader((int) dataBytesWritten);
            wavRaf.close();
        } catch (IOException ignored) {
        } finally {
            wavRaf = null;
        }
    }

    private void writeWavHeader(int pcmDataLength) throws IOException {
        int sampleRate = 44100;
        int channels = 2;
        int byteRate = sampleRate * channels * 2;
        wavRaf.writeBytes("RIFF");
        wavRaf.writeInt(Integer.reverseBytes(36 + pcmDataLength));
        wavRaf.writeBytes("WAVEfmt ");
        wavRaf.writeInt(Integer.reverseBytes(16));
        wavRaf.writeShort(Short.reverseBytes((short) 1)); // PCM
        wavRaf.writeShort(Short.reverseBytes((short) channels));
        wavRaf.writeInt(Integer.reverseBytes(sampleRate));
        wavRaf.writeInt(Integer.reverseBytes(byteRate));
        wavRaf.writeShort(Short.reverseBytes((short) (channels * 2)));
        wavRaf.writeShort(Short.reverseBytes((short) 16));
        wavRaf.writeBytes("data");
        wavRaf.writeInt(Integer.reverseBytes(pcmDataLength));
    }
}
