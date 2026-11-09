package com.ahmed.screenrecorder.gui;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Crisp High-Definition In-Game MP4 Video Player with True 1x Timestamp Pacing.
 */
public class VideoPlayerScreen extends Screen {
    private final Screen parent;
    private final File videoFile;

    private SeekableByteChannel channel;
    private FrameGrab grabber;
    private int totalFrames = 1;
    private double videoFps = 30.0;
    private int currentFrameIdx = 0;
    private boolean playing = true;

    // High-clarity viewport buffer (480x270 crisp 16:9 matrix with zero blur)
    private static final int DISP_W = 426;
    private static final int DISP_H = 240;
    private final int[] pixelBuffer = new int[DISP_W * DISP_H];
    private final AtomicBoolean hasDecodedFrame = new AtomicBoolean(false);
    private final AtomicInteger loadProgressPct = new AtomicInteger(0);
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);

    private ThemeButton playPauseBtn;

    public VideoPlayerScreen(Screen parent, File videoFile) {
        super(Text.literal(videoFile.getName()));
        this.parent = parent;
        this.videoFile = videoFile;
        startDecoderThread();
    }

    private void startDecoderThread() {
        workerRunning.set(true);
        Thread t = new Thread(() -> {
            try {
                loadProgressPct.set(25);
                channel = NIOUtils.readableFileChannel(videoFile.getAbsolutePath());
                loadProgressPct.set(65);
                grabber = FrameGrab.createFrameGrab(channel);
                DemuxerTrackMeta meta = grabber.getVideoTrack().getMeta();
                totalFrames = Math.max(1, meta.getTotalFrames());
                if (meta.getTotalDuration() > 0 && totalFrames > 0) {
                    videoFps = Math.max(10.0, Math.min(120.0, totalFrames / meta.getTotalDuration()));
                }
                loadProgressPct.set(100);

                long frameIntervalMs = Math.max(8L, Math.round(1000.0 / videoFps));
                long nextFrameTargetMs = System.currentTimeMillis();

                while (workerRunning.get()) {
                    if (!playing) {
                        Thread.sleep(20);
                        nextFrameTargetMs = System.currentTimeMillis();
                        continue;
                    }

                    Picture pic = grabber.getNativeFrame();
                    if (pic == null) {
                        grabber.seekToFramePrecise(0);
                        currentFrameIdx = 0;
                        playing = false;
                        continue;
                    }
                    currentFrameIdx++;
                    convertFrameToDisplayBuffer(pic);
                    hasDecodedFrame.set(true);

                    nextFrameTargetMs += frameIntervalMs;
                    long sleepMs = nextFrameTargetMs - System.currentTimeMillis();
                    if (sleepMs > 0) {
                        Thread.sleep(sleepMs);
                    } else if (sleepMs < -250) {
                        nextFrameTargetMs = System.currentTimeMillis();
                    }
                }
            } catch (Exception ignored) {
                loadProgressPct.set(-1);
            }
        }, "OBS-HD-Player");
        t.setDaemon(true);
        t.start();
    }

    private void convertFrameToDisplayBuffer(Picture src) {
        Picture rgb = src;
        if (src.getColor() != ColorSpace.RGB) {
            Transform tr = ColorUtil.getTransform(src.getColor(), ColorSpace.RGB);
            if (tr != null) {
                rgb = Picture.create(src.getWidth(), src.getHeight(), ColorSpace.RGB);
                tr.transform(src, rgb);
            }
        }
        int sw = rgb.getWidth();
        int sh = rgb.getHeight();
        byte[] data = rgb.getPlaneData(0);

        synchronized (pixelBuffer) {
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
        }
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int by = this.height - 25;

        playPauseBtn = new ThemeButton(cx - 135, by, 85, 19,
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

        this.addDrawableChild(new ThemeButton(cx - 45, by, 85, 19,
                UIHelper.Icon.RESTART, Text.literal("Restart"),
                () -> {
                    try {
                        if (grabber != null) {
                            grabber.seekToFramePrecise(0);
                            currentFrameIdx = 0;
                            playing = true;
                        }
                    } catch (Exception ignored) {}
                }
        ));

        this.addDrawableChild(new ThemeButton(cx + 45, by, 90, 19,
                UIHelper.Icon.CLOSE, Text.literal("Close"),
                this::close
        ));
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int availW = this.width - 36;
        int availH = this.height - 60;
        int viewW = Math.min(availW, (availH * 16) / 9);
        int vx = (this.width - viewW) / 2;
        int barY = this.height - 33;

        if (click.x() >= vx && click.x() <= vx + viewW && click.y() >= barY - 4 && click.y() <= barY + 8) {
            double ratio = (click.x() - vx) / (double) viewW;
            int target = (int) (ratio * totalFrames);
            try {
                if (grabber != null) {
                    grabber.seekToFramePrecise(Math.max(0, target));
                    currentFrameIdx = target;
                }
            } catch (Exception ignored) {}
            return true;
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xE6101216);
        context.fill(0, 0, this.width, 18, 0xFF23262E);
        context.drawCenteredTextWithShadow(this.textRenderer, "OBS Media Player — " + videoFile.getName(), this.width / 2, 5, 0xFFFFFFFF);

        if (playPauseBtn != null) {
            playPauseBtn.setIcon(playing ? UIHelper.Icon.PAUSE : UIHelper.Icon.PLAY);
            playPauseBtn.setMessage(Text.literal(playing ? "Pause" : "Play"));
        }

        int availW = this.width - 36;
        int availH = this.height - 60;
        int viewW = availW;
        int viewH = (viewW * 9) / 16;
        if (viewH > availH) {
            viewH = availH;
            viewW = (viewH * 16) / 9;
        }
        int vx = (this.width - viewW) / 2;
        int vy = 22 + (availH - viewH) / 2;

        context.fill(vx - 1, vy - 1, vx + viewW + 1, vy + viewH + 1, 0xFF000000);
        UIHelper.drawBorder(context, vx - 1, vy - 1, viewW + 2, viewH + 2, 0xFF3A3E4B);

        int pct = loadProgressPct.get();
        if (!hasDecodedFrame.get()) {
            String loadTxt = pct < 0 ? "Unable to decode MP4 stream" : ("Loading HD Video... " + pct + "%");
            context.drawCenteredTextWithShadow(this.textRenderer, loadTxt, this.width / 2, vy + viewH / 2 - 10, 0xFFFFFFFF);
            int lbW = Math.min(180, viewW - 20);
            int lbX = (this.width - lbW) / 2;
            int lbY = vy + viewH / 2 + 4;
            context.fill(lbX, lbY, lbX + lbW, lbY + 6, 0xFF2B2E38);
            if (pct > 0) {
                context.fill(lbX, lbY, lbX + (lbW * pct) / 100, lbY + 6, 0xFF2865C7);
            }
        } else {
            synchronized (pixelBuffer) {
                for (int gy = 0; gy < DISP_H; gy++) {
                    int py0 = vy + (gy * viewH) / DISP_H;
                    int py1 = vy + ((gy + 1) * viewH) / DISP_H;
                    if (py1 <= py0) py1 = py0 + 1;
                    int rowIdx = gy * DISP_W;
                    int gx = 0;
                    while (gx < DISP_W) {
                        int col = pixelBuffer[rowIdx + gx];
                        int runEnd = gx + 1;
                        while (runEnd < DISP_W && pixelBuffer[rowIdx + runEnd] == col) {
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
        }

        int barY = this.height - 33;
        context.fill(vx, barY, vx + viewW, barY + 4, 0xFF2B2E38);
        int progW = (int) ((Math.min(currentFrameIdx, totalFrames) / (float) Math.max(1, totalFrames)) * viewW);
        context.fill(vx, barY, vx + progW, barY + 4, 0xFF2865C7);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        workerRunning.set(false);
        try {
            if (channel != null) NIOUtils.closeQuietly(channel);
        } catch (Exception ignored) {}
        this.client.setScreen(parent);
    }
}
