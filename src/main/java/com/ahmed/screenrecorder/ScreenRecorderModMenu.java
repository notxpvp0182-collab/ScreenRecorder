package com.ahmed.screenrecorder;

import com.ahmed.screenrecorder.gui.RecorderScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ScreenRecorderModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return RecorderScreen::new;
    }
}
