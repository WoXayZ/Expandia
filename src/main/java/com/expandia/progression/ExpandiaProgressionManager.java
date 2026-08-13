package com.expandia.progression;

import com.expandia.Expandia;
import com.expandia.ExpandiaConstants;
import com.expandia.config.ExpandiaConfig;
import com.expandia.config.ExpandiaConfigManager;
import com.expandia.persistence.ExpandiaSavedData;
import com.expandia.worldgen.ExpandiaChunkRegenManager;
import com.expandia.worldgen.ExpandiaIslandLayout;
import com.expandia.worldgen.ExpandiaRadiusState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

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
        ExpandiaSavedData data = ExpandiaSavedData.get(server);
        ExpandiaRadiusState.OVERWORLD.setSpiralIndices(data.overworld().getSpiralIndices());
        ExpandiaRadiusState.NETHER.setSpiralIndices(data.nether().getSpiralIndices());
        clearWorldBorder(server);
    }

    private static void clearWorldBorder(MinecraftServer server) {
        server.overworld().getWorldBorder().setSize(59_999_968.0D);
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether != null) {
            nether.getWorldBorder().setSize(59_999_968.0D);
        }
    }

    public static int getIslandCount(MinecraftServer server, ResourceKey<Level> dimension) {
        return ExpandiaSavedData.get(server).of(dimension).getIslandCount();
    }

    public static int getOverworldIslandCount(MinecraftServer server) {
        return getIslandCount(server, Level.OVERWORLD);
    }

    public static int getNetherIslandCount(MinecraftServer server) {
        return getIslandCount(server, Level.NETHER);
    }

    public static boolean grantFromAdvancement(MinecraftServer server, String advancementId, @Nullable ServerPlayer player) {
        ResourceLocation id = ResourceLocation.parse(advancementId);
        if (ExpandiaConfig.isNetherEntryAdvancement(id)) {
            return false;
        }
        ResourceKey<Level> dimension = ExpandiaConfigManager.get().dimensionForAdvancement(id);
        ExpandiaSavedData.DimProgress data = ExpandiaSavedData.get(server).of(dimension);
        if (data.hasCounted(advancementId)) {
            return false;
        }
        int slot = nextFreeSlot(data);
        data.addIsland(slot, advancementId);
        syncRuntimeState(server);
        revealNewIsland(server, player, dimension, slot);
        return true;
    }

    public static void grantIsland(MinecraftServer server, @Nullable ServerPlayer player) {
        grantIsland(server, player, dimensionOf(player));
    }

    public static void grantIsland(MinecraftServer server, @Nullable ServerPlayer player, ResourceKey<Level> dimension) {
        ExpandiaSavedData.DimProgress data = ExpandiaSavedData.get(server).of(dimension);
        int slot = nextFreeSlot(data);
        data.addIsland(slot);
        syncRuntimeState(server);
        revealNewIsland(server, player, dimension, slot);
    }

    private static ResourceKey<Level> dimensionOf(@Nullable ServerPlayer player) {
        if (player != null) {
            ResourceKey<Level> dimension = player.level().dimension();
            if (Level.NETHER.equals(dimension) || Level.OVERWORLD.equals(dimension)) {
                return dimension;
            }
        }
        return Level.OVERWORLD;
    }

    private static int nextFreeSlot(ExpandiaSavedData.DimProgress data) {
        return ExpandiaIslandLayout.nextSlot(data.getHighestSpiralIndex());
    }

    private static void revealNewIsland(
            MinecraftServer server, @Nullable ServerPlayer player, ResourceKey<Level> dimension, int spiralIndex) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            Expandia.LOGGER.warn("Expandia: cannot reveal island, missing dimension {}", dimension.location());
            return;
        }
        ExpandiaConfig config = ExpandiaConfigManager.get();
        ExpandiaRadiusState.DimState state = ExpandiaRadiusState.of(dimension);
        double[] center = ExpandiaIslandLayout.center(
                spiralIndex, config.baseRadius(), config.transitionWidth(), state.getOceanGap());
        int cx = (int) Math.round(center[0]);
        int cz = (int) Math.round(center[1]);

        Expandia.LOGGER.info("Expandia: revealing {} island slot {} at ({}, {})",
                dimension.location(), spiralIndex, cx, cz);
        ExpandiaChunkRegenManager.revealIsland(
                level, center[0], center[1], config.baseRadius(), config.transitionWidth(), spiralIndex);

        if (player != null) {
            player.sendSystemMessage(Component.translatable(
                    ExpandiaConstants.Lang.MESSAGE_ISLAND_REVEALED, cx, cz));
        }

        ExpandiaProgressionSync.sendToAll(server);
    }

    public static void repairNetherSpawn(MinecraftServer server) {
        ExpandiaSavedData data = ExpandiaSavedData.get(server);
        if (data.netherSpawnRepaired()) {
            return;
        }
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            return;
        }
        data.setNetherSpawnRepaired(true);
        ExpandiaConfig config = ExpandiaConfigManager.get();
        ExpandiaChunkRegenManager.revealIsland(
                nether, 0.0D, 0.0D, config.baseRadius(), config.transitionWidth(), 0);
        Expandia.LOGGER.info("Expandia: rebuilding nether spawn island at (0, 0)");
    }

    public static void reset(MinecraftServer server) {
        ExpandiaConfig config = ExpandiaConfigManager.get();
        ExpandiaSavedData data = ExpandiaSavedData.get(server);
        int[] overworldSlots = data.overworld().getSpiralIndices();
        int[] netherSlots = data.nether().getSpiralIndices();

        data.resetAll();
        syncRuntimeState(server);

        reReveal(server, Level.OVERWORLD, overworldSlots, config);
        reReveal(server, Level.NETHER, netherSlots, config);

        ExpandiaProgressionSync.sendToAll(server);
    }

    private static void reReveal(MinecraftServer server, ResourceKey<Level> dimension, int[] previousSlots, ExpandiaConfig config) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return;
        }
        for (int slot : previousSlots) {
            if (slot == 0 && !Level.NETHER.equals(dimension)) {
                continue;
            }
            double[] center = ExpandiaIslandLayout.center(
                    slot, config.baseRadius(), config.transitionWidth(), ExpandiaIslandLayout.oceanGap(dimension));
            ExpandiaChunkRegenManager.revealIsland(
                    level, center[0], center[1], config.baseRadius(), config.transitionWidth(), slot);
        }
    }
}
