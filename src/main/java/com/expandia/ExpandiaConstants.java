package com.expandia;

import net.minecraft.resources.Identifier;

public final class ExpandiaConstants {

    private ExpandiaConstants() {
    }

    public static final String MOD_ID = "expandia";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static final String CONFIG_FILE = "progression.json";
    public static final String DATAPACK_DIRECTORY = MOD_ID;
    public static final String DATAPACK_FILE = "progression";

    public static final class Lang {

        private Lang() {
        }

        public static final String COMMAND_ISLANDS = "expandia.command.islands";
        public static final String COMMAND_UNLOCK = "expandia.command.unlock";
        public static final String COMMAND_RESET = "expandia.command.reset";
        public static final String COMMAND_RELOAD = "expandia.command.reload";

        public static final String MESSAGE_ISLAND_REVEALED = "expandia.message.island_revealed";
        public static final String MESSAGE_ADVANCEMENT_ALREADY_COUNTED = "expandia.message.advancement_already_counted";

        public static final String KEY_OPEN_SCREEN = "key.expandia.open_screen";
        public static final String KEY_CATEGORY = "key.category.expandia.main";

        public static final String SCREEN_TITLE = "expandia.screen.title";
        public static final String SCREEN_SUBTITLE = "expandia.screen.subtitle";
        public static final String SCREEN_STATUS_UNLOCKED = "expandia.screen.status.unlocked";
        public static final String SCREEN_STATUS_LOCKED = "expandia.screen.status.locked";
        public static final String SCREEN_EMPTY = "expandia.screen.empty";
        public static final String SCREEN_NO_DESCRIPTION = "expandia.screen.no_description";
        public static final String SCREEN_LOADING = "expandia.screen.loading";
    }
}
