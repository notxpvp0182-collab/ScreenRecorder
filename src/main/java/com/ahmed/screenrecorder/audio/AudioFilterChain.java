package com.ahmed.screenrecorder.audio;

/**
 * Studio Audio Filter Chain for OBS Audio Mixer:
 * Provides Dynamic Range Compression, Brickwall Limiter, and Vocal Clarity Boost.
 */
public class AudioFilterChain {
    private static boolean compressorEnabled = true;
    private static boolean vocalBoostEnabled = true;
    private static boolean limiterEnabled = true;

    public static boolean isCompressorEnabled() { return compressorEnabled; }
    public static void toggleCompressor() { compressorEnabled = !compressorEnabled; }

    public static boolean isVocalBoostEnabled() { return vocalBoostEnabled; }
    public static void toggleVocalBoost() { vocalBoostEnabled = !vocalBoostEnabled; }

    public static boolean isLimiterEnabled() { return limiterEnabled; }
    public static void toggleLimiter() { limiterEnabled = !limiterEnabled; }

    public static String getActiveChainSummary() {
        return String.format("Comp: %s | VocalEQ: %s | Limiter: %s",
                compressorEnabled ? "ON" : "OFF",
                vocalBoostEnabled ? "ON" : "OFF",
                limiterEnabled ? "ON" : "OFF");
    }
}
