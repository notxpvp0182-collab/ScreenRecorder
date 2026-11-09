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
 * Finds or downloads the native FFmpeg binary and places it in an OS-executable partition
 * (including Android's internal app data partition where chmod +x is allowed).
 */
public class NativeFFmpegExtractor {
    private static volatile String cachedWorkingBinary = null;
    private static final AtomicBoolean downloading = new AtomicBoolean(false);

    public static void warmUpAsync() {
        if (downloading.get() || cachedWorkingBinary != null) return;
        Thread t = new Thread(NativeFFmpegExtractor::backgroundLocateOrDownload, "OBS-FFmpeg-Setup");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    public static String getReadyFFmpegPathInstantly() {
        return cachedWorkingBinary;
    }

    private static void backgroundLocateOrDownload() {
        if (cachedWorkingBinary != null) return;

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String binaryFileName = isWindows ? "ffmpeg.exe" : "ffmpeg";

        String cfgBin = RecordingConfig.get().ffmpegPath;
        if (cfgBin != null && !cfgBin.isBlank() && testBinary(cfgBin)) {
            cachedWorkingBinary = cfgBin;
            return;
        }
        if (testBinary(binaryFileName)) {
            cachedWorkingBinary = binaryFileName;
            return;
        }

        List<File> execDirs = getExecutableCandidateDirs();

        for (File dir : execDirs) {
            File bin = new File(dir, binaryFileName);
            if (bin.exists() && bin.length() > 5 * 1024 * 1024) {
                makeExecutable(bin, isWindows);
                if (testBinary(bin.getAbsolutePath())) {
                    cachedWorkingBinary = bin.getAbsolutePath();
                    ScreenRecorderClient.LOGGER.info("Active Native FFmpeg binary: {}", cachedWorkingBinary);
                    return;
                }
            }
        }

        if (!downloading.compareAndSet(false, true)) return;
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            String arch = System.getProperty("os.arch", "").toLowerCase();
            String url = resolvePlatformFFmpegUrl(osName, arch);
            if (url == null) return;

            File storageDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "obs_ffmpeg_engine");
            storageDir.mkdirs();
            File downloadedFile = new File(storageDir, binaryFileName);

            if (!downloadedFile.exists() || downloadedFile.length() < 5 * 1024 * 1024) {
                downloadBinaryQuietly(url, downloadedFile);
            }

            if (downloadedFile.exists() && downloadedFile.length() > 5 * 1024 * 1024) {
                for (File dir : execDirs) {
                    try {
                        dir.mkdirs();
                        File targetBin = new File(dir, binaryFileName);
                        if (!targetBin.equals(downloadedFile)) {
                            java.nio.file.Files.copy(downloadedFile.toPath(), targetBin.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        makeExecutable(targetBin, isWindows);
                        if (testBinary(targetBin.getAbsolutePath())) {
                            cachedWorkingBinary = targetBin.getAbsolutePath();
                            ScreenRecorderClient.LOGGER.info("Verified Native FFmpeg at: {}", cachedWorkingBinary);
                            return;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } finally {
            downloading.set(false);
        }
    }

    private static List<File> getExecutableCandidateDirs() {
        List<File> dirs = new ArrayList<>();
        // 1. Java native library path (On Android launchers this is inside /data/data/... or /data/user/0/... which allows exec!)
        String libPath = System.getProperty("java.library.path", "");
        for (String part : libPath.split(File.pathSeparator)) {
            if (!part.isBlank() && (part.contains("/data/") || part.contains("runtimes") || part.contains("bin"))) {
                File f = new File(part);
                if (f.exists() && f.canWrite()) dirs.add(f);
            }
        }
        // 2. Java home bin dir (Also inside internal executable storage on Pojav/Mojo/Zalith)
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File jBin = new File(javaHome, "bin");
            if (jBin.exists()) dirs.add(jBin);
        }
        // 3. Temp dir & user home
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null) dirs.add(new File(tmp, "obs_bin"));
        String home = System.getProperty("user.home");
        if (home != null) dirs.add(new File(home, "obs_bin"));
        dirs.add(new File(FabricLoader.getInstance().getConfigDir().toFile(), "obs_ffmpeg_engine"));
        return dirs;
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
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "OBS-Minecraft-Mod/1.21.11");

            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream(), 65536);
                 FileOutputStream out = new FileOutputStream(partFile)) {
                byte[] buf = new byte[65536];
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
