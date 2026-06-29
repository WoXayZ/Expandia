package com.expandia.progression;

import com.expandia.ExpandiaConstants;
import com.expandia.config.ExpandiaConfig;
import com.expandia.config.ExpandiaConfigManager;
import com.expandia.persistence.ExpandiaSavedData;
import com.expandia.worldgen.ExpandiaChunkRegenManager;
import com.expandia.worldgen.ExpandiaIslandLayout;
import com.expandia.worldgen.ExpandiaRadiusState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class ExpandiaProgressionManager {

    private ExpandiaProgressionManager() {
    }

    public static int getIslandRadius() {
        return ExpandiaConfigManager.get().baseRadius();
    }

    public static void syncRuntimeState(MinecraftServer server) {
        ExpandiaConfig config = ExpandiaConfigManager.get();
        ExpandiaRadiusState.setIslandRadius(config.baseRadius());
        ExpandiaRadiusState.setTransitionWidth(config.transitionWidth());
        ExpandiaRadiusState.setSpiralIndices(ExpandiaSavedData.get(server).getSpiralIndices());
        clearWorldBorder(server);
    }

    private static void clearWorldBorder(MinecraftServer server) {
        server.overworld().getWorldBorder().setSize(59_999_968.0D);
    }

    public static int getIslandCount(MinecraftServer server) {
        return ExpandiaSavedData.get(server).getIslandCount();
    }

    public static boolean grantFromAdvancement(MinecraftServer server, String advancementId, @Nullable ServerPlayer player) {
        ExpandiaSavedData data = ExpandiaSavedData.get(server);
        if (data.hasCounted(advancementId)) {
            return false;
        }
        int slot = nextFreeSlot(server);
        data.addIsland(slot, advancementId);
        syncRuntimeState(server);
        revealNewIsland(server, player, slot);
        return true;
    }

    public static void grantIsland(MinecraftServer server, @Nullable ServerPlayer player) {
        int slot = nextFreeSlot(server);
        ExpandiaSavedData.get(server).addIsland(slot);
        syncRuntimeState(server);
        revealNewIsland(server, player, slot);
    }

    private static int nextFreeSlot(MinecraftServer server) {
        ExpandiaConfig config = ExpandiaConfigManager.get();
        int searchStart = ExpandiaSavedData.get(server).getHighestSpiralIndex() + 1;
        return ExpandiaIslandLayout.findPlaceableSlot(
                server.overworld(), searchStart, config.baseRadius(), config.transitionWidth());
    }

    private static void revealNewIsland(MinecraftServer server, @Nullable ServerPlayer player, int spiralIndex) {
        ServerLevel overworld = server.overworld();
        ExpandiaConfig config = ExpandiaConfigManager.get();
        double[] center = ExpandiaIslandLayout.center(spiralIndex, config.baseRadius(), config.transitionWidth());
        int cx = (int) Math.round(center[0]);
        int cz = (int) Math.round(center[1]);

        ExpandiaChunkRegenManager.revealIsland(overworld, center[0], center[1], config.baseRadius());

        if (player != null) {
            player.sendSystemMessage(Component.translatable(
                    ExpandiaConstants.Lang.MESSAGE_ISLAND_REVEALED, cx, cz));
        }

        ExpandiaProgressionSync.sendToAll(server);
    }

    public static void reset(MinecraftServer server) {
        ExpandiaConfig config = ExpandiaConfigManager.get();
        int[] previousSlots = ExpandiaSavedData.get(server).getSpiralIndices();

        ExpandiaSavedData.get(server).reset();
        syncRuntimeState(server);

        ServerLevel overworld = server.overworld();
        for (int slot : previousSlots) {
            if (slot == 0) {
                continue;
            }
            double[] center = ExpandiaIslandLayout.center(slot, config.baseRadius(), config.transitionWidth());
            ExpandiaChunkRegenManager.revealIsland(overworld, center[0], center[1], config.baseRadius());
        }

        ExpandiaProgressionSync.sendToAll(server);
    }
}
