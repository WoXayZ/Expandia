package com.expandia.progression;

import com.expandia.config.ExpandiaConfigManager;
import com.expandia.network.IslandAdvancementsPayload;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ExpandiaProgressionSync {

    private ExpandiaProgressionSync() {
    }

    public static void sendToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }

    public static void sendTo(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new IslandAdvancementsPayload(buildEntries(server, player)));
    }

    private static List<IslandAdvancementsPayload.Entry> buildEntries(MinecraftServer server, ServerPlayer player) {
        ServerAdvancementManager manager = server.getAdvancements();
        List<IslandAdvancementsPayload.Entry> entries = new ArrayList<>();
        for (ResourceLocation id : ExpandiaConfigManager.get().allAdvancements()) {
            AdvancementHolder holder = manager.get(id);
            Component title = Component.literal(id.toString());
            Component description = Component.empty();
            ItemStack icon = ItemStack.EMPTY;
            boolean completed = false;
            if (holder != null) {
                Optional<DisplayInfo> display = holder.value().display();
                if (display.isPresent()) {
                    DisplayInfo info = display.get();
                    title = info.getTitle();
                    description = info.getDescription();
                    icon = info.getIcon();
                }
                completed = player.getAdvancements().getOrStartProgress(holder).isDone();
            }
            entries.add(new IslandAdvancementsPayload.Entry(id, title, description, icon, completed));
        }
        return entries;
    }
}
