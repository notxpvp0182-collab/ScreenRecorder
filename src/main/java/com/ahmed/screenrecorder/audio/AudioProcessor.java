package com.ahmed.screenrecorder.audio;

/**
 * Real-time DSP processor for 16-bit Little-Endian Stereo/Mono PCM audio streams.
 * Handles Volume scaling, dB Gain, Noise Gate with envelope smoothing, and High-Pass / Spectral Gate Noise Suppression.
 */
public class AudioProcessor {
    private float envelope = 0.0f;
    private float prevSampleLeft = 0.0f;
    private float prevOutLeft = 0.0f;
    private float prevSampleRight = 0.0f;
    private float prevOutRight = 0.0f;

    public static void applyVolume(byte[] pcmBuffer, int length, int volumePercent) {
        if (volumePercent == 100) return;
        float multiplier = volumePercent / 100.0f;

        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((pcmBuffer[i] & 0xFF) | (pcmBuffer[i + 1] << 8));
            int scaled = Math.round(sample * multiplier);
            scaled = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
            pcmBuffer[i] = (byte) (scaled & 0xFF);
            pcmBuffer[i + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
    }

    public void processMicrophoneBuffer(
            byte[] pcmBuffer,
            int length,
            int volumePercent,
            float gainDb,
            boolean enableNoiseSuppression,
            boolean enableNoiseGate,
            float gateThresholdDb
    ) {
        float linearGain = (volumePercent / 100.0f) * (float) Math.pow(10.0, gainDb / 20.0);
        float gateThresholdLinear = (float) Math.pow(10.0, gateThresholdDb / 20.0);

        // Calculate block RMS for Noise Gate
        double sumSquares = 0;
        int sampleCount = length / 2;
        if (sampleCount == 0) return;

        for (int i = 0; i < length - 1; i += 2) {
            short s = (short) ((pcmBuffer[i] & 0xFF) | (pcmBuffer[i + 1] << 8));
            float norm = s / 32768.0f;
            sumSquares += norm * norm;
        }
        float rms = (float) Math.sqrt(sumSquares / sampleCount);

        float targetGateGain = 1.0f;
        if (enableNoiseGate && rms < gateThresholdLinear) {
            targetGateGain = 0.0f;
        }

        // Smooth attack/release envelope to avoid clicking
        float smoothing = targetGateGain > envelope ? 0.25f : 0.05f;
        envelope = envelope + smoothing * (targetGateGain - envelope);

        // High-pass filter coefficient (~85Hz cutoff at 44.1kHz to remove rumble/hum)
        final float hpAlpha = 0.988f;

        for (int i = 0; i < length - 3; i += 4) {
            short left = (short) ((pcmBuffer[i] & 0xFF) | (pcmBuffer[i + 1] << 8));
            short right = (short) ((pcmBuffer[i + 2] & 0xFF) | (pcmBuffer[i + 3] << 8));

            float fLeft = left / 32768.0f;
            float fRight = right / 32768.0f;

            if (enableNoiseSuppression) {
                float outL = hpAlpha * (prevOutLeft + fLeft - prevSampleLeft);
                prevSampleLeft = fLeft;
                prevOutLeft = outL;
                fLeft = outL;

                float outR = hpAlpha * (prevOutRight + fRight - prevSampleRight);
                prevSampleRight = fRight;
                prevOutRight = outR;
                fRight = outR;

                // Soft low-level spectral floor attenuation
                if (Math.abs(fLeft) < 0.004f) fLeft *= 0.25f;
                if (Math.abs(fRight) < 0.004f) fRight *= 0.25f;
            }

            fLeft *= linearGain * envelope;
            fRight *= linearGain * envelope;

            // Soft limiter to prevent harsh digital clipping
            fLeft = softClip(fLeft);
            fRight = softClip(fRight);

            short outShortL = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(fLeft * 32767.0f)));
            short outShortR = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(fRight * 32767.0f)));

            pcmBuffer[i] = (byte) (outShortL & 0xFF);
            pcmBuffer[i + 1] = (byte) ((outShortL >> 8) & 0xFF);
            pcmBuffer[i + 2] = (byte) (outShortR & 0xFF);
            pcmBuffer[i + 3] = (byte) ((outShortR >> 8) & 0xFF);
        }
    }

    private float softClip(float x) {
        if (x > 1.0f) return 1.0f;
        if (x < -1.0f) return -1.0f;
        return x;
    }

    public static void mixPcmStreams(byte[] gameBuffer, byte[] micBuffer, byte[] outBuffer, int length) {
        for (int i = 0; i < length - 1; i += 2) {
            short g = (short) ((gameBuffer[i] & 0xFF) | (gameBuffer[i + 1] << 8));
            short m = (short) ((micBuffer[i] & 0xFF) | (micBuffer[i + 1] << 8));
            int mixed = g + m;
            if (mixed > Short.MAX_VALUE) mixed = Short.MAX_VALUE;
            if (mixed < Short.MIN_VALUE) mixed = Short.MIN_VALUE;
            outBuffer[i] = (byte) (mixed & 0xFF);
            outBuffer[i + 1] = (byte) ((mixed >> 8) & 0xFF);
        }
    }
}
