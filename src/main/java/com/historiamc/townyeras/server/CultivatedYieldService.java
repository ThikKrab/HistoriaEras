package com.historiamc.townyeras.server;

import com.historiamc.townyeras.tech.TechnologyManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The cultivated crop bonus is separate from wild Foraging and never duplicates seeds. */
final class CultivatedYieldService {
    private CultivatedYieldService() {}
    static boolean roll(ServerPlayer player) {
        return ServerSessionPolicy.isActive(player.server) && com.historiamc.townyeras.tech.CultivatedYieldPolicy.bonus(
                ProgressService.hasNodeAccess(player, "yield_i"), player.serverLevel().random.nextFloat());
    }
    static void apply(ItemStack output) {
        String id = BuiltInRegistries.ITEM.getKey(output.getItem()).toString();
        var tree = TechnologyManager.current();
        if (com.historiamc.townyeras.tech.CultivatedYieldPolicy.eligible(tree, id)) output.grow(output.getCount());
    }
}
