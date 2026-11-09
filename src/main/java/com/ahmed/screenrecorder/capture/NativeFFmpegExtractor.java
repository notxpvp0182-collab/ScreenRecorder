package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Universal Cross-Platform FFmpeg Engine Manager for:
 * - Windows PC & Laptop (64-bit & 32-bit -> ffmpeg.exe)
 * - Linux PC & Laptop (x86_64 -> ffmpeg-linux-x64)
 * - macOS / MacBook (Intel x64 & Apple Silicon M1/M2/M3 arm64)
 * - Android Mobile Launchers (Pojav/Zalith/FoldCraft ARM64 & ARMv7 32-bit)
 *
 * Keeps the mod JAR lightweight (~3 MB, zero ZIP corruption) and automatically downloads & caches
 * the exact native FFmpeg binary for the user's PC, Laptop, or Mobile on first launch!
 */
public class NativeFFmpegExtractor {
    private static String cachedWorkingBinary = null;
    private static final AtomicBoolean downloading = new AtomicBoolean(false);
    private static volatile int downloadProgressPct = 0;

    public static void warmUpAsync() {
        Thread t = new Thread(() -> {
            try {
                getExecutableFFmpegPath();
            } catch (Exception ignored) {}
        }, "OBS-Universal-FFmpeg-Setup");
        t.setDaemon(true);
        t.start();
    }

    public static boolean isDownloading() {
        return downloading.get();
    }

    public static int getDownloadProgressPct() {
        return downloadProgressPct;
    }

    public static synchronized String getExecutableFFmpegPath() {
        if (cachedWorkingBinary != null) {
            return cachedWorkingBinary;
        }

        // 1. Check user custom path or system-installed FFmpeg (common on PC/Linux/macOS)
        String cfgBin = RecordingConfig.get().ffmpegPath;
        if (cfgBin != null && !cfgBin.isBlank() && testBinary(cfgBin)) {
            cachedWorkingBinary = cfgBin;
            return cachedWorkingBinary;
        }

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String defaultSysCmd = isWindows ? "ffmpeg.exe" : "ffmpeg";
        if (testBinary(defaultSysCmd)) {
            cachedWorkingBinary = defaultSysCmd;
            return cachedWorkingBinary;
        }

        // 2. Determine exact OS & CPU Architecture (Windows, Linux, macOS, Android ARM64/ARMv7)
        String osName = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String downloadUrl = resolvePlatformFFmpegUrl(osName, arch);
        String binaryFileName = isWindows ? "ffmpeg.exe" : "ffmpeg";

        // 3. Check candidate executable directories (handles both PC folders and Android non-noexec temp dirs)
        List<File> candidateDirs = new ArrayList<>();
        candidateDirs.add(new File(FabricLoader.getInstance().getConfigDir().toFile(), "obs_ffmpeg_engine"));
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir != null) candidateDirs.add(new File(tmpDir, "obs_ffmpeg_engine"));
        String userHome = System.getProperty("user.home");
        if (userHome != null) candidateDirs.add(new File(userHome, "obs_ffmpeg_engine"));

        // First check if already downloaded in any candidate folder
        for (File dir : candidateDirs) {
            File bin = new File(dir, binaryFileName);
            if (bin.exists() && bin.length() > 5 * 1024 * 1024) {
                makeExecutable(bin, isWindows);
                if (testBinary(bin.getAbsolutePath())) {
                    cachedWorkingBinary = bin.getAbsolutePath();
                    ScreenRecorderClient.LOGGER.info("Loaded cached Native FFmpeg for [{} / {}]: {}", osName, arch, cachedWorkingBinary);
                    return cachedWorkingBinary;
                }
            }
        }

        // 4. Download the exact native binary for this PC / Laptop / Mobile if not cached yet
        if (downloadUrl != null && !downloading.get()) {
            downloading.set(true);
            try {
                File downloadTarget = new File(candidateDirs.get(0), binaryFileName);
                downloadTarget.getParentFile().mkdirs();

                if (downloadBinaryWithProgress(downloadUrl, downloadTarget)) {
                    for (File dir : candidateDirs) {
                        try {
                            dir.mkdirs();
                            File testBin = new File(dir, binaryFileName);
                            if (!testBin.equals(downloadTarget)) {
                                java.nio.file.Files.copy(downloadTarget.toPath(), testBin.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                            makeExecutable(testBin, isWindows);
                            if (testBinary(testBin.getAbsolutePath())) {
                                cachedWorkingBinary = testBin.getAbsolutePath();
                                ScreenRecorderClient.LOGGER.info("Downloaded & verified Native FFmpeg for [{} / {}]", osName, arch);
                                return cachedWorkingBinary;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } finally {
                downloading.set(false);
            }
        }

        return null;
    }

    private static String resolvePlatformFFmpegUrl(String os, String arch) {
        String base = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.0/";
        if (os.contains("win")) {
            if (arch.contains("64")) return base + "ffmpeg-win32-x64";
            return base + "ffmpeg-win32-ia32";
        } else if (os.contains("mac") || os.contains("darwin")) {
            if (arch.contains("aarch64") || arch.contains("arm")) return base + "ffmpeg-darwin-arm64";
            return base + "ffmpeg-darwin-x64";
        } else {
            // Linux PC or Android Mobile
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                return base + "ffmpeg-linux-arm64";
            } else if (arch.contains("arm")) {
                return base + "ffmpeg-linux-arm";
            } else if (arch.contains("64")) {
                return base + "ffmpeg-linux-x64";
            } else {
                return base + "ffmpeg-linux-ia32";
            }
        }
    }

    private static boolean downloadBinaryWithProgress(String urlStr, File targetFile) {
        File partFile = new File(targetFile.getParentFile(), targetFile.getName() + ".part");
        try {
            ScreenRecorderClient.LOGGER.info("Downloading Native FFmpeg Engine from: {}", urlStr);
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "OBS-Minecraft-Mod/1.21.11");

            int status = conn.getResponseCode();
            if (status >= 300 && status < 400) {
                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    conn = (HttpURLConnection) URI.create(loc).toURL().openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(20000);
                }
            }

            long totalLen = conn.getContentLengthLong();
            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream(), 65536);
                 FileOutputStream out = new FileOutputStream(partFile)) {
                byte[] buf = new byte[65536];
                long readTotal = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    readTotal += n;
                    if (totalLen > 0) {
                        downloadProgressPct = (int) ((readTotal * 100L) / totalLen);
                    }
                }
            }

            if (partFile.exists() && partFile.length() > 5 * 1024 * 1024) {
                if (targetFile.exists()) targetFile.delete();
                return partFile.renameTo(targetFile);
            }
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.warn("FFmpeg auto-download fallback (offline or blocked): {}", e.getMessage());
        } finally {
            if (partFile.exists()) partFile.delete();
        }
        return false;
    }

    private static void makeExecutable(File bin, boolean isWindows) {
        try {
            bin.setExecutable(true, false);
            if (!isWindows) {
                Runtime.getRuntime().exec(new String[]{"chmod", "755", bin.getAbsolutePath()}).waitFor(1, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {}
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
