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
 * Lock-Free Non-Blocking Cross-Platform FFmpeg Manager.
 * NEVER blocks the Minecraft Main/Render thread! If a background download is still in progress
 * or offline, getReadyFFmpegPathInstantly() returns null in 0ms so recording starts immediately without freezing!
 */
public class NativeFFmpegExtractor {
    private static volatile String cachedWorkingBinary = null;
    private static final AtomicBoolean checkedLocal = new AtomicBoolean(false);
    private static final AtomicBoolean downloading = new AtomicBoolean(false);
    private static volatile int downloadProgressPct = 0;

    public static void warmUpAsync() {
        if (downloading.get() || cachedWorkingBinary != null) return;
        Thread t = new Thread(NativeFFmpegExtractor::backgroundLocateOrDownload, "OBS-FFmpeg-BackgroundWorker");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * Called when clicking Start Recording: Returns in 0.00ms!
     * Never waits for network or heavy process checks on the UI thread.
     */
    public static String getReadyFFmpegPathInstantly() {
        return cachedWorkingBinary;
    }

    private static void backgroundLocateOrDownload() {
        if (cachedWorkingBinary != null) return;

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String binaryFileName = isWindows ? "ffmpeg.exe" : "ffmpeg";

        // 1. Check custom or system PATH in background
        String cfgBin = RecordingConfig.get().ffmpegPath;
        if (cfgBin != null && !cfgBin.isBlank() && testBinary(cfgBin)) {
            cachedWorkingBinary = cfgBin;
            return;
        }
        if (testBinary(isWindows ? "ffmpeg.exe" : "ffmpeg")) {
            cachedWorkingBinary = isWindows ? "ffmpeg.exe" : "ffmpeg";
            return;
        }

        // 2. Check cached local directories
        List<File> candidateDirs = new ArrayList<>();
        candidateDirs.add(new File(FabricLoader.getInstance().getConfigDir().toFile(), "obs_ffmpeg_engine"));
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir != null) candidateDirs.add(new File(tmpDir, "obs_ffmpeg_engine"));
        String userHome = System.getProperty("user.home");
        if (userHome != null) candidateDirs.add(new File(userHome, "obs_ffmpeg_engine"));

        for (File dir : candidateDirs) {
            File bin = new File(dir, binaryFileName);
            if (bin.exists() && bin.length() > 5 * 1024 * 1024) {
                makeExecutable(bin, isWindows);
                if (testBinary(bin.getAbsolutePath())) {
                    cachedWorkingBinary = bin.getAbsolutePath();
                    return;
                }
            }
        }

        // 3. Background download if online
        if (!downloading.compareAndSet(false, true)) return;
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            String arch = System.getProperty("os.arch", "").toLowerCase();
            String url = resolvePlatformFFmpegUrl(osName, arch);
            if (url == null) return;

            File target = new File(candidateDirs.get(0), binaryFileName);
            target.getParentFile().mkdirs();
            if (downloadBinaryQuietly(url, target)) {
                for (File dir : candidateDirs) {
                    try {
                        dir.mkdirs();
                        File testBin = new File(dir, binaryFileName);
                        if (!testBin.equals(target)) {
                            java.nio.file.Files.copy(target.toPath(), testBin.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        makeExecutable(testBin, isWindows);
                        if (testBinary(testBin.getAbsolutePath())) {
                            cachedWorkingBinary = testBin.getAbsolutePath();
                            ScreenRecorderClient.LOGGER.info("Background FFmpeg ready: {}", cachedWorkingBinary);
                            return;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } finally {
            downloading.set(false);
        }
    }

    private static String resolvePlatformFFmpegUrl(String os, String arch) {
        String base = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.0/";
        if (os.contains("win")) {
            return arch.contains("64") ? base + "ffmpeg-win32-x64" : base + "ffmpeg-win32-ia32";
        } else if (os.contains("mac") || os.contains("darwin")) {
            return (arch.contains("aarch64") || arch.contains("arm")) ? base + "ffmpeg-darwin-arm64" : base + "ffmpeg-darwin-x64";
        } else {
            if (arch.contains("aarch64") || arch.contains("arm64")) return base + "ffmpeg-linux-arm64";
            if (arch.contains("arm")) return base + "ffmpeg-linux-arm";
            return arch.contains("64") ? base + "ffmpeg-linux-x64" : base + "ffmpeg-linux-ia32";
        }
    }

    private static boolean downloadBinaryQuietly(String urlStr, File targetFile) {
        File partFile = new File(targetFile.getParentFile(), targetFile.getName() + ".part");
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "OBS-Minecraft-Mod/1.21.11");

            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream(), 32768);
                 FileOutputStream out = new FileOutputStream(partFile)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            if (partFile.exists() && partFile.length() > 5 * 1024 * 1024) {
                if (targetFile.exists()) targetFile.delete();
                return partFile.renameTo(targetFile);
            }
        } catch (Exception ignored) {
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
            return p.waitFor(1, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
