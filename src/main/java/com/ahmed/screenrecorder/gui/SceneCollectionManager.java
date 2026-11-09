package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.config.RecordingConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages OBS Scenes, Sources, Visibility States, Lock States, and Capture Modes.
 */
public class SceneCollectionManager {
    public static class SourceItem {
        public String name;
        public UIHelper.Icon icon;
        public boolean visible;
        public boolean locked;

        public SourceItem(String name, UIHelper.Icon icon, boolean visible, boolean locked) {
            this.name = name;
            this.icon = icon;
            this.visible = visible;
            this.locked = locked;
        }
    }

    private static final List<String> SCENES = new ArrayList<>();
    private static final List<SourceItem> SOURCES = new ArrayList<>();
    private static int selectedSceneIdx = 0;
    private static int selectedSourceIdx = 0;

    static {
        SCENES.add("Main Scene (Full HD)");
        SCENES.add("Display Capture");
        SCENES.add("Gameplay + Mic");
        SCENES.add("Cinematic Clean");

        SOURCES.add(new SourceItem("Display Capture", UIHelper.Icon.VIDEO, true, false));
        SOURCES.add(new SourceItem("Game Capture", UIHelper.Icon.VIDEO, true, false));
        SOURCES.add(new SourceItem("In-Game Audio", UIHelper.Icon.AUDIO, true, false));
        SOURCES.add(new SourceItem("Mic/Aux Input", UIHelper.Icon.MICROPHONE, false, false));
    }

    public static List<String> getScenes() { return SCENES; }
    public static List<SourceItem> getSources() {
        RecordingConfig cfg = RecordingConfig.get();
        for (SourceItem item : SOURCES) {
            if (item.name.contains("Audio")) item.visible = cfg.gameAudioEnabled;
            if (item.name.contains("Mic")) item.visible = cfg.microphoneEnabled;
        }
        return SOURCES;
    }

    public static int getSelectedSceneIdx() { return selectedSceneIdx; }
    public static void setSelectedSceneIdx(int idx) {
        if (idx >= 0 && idx < SCENES.size()) selectedSceneIdx = idx;
    }

    public static int getSelectedSourceIdx() { return selectedSourceIdx; }
    public static void setSelectedSourceIdx(int idx) {
        if (idx >= 0 && idx < SOURCES.size()) selectedSourceIdx = idx;
    }

    public static void addScene() {
        SCENES.add("Custom Scene " + (SCENES.size() + 1));
        selectedSceneIdx = SCENES.size() - 1;
    }

    public static void removeSelectedScene() {
        if (SCENES.size() > 1) {
            SCENES.remove(selectedSceneIdx);
            selectedSceneIdx = Math.max(0, selectedSceneIdx - 1);
        }
    }

    public static void moveSceneUp() {
        if (selectedSceneIdx > 0) {
            String tmp = SCENES.set(selectedSceneIdx - 1, SCENES.get(selectedSceneIdx));
            SCENES.set(selectedSceneIdx, tmp);
            selectedSceneIdx--;
        }
    }

    public static void moveSceneDown() {
        if (selectedSceneIdx < SCENES.size() - 1) {
            String tmp = SCENES.set(selectedSceneIdx + 1, SCENES.get(selectedSceneIdx));
            SCENES.set(selectedSceneIdx, tmp);
            selectedSceneIdx++;
        }
    }

    public static void addSource(String name, UIHelper.Icon icon) {
        SOURCES.add(new SourceItem(name, icon, true, false));
        selectedSourceIdx = SOURCES.size() - 1;
    }

    public static void removeSelectedSource() {
        if (SOURCES.size() > 1 && !SOURCES.get(selectedSourceIdx).locked) {
            SOURCES.remove(selectedSourceIdx);
            selectedSourceIdx = Math.max(0, selectedSourceIdx - 1);
        }
    }

    public static void toggleSourceVisibility(int idx) {
        if (idx >= 0 && idx < SOURCES.size()) {
            SourceItem item = SOURCES.get(idx);
            if (item.locked) return;
            item.visible = !item.visible;
            RecordingConfig cfg = RecordingConfig.get();
            if (item.name.contains("Audio")) cfg.gameAudioEnabled = item.visible;
            if (item.name.contains("Mic")) cfg.microphoneEnabled = item.visible;
            RecordingConfig.save();
        }
    }

    public static void toggleSourceLock(int idx) {
        if (idx >= 0 && idx < SOURCES.size()) {
            SOURCES.get(idx).locked = !SOURCES.get(idx).locked;
        }
    }
}
