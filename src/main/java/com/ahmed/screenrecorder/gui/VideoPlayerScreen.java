package com.ahmed.screenrecorder.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.ColorUtil;
import org.jcodec.scale.Transform;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hardware-Accelerated GPU Texture In-Game H.264 MP4 Video Player.
 * Decodes frames on a background thread and uploads a single NativeImageBackedTexture to OpenGL (zero lag).
 */
public class VideoPlayerScreen extends Screen {
    private final Screen parent;
    private final File videoFile;

    private SeekableByteChannel channel;
    private FrameGrab grabber;
    private int totalFrames = 1;
    private int currentFrameIdx = 0;
    private boolean playing = true;

    private NativeImage nativeImage;
    private NativeImageBackedTexture gpuTexture;
    private Identifier textureId;
    private int videoW = 0;
    private int videoH = 0;

    private final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private final AtomicBoolean frameDirty = new AtomicBoolean(false);
    private Thread decodeThread;
    private ThemeButton playPauseBtn;

    public VideoPlayerScreen(Screen parent, File videoFile) {
        super(Text.literal(videoFile.getName()));
        this.parent = parent;
        this.videoFile = videoFile;
        startBackgroundDecoder();
    }

    private void startBackgroundDecoder() {
        workerRunning.set(true);
        decodeThread = new Thread(() -> {
            try {
                channel = NIOUtils.readableFileChannel(videoFile.getAbsolutePath());
                grabber = FrameGrab.createFrameGrab(channel);
                totalFrames = Math.max(1, grabber.getVideoTrack().getMeta().getTotalFrames());

                while (workerRunning.get()) {
                    if (!playing) {
                        Thread.sleep(20);
                        continue;
                    }
                    long startMs = System.currentTimeMillis();
                    Picture pic = grabber.getNativeFrame();
                    if (pic == null) {
                        grabber.seekToFramePrecise(0);
                        currentFrameIdx = 0;
                        playing = false;
                        continue;
                    }
                    currentFrameIdx++;
                    convertPictureToNativeImage(pic);
                    frameDirty.set(true);

                    long elapsed = System.currentTimeMillis() - startMs;
                    long sleep = Math.max(4, 33 - elapsed);
                    Thread.sleep(sleep);
                }
            } catch (Exception ignored) {}
        }, "OBS-MP4-Decoder");
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    private synchronized void convertPictureToNativeImage(Picture src) {
        Picture rgb = src;
        if (src.getColor() != ColorSpace.RGB) {
            Transform transform = ColorUtil.getTransform(src.getColor(), ColorSpace.RGB);
            if (transform != null) {
                rgb = Picture.create(src.getWidth(), src.getHeight(), ColorSpace.RGB);
                transform.transform(src, rgb);
            }
        }

        int w = rgb.getWidth();
        int h = rgb.getHeight();
        if (nativeImage == null || videoW != w || videoH != h) {
            videoW = w;
            videoH = h;
            nativeImage = new NativeImage(w, h, false);
        }

        byte[] data = rgb.getPlaneData(0);
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = (data[idx] + 128) & 0xFF;
                int g = (data[idx + 1] + 128) & 0xFF;
                int b = (data[idx + 2] + 128) & 0xFF;
                idx += 3;
                // NativeImage ABGR format
                int abgr = 0xFF000000 | (b << 16) | (g << 8) | r;
                nativeImage.setColor(x, y, abgr);
            }
        }
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int by = this.height - 26;

        playPauseBtn = new ThemeButton(cx - 135, by, 85, 20,
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

        this.addDrawableChild(new ThemeButton(cx - 45, by, 85, 20,
                UIHelper.Icon.RESTART, Text.literal("Restart"),
                () -> {
                    try {
                        if (grabber != null) {
                            grabber.seekToFramePrecise(0);
                            currentFrameIdx = 0;
                            playing = true;
                            playPauseBtn.setIcon(UIHelper.Icon.PAUSE);
                            playPauseBtn.setMessage(Text.literal("Pause"));
                        }
                    } catch (Exception ignored) {}
                }
        ));

        this.addDrawableChild(new ThemeButton(cx + 45, by, 90, 20,
                UIHelper.Icon.CLOSE, Text.literal("Close"),
                this::close
        ));
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        // Click on progress bar to seek
        int availW = this.width - 36;
        int availH = this.height - 62;
        int viewW = Math.min(availW, (availH * 16) / 9);
        int vx = (this.width - viewW) / 2;
        int barY = this.height - 34;

        if (click.x() >= vx && click.x() <= vx + viewW && click.y() >= barY - 4 && click.y() <= barY + 8) {
            double ratio = (click.x() - vx) / (double) viewW;
            int targetFrame = (int) (ratio * totalFrames);
            try {
                if (grabber != null) {
                    grabber.seekToFramePrecise(Math.max(0, targetFrame));
                    currentFrameIdx = targetFrame;
                }
            } catch (Exception ignored) {}
            return true;
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF181A20);
        context.fill(0, 0, this.width, 20, 0xFF23262E);
        context.drawCenteredTextWithShadow(this.textRenderer, "OBS Media Player — " + videoFile.getName(), this.width / 2, 6, 0xFFFFFFFF);

        if (frameDirty.compareAndSet(true, false)) {
            synchronized (this) {
                if (nativeImage != null) {
                    if (gpuTexture == null) {
                        gpuTexture = new NativeImageBackedTexture(nativeImage);
                        textureId = Identifier.of("screen_recorder", "mp4_gpu_frame");
                        this.client.getTextureManager().registerTexture(textureId, gpuTexture);
                    } else {
                        gpuTexture.upload();
                    }
                }
            }
        }

        if (playPauseBtn != null) {
            playPauseBtn.setIcon(playing ? UIHelper.Icon.PAUSE : UIHelper.Icon.PLAY);
            playPauseBtn.setMessage(Text.literal(playing ? "Pause" : "Play"));
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

        context.fill(vx - 1, vy - 1, vx + viewW + 1, vy + viewH + 1, 0xFF000000);
        UIHelper.drawBorder(context, vx - 1, vy - 1, viewW + 2, viewH + 2, 0xFF3A3E4B);

        if (textureId != null && videoW > 0 && videoH > 0) {
            context.drawTexture(RenderLayer::getGuiTextured, textureId, vx, vy, 0.0f, 0.0f, viewW, viewH, videoW, videoH, videoW, videoH);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, "Loading H.264 video stream...", this.width / 2, vy + viewH / 2 - 4, 0xFF9AA0A6);
        }

        int barY = this.height - 34;
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
