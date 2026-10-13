package com.historiamc.townyeras.network;

import com.historiamc.townyeras.HistoriaEras;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GettingStartedReminderPayload(boolean show) implements CustomPacketPayload {
    public static final Type<GettingStartedReminderPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HistoriaEras.MOD_ID, "getting_started_reminder"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GettingStartedReminderPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, GettingStartedReminderPayload::show, GettingStartedReminderPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
