package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.capture.NativeMjpegMp4Muxer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.ColorUtil;
import org.jcodec.scale.Transform;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VideoPlayerScreen extends Screen {
    private final Screen parent;
    private final File videoFile;

    private static final int DISP_W = NativeMjpegMp4Muxer.PLAYER_CACHE_W;
    private static final int DISP_H = NativeMjpegMp4Muxer.PLAYER_CACHE_H;
    private final int[] pixelBuffer = new int[DISP_W * DISP_H];

    private int totalFrames = 1;
    private int currentFrameIdx = 0;
    private long totalDurationMs = 10000L;
    private volatile boolean playing = true;
    private volatile int seekRequestFrame = -1;

    private final AtomicBoolean hasDecodedFrame = new AtomicBoolean(false);
    private final AtomicInteger loadProgressPct = new AtomicInteger(0);
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);

    private ThemeButton playPauseBtn;
    private int winX, winY, winW, winH;

    public VideoPlayerScreen(Screen parent, File videoFile) {
        super(Text.literal(videoFile.getName()));
        this.parent = parent;
        this.videoFile = videoFile;
        startNonBlockingDecoder();
    }

    private void computeWindowBounds() {
        this.winW = Math.min(500, Math.max(320, (int) (this.width * 0.88f)));
        this.winH = Math.min(285, Math.max(195, (int) (this.height * 0.86f)));
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;
    }

    private void startNonBlockingDecoder() {
        workerRunning.set(true);
        Thread t = new Thread(() -> {
            File streamCache = NativeMjpegMp4Muxer.getReplayCacheFile(videoFile);
            if (streamCache.exists() && streamCache.length() > 24) {
                playFromFastCache(streamCache);
            } else {
                playFromJCodecMp4();
            }
        }, "OBS-FastVideoPlayer");
        t.setDaemon(true);
        t.start();
    }

    private void playFromFastCache(File cacheFile) {
        try (RandomAccessFile raf = new RandomAccessFile(cacheFile, "r")) {
            int magic = raf.readInt();
            if (magic != 0x4F425332) {
                playFromJCodecMp4();
                return;
            }
            int w = raf.readInt();
            int h = raf.readInt();
            this.totalDurationMs = Math.max(1000L, raf.readLong());
            final int frameBytes = w * h * 3;
            this.totalFrames = Math.max(1, (int) ((raf.length() - 20) / frameBytes));
            loadProgressPct.set(100);

            byte[] raw = new byte[frameBytes];
            long intervalMs = Math.max(16L, this.totalDurationMs / this.totalFrames);

            while (workerRunning.get()) {
                if (seekRequestFrame >= 0) {
                    currentFrameIdx = Math.min(totalFrames - 1, seekRequestFrame);
                    seekRequestFrame = -1;
                }

                if (!playing && hasDecodedFrame.get()) {
                    Thread.sleep(25);
                    continue;
                }

                long t0 = System.currentTimeMillis();
                long offset = 20L + (long) currentFrameIdx * frameBytes;
                if (offset + frameBytes > raf.length()) {
                    currentFrameIdx = 0;
                    playing = false;
                    continue;
                }

                raf.seek(offset);
                raf.readFully(raw);

                int idx = 0;
                for (int i = 0; i < DISP_W * DISP_H; i++) {
                    int r = raw[idx] & 0xFF;
                    int g = raw[idx + 1] & 0xFF;
                    int b = raw[idx + 2] & 0xFF;
                    idx += 3;
                    pixelBuffer[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                hasDecodedFrame.set(true);
                if (playing) currentFrameIdx++;

                long elapsed = System.currentTimeMillis() - t0;
                Thread.sleep(Math.max(8L, intervalMs - elapsed));
            }
        } catch (Exception e) {
            playFromJCodecMp4();
        }
    }

    private void playFromJCodecMp4() {
        SeekableByteChannel ch = null;
        try {
            loadProgressPct.set(40);
            ch = NIOUtils.readableFileChannel(videoFile.getAbsolutePath());
            FrameGrab grab = FrameGrab.createFrameGrab(ch);
            DemuxerTrackMeta meta = grab.getVideoTrack().getMeta();
            totalFrames = Math.max(1, meta.getTotalFrames());
            if (meta.getTotalDuration() > 0) {
                totalDurationMs = Math.round(meta.getTotalDuration() * 1000.0);
            }
            loadProgressPct.set(100);

            long intervalMs = Math.max(16L, totalDurationMs / totalFrames);

            while (workerRunning.get()) {
                if (seekRequestFrame >= 0) {
                    grab.seekToFramePrecise(seekRequestFrame);
                    currentFrameIdx = seekRequestFrame;
                    seekRequestFrame = -1;
                }
                if (!playing && hasDecodedFrame.get()) {
                    Thread.sleep(25);
                    continue;
                }
                long t0 = System.currentTimeMillis();
                Picture pic = grab.getNativeFrame();
                if (pic == null) {
                    grab.seekToFramePrecise(0);
                    currentFrameIdx = 0;
                    playing = false;
                    continue;
                }
                Picture rgb = pic;
                if (pic.getColor() != ColorSpace.RGB) {
                    Transform tr = ColorUtil.getTransform(pic.getColor(), ColorSpace.RGB);
                    if (tr != null) {
                        rgb = Picture.create(pic.getWidth(), pic.getHeight(), ColorSpace.RGB);
                        tr.transform(pic, rgb);
                    }
                }
                int sw = rgb.getWidth();
                int sh = rgb.getHeight();
                byte[] data = rgb.getPlaneData(0);
                for (int y = 0; y < DISP_H; y++) {
                    int sy = (y * sh) / DISP_H;
                    int rowOff = sy * sw * 3;
                    int dstRow = y * DISP_W;
                    for (int x = 0; x < DISP_W; x++) {
                        int sx = (x * sw) / DISP_W;
                        int idx = rowOff + sx * 3;
                        int r = (data[idx] + 128) & 0xFF;
                        int g = (data[idx + 1] + 128) & 0xFF;
                        int b = (data[idx + 2] + 128) & 0xFF;
                        pixelBuffer[dstRow + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                    }
                }
                hasDecodedFrame.set(true);
                if (playing) currentFrameIdx++;

                long elapsed = System.currentTimeMillis() - t0;
                Thread.sleep(Math.max(8L, intervalMs - elapsed));
            }
        } catch (Exception ignored) {
            loadProgressPct.set(-1);
        } finally {
            if (ch != null) NIOUtils.closeQuietly(ch);
        }
    }

    @Override
    protected void init() {
        computeWindowBounds();
        int cx = winX + winW / 2;
        int by = winY + winH - 24;

        playPauseBtn = new ThemeButton(cx - 135, by, 85, 18,
                playing ? UIHelper.Icon.PAUSE : UIHelper.Icon.PLAY,
                Text.literal(playing ? "Pause" : "Play"),
                () -> {
                    playing = !playing;
                    playPauseBtn.setIcon(playing ? UIHelper.Icon.PAUSE : UIHelper.Icon.PLAY);
                    playPauseBtn.setMessage(Text.literal(playing ? "Pause" : "Play"));
                },
                true
        );
        this.addDrawableChild(playPauseBtn);

        this.addDrawableChild(new ThemeButton(cx - 45, by, 85, 18,
                UIHelper.Icon.RESTART, Text.literal("Restart"),
                () -> {
                    seekRequestFrame = 0;
                    playing = true;
                }
        ));

        this.addDrawableChild(new ThemeButton(cx + 45, by, 90, 18,
                UIHelper.Icon.CLOSE, Text.literal("Close"),
                this::close
        ));
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        computeWindowBounds();
        int vx = winX + 16;
        int vw = winW - 32;
        int barY = winY + winH - 32;

        if (click.x() >= vx && click.x() <= vx + vw && click.y() >= barY - 4 && click.y() <= barY + 8) {
            double ratio = (click.x() - vx) / (double) vw;
            seekRequestFrame = Math.max(0, Math.min(totalFrames - 1, (int) (ratio * totalFrames)));
            return true;
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        computeWindowBounds();
        context.fill(0, 0, this.width, this.height, 0x66000000);

        context.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x99000000);
        context.fill(winX, winY, winX + winW, winY + winH, 0xFF1D1F26);
        UIHelper.drawBorder(context, winX, winY, winW, winH, 0xFF3F4454);

        context.fill(winX + 1, winY + 1, winX + winW - 1, winY + 15, 0xFF2B2E38);
        int totSec = (int) Math.round(totalDurationMs / 1000.0);
        int curSec = (int) Math.round((currentFrameIdx / (double) Math.max(1, totalFrames)) * totSec);
        String timeStr = String.format(" [%02d:%02d / %02d:%02d]", curSec / 60, curSec % 60, totSec / 60, totSec % 60);
        context.drawCenteredTextWithShadow(this.textRenderer, "OBS Media Player — " + videoFile.getName() + timeStr, winX + winW / 2, winY + 4, 0xFFFFFFFF);

        if (playPauseBtn != null) {
            playPauseBtn.setIcon(playing ? UIHelper.Icon.PAUSE : UIHelper.Icon.PLAY);
            playPauseBtn.setMessage(Text.literal(playing ? "Pause" : "Play"));
        }

        int availW = winW - 32;
        int availH = winH - 56;
        int viewW = availW;
        int viewH = (viewW * 9) / 16;
        if (viewH > availH) {
            viewH = availH;
            viewW = (viewH * 16) / 9;
        }
        int vx = winX + (winW - viewW) / 2;
        int vy = winY + 18 + (availH - viewH) / 2;

        context.fill(vx - 1, vy - 1, vx + viewW + 1, vy + viewH + 1, 0xFF000000);
        UIHelper.drawBorder(context, vx - 1, vy - 1, viewW + 2, viewH + 2, 0xFF3A3E4B);

        if (!hasDecodedFrame.get()) {
            int pct = loadProgressPct.get();
            String loadTxt = pct < 0 ? "Cannot read video file" : ("Opening Video Stream... " + pct + "%");
            context.drawCenteredTextWithShadow(this.textRenderer, loadTxt, winX + winW / 2, vy + viewH / 2 - 6, 0xFFFFFFFF);
        } else {
            for (int gy = 0; gy < DISP_H; gy++) {
                int py0 = vy + (gy * viewH) / DISP_H;
                int py1 = vy + ((gy + 1) * viewH) / DISP_H;
                if (py1 <= py0) py1 = py0 + 1;
                int rowIdx = gy * DISP_W;
                int gx = 0;
                while (gx < DISP_W) {
                    int col = pixelBuffer[rowIdx + gx];
                    int runEnd = gx + 1;
                    while (runEnd < DISP_W && colorClose(col, pixelBuffer[rowIdx + runEnd])) {
                        runEnd++;
                    }
                    int px0 = vx + (gx * viewW) / DISP_W;
                    int px1 = vx + (runEnd * viewW) / DISP_W;
                    if (px1 <= px0) px1 = px0 + 1;
                    context.fill(px0, py0, px1, py1, col);
                    gx = runEnd;
                }
            }
        }

        int barX = winX + 16;
        int barW = winW - 32;
        int barY = winY + winH - 32;
        context.fill(barX, barY, barX + barW, barY + 4, 0xFF2B2E38);
        int progW = (int) ((Math.min(currentFrameIdx, totalFrames) / (float) Math.max(1, totalFrames)) * barW);
        context.fill(barX, barY, barX + progW, barY + 4, 0xFF2865C7);

        super.render(context, mouseX, mouseY, delta);
    }

    private boolean colorClose(int c1, int c2) {
        if (c1 == c2) return true;
        int dr = Math.abs(((c1 >> 16) & 0xFF) - ((c2 >> 16) & 0xFF));
        int dg = Math.abs(((c1 >> 8) & 0xFF) - ((c2 >> 8) & 0xFF));
        int db = Math.abs((c1 & 0xFF) - (c2 & 0xFF));
        return (dr + dg + db) < 12;
    }

    @Override
    public void close() {
        workerRunning.set(false);
        this.client.setScreen(parent);
    }
}
