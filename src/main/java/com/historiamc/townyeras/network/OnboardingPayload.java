package com.historiamc.townyeras.network;

import com.historiamc.townyeras.HistoriaEras;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OnboardingPayload(boolean treeViewed, boolean tutorialDismissed) implements CustomPacketPayload {
    public static final Type<OnboardingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HistoriaEras.MOD_ID, "onboarding"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OnboardingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OnboardingPayload::treeViewed, ByteBufCodecs.BOOL, OnboardingPayload::tutorialDismissed, OnboardingPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
