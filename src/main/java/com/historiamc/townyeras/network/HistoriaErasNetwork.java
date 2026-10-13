package com.historiamc.townyeras.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class HistoriaErasNetwork {
    public static final int SESSION_PROTOCOL = 4;
    public static final String SESSION_SERVER_ID = "historiamc";
    public static final String SESSION_AUTHORIZED_KEY = "townyErasAuthorized";
    public static final String SESSION_PROTOCOL_KEY = "townyErasSessionProtocol";
    public static final String SESSION_SERVER_ID_KEY = "townyErasServerId";
    private static Consumer<ServerPlayer> snapshotRequestHandler = player -> { };
    private static BiConsumer<ServerPlayer, String> trackNodeHandler = (player, nodeId) -> { };
    private static Consumer<Boolean> reminderHandler = show -> { };

    private HistoriaErasNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // Optional payloads let clients keep HistoriaEras installed while joining
        // single-player worlds and servers that do not run HistoriaEras.
        PayloadRegistrar registrar = event.registrar(Integer.toString(SESSION_PROTOCOL)).optional();
        registrar.playToServer(OnboardingPayload.TYPE, OnboardingPayload.STREAM_CODEC,
                (payload, context) -> com.historiamc.townyeras.server.OnboardingService.receive((ServerPlayer) context.player(), payload));
        registrar.playToClient(GettingStartedReminderPayload.TYPE, GettingStartedReminderPayload.STREAM_CODEC,
                (payload, context) -> reminderHandler.accept(payload.show()));
        registrar.playToClient(
                ProgressSnapshotPayload.TYPE,
                ProgressSnapshotPayload.STREAM_CODEC,
                (payload, context) -> ClientSnapshotCache.accept(payload.snapshot())
        );
        registrar.playToServer(
                RequestSnapshotPayload.TYPE,
                RequestSnapshotPayload.STREAM_CODEC,
                (payload, context) -> snapshotRequestHandler.accept((ServerPlayer) context.player())
        );
        registrar.playToServer(
                TrackNodePayload.TYPE,
                TrackNodePayload.STREAM_CODEC,
                (payload, context) -> trackNodeHandler.accept((ServerPlayer) context.player(), payload.nodeId())
        );
    }

    public static void installServerHandlers(
            Consumer<ServerPlayer> snapshotRequests,
            BiConsumer<ServerPlayer, String> trackRequests
    ) {
        snapshotRequestHandler = Objects.requireNonNull(snapshotRequests);
        trackNodeHandler = Objects.requireNonNull(trackRequests);
    }

    public static void sendSnapshot(ServerPlayer player, CompoundTag snapshot) {
        if (NetworkRegistry.hasChannel(player.connection, ProgressSnapshotPayload.TYPE.id())) {
            PacketDistributor.sendToPlayer(player, new ProgressSnapshotPayload(snapshot.copy()));
        }
    }

    public static void requestSnapshot() {
        PacketDistributor.sendToServer(new RequestSnapshotPayload(true));
    }

    public static void installReminderHandler(Consumer<Boolean> handler) { reminderHandler = Objects.requireNonNull(handler); }

    public static void acknowledgeOnboarding(boolean treeViewed, boolean tutorialDismissed) {
        if (ClientSnapshotCache.isSynchronizedWithServer())
            PacketDistributor.sendToServer(new OnboardingPayload(treeViewed, tutorialDismissed));
    }

    public static boolean trackNode(String nodeId) {
        if (!ClientSnapshotCache.isSynchronizedWithServer()) {
            return false;
        }
        PacketDistributor.sendToServer(new TrackNodePayload(nodeId == null ? "" : nodeId));
        return true;
    }
}
