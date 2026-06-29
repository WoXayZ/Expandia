package com.expandia.network;

import com.expandia.progression.ExpandiaProgressionSync;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ExpandiaNetwork {

    private ExpandiaNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                IslandAdvancementsPayload.TYPE,
                IslandAdvancementsPayload.STREAM_CODEC,
                (payload, context) -> ExpandiaIslandAdvancementsCache.update(payload.entries()));

        registrar.playToServer(
                RequestIslandAdvancementsPayload.TYPE,
                RequestIslandAdvancementsPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        ExpandiaProgressionSync.sendTo(serverPlayer);
                    }
                });
    }
}
