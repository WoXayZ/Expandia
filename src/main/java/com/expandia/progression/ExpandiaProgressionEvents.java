package com.expandia.progression;

import com.expandia.Expandia;
import com.expandia.ExpandiaConstants;
import com.expandia.config.ExpandiaConfig;
import com.expandia.config.ExpandiaConfigManager;
import com.expandia.worldgen.ExpandiaChunkRegenManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
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
    public static void onServerStopping(ServerStoppingEvent event) {
        ExpandiaChunkRegenManager.discard();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ExpandiaChunkRegenManager.tick(server.overworld());
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether != null) {
            ExpandiaChunkRegenManager.tick(nether);
        }
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
        ResourceLocation advancementId = event.getAdvancement().id();
        if (!ExpandiaConfigManager.get().isIslandAdvancement(advancementId)) {
            return;
        }
        boolean applied = ExpandiaProgressionManager.grantFromAdvancement(
                server, advancementId.toString(), player);
        if (applied) {
            Expandia.LOGGER.info("Expandia: {} unlocked a new island from {} (overworld {}, nether {})",
                    player.getName().getString(), advancementId,
                    ExpandiaProgressionManager.getOverworldIslandCount(server),
                    ExpandiaProgressionManager.getNetherIslandCount(server));
        } else if (!ExpandiaConfig.isNetherEntryAdvancement(advancementId)) {
            player.sendSystemMessage(Component.translatable(
                    ExpandiaConstants.Lang.MESSAGE_ADVANCEMENT_ALREADY_COUNTED));
        }
    }
}
