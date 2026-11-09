package com.ahmed.screenrecorder;

import com.ahmed.screenrecorder.capture.RecordingManager;
import com.ahmed.screenrecorder.config.RecordingConfig;
import com.ahmed.screenrecorder.gui.RecordingIndicator;
import com.ahmed.screenrecorder.util.KeybindManager;
import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScreenRecorderClient implements ClientModInitializer {
    public static final String MOD_ID = "screen_recorder";
    public static final Logger LOGGER = LoggerFactory.getLogger("ScreenRecorder");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Screen Recorder Mod by Ahmed...");

        RecordingConfig.load();
        RecordingFileManager.initDirectory();
        KeybindManager.register();

        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            RecordingIndicator.render(drawContext);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("Minecraft shutting down. Checking active recordings...");
            RecordingManager.getInstance().shutdownSafely();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            RecordingManager.getInstance().shutdownSafely();
        }, "ScreenRecorder-ShutdownHook"));

        LOGGER.info("Screen Recorder initialized successfully.");
    }
}
