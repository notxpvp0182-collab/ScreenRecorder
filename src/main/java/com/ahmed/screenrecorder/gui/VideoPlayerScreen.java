package com.ahmed.screenrecorder.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal In-Game MP4 Video Player Screen with Custom Vector Icons and Direct Scanline Renderer.
 */
public class VideoPlayerScreen extends Screen {
    private final Screen parent;
    private final File videoFile;
    private RandomAccessFile raf;
    private final List<Long> frameOffsets = new ArrayList<>();
    private final List<Integer> frameSizes = new ArrayList<>();
    private int videoFps = 30;
    private int currentFrame = 0;
    private boolean playing = true;
    private long lastFrameTimeMs = 0;

    private static final int GRID_W = 160;
    private static final int GRID_H = 90;
    private final int[] frameGrid = new int[GRID_W * GRID_H];
    private boolean hasFrameLoaded = false;

    private ThemeButton playPauseBtn;

    public VideoPlayerScreen(Screen parent, File videoFile) {
        super(Text.literal("MP4 Player - " + videoFile.getName()));
        this.parent = parent;
        this.videoFile = videoFile;
        loadVideoIndex();
    }

    private void loadVideoIndex() {
        try {
            raf = new RandomAccessFile(videoFile, "r");
            long len = raf.length();
            if (len > 20) {
                raf.seek(len - 16);
                int w = raf.readInt();
                int h = raf.readInt();
                int fps = raf.readInt();
                int magic = raf.readInt();
                if (magic == 0x53524D50 && fps > 0 && fps <= 240) {
                    this.videoFps = fps;
                }
            }
            raf.seek(0);
            byte[] data = new byte[(int) Math.min(len, 64 * 1024 * 1024)];
            raf.readFully(data);
            int i = 0;
            while (i < data.length - 4) {
                if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD8 && (data[i + 2] & 0xFF) == 0xFF) {
                    int start = i;
                    i += 3;
                    while (i < data.length - 1) {
                        if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD9) {
                            int end = i + 2;
                            frameOffsets.add((long) start);
                            frameSizes.add(end - start);
                            i = end;
                            break;
                        }
                        i++;
                    }
                } else {
                    i++;
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int by = this.height - 26;

        playPauseBtn = new ThemeButton(cx - 135, by, 85, 20,
                playing ? UIHelper.Icon.PAUSE : UIHelper.Icon.PLAY,
                Text.literal(playing ? "PAUSE" : "PLAY"),
                () -> {
                    playing = !playing;
                    playPauseBtn.setIcon(playing ? UIHelper.Icon.PAUSE : UIHelper.Icon.PLAY);
                    playPauseBtn.setMessage(Text.literal(playing ? "PAUSE" : "PLAY"));
                },
                true
        );
        this.addDrawableChild(playPauseBtn);

        this.addDrawableChild(new ThemeButton(cx - 45, by, 85, 20,
                UIHelper.Icon.RESTART, Text.literal("RESTART"),
                () -> {
                    currentFrame = 0;
                    playing = true;
                    playPauseBtn.setIcon(UIHelper.Icon.PAUSE);
                    playPauseBtn.setMessage(Text.literal("PAUSE"));
                    decodeCurrentFrame();
                }
        ));

        this.addDrawableChild(new ThemeButton(cx + 45, by, 90, 20,
                UIHelper.Icon.CLOSE, Text.literal("CLOSE"),
                this::close
        ));

        decodeCurrentFrame();
    }

    private void decodeCurrentFrame() {
        if (raf == null || frameOffsets.isEmpty() || currentFrame >= frameOffsets.size()) return;
        try {
            long offset = frameOffsets.get(currentFrame);
            int size = frameSizes.get(currentFrame);
            byte[] buf = new byte[size];
            raf.seek(offset);
            raf.readFully(buf);

            BufferedImage img = ImageIO.read(new ByteArrayInputStream(buf));
            if (img != null) {
                int iw = img.getWidth();
                int ih = img.getHeight();
                for (int gy = 0; gy < GRID_H; gy++) {
                    int sy = (gy * ih) / GRID_H;
                    for (int gx = 0; gx < GRID_W; gx++) {
                        int sx = (gx * iw) / GRID_W;
                        frameGrid[gy * GRID_W + gx] = 0xFF000000 | (img.getRGB(sx, sy) & 0x00FFFFFF);
                    }
                }
                hasFrameLoaded = true;
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF131418);

        context.fill(0, 0, this.width, 20, 0xFF1D1F26);
        context.drawCenteredTextWithShadow(this.textRenderer, "IN-GAME MP4 PLAYER — " + videoFile.getName(), this.width / 2, 6, 0xFFFFFFFF);

        long now = System.currentTimeMillis();
        long interval = 1000L / Math.max(10, videoFps);
        if (playing && !frameOffsets.isEmpty() && now - lastFrameTimeMs >= interval) {
            lastFrameTimeMs = now;
            currentFrame++;
            if (currentFrame >= frameOffsets.size()) {
                currentFrame = 0;
                playing = false;
                if (playPauseBtn != null) {
                    playPauseBtn.setIcon(UIHelper.Icon.PLAY);
                    playPauseBtn.setMessage(Text.literal("PLAY"));
                }
            }
            decodeCurrentFrame();
        }

        int availW = this.width - 36;
        int availH = this.height - 62;
        int viewW = availW;
        int viewH = (viewW * 9) / 16;
        if (viewH > availH) {
            viewH = availH;
            viewW = (viewH * 16) / 9;
        }
        int vx = (this.width - viewW) / 2;
        int vy = 24 + (availH - viewH) / 2;

        context.fill(vx - 2, vy - 2, vx + viewW + 2, vy + viewH + 2, 0xFF000000);
        UIHelper.drawBorder(context, vx - 2, vy - 2, viewW + 4, viewH + 4, 0xFF3A3D4A);

        if (hasFrameLoaded) {
            for (int gy = 0; gy < GRID_H; gy++) {
                int py0 = vy + (gy * viewH) / GRID_H;
                int py1 = vy + ((gy + 1) * viewH) / GRID_H;
                if (py1 <= py0) py1 = py0 + 1;
                for (int gx = 0; gx < GRID_W; gx++) {
                    int px0 = vx + (gx * viewW) / GRID_W;
                    int px1 = vx + ((gx + 1) * viewW) / GRID_W;
                    if (px1 <= px0) px1 = px0 + 1;
                    context.fill(px0, py0, px1, py1, frameGrid[gy * GRID_W + gx]);
                }
            }
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, "No decodable frames in file.", this.width / 2, vy + viewH / 2, 0xFFAAAAAA);
        }

        int barY = this.height - 34;
        context.fill(vx, barY, vx + viewW, barY + 4, 0xFF2B2E38);
        if (!frameOffsets.isEmpty()) {
            int progW = (int) (((currentFrame + 1) / (float) frameOffsets.size()) * viewW);
            context.fill(vx, barY, vx + progW, barY + 4, 0xFF2865C7);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        try {
            if (raf != null) raf.close();
        } catch (Exception ignored) {}
        this.client.setScreen(parent);
    }
}
