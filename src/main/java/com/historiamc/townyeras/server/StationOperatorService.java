package com.historiamc.townyeras.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.crafting.RecipeHolder;

/** The last authorized operator is persisted on the station; unowned/unauthorized automation fails closed. */
public final class StationOperatorService {
    private static final String OPERATOR = "townyeras:station_operator";
    private StationOperatorService() {}

    public static void claim(BlockEntity station, ServerPlayer player) {
        if (station != null && ServerSessionPolicy.isActive(player.server)) {
            station.getPersistentData().putUUID(OPERATOR, player.getUUID());
            station.setChanged();
        }
    }

    public static boolean denied(BlockEntity station, RecipeHolder<?> recipe) {
        if (!(station.getLevel() instanceof ServerLevel level) || !ServerSessionPolicy.isActive(level.getServer())) return false;
        if (recipe == null) return true;
        var tag = station.getPersistentData();
        if (!tag.hasUUID(OPERATOR)) return true;
        ServerPlayer operator = level.getServer().getPlayerList().getPlayer(tag.getUUID(OPERATOR));
        String block = BuiltInRegistries.BLOCK.getKey(station.getBlockState().getBlock()).toString();
        if (operator == null) return GateService.denyOfflineProcess(block, recipe, level.getServer(), tag.getUUID(OPERATOR));
        return GateService.denyProcess(block, recipe, operator, false);
    }

    /** Credit real production to its assigned operator, never merely taking a donated output stack. */
    public static void produced(BlockEntity station, RecipeHolder<?> recipe) {
        if (recipe == null || !(station.getLevel() instanceof ServerLevel level)
                || !ServerSessionPolicy.isActive(level.getServer())) return;
        var tag = station.getPersistentData();
        if (!tag.hasUUID(OPERATOR)) return;
        ServerPlayer operator = level.getServer().getPlayerList().getPlayer(tag.getUUID(OPERATOR));
        if (operator == null) return; // Never credit a different player who later takes the output.
        var output = recipe.value().getResultItem(level.registryAccess());
        if (output.isEmpty()) return;
        String id = BuiltInRegistries.ITEM.getKey(output.getItem()).toString();
        ServerSync.progress(operator, ProgressService.recordAction(operator, "smelt_item", id, output.getCount()));
    }
}
