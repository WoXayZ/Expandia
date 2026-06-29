package com.expandia.network;

import java.util.List;

public final class ExpandiaIslandAdvancementsCache {

    private static volatile List<IslandAdvancementsPayload.Entry> entries = List.of();
    private static volatile boolean received = false;

    private ExpandiaIslandAdvancementsCache() {
    }

    public static void update(List<IslandAdvancementsPayload.Entry> newEntries) {
        entries = List.copyOf(newEntries);
        received = true;
    }

    public static List<IslandAdvancementsPayload.Entry> entries() {
        return entries;
    }

    public static boolean hasReceived() {
        return received;
    }
}
