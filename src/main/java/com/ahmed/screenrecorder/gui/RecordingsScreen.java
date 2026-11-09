package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * OBS Recordings Browser Window matching the exact floating Click-GUI size and OBS Yami theme.
 */
public class RecordingsScreen extends Screen {
    private final Screen parent;
    private List<RecordingFileManager.RecordingMetadata> recordings = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 4;

    private ThemeButton playButton;
    private ThemeButton deleteButton;
    private int winX, winY, winW, winH;

    public RecordingsScreen(Screen parent) {
        super(Text.literal("OBS Recordings"));
        this.parent = parent;
    }

    private void computeWindowBounds() {
        this.winW = Math.min(500, Math.max(320, (int) (this.width * 0.88f)));
        this.winH = Math.min(285, Math.max(195, (int) (this.height * 0.86f)));
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;
    }

    @Override
    protected void init() {
        computeWindowBounds();
        refreshList();

        int bottomY = winY + winH - 26;
        int totalBtnW = winW - 20;
        int startX = winX + 10;

        // Top Action: Play Selected Video In-Game
        playButton = new ThemeButton(
                startX, bottomY - 22, (totalBtnW * 2) / 3 - 2, 19,
                UIHelper.Icon.PLAY, Text.literal("Play Selected MP4 In-Game"),
                this::playSelected,
                true
        );
        this.addDrawableChild(playButton);

        // Open OBS Studio Main Screen directly from Recordings
        this.addDrawableChild(new ThemeButton(
                startX + (totalBtnW * 2) / 3 + 2, bottomY - 22, totalBtnW / 3 - 2, 19,
                UIHelper.Icon.VIDEO, Text.literal("OBS Studio"),
                () -> this.client.setScreen(new RecorderScreen(this))
        ));

        int colW = (totalBtnW - 12) / 4;
        this.addDrawableChild(new ThemeButton(
                startX, bottomY, colW, 19,
                UIHelper.Icon.FOLDER, Text.literal("Open Folder"),
                RecordingFileManager::openRecordingsFolder
        ));

        deleteButton = new ThemeButton(
                startX + colW + 4, bottomY, colW, 19,
                UIHelper.Icon.DELETE, Text.literal("Delete"),
                this::deleteSelected
        );
        this.addDrawableChild(deleteButton);

        this.addDrawableChild(new ThemeButton(
                startX + (colW + 4) * 2, bottomY, colW, 19,
                UIHelper.Icon.REFRESH, Text.literal("Refresh"),
                this::refreshList
        ));

        this.addDrawableChild(new ThemeButton(
                startX + (colW + 4) * 3, bottomY, colW, 19,
                UIHelper.Icon.CLOSE, Text.literal("Close"),
                this::close
        ));

        updateActionButtons();
    }

    private void refreshList() {
        this.recordings = RecordingFileManager.listRecordings();
        if (!recordings.isEmpty() && selectedIndex == -1) {
            selectedIndex = 0;
        } else if (selectedIndex >= recordings.size()) {
            selectedIndex = recordings.isEmpty() ? -1 : recordings.size() - 1;
        }
        updateActionButtons();
    }

    private void playSelected() {
        if (selectedIndex >= 0 && selectedIndex < recordings.size()) {
            this.client.setScreen(new VideoPlayerScreen(this, recordings.get(selectedIndex).file()));
        }
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
        boolean hasSelection = (selectedIndex >= 0 && selectedIndex < recordings.size());
        if (deleteButton != null) deleteButton.active = hasSelection;
        if (playButton != null) playButton.active = hasSelection;
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        computeWindowBounds();
        double mouseX = click.x();
        double mouseY = click.y();
        int listX = winX + 10;
        int listW = winW - 20;
        int listY = winY + 34;
        int rowH = 34;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + VISIBLE_ROWS * rowH) {
            int clickedRow = (int) ((mouseY - listY) / rowH) + scrollOffset;
            if (clickedRow >= 0 && clickedRow < recordings.size()) {
                if (selectedIndex == clickedRow && click.button() == 0) {
                    playSelected();
                } else {
                    selectedIndex = clickedRow;
                    updateActionButtons();
                }
                return true;
            }
        }
        return super.mouseClicked(click, bl);
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
        computeWindowBounds();
        context.fill(0, 0, this.width, this.height, 0x66000000);

        // Floating OBS Window Shadow & Body
        context.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x99000000);
        context.fill(winX, winY, winX + winW, winY + winH, 0xFF1D1F26);
        UIHelper.drawBorder(context, winX, winY, winW, winH, 0xFF3F4454);

        // Window Titlebar
        context.fill(winX + 1, winY + 1, winX + winW - 1, winY + 15, 0xFF2B2E38);
        context.fill(winX + 6, winY + 5, winX + 11, winY + 10, 0xFFFF5F56);
        context.fill(winX + 14, winY + 5, winX + 19, winY + 10, 0xFFFFBD2E);
        context.fill(winX + 22, winY + 5, winX + 27, winY + 10, 0xFF27C93F);
        context.drawCenteredTextWithShadow(this.textRenderer, "OBS Studio — Saved Recordings (" + recordings.size() + ")", winX + winW / 2, winY + 4, 0xFFDADCE0);

        int listX = winX + 10;
        int listY = winY + 30;
        int rowW = winW - 20;
        int rowH = 34;

        context.fill(listX - 2, listY - 2, listX + rowW + 2, winY + winH - 54, 0xFF14161B);
        UIHelper.drawBorder(context, listX - 2, listY - 2, rowW + 4, (winY + winH - 54) - (listY - 2), 0xFF2E323E);

        if (recordings.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "No MP4 recordings saved yet.", winX + winW / 2, listY + 50, 0xFF9AA0A6);
        } else {
            int end = Math.min(recordings.size(), scrollOffset + VISIBLE_ROWS);
            for (int i = scrollOffset; i < end; i++) {
                RecordingFileManager.RecordingMetadata rec = recordings.get(i);
                int ry = listY + (i - scrollOffset) * rowH;
                boolean selected = (i == selectedIndex);

                int bg = selected ? 0xFF2865C7 : 0xFF20232B;
                int border = selected ? 0xFF60A5FA : 0xFF323644;

                context.fill(listX, ry, listX + rowW, ry + rowH - 3, bg);
                UIHelper.drawBorder(context, listX, ry, rowW, rowH - 3, border);

                UIHelper.drawIcon(context, UIHelper.Icon.VIDEO, listX + 6, ry + 5, 0xFFFFFFFF);
                context.drawTextWithShadow(this.textRenderer, rec.filename(), listX + 18, ry + 5, 0xFFFFFFFF);
                String details = String.format(
                        "Size: %s  |  %s @ %s  |  Date: %s",
                        rec.sizeFormatted(), rec.resolution(), rec.fps(), rec.creationDate()
                );
                context.drawTextWithShadow(this.textRenderer, details, listX + 18, ry + 18, 0xFFDADCE0);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
