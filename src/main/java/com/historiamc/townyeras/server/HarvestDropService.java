package com.historiamc.townyeras.server;

import com.historiamc.townyeras.HistoriaEras;
import com.historiamc.townyeras.tech.TechnologyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.util.HashMap;
import java.util.Map;

/** Attributes actual Block.popResource harvest drops, never nearby unrelated loose items. */
@EventBusSubscriber(modid = HistoriaEras.MOD_ID)
public final class HarvestDropService {
    private static final Map<Key, Pending> PENDING = new HashMap<>();
    private static final ThreadLocal<Pending> ACTIVE = new ThreadLocal<>();
    private HarvestDropService() {}

    @SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        PENDING.clear();
        ACTIVE.remove();
    }

    public static void expect(ServerPlayer player, BlockPos pos, String blockId) {
        ServerLevel level = player.serverLevel();
        PENDING.entrySet().removeIf(entry -> entry.getValue().player().isRemoved()
                || entry.getValue().tick() < level.getGameTime() - 1);
        boolean bonus = PlayerPlacedBlockData.get(player.server).isPlaced(level, pos)
                && TechnologyManager.current().matches(blockId, "#reviewed_crop_blocks")
                && CultivatedYieldService.roll(player);
        PENDING.put(new Key(level, pos.immutable()), new Pending(player, blockId, level.getGameTime(), bonus));
    }
    public static void begin(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        Pending pending = PENDING.get(new Key(serverLevel, pos));
        if (pending != null && pending.tick() == level.getGameTime()) ACTIVE.set(pending);
    }
    public static void end() { ACTIVE.remove(); }

    @SubscribeEvent
    public static void spawned(EntityJoinLevelEvent event) {
        Pending pending = ACTIVE.get();
        if (pending != null && event.getEntity() instanceof ItemEntity item
                && event.getLevel() == pending.player().level()) {
            NodeProgressEvents.tagSource(item, pending.player(), "harvest", pending.blockId());
            if (pending.bonus()) CultivatedYieldService.apply(item.getItem());
        }
    }
    private record Key(ServerLevel level, BlockPos pos) {}
    private record Pending(ServerPlayer player, String blockId, long tick, boolean bonus) {}
}
