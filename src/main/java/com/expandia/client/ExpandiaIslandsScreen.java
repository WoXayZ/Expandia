package com.expandia.client;

import com.expandia.ExpandiaConstants;
import com.expandia.network.ExpandiaIslandAdvancementsCache;
import com.expandia.network.IslandAdvancementsPayload;
import com.expandia.network.RequestIslandAdvancementsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExpandiaIslandsScreen extends Screen {

    private static final int PANEL_HALF_WIDTH = 175;
    private static final int SEARCH_TOP = 28;
    private static final int LIST_TOP = 72;
    private static final int FOOTER_HEIGHT = 36;
    private static final int ROW_HEIGHT = 34;
    private static final int ICON_SIZE = 16;

    private static final int COLOR_TITLE = 0xFFFFFF;
    private static final int COLOR_SUBTITLE = 0xA0A0A0;
    private static final int COLOR_DESCRIPTION = 0xB0B0B0;
    private static final int COLOR_ROW_LOCKED = 0x26FFFFFF;
    private static final int COLOR_ROW_UNLOCKED = 0x3355FF55;
    private static final int COLOR_TITLE_UNLOCKED = 0x8CF58C;
    private static final int COLOR_STATUS_UNLOCKED = 0x55FF55;
    private static final int COLOR_STATUS_LOCKED = 0x909090;

    private enum Filter {
        ALL, UNLOCKED, LOCKED;

        Component label() {
            return Component.translatable(switch (this) {
                case ALL -> ExpandiaConstants.Lang.SCREEN_FILTER_ALL;
                case UNLOCKED -> ExpandiaConstants.Lang.SCREEN_FILTER_UNLOCKED;
                case LOCKED -> ExpandiaConstants.Lang.SCREEN_FILTER_LOCKED;
            });
        }
    }

    private double scroll;
    private String query = "";
    private Filter filter = Filter.ALL;
    private EditBox searchBox;

    public ExpandiaIslandsScreen() {
        super(Component.translatable(ExpandiaConstants.Lang.SCREEN_TITLE));
    }

    @Override
    protected void init() {
        PacketDistributor.sendToServer(RequestIslandAdvancementsPayload.INSTANCE);

        int left = this.width / 2 - PANEL_HALF_WIDTH;
        int right = this.width / 2 + PANEL_HALF_WIDTH;
        int filterWidth = 110;
        this.searchBox = new EditBox(this.font, left, SEARCH_TOP, (right - left) - filterWidth - 6, 20,
                Component.translatable(ExpandiaConstants.Lang.SCREEN_SEARCH));
        this.searchBox.setHint(Component.translatable(ExpandiaConstants.Lang.SCREEN_SEARCH));
        this.searchBox.setMaxLength(64);
        this.searchBox.setValue(this.query);
        this.searchBox.setResponder(value -> {
            this.query = value;
            this.scroll = 0.0D;
        });
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(CycleButton.builder(Filter::label)
                .withValues(Filter.values())
                .withInitialValue(this.filter)
                .create(right - filterWidth, SEARCH_TOP, filterWidth, 20,
                        Component.translatable(ExpandiaConstants.Lang.SCREEN_FILTER),
                        (button, value) -> {
                            this.filter = value;
                            this.scroll = 0.0D;
                        }));

        int buttonWidth = 200;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - buttonWidth / 2, this.height - 28, buttonWidth, 20)
                .build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll = Mth.clamp(this.scroll - scrollY * (ROW_HEIGHT / 2.0D), 0.0D, maxScroll());
        return true;
    }

    private int listBottom() {
        return this.height - FOOTER_HEIGHT;
    }

    private List<IslandAdvancementsPayload.Entry> filteredEntries() {
        List<IslandAdvancementsPayload.Entry> source = ExpandiaIslandAdvancementsCache.entries();
        String needle = this.query.trim().toLowerCase(Locale.ROOT);
        List<IslandAdvancementsPayload.Entry> result = new ArrayList<>();
        for (IslandAdvancementsPayload.Entry entry : source) {
            if (this.filter == Filter.UNLOCKED && !entry.completed()) {
                continue;
            }
            if (this.filter == Filter.LOCKED && entry.completed()) {
                continue;
            }
            if (!needle.isEmpty() && !matches(entry, needle)) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    private static boolean matches(IslandAdvancementsPayload.Entry entry, String needle) {
        return contains(entry.title().getString(), needle)
                || contains(entry.description().getString(), needle)
                || contains(entry.id().toString(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private int maxScroll() {
        int viewport = listBottom() - LIST_TOP;
        int content = filteredEntries().size() * ROW_HEIGHT;
        return Math.max(0, content - viewport);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, COLOR_TITLE);

        List<IslandAdvancementsPayload.Entry> all = ExpandiaIslandAdvancementsCache.entries();
        if (!ExpandiaIslandAdvancementsCache.hasReceived()) {
            graphics.drawCenteredString(this.font, Component.translatable(ExpandiaConstants.Lang.SCREEN_LOADING),
                    this.width / 2, LIST_TOP + 12, COLOR_SUBTITLE);
            return;
        }
        if (all.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable(ExpandiaConstants.Lang.SCREEN_EMPTY),
                    this.width / 2, LIST_TOP + 12, COLOR_SUBTITLE);
            return;
        }

        long unlocked = all.stream().filter(IslandAdvancementsPayload.Entry::completed).count();
        graphics.drawCenteredString(this.font,
                Component.translatable(ExpandiaConstants.Lang.SCREEN_SUBTITLE, unlocked, all.size()),
                this.width / 2, 52, COLOR_SUBTITLE);

        List<IslandAdvancementsPayload.Entry> visible = filteredEntries();
        if (visible.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable(ExpandiaConstants.Lang.SCREEN_NO_RESULTS),
                    this.width / 2, LIST_TOP + 12, COLOR_SUBTITLE);
        } else {
            renderList(graphics, visible);
        }
    }

    private void renderList(GuiGraphics graphics, List<IslandAdvancementsPayload.Entry> entries) {
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

    private void renderRow(GuiGraphics graphics, IslandAdvancementsPayload.Entry entry, int left, int right, int y) {
        boolean completed = entry.completed();
        graphics.fill(left, y + 2, right, y + ROW_HEIGHT - 2, completed ? COLOR_ROW_UNLOCKED : COLOR_ROW_LOCKED);

        int iconX = left + 8;
        int iconY = y + (ROW_HEIGHT - ICON_SIZE) / 2;
        if (!entry.icon().isEmpty()) {
            graphics.renderItem(entry.icon(), iconX, iconY);
        }

        Component status = Component.translatable(
                completed ? ExpandiaConstants.Lang.SCREEN_STATUS_UNLOCKED : ExpandiaConstants.Lang.SCREEN_STATUS_LOCKED);
        int statusWidth = this.font.width(status);
        graphics.drawString(this.font, status, right - 8 - statusWidth, y + (ROW_HEIGHT - this.font.lineHeight) / 2,
                completed ? COLOR_STATUS_UNLOCKED : COLOR_STATUS_LOCKED, false);

        int textX = iconX + ICON_SIZE + 8;
        int textWidth = (right - 8 - statusWidth - 6) - textX;
        graphics.drawString(this.font, entry.title(), textX, y + 6,
                completed ? COLOR_TITLE_UNLOCKED : COLOR_TITLE, false);

        Component description = entry.description();
        if (description.getString().isEmpty()) {
            description = Component.translatable(ExpandiaConstants.Lang.SCREEN_NO_DESCRIPTION);
        }
        if (textWidth > 0) {
            List<FormattedCharSequence> lines = this.font.split(description, textWidth);
            if (!lines.isEmpty()) {
                graphics.drawString(this.font, lines.get(0), textX, y + 6 + this.font.lineHeight + 2,
                        COLOR_DESCRIPTION, false);
            }
        }
    }
}
