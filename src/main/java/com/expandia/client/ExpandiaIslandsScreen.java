package com.expandia.client;

import com.expandia.ExpandiaConstants;
import com.expandia.network.ExpandiaIslandAdvancementsCache;
import com.expandia.network.IslandAdvancementsPayload;
import com.expandia.network.RequestIslandAdvancementsPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

public final class ExpandiaIslandsScreen extends Screen {

    private static final int PANEL_HALF_WIDTH = 175;
    private static final int LIST_TOP = 46;
    private static final int FOOTER_HEIGHT = 36;
    private static final int ROW_HEIGHT = 34;
    private static final int ICON_SIZE = 16;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_SUBTITLE = 0xFFA0A0A0;
    private static final int COLOR_DESCRIPTION = 0xFFB0B0B0;
    private static final int COLOR_ROW_LOCKED = 0x26FFFFFF;
    private static final int COLOR_ROW_UNLOCKED = 0x3355FF55;
    private static final int COLOR_TITLE_UNLOCKED = 0xFF8CF58C;
    private static final int COLOR_STATUS_UNLOCKED = 0xFF55FF55;
    private static final int COLOR_STATUS_LOCKED = 0xFF909090;

    private double scroll;

    public ExpandiaIslandsScreen() {
        super(Component.translatable(ExpandiaConstants.Lang.SCREEN_TITLE));
    }

    @Override
    protected void init() {
        ClientPacketDistributor.sendToServer(RequestIslandAdvancementsPayload.INSTANCE);

        int buttonWidth = 200;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - buttonWidth / 2, this.height - 28, buttonWidth, 20)
                .build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll = Mth.clamp(this.scroll - scrollY * (ROW_HEIGHT / 2.0D), 0.0D, maxScroll());
        return true;
    }

    private int listBottom() {
        return this.height - FOOTER_HEIGHT;
    }

    private int maxScroll() {
        int viewport = listBottom() - LIST_TOP;
        int content = ExpandiaIslandAdvancementsCache.entries().size() * ROW_HEIGHT;
        return Math.max(0, content - viewport);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.centeredText(this.font, this.title, this.width / 2, 16, COLOR_TITLE);

        List<IslandAdvancementsPayload.Entry> entries = ExpandiaIslandAdvancementsCache.entries();
        if (!ExpandiaIslandAdvancementsCache.hasReceived()) {
            graphics.centeredText(this.font, Component.translatable(ExpandiaConstants.Lang.SCREEN_LOADING),
                    this.width / 2, LIST_TOP + 12, COLOR_SUBTITLE);
        } else if (entries.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable(ExpandiaConstants.Lang.SCREEN_EMPTY),
                    this.width / 2, LIST_TOP + 12, COLOR_SUBTITLE);
        } else {
            long unlocked = entries.stream().filter(IslandAdvancementsPayload.Entry::completed).count();
            graphics.centeredText(this.font,
                    Component.translatable(ExpandiaConstants.Lang.SCREEN_SUBTITLE, unlocked, entries.size()),
                    this.width / 2, 30, COLOR_SUBTITLE);
            renderList(graphics, entries);
        }

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void renderList(GuiGraphicsExtractor graphics, List<IslandAdvancementsPayload.Entry> entries) {
        int left = this.width / 2 - PANEL_HALF_WIDTH;
        int right = this.width / 2 + PANEL_HALF_WIDTH;
        int top = LIST_TOP;
        int bottom = listBottom();

        this.scroll = Mth.clamp(this.scroll, 0.0D, maxScroll());

        graphics.enableScissor(left, top, right, bottom);
        int y = top - (int) this.scroll;
        for (IslandAdvancementsPayload.Entry entry : entries) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                renderRow(graphics, entry, left, right, y);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
    }

    private void renderRow(GuiGraphicsExtractor graphics, IslandAdvancementsPayload.Entry entry, int left, int right, int y) {
        boolean completed = entry.completed();
        graphics.fill(left, y + 2, right, y + ROW_HEIGHT - 2, completed ? COLOR_ROW_UNLOCKED : COLOR_ROW_LOCKED);

        int iconX = left + 8;
        int iconY = y + (ROW_HEIGHT - ICON_SIZE) / 2;
        if (!entry.icon().isEmpty()) {
            graphics.item(entry.icon(), iconX, iconY);
        }

        Component status = Component.translatable(
                completed ? ExpandiaConstants.Lang.SCREEN_STATUS_UNLOCKED : ExpandiaConstants.Lang.SCREEN_STATUS_LOCKED);
        int statusWidth = this.font.width(status);
        graphics.text(this.font, status, right - 8 - statusWidth, y + (ROW_HEIGHT - this.font.lineHeight) / 2,
                completed ? COLOR_STATUS_UNLOCKED : COLOR_STATUS_LOCKED);

        int textX = iconX + ICON_SIZE + 8;
        int textWidth = (right - 8 - statusWidth - 6) - textX;
        graphics.text(this.font, entry.title(), textX, y + 6, completed ? COLOR_TITLE_UNLOCKED : COLOR_TITLE);

        Component description = entry.description();
        if (description.getString().isEmpty()) {
            description = Component.translatable(ExpandiaConstants.Lang.SCREEN_NO_DESCRIPTION);
        }
        if (textWidth > 0) {
            List<FormattedCharSequence> lines = this.font.split(description, textWidth);
            if (!lines.isEmpty()) {
                graphics.text(this.font, lines.get(0), textX, y + 6 + this.font.lineHeight + 2, COLOR_DESCRIPTION);
            }
        }
    }
}
