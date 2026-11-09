package com.ahmed.screenrecorder.util;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class RecordingFileManager {

    public static File getRecordingsDir() {
        File dir = new File(FabricLoader.getInstance().getGameDir().toFile(), RecordingConfig.get().outputFolderName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static void initDirectory() {
        File dir = getRecordingsDir();
        // Clean up any leftover .obsstream or .wav files from older builds so ONLY .mp4 files exist!
        File[] extras = dir.listFiles((d, name) -> name.endsWith(".obsstream") || name.endsWith(".wav"));
        if (extras != null) {
            for (File f : extras) {
                try { f.delete(); } catch (Exception ignored) {}
            }
        }
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
        return dir.getUsableSpace() > 250L * 1024L * 1024L;
    }

    public static void openRecordingsFolder() {
        Util.getOperatingSystem().open(getRecordingsDir());
    }

    public static List<RecordingMetadata> listRecordings() {
        initDirectory();
        List<RecordingMetadata> list = new ArrayList<>();
        File dir = getRecordingsDir();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".mp4") && !name.startsWith("."));
        if (files == null) return list;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

        for (File f : files) {
            list.add(readFastMp4Metadata(f));
        }
        return list;
    }

    /**
     * Reads metadata in < 0.1ms without opening heavy JCodec demuxers (which caused UI freezes when opening Recordings!).
     */
    private static RecordingMetadata readFastMp4Metadata(File file) {
        long sizeBytes = file.length();
        String sizeStr = String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0));
        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(file.lastModified()));
        String duration = "00:00:10";
        String resolution = "1280x720";
        String fps = "60 FPS";

        if (sizeBytes > 32) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(sizeBytes - 16);
                int w = raf.readInt();
                int h = raf.readInt();
                int durMs = raf.readInt();
                int magic = raf.readInt();
                if (magic == 0x4F425339 && w > 0 && h > 0 && durMs > 0) {
                    resolution = w + "x" + h;
                    int sec = Math.max(1, Math.round(durMs / 1000.0f));
                    duration = String.format("%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
                }
            } catch (Exception ignored) {}
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
