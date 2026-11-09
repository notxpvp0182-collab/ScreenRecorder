package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Extracts and verifies the real bundled static FFmpeg binary (ARMv7 32-bit, ARM64 64-bit, or System PATH)
 * from inside the mod JAR onto an executable directory on Android/Linux/Windows.
 */
public class NativeFFmpegExtractor {
    private static String cachedWorkingBinary = null;

    public static synchronized String getExecutableFFmpegPath() {
        if (cachedWorkingBinary != null) {
            return cachedWorkingBinary;
        }

        // 1. Check user custom/system PATH first
        String cfgBin = RecordingConfig.get().ffmpegPath;
        if (cfgBin != null && !cfgBin.isBlank() && testBinary(cfgBin)) {
            cachedWorkingBinary = cfgBin;
            return cachedWorkingBinary;
        }
        if (testBinary("ffmpeg")) {
            cachedWorkingBinary = "ffmpeg";
            return cachedWorkingBinary;
        }

        // 2. Detect device CPU architecture (Your crash log: ARMv7 Processor rev 4 (v7l) -> os.arch = "arm")
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String resourcePath;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            resourcePath = "/assets/screen_recorder/bin/ffmpeg-arm64";
        } else {
            resourcePath = "/assets/screen_recorder/bin/ffmpeg-arm";
        }

        // 3. Try extracting into executable directories (Android blocks exec on /sdcard, so we also test java.io.tmpdir & user.home)
        List<File> candidateDirs = new ArrayList<>();
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir != null) candidateDirs.add(new File(tmpDir, "obs_ffmpeg_bin"));
        String userHome = System.getProperty("user.home");
        if (userHome != null) candidateDirs.add(new File(userHome, "obs_ffmpeg_bin"));
        candidateDirs.add(new File(FabricLoader.getInstance().getConfigDir().toFile(), "obs_ffmpeg_bin"));

        for (File dir : candidateDirs) {
            try {
                dir.mkdirs();
                File targetBin = new File(dir, "ffmpeg");
                if (!targetBin.exists() || targetBin.length() < 1024 * 1024) {
                    try (InputStream in = NativeFFmpegExtractor.class.getResourceAsStream(resourcePath)) {
                        if (in == null) break;
                        Files.copy(in, targetBin.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                targetBin.setExecutable(true, false);
                Runtime.getRuntime().exec(new String[]{"chmod", "755", targetBin.getAbsolutePath()}).waitFor(1, TimeUnit.SECONDS);

                if (testBinary(targetBin.getAbsolutePath())) {
                    cachedWorkingBinary = targetBin.getAbsolutePath();
                    ScreenRecorderClient.LOGGER.info("Verified Native Bundled FFmpeg ({}) at: {}", arch, cachedWorkingBinary);
                    return cachedWorkingBinary;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private static boolean testBinary(String binPath) {
        try {
            Process p = new ProcessBuilder(binPath, "-version").start();
            return p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
