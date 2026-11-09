package com.ahmed.screenrecorder.util;

import com.ahmed.screenrecorder.ScreenRecorderClient;
import com.ahmed.screenrecorder.config.RecordingConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Size;

import java.io.File;
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
        return dir.getUsableSpace() > 250L * 1024L * 1024L;
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
            list.add(probeMp4WithJCodec(f));
        }
        return list;
    }

    private static RecordingMetadata probeMp4WithJCodec(File file) {
        long sizeBytes = file.length();
        String sizeStr = String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0));
        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(file.lastModified()));
        String duration = "00:00:00";
        String resolution = "854x480";
        String fps = "20 FPS";

        SeekableByteChannel ch = null;
        try {
            ch = NIOUtils.readableFileChannel(file.getAbsolutePath());
            FrameGrab grab = FrameGrab.createFrameGrab(ch);
            DemuxerTrackMeta meta = grab.getVideoTrack().getMeta();
            if (meta != null) {
                double durSec = meta.getTotalDuration();
                int totalFrames = meta.getTotalFrames();
                if (durSec <= 0 && totalFrames > 0) {
                    durSec = totalFrames / 20.0;
                }
                int sec = (int) Math.round(durSec);
                duration = String.format("%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);

                Size dim = meta.getDimensions();
                if (dim != null && dim.getWidth() > 0) {
                    resolution = dim.getWidth() + "x" + dim.getHeight();
                }
                if (durSec > 0 && totalFrames > 0) {
                    fps = Math.round(totalFrames / durSec) + " FPS";
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (ch != null) NIOUtils.closeQuietly(ch);
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
