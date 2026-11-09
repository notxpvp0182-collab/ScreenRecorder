# Screen Recorder (Fabric, Minecraft 1.21.11)

In-game MP4 screen recorder with game/mic audio, OBS-style dark/red GUI.

## Build
1. Install JDK 21 and ffmpeg (in PATH, or set `ffmpegPath` in `config/screen_recorder.json`).
2. First time only: run `gradle wrapper --gradle-version 9.2.1` (or open the folder in IntelliJ IDEA, it sets up Gradle itself).
3. `./gradlew build`  ->  `build/libs/screen-recorder-1.0.0.jar`
4. Put the jar in `.minecraft/mods/` with Fabric API and Mod Menu.

Keys: F8 open GUI, F9 start/stop recording (rebindable).
Recordings: `.minecraft/screen-recordings/`
