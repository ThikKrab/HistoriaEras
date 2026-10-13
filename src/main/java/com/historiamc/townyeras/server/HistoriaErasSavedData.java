package com.historiamc.townyeras.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One overworld-scoped save file for the global clock and every player's progress. */
public final class HistoriaErasSavedData extends SavedData {
    private static final String FILE_NAME = "townyeras_progress";
    private static final int DATA_VERSION = 1;
    private static final SavedData.Factory<HistoriaErasSavedData> FACTORY =
            new SavedData.Factory<>(HistoriaErasSavedData::new, HistoriaErasSavedData::load);

    private final LinkedHashMap<UUID, PlayerProgress> players = new LinkedHashMap<>();
    private TimelineState timeline = new TimelineState();

    public static HistoriaErasSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    private static HistoriaErasSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        HistoriaErasSavedData data = new HistoriaErasSavedData();
        data.timeline = TimelineState.load(tag.getCompound("timeline"));

        ListTag savedPlayers = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < savedPlayers.size(); i++) {
            CompoundTag savedPlayer = savedPlayers.getCompound(i);
            if (!savedPlayer.hasUUID("uuid")) {
                continue;
            }
            data.players.put(savedPlayer.getUUID("uuid"), PlayerProgress.load(savedPlayer.getCompound("progress")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("timeline", timeline.save());

        ListTag savedPlayers = new ListTag();
        for (Map.Entry<UUID, PlayerProgress> entry : players.entrySet()) {
            CompoundTag savedPlayer = new CompoundTag();
            savedPlayer.putUUID("uuid", entry.getKey());
            savedPlayer.put("progress", entry.getValue().save());
            savedPlayers.add(savedPlayer);
        }
        tag.put("players", savedPlayers);
        return tag;
    }

    PlayerProgress player(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> {
            setDirty();
            return new PlayerProgress();
        });
    }

    boolean removePlayer(UUID playerId) {
        boolean changed = players.remove(playerId) != null;
        if (changed) {
            setDirty();
        }
        return changed;
    }

    TimelineState timeline() {
        return timeline;
    }
}
