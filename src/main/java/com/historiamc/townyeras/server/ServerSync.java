package com.historiamc.townyeras.server;

import com.historiamc.townyeras.network.HistoriaErasNetwork;
import com.historiamc.townyeras.tech.TechNode;
import com.historiamc.townyeras.tech.TechRegistry;
import com.historiamc.townyeras.tech.TechnologyManager;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps each client view current and turns server-side progress into concise feedback. */
public final class ServerSync {
    private static volatile TechRegistry registry;
    private static final Set<UUID> PENDING_SNAPSHOTS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, String> SENT_TECHNOLOGY_REVISIONS = new ConcurrentHashMap<>();

    private ServerSync() {
    }

    public static void configure(TechRegistry technology) {
        registry = Objects.requireNonNull(technology, "technology");
    }

    public static void sync(ServerPlayer player) {
        if (!ServerSessionPolicy.isActive(player.server)) {
            return;
        }
        PENDING_SNAPSHOTS.remove(player.getUUID());
        var snapshot = ProgressService.createSnapshot(player);
        snapshot.putBoolean(HistoriaErasNetwork.SESSION_AUTHORIZED_KEY, true);
        snapshot.putInt(HistoriaErasNetwork.SESSION_PROTOCOL_KEY, HistoriaErasNetwork.SESSION_PROTOCOL);
        snapshot.putString(HistoriaErasNetwork.SESSION_SERVER_ID_KEY, HistoriaErasNetwork.SESSION_SERVER_ID);
        String revision = TechnologyManager.revision();
        snapshot.putString("technologyRevision", revision);
        if (!revision.equals(SENT_TECHNOLOGY_REVISIONS.get(player.getUUID()))) {
            ListTag chunks = new ListTag();
            String json = TechnologyManager.current().toNormalizedJson();
            for (int offset = 0; offset < json.length(); offset += 30_000) {
                chunks.add(StringTag.valueOf(json.substring(offset, Math.min(json.length(), offset + 30_000))));
            }
            snapshot.put("technologyJson", chunks);
            SENT_TECHNOLOGY_REVISIONS.put(player.getUUID(), revision);
        }
        HistoriaErasNetwork.sendSnapshot(player, snapshot);
    }

    public static void forget(ServerPlayer player) {
        SENT_TECHNOLOGY_REVISIONS.remove(player.getUUID());
        PENDING_SNAPSHOTS.remove(player.getUUID());
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    /** Coalesces all objective changes in one server tick into one network snapshot. */
    public static void flushPending(MinecraftServer server) {
        for (UUID playerId : Set.copyOf(PENDING_SNAPSHOTS)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                PENDING_SNAPSHOTS.remove(playerId);
            } else {
                sync(player);
            }
        }
    }

    public static void progress(ServerPlayer player, ProgressService.ProgressUpdate update) {
        if (update == null || !update.changed()) {
            return;
        }

        String tracked = ProgressService.snapshot(player).trackedNodeId();
        update.requirementDeltas().stream()
                .filter(delta -> delta.nodeId().equals(tracked))
                .findFirst()
                .ifPresent(delta -> player.displayClientMessage(
                        Component.literal("HistoriaEras: ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal(displayName(delta.nodeId())).withStyle(ChatFormatting.YELLOW))
                                .append(Component.literal("  " + format(delta.after()) + "/" + format(delta.required()))
                                        .withStyle(ChatFormatting.WHITE)),
                        true
                ));

        for (String nodeId : update.completedNodeIds()) {
            player.sendSystemMessage(
                    Component.literal("Technology unlocked: ")
                            .withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(displayName(nodeId)).withStyle(ChatFormatting.GOLD))
            );
            player.level().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS,
                    0.7F,
                    1.15F
            );
        }
        PENDING_SNAPSHOTS.add(player.getUUID());
    }

    private static String displayName(String nodeId) {
        TechRegistry technology = registry;
        TechNode node = technology == null ? null : technology.node(nodeId);
        return node == null ? nodeId : node.displayName();
    }

    private static String format(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.000_001D
                ? Long.toString(rounded)
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
