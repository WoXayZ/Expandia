package com.expandia.network;

import com.expandia.ExpandiaConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestIslandAdvancementsPayload() implements CustomPacketPayload {

    public static final RequestIslandAdvancementsPayload INSTANCE = new RequestIslandAdvancementsPayload();

    public static final Type<RequestIslandAdvancementsPayload> TYPE =
            new Type<>(ExpandiaConstants.id("request_island_advancements"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestIslandAdvancementsPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
