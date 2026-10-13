package com.historiamc.townyeras.network;

import com.historiamc.townyeras.HistoriaEras;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProgressSnapshotPayload(CompoundTag snapshot) implements CustomPacketPayload {
    public static final Type<ProgressSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HistoriaEras.MOD_ID, "progress_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.COMPOUND_TAG,
                    ProgressSnapshotPayload::snapshot,
                    ProgressSnapshotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
