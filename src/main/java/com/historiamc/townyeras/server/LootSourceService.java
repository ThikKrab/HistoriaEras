package com.historiamc.townyeras.server;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Marks freshly generated chest loot; credit is only awarded when its opener takes it. */
public final class LootSourceService {
    private static final String OWNER = "townyeras:loot_owner";
    private static final ThreadLocal<java.util.Deque<Boolean>> GENERATING = ThreadLocal.withInitial(java.util.ArrayDeque::new);
    private LootSourceService() {}
    public static void begin(RandomizableContainer container, Player player) {
        GENERATING.get().push(player instanceof ServerPlayer serverPlayer
                && ServerSessionPolicy.isActive(serverPlayer.server) && container.getLootTable() != null);
    }
    public static void end(RandomizableContainer container, Player player) {
        var stack = GENERATING.get();
        boolean generated = !stack.isEmpty() && stack.pop();
        if (stack.isEmpty()) GENERATING.remove();
        if (!generated || !(player instanceof ServerPlayer opener)) return;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack item = container.getItem(slot);
            if (!item.isEmpty()) CustomData.update(DataComponents.CUSTOM_DATA, item, tag -> tag.putUUID(OWNER, opener.getUUID()));
        }
        container.setChanged();
    }
    public static void acquired(ServerPlayer player, ItemStack item) {
        CustomData custom = item.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return;
        var data = custom.copyTag();
        if (!data.hasUUID(OWNER)) return;
        boolean owner = data.getUUID(OWNER).equals(player.getUUID());
        clear(item);
        if (!owner) return;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        ServerSync.progress(player, ProgressService.recordAction(player, "loot_obtain_item", id, item.getCount()));
        // One acquisition event only; the policy also matches Cordage's string-from-loot rule.
    }
    public static void clear(ItemStack item) {
        if (item.get(DataComponents.CUSTOM_DATA) == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, item, tag -> tag.remove(OWNER));
    }
}
