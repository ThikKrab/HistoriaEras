package com.historiamc.townyeras.network;

import com.historiamc.townyeras.HistoriaEras;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TrackNodePayload(String nodeId) implements CustomPacketPayload {
    public static final Type<TrackNodePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HistoriaEras.MOD_ID, "track_node")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, TrackNodePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    TrackNodePayload::nodeId,
                    TrackNodePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
