package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * OBS Saved Recordings Manager (In-Game Video Player removed as requested).
 * Shows total saved MP4 count, file sizes in MB, durations, resolutions, and dates,
 * with quick actions to Open System Folder, Delete, Refresh, or launch OBS Studio.
 */
public class RecordingsScreen extends Screen {
    private final Screen parent;
    private List<RecordingFileManager.RecordingMetadata> recordings = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 4;

    private ThemeButton deleteButton;
    private int winX, winY, winW, winH;

    public RecordingsScreen(Screen parent) {
        super(Text.literal("OBS Saved Recordings"));
        this.parent = parent;
    }

    private void computeWindowBounds() {
        this.winW = Math.min(460, Math.max(310, (int) (this.width * 0.86f)));
        this.winH = Math.min(235, Math.max(180, (int) (this.height * 0.80f)));
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;
    }

    @Override
    protected void init() {
        computeWindowBounds();
        refreshList();

        // Top-Right [ X ] Close Button
        this.addDrawableChild(new ThemeButton(winX + winW - 18, winY + 2, 16, 12,
                UIHelper.Icon.CLOSE, Text.empty(), this::close, true));

        int bottomY = winY + winH - 25;
        int totalBtnW = winW - 20;
        int startX = winX + 10;
        int colW = (totalBtnW - 12) / 4;

        this.addDrawableChild(new ThemeButton(
                startX, bottomY, colW, 19,
                UIHelper.Icon.VIDEO, Text.literal("OBS Studio"),
                () -> this.client.setScreen(new RecorderScreen(this)),
                true
        ));

        this.addDrawableChild(new ThemeButton(
                startX + colW + 4, bottomY, colW, 19,
                UIHelper.Icon.FOLDER, Text.literal("Open Folder"),
                RecordingFileManager::openRecordingsFolder
        ));

        deleteButton = new ThemeButton(
                startX + (colW + 4) * 2, bottomY, colW, 19,
                UIHelper.Icon.DELETE, Text.literal("Delete"),
                this::deleteSelected
        );
        this.addDrawableChild(deleteButton);

        this.addDrawableChild(new ThemeButton(
                startX + (colW + 4) * 3, bottomY, colW, 19,
                UIHelper.Icon.REFRESH, Text.literal("Refresh"),
                this::refreshList
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
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        computeWindowBounds();
        double mouseX = click.x();
        double mouseY = click.y();
        int listX = winX + 10;
        int listW = winW - 20;
        int listY = winY + 26;
        int rowH = 36;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + VISIBLE_ROWS * rowH) {
            int clickedRow = (int) ((mouseY - listY) / rowH) + scrollOffset;
            if (clickedRow >= 0 && clickedRow < recordings.size()) {
                selectedIndex = clickedRow;
                updateActionButtons();
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
        context.fill(0, 0, this.width, this.height, 0x55000000);

        context.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x99000000);
        context.fill(winX, winY, winX + winW, winY + winH, 0xFF1D1F26);
        UIHelper.drawBorder(context, winX, winY, winW, winH, 0xFF3F4454);

        context.fill(winX + 1, winY + 1, winX + winW - 1, winY + 16, 0xFF282B34);
        UIHelper.drawIcon(context, UIHelper.Icon.FOLDER, winX + 6, winY + 4, 0xFF60A5FA);
        context.drawTextWithShadow(this.textRenderer, "Saved Recordings — Total: " + recordings.size() + " MP4 files", winX + 18, winY + 4, 0xFFFFFFFF);

        int listX = winX + 10;
        int listY = winY + 24;
        int rowW = winW - 20;
        int rowH = 36;

        context.fill(listX - 2, listY - 2, listX + rowW + 2, winY + winH - 32, 0xFF14161B);
        UIHelper.drawBorder(context, listX - 2, listY - 2, rowW + 4, (winY + winH - 32) - (listY - 2), 0xFF2E323E);

        if (recordings.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "No recordings saved in .minecraft/screen-recordings/ yet.", winX + winW / 2, listY + 55, 0xFF9AA0A6);
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

                UIHelper.drawIcon(context, UIHelper.Icon.VIDEO, listX + 6, ry + 6, 0xFFFFFFFF);
                context.drawTextWithShadow(this.textRenderer, rec.filename(), listX + 18, ry + 6, 0xFFFFFFFF);
                String details = String.format(
                        "Size: %s   |   Duration: %s   |   %s @ %s   |   %s",
                        rec.sizeFormatted(), rec.duration(), rec.resolution(), rec.fps(), rec.creationDate()
                );
                context.drawTextWithShadow(this.textRenderer, details, listX + 18, ry + 20, 0xFFDADCE0);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
