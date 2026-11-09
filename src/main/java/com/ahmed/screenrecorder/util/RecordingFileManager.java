package com.ahmed.screenrecorder.util;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RecordingFileManager {

    public static File getRecordingsDir() {
        File dir = new File(FabricLoader.getInstance().getGameDir().toFile(), RecordingConfig.get().outputFolderName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static void initDirectory() {
        getRecordingsDir();
    }

    public static File createUniqueRecordingFile() {
        File dir = getRecordingsDir();
        String prefix = RecordingConfig.get().fileNamingPrefix;
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = "Recording";
        }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String baseName = prefix + "_" + timestamp;
        File file = new File(dir, baseName + ".mp4");
        int counter = 1;
        while (file.exists()) {
            file = new File(dir, baseName + "_" + counter + ".mp4");
            counter++;
        }
        return file;
    }

    public static boolean hasEnoughDiskSpace() {
        File dir = getRecordingsDir();
        return dir.getUsableSpace() > 250L * 1024L * 1024L; // Minimum 250 MB free
    }

    public static void openRecordingsFolder() {
        Util.getOperatingSystem().open(getRecordingsDir());
    }

    public static List<RecordingMetadata> listRecordings() {
        List<RecordingMetadata> list = new ArrayList<>();
        File dir = getRecordingsDir();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".mp4"));
        if (files == null) return list;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

        for (File f : files) {
            list.add(probeFile(f));
        }
        return list;
    }

    private static RecordingMetadata probeFile(File file) {
        long sizeBytes = file.length();
        String sizeStr = String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0));
        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified()));
        String duration = "Unknown";
        String resolution = "Unknown";
        String fps = "Unknown";

        // Attempt fast ffprobe metadata read if available
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error", "-select_streams", "v:0",
                    "-show_entries", "stream=width,height,r_frame_rate,duration",
                    "-of", "default=noprint_wrappers=1:nokey=0",
                    file.getAbsolutePath()
            );
            Process process = pb.start();
            if (process.waitFor(400, TimeUnit.MILLISECONDS)) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    String w = null, h = null;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("width=")) w = line.substring(6);
                        else if (line.startsWith("height=")) h = line.substring(7);
                        else if (line.startsWith("r_frame_rate=")) {
                            String rate = line.substring(13);
                            if (rate.contains("/")) {
                                String[] parts = rate.split("/");
                                double num = Double.parseDouble(parts[0]);
                                double den = Double.parseDouble(parts[1]);
                                if (den > 0) fps = String.format("%.0f FPS", num / den);
                            }
                        } else if (line.startsWith("duration=")) {
                            String dur = line.substring(9);
                            if (!dur.equals("N/A")) {
                                double secs = Double.parseDouble(dur);
                                int totalSec = (int) Math.round(secs);
                                duration = String.format("%02d:%02d:%02d", totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60);
                            }
                        }
                    }
                    if (w != null && h != null) {
                        resolution = w + "x" + h;
                    }
                }
            } else {
                process.destroyForcibly();
            }
        } catch (Exception ignored) {
            // Fallback silently if ffprobe is not installed
        }

        return new RecordingMetadata(file, file.getName(), duration, resolution, fps, sizeStr, dateStr);
    }

    public static boolean deleteRecording(File file) {
        try {
            return Files.deleteIfExists(file.toPath());
        } catch (Exception e) {
            ScreenRecorderClient.LOGGER.error("Failed to delete recording file: " + file.getName(), e);
            return false;
        }
    }

    public record RecordingMetadata(
            File file,
            String filename,
            String duration,
            String resolution,
            String fps,
            String sizeFormatted,
            String creationDate
    ) {}
}
