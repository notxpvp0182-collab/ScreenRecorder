package com.ahmed.screenrecorder.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * In-Game MP4 Video Player Screen with Play/Pause, Seek Restart, and Responsive 16:9 Viewport.
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

    private NativeImageBackedTexture frameTexture;
    private Identifier frameTextureId;
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
                    raf.seek(len - 20);
                    // Read index count stored before footer
                }
            }
            // Scan JPEG SOI/EOI markers inside MP4 mdat for universal playback
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
        int by = this.height - 28;

        playPauseBtn = new ThemeButton(cx - 135, by, 85, 20, Text.literal(playing ? "⏸ PAUSE" : "▶ PLAY"), () -> {
            playing = !playing;
            playPauseBtn.setMessage(Text.literal(playing ? "⏸ PAUSE" : "▶ PLAY"));
        }, true);
        this.addDrawableChild(playPauseBtn);

        this.addDrawableChild(new ThemeButton(cx - 45, by, 85, 20, Text.literal("⏮ RESTART"), () -> {
            currentFrame = 0;
            playing = true;
            playPauseBtn.setMessage(Text.literal("⏸ PAUSE"));
            decodeCurrentFrame();
        }));

        this.addDrawableChild(new ThemeButton(cx + 45, by, 90, 20, Text.literal("X CLOSE"), this::close));

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

            NativeImage img = NativeImage.read(new ByteArrayInputStream(buf));
            if (frameTexture != null) {
                frameTexture.close();
            }
            frameTexture = new NativeImageBackedTexture(img);
            frameTextureId = Identifier.of("screen_recorder", "video_preview_frame");
            this.client.getTextureManager().registerTexture(frameTextureId, frameTexture);
        } catch (Exception ignored) {}
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF131418);

        // Top Bar
        context.fill(0, 0, this.width, 22, 0xFF1D1F26);
        context.drawCenteredTextWithShadow(this.textRenderer, "▶ IN-GAME MP4 PLAYER — " + videoFile.getName(), this.width / 2, 7, 0xFFFFFFFF);

        // Advance frame according to FPS
        long now = System.currentTimeMillis();
        long interval = 1000L / Math.max(10, videoFps);
        if (playing && !frameOffsets.isEmpty() && now - lastFrameTimeMs >= interval) {
            lastFrameTimeMs = now;
            currentFrame++;
            if (currentFrame >= frameOffsets.size()) {
                currentFrame = 0;
                playing = false;
                if (playPauseBtn != null) playPauseBtn.setMessage(Text.literal("▶ PLAY"));
            }
            decodeCurrentFrame();
        }

        // Responsive 16:9 Viewport Area
        int availW = this.width - 40;
        int availH = this.height - 68;
        int viewW = availW;
        int viewH = (viewW * 9) / 16;
        if (viewH > availH) {
            viewH = availH;
            viewW = (viewH * 16) / 9;
        }
        int vx = (this.width - viewW) / 2;
        int vy = 26 + (availH - viewH) / 2;

        context.fill(vx - 2, vy - 2, vx + viewW + 2, vy + viewH + 2, 0xFF000000);
        UIHelper.drawBorder(context, vx - 2, vy - 2, viewW + 4, viewH + 4, 0xFF3A3D4A);

        if (frameTextureId != null && frameTexture != null && frameTexture.getImage() != null) {
            int tw = frameTexture.getImage().getWidth();
            int th = frameTexture.getImage().getHeight();
            context.drawTexture(RenderLayer::getGuiTextured, frameTextureId, vx, vy, 0.0f, 0.0f, viewW, viewH, tw, th, tw, th);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, "No decodable frames in file.", this.width / 2, vy + viewH / 2, 0xFFAAAAAA);
        }

        // Progress Bar
        int barY = this.height - 36;
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
            if (frameTexture != null) frameTexture.close();
        } catch (Exception ignored) {}
        this.client.setScreen(parent);
    }
}
