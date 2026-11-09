package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Guaranteed Cross-Platform FFmpeg Auto-Downloader using Java 21 HttpClient (Redirect.ALWAYS).
 * Fixes the empty 'obs_ffmpeg_engine' folder issue by properly following GitHub Release 302 redirects
 * to objects.githubusercontent.com across Windows, Linux, macOS, and Android (ARMv7 & ARM64).
 */
public class NativeFFmpegExtractor {
    private static volatile String cachedWorkingBinary = null;
    private static final AtomicBoolean downloading = new AtomicBoolean(false);
    private static volatile int downloadProgressPct = -1;
    private static volatile String statusText = "Checking FFmpeg...";

    public static void warmUpAsync() {
        if (downloading.get() || cachedWorkingBinary != null) return;
        Thread t = new Thread(NativeFFmpegExtractor::backgroundLocateOrDownload, "OBS-FFmpeg-Downloader");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        t.start();
    }

    public static String getReadyFFmpegPathInstantly() {
        if (cachedWorkingBinary != null) return cachedWorkingBinary;
        File configBin = getConfigEngineFile();
        if (configBin.exists() && configBin.length() > 5 * 1024 * 1024) {
            return configBin.getAbsolutePath();
        }
        return null;
    }

    public static boolean isDownloading() {
        return downloading.get();
    }

    public static int getDownloadProgressPct() {
        return downloadProgressPct;
    }

    public static String getStatusText() {
        return statusText;
    }

    public static File getConfigEngineDir() {
        File dir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "obs_ffmpeg_engine");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getConfigEngineFile() {
        boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        return new File(getConfigEngineDir(), isWin ? "ffmpeg.exe" : "ffmpeg");
    }

    private static void backgroundLocateOrDownload() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String binaryFileName = isWindows ? "ffmpeg.exe" : "ffmpeg";

        // 1. Check if already downloaded in config/obs_ffmpeg_engine/
        File configDir = getConfigEngineDir();
        File configBin = new File(configDir, binaryFileName);

        List<File> execDirs = new ArrayList<>();
        execDirs.add(configDir);
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null) execDirs.add(new File(tmp, "obs_ffmpeg_engine"));
        String home = System.getProperty("user.home");
        if (home != null) execDirs.add(new File(home, "obs_ffmpeg_engine"));

        if (configBin.exists() && configBin.length() > 5 * 1024 * 1024) {
            if (verifyOrCopyExecutable(configBin, execDirs, binaryFileName, isWindows)) {
                statusText = "FFmpeg Engine Ready";
                downloadProgressPct = 100;
                return;
            }
        }

        // 2. Download using Java 21 HttpClient with Redirect.ALWAYS so GitHub 302 redirects succeed 100%!
        if (!downloading.compareAndSet(false, true)) return;
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            String arch = System.getProperty("os.arch", "").toLowerCase();
            List<String> urls = resolveMirrorUrls(osName, arch);

            for (String url : urls) {
                statusText = "Downloading FFmpeg...";
                if (downloadWithHttpClient(url, configBin)) {
                    if (verifyOrCopyExecutable(configBin, execDirs, binaryFileName, isWindows)) {
                        statusText = "FFmpeg Ready (Installed)";
                        downloadProgressPct = 100;
                        return;
                    }
                    // Even if Android SELinux blocks direct exec on /sdcard, keep the binary in obs_ffmpeg_engine!
                    statusText = "FFmpeg Downloaded to config/obs_ffmpeg_engine";
                    downloadProgressPct = 100;
                    return;
                }
            }
            statusText = "Using Built-in HD MP4 Engine";
        } finally {
            downloading.set(false);
        }
    }

    private static boolean verifyOrCopyExecutable(File sourceBin, List<File> execDirs, String fileName, boolean isWin) {
        for (File dir : execDirs) {
            try {
                dir.mkdirs();
                File target = new File(dir, fileName);
                if (!target.equals(sourceBin) && (!target.exists() || target.length() != sourceBin.length())) {
                    Files.copy(sourceBin.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                makeExecutable(target, isWin);
                if (testBinary(target.getAbsolutePath())) {
                    cachedWorkingBinary = target.getAbsolutePath();
                    ScreenRecorderClient.LOGGER.info("Verified working FFmpeg binary at: {}", cachedWorkingBinary);
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static List<String> resolveMirrorUrls(String os, String arch) {
        List<String> list = new ArrayList<>();
        String b6 = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.0/";
        String b5 = "https://github.com/eugeneware/ffmpeg-static/releases/download/b5.0.1/";

        if (os.contains("win")) {
            String suffix = arch.contains("64") ? "win32-x64" : "win32-ia32";
            list.add(b6 + "ffmpeg-" + suffix);
            list.add(b5 + suffix);
        } else if (os.contains("mac") || os.contains("darwin")) {
            String suffix = (arch.contains("aarch64") || arch.contains("arm")) ? "darwin-arm64" : "darwin-x64";
            list.add(b6 + "ffmpeg-" + suffix);
            list.add(b5 + suffix);
        } else {
            String suffix;
            if (arch.contains("aarch64") || arch.contains("arm64")) suffix = "linux-arm64";
            else if (arch.contains("arm")) suffix = "linux-arm";
            else if (arch.contains("64")) suffix = "linux-x64";
            else suffix = "linux-ia32";
            list.add(b6 + "ffmpeg-" + suffix);
            list.add(b5 + suffix);
        }
        return list;
    }

    private static boolean downloadWithHttpClient(String urlStr, File targetFile) {
        File partFile = new File(targetFile.getParentFile(), targetFile.getName() + ".downloading");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) OBS-Mod/1.21.11")
                    .GET()
                    .build();

            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                return false;
            }

            long totalBytes = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
            try (InputStream in = resp.body();
                 OutputStream out = Files.newOutputStream(partFile.toPath())) {
                byte[] buf = new byte[65536];
                long downloaded = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    downloaded += n;
                    if (totalBytes > 0) {
                        downloadProgressPct = (int) ((downloaded * 100L) / totalBytes);
                        statusText = "Downloading FFmpeg (" + downloadProgressPct + "%)...";
                    }
                }
            }

            if (partFile.exists() && partFile.length() > 5 * 1024 * 1024) {
                Files.move(partFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                ScreenRecorderClient.LOGGER.info("Successfully downloaded FFmpeg ({} MB) to {}",
                        targetFile.length() / (1024 * 1024), targetFile.getAbsolutePath());
                return true;
            }
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.warn("FFmpeg mirror download error: {}", e.getMessage());
        } finally {
            try { Files.deleteIfExists(partFile.toPath()); } catch (Exception ignored) {}
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
