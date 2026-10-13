package com.historiamc.townyeras.network;

import com.historiamc.townyeras.HistoriaEras;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestSnapshotPayload(boolean refresh) implements CustomPacketPayload {
    public static final Type<RequestSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HistoriaEras.MOD_ID, "request_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    RequestSnapshotPayload::refresh,
                    RequestSnapshotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
