package com.ahmed.screenrecorder.gui;

import com.ahmed.screenrecorder.util.RecordingFileManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class RecordingsScreen extends Screen {
    private final Screen parent;
    private List<RecordingFileManager.RecordingMetadata> recordings = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 4;

    private ThemeButton playButton;
    private ThemeButton deleteButton;

    public RecordingsScreen(Screen parent) {
        super(Text.literal("RECORDINGS"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshList();

        int centerX = this.width / 2;
        int bottomY = this.height - 30;

        playButton = new ThemeButton(
                centerX - 170, bottomY - 24, 338, 20,
                UIHelper.Icon.PLAY, Text.literal("PLAY SELECTED MP4 IN-GAME"),
                this::playSelected,
                true
        );
        this.addDrawableChild(playButton);

        this.addDrawableChild(new ThemeButton(
                centerX - 170, bottomY, 82, 20,
                UIHelper.Icon.FOLDER, Text.literal("FOLDER"),
                RecordingFileManager::openRecordingsFolder
        ));

        deleteButton = new ThemeButton(
                centerX - 84, bottomY, 82, 20,
                UIHelper.Icon.DELETE, Text.literal("DELETE"),
                this::deleteSelected
        );
        this.addDrawableChild(deleteButton);

        this.addDrawableChild(new ThemeButton(
                centerX + 2, bottomY, 82, 20,
                UIHelper.Icon.REFRESH, Text.literal("REFRESH"),
                this::refreshList
        ));

        this.addDrawableChild(new ThemeButton(
                centerX + 88, bottomY, 80, 20,
                UIHelper.Icon.CLOSE, Text.literal("BACK"),
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
        double mouseX = click.x();
        double mouseY = click.y();
        int panelW = Math.min(360, this.width - 16);
        int listX = (this.width - panelW) / 2 + 10;
        int listW = panelW - 20;
        int listY = 40;
        int rowH = 34;

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
        context.fill(0, 0, this.width, this.height, 0xFF181A20);

        int panelW = Math.min(360, this.width - 16);
        int panelH = this.height - 20;
        int px = (this.width - panelW) / 2;
        int py = 10;

        context.fill(px, py, px + panelW, py + panelH, 0xFF21242D);
        UIHelper.drawBorder(context, px, py, panelW, panelH, 0xFF3A3E4B);

        context.drawCenteredTextWithShadow(this.textRenderer, "SAVED MP4 RECORDINGS", this.width / 2, py + 8, 0xFFFFFFFF);

        int listX = px + 10;
        int listY = 40;
        int rowW = panelW - 20;
        int rowH = 34;

        if (recordings.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "No recordings found in screen-recordings/", this.width / 2, listY + 50, 0xFF9AA0A6);
        } else {
            int end = Math.min(recordings.size(), scrollOffset + VISIBLE_ROWS);
            for (int i = scrollOffset; i < end; i++) {
                RecordingFileManager.RecordingMetadata rec = recordings.get(i);
                int ry = listY + (i - scrollOffset) * rowH;
                boolean selected = (i == selectedIndex);

                int bg = selected ? 0xFF2865C7 : 0xFF191B22;
                int border = selected ? 0xFF60A5FA : 0xFF2E323E;

                context.fill(listX, ry, listX + rowW, ry + rowH - 2, bg);
                UIHelper.drawBorder(context, listX, ry, rowW, rowH - 2, border);

                UIHelper.drawIcon(context, UIHelper.Icon.VIDEO, listX + 6, ry + 5, 0xFFFFFFFF);
                context.drawTextWithShadow(this.textRenderer, rec.filename(), listX + 18, ry + 5, 0xFFFFFFFF);
                String details = String.format(
                        "%s | %s @ %s | %s",
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
