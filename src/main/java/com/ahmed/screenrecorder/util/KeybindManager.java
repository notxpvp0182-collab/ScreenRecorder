package com.ahmed.screenrecorder.util;

import com.ahmed.screenrecorder.capture.RecordingManager;
import com.ahmed.screenrecorder.gui.RecorderScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class KeybindManager {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("screen_recorder", "general"));

    public static KeyBinding openGuiKey;
    public static KeyBinding toggleRecordingKey;

    public static void register() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.screen_recorder.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                CATEGORY
        ));

        toggleRecordingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.screen_recorder.toggle_recording",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new RecorderScreen(null));
                }
            }

            while (toggleRecordingKey.wasPressed()) {
                RecordingManager.getInstance().toggleRecording();
            }
        });
    }
}
