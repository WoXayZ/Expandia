package com.expandia.progression;

import com.expandia.Expandia;
import com.expandia.ExpandiaConstants;
import com.expandia.config.ExpandiaConfigManager;
import com.expandia.worldgen.ExpandiaChunkRegenManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = ExpandiaConstants.MOD_ID)
public final class ExpandiaProgressionEvents {

    private ExpandiaProgressionEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ExpandiaProgressionManager.syncRuntimeState(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ExpandiaChunkRegenManager.tick(event.getServer().overworld());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ExpandiaProgressionSync.sendTo(player);
        }
    }

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        Identifier advancementId = event.getAdvancement().id();
        if (!ExpandiaConfigManager.get().isIslandAdvancement(advancementId)) {
            return;
        }
        boolean applied = ExpandiaProgressionManager.grantFromAdvancement(
                server, advancementId.toString(), player);
        if (applied) {
            Expandia.LOGGER.info("Expandia: {} unlocked a new island from {} (total islands: {})",
                    player.getName().getString(), advancementId,
                    ExpandiaProgressionManager.getIslandCount(server));
        } else {
            player.sendSystemMessage(Component.translatable(
                    ExpandiaConstants.Lang.MESSAGE_ADVANCEMENT_ALREADY_COUNTED));
        }
    }
}
