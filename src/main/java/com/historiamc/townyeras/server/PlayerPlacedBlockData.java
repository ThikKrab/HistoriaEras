package com.historiamc.townyeras.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persistent provenance for blocks placed by players.
 *
 * <p>Source progression must not be farmable by repeatedly placing and mining the
 * same ore, stone, plant, or other source block. Positions are kept per dimension
 * in the overworld data storage and removed after a successful player break.</p>
 */
final class PlayerPlacedBlockData extends SavedData {
    private static final String FILE_NAME = "townyeras_player_placed_blocks";
    private static final SavedData.Factory<PlayerPlacedBlockData> FACTORY =
            new SavedData.Factory<>(PlayerPlacedBlockData::new, PlayerPlacedBlockData::load);

    private final Map<String, Set<Long>> positionsByDimension = new LinkedHashMap<>();

    static PlayerPlacedBlockData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    private static PlayerPlacedBlockData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerPlacedBlockData data = new PlayerPlacedBlockData();
        ListTag dimensions = tag.getList("dimensions", Tag.TAG_COMPOUND);
        for (int index = 0; index < dimensions.size(); index++) {
            CompoundTag savedDimension = dimensions.getCompound(index);
            String dimensionId = savedDimension.getString("id");
            if (dimensionId.isBlank()) {
                continue;
            }
            LinkedHashSet<Long> positions = new LinkedHashSet<>();
            for (long position : savedDimension.getLongArray("positions")) {
                positions.add(position);
            }
            if (!positions.isEmpty()) {
                data.positionsByDimension.put(dimensionId, positions);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag dimensions = new ListTag();
        positionsByDimension.forEach((dimensionId, positions) -> {
            if (positions.isEmpty()) {
                return;
            }
            CompoundTag savedDimension = new CompoundTag();
            savedDimension.putString("id", dimensionId);
            savedDimension.putLongArray("positions", positions.stream().mapToLong(Long::longValue).toArray());
            dimensions.add(savedDimension);
        });
        tag.put("dimensions", dimensions);
        return tag;
    }

    void markPlaced(ServerLevel level, BlockPos pos) {
        if (positionsByDimension
                .computeIfAbsent(dimensionId(level), ignored -> new LinkedHashSet<>())
                .add(pos.asLong())) {
            setDirty();
        }
    }

    /** Returns whether the position was player-placed and forgets it after the successful break. */
    boolean consumePlaced(ServerLevel level, BlockPos pos) {
        String dimensionId = dimensionId(level);
        Set<Long> positions = positionsByDimension.get(dimensionId);
        if (positions == null || !positions.remove(pos.asLong())) {
            return false;
        }
        if (positions.isEmpty()) {
            positionsByDimension.remove(dimensionId);
        }
        setDirty();
        return true;
    }

    boolean isPlaced(ServerLevel level, BlockPos pos) {
        Set<Long> positions = positionsByDimension.get(dimensionId(level));
        return positions != null && positions.contains(pos.asLong());
    }

    private static String dimensionId(ServerLevel level) {
        return level.dimension().location().toString();
    }
}
