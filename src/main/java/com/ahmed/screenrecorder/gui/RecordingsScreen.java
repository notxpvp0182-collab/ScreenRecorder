package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays saved MP4 recordings with filename, duration, resolution, FPS, file size,
 * creation date, and actions to Open Folder, Delete Recording, or Refresh List.
 */
public class RecordingsScreen extends Screen {
    private final Screen parent;
    private List<RecordingFileManager.RecordingMetadata> recordings = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 5;

    private ThemeButton deleteButton;

    public RecordingsScreen(Screen parent) {
        super(Text.literal("RECORDINGS"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshList();

        int centerX = this.width / 2;
        int bottomY = this.height - 34;

        this.addDrawableChild(new ThemeButton(
                centerX - 170, bottomY, 80, 22,
                Text.literal("OPEN FOLDER"),
                RecordingFileManager::openRecordingsFolder,
                true
        ));

        deleteButton = new ThemeButton(
                centerX - 84, bottomY, 80, 22,
                Text.literal("DELETE"),
                this::deleteSelected
        );
        this.addDrawableChild(deleteButton);

        this.addDrawableChild(new ThemeButton(
                centerX + 2, bottomY, 80, 22,
                Text.literal("REFRESH"),
                this::refreshList
        ));

        this.addDrawableChild(new ThemeButton(
                centerX + 88, bottomY, 80, 22,
                Text.literal("BACK"),
                this::close
        ));

        updateActionButtons();
    }

    private void refreshList() {
        this.recordings = RecordingFileManager.listRecordings();
        if (selectedIndex >= recordings.size()) {
            selectedIndex = recordings.isEmpty() ? -1 : recordings.size() - 1;
        }
        updateActionButtons();
    }

    private void deleteSelected() {
        if (selectedIndex >= 0 && selectedIndex < recordings.size()) {
            RecordingFileManager.RecordingMetadata meta = recordings.get(selectedIndex);
            if (RecordingFileManager.deleteRecording(meta.file())) {
                refreshList();
            }
        }
    }

    private void updateActionButtons() {
        if (deleteButton != null) {
            deleteButton.active = (selectedIndex >= 0 && selectedIndex < recordings.size());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = (this.width - 340) / 2;
        int listY = 48;
        int rowH = 34;

        if (mouseX >= listX && mouseX <= listX + 340 && mouseY >= listY && mouseY <= listY + VISIBLE_ROWS * rowH) {
            int clickedRow = (int) ((mouseY - listY) / rowH) + scrollOffset;
            if (clickedRow >= 0 && clickedRow < recordings.size()) {
                selectedIndex = clickedRow;
                updateActionButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (recordings.size() > VISIBLE_ROWS) {
            int maxScroll = recordings.size() - VISIBLE_ROWS;
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xE608080A);

        int panelW = 360;
        int panelH = this.height - 30;
        int px = (this.width - panelW) / 2;
        int py = 14;

        context.fill(px, py, px + panelW, py + panelH, 0xFF101014);
        context.drawBorder(px, py, panelW, panelH, 0xFF8E1616);

        context.drawCenteredTextWithShadow(this.textRenderer, "SAVED MP4 RECORDINGS", this.width / 2, py + 10, 0xFFFFFFFF);

        int listX = px + 10;
        int listY = 48;
        int rowW = panelW - 20;
        int rowH = 34;

        if (recordings.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "No recordings found in screen-recordings/", this.width / 2, listY + 60, 0xFF8E8E93);
        } else {
            int end = Math.min(recordings.size(), scrollOffset + VISIBLE_ROWS);
            for (int i = scrollOffset; i < end; i++) {
                RecordingFileManager.RecordingMetadata rec = recordings.get(i);
                int ry = listY + (i - scrollOffset) * rowH;
                boolean selected = (i == selectedIndex);

                int bg = selected ? 0xFF2B1215 : 0xFF16161B;
                int border = selected ? 0xFFE53935 : 0xFF2C2C34;

                context.fill(listX, ry, listX + rowW, ry + rowH - 2, bg);
                context.drawBorder(listX, ry, rowW, rowH - 2, border);

                context.drawTextWithShadow(this.textRenderer, rec.filename(), listX + 6, ry + 5, 0xFFFFFFFF);
                String details = String.format(
                        "%s | %s @ %s | Duration: %s | %s",
                        rec.sizeFormatted(), rec.resolution(), rec.fps(), rec.duration(), rec.creationDate()
                );
                context.drawTextWithShadow(this.textRenderer, details, listX + 6, ry + 18, 0xFFB0B0B5);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
