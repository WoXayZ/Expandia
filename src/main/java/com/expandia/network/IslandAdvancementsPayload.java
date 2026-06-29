package com.expandia.network;

import com.expandia.ExpandiaConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record IslandAdvancementsPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<IslandAdvancementsPayload> TYPE =
            new Type<>(ExpandiaConstants.id("island_advancements"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IslandAdvancementsPayload> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(
                    IslandAdvancementsPayload::new, IslandAdvancementsPayload::entries);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(Identifier id, Component title, Component description, ItemStack icon, boolean completed) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Entry::id,
                ComponentSerialization.STREAM_CODEC, Entry::title,
                ComponentSerialization.STREAM_CODEC, Entry::description,
                ItemStack.OPTIONAL_STREAM_CODEC, Entry::icon,
                ByteBufCodecs.BOOL, Entry::completed,
                Entry::new);
    }
}
