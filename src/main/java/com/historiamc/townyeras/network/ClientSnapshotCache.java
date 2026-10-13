package com.historiamc.townyeras.network;

import com.historiamc.townyeras.HistoriaEras;
import com.historiamc.townyeras.tech.TechRegistry;
import com.historiamc.townyeras.tech.TechnologyManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Side-safe cache for the latest server-authoritative dashboard state.
 *
 * <p>This class deliberately has no references to Minecraft client classes, so the payload
 * handler can be registered from the common mod entry point on a dedicated server.</p>
 */
public final class ClientSnapshotCache {
    private static volatile CompoundTag latest = new CompoundTag();
    private static volatile boolean synchronizedWithServer;
    private static volatile long revision;

    private ClientSnapshotCache() {
    }

    public static void accept(CompoundTag snapshot) {
        if (!isAuthorizedSnapshot(snapshot)) {
            HistoriaEras.LOGGER.warn("Ignored a HistoriaEras snapshot without a valid HistoriaMC session handshake");
            clear();
            return;
        }
        if (snapshot != null && snapshot.contains("technologyJson", Tag.TAG_LIST)) {
            try {
                ListTag chunks = snapshot.getList("technologyJson", Tag.TAG_STRING);
                StringBuilder json = new StringBuilder();
                for (int index = 0; index < chunks.size(); index++) json.append(chunks.getString(index));
                TechnologyManager.installFromNetwork(TechRegistry.fromNormalizedJson(json.toString()));
            } catch (Exception exception) {
                HistoriaEras.LOGGER.error("The server sent invalid HistoriaEras technology definitions", exception);
            }
        }
        latest = snapshot == null ? new CompoundTag() : snapshot.copy();
        synchronizedWithServer = true;
        revision++;
    }

    public static CompoundTag latest() {
        return latest.copy();
    }

    public static boolean isSynchronizedWithServer() {
        return synchronizedWithServer;
    }

    public static long revision() {
        return revision;
    }

    public static void clear() {
        latest = new CompoundTag();
        synchronizedWithServer = false;
        TechnologyManager.restoreBundledForClient();
        revision++;
    }

    private static boolean isAuthorizedSnapshot(CompoundTag snapshot) {
        return snapshot != null
                && snapshot.getBoolean(HistoriaErasNetwork.SESSION_AUTHORIZED_KEY)
                && snapshot.getInt(HistoriaErasNetwork.SESSION_PROTOCOL_KEY) == HistoriaErasNetwork.SESSION_PROTOCOL
                && HistoriaErasNetwork.SESSION_SERVER_ID.equals(
                        snapshot.getString(HistoriaErasNetwork.SESSION_SERVER_ID_KEY)
                );
    }
}
