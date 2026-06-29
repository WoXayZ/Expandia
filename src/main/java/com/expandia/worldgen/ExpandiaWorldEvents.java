package com.expandia.worldgen;

import com.expandia.Expandia;
import com.expandia.ExpandiaConstants;
import com.expandia.progression.ExpandiaProgressionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = ExpandiaConstants.MOD_ID)
public final class ExpandiaWorldEvents {

    private ExpandiaWorldEvents() {
    }

    @SubscribeEvent
    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (level.dimension() != Level.OVERWORLD) {
            return;
        }

        ExpandiaProgressionManager.syncRuntimeState(level.getServer());

        try {
            ExpandiaWorldGenInjector.injectInto(level);
        } catch (Throwable t) {
            Expandia.LOGGER.error("Expandia: could not inject land mask before spawn generation on overworld", t);
            return;
        }

        int seaLevel = level.getSeaLevel();
        event.getSettings().setSpawn(LevelData.RespawnData.of(
                level.dimension(),
                new BlockPos(0, seaLevel, 0),
                0.0F,
                0.0F
        ));
        event.setCanceled(true);
        Expandia.LOGGER.info("Expandia: overworld spawn pinned to (0, {}, 0)", seaLevel);
    }
}
