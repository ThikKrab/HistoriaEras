package com.historiamc.townyeras.server;

import com.historiamc.townyeras.HistoriaEras;
import com.historiamc.townyeras.tech.TechRegistry;
import com.historiamc.townyeras.tech.TechnologyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Translates successful server gameplay into the original technology action names. */
@EventBusSubscriber(modid = HistoriaEras.MOD_ID)
public final class NodeProgressEvents {
    private static final String PLAYER_TOSSED_TAG = "townyeras:player_tossed";
    public static final String SOURCE_OWNER = "townyeras:source_owner";
    public static final String SOURCE_KIND = "townyeras:source_kind";
    public static final String SOURCE_BLOCK = "townyeras:source_block";
    public static final String BLOCK_CREDIT = "townyeras:block_credit";
    private static final Map<MinecraftServer, Queue<PendingComposter>> PENDING_COMPOSTERS =
            new ConcurrentHashMap<>();

    private NodeProgressEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level) {
            PlayerPlacedBlockData provenance = PlayerPlacedBlockData.get(player.server);
            if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
                multiPlace.getReplacedBlockSnapshots().forEach(snapshot -> {
                    if (isSourceSensitiveBlock(blockId(level.getBlockState(snapshot.getPos())))) {
                        provenance.markPlaced(level, snapshot.getPos());
                    }
                });
            } else if (isSourceSensitiveBlock(blockId(event.getPlacedBlock()))) {
                provenance.markPlaced(level, event.getPos());
            }

            String blockId = blockId(event.getPlacedBlock());
            record(player, "place_block", blockId, 1.0D);
            if (technology().matches(blockId, "#reviewed_crop_blocks") || technology().matches(blockId, "#crops")) {
                record(player, "plant_crop", blockId, 1.0D);
            }
            StationOperatorService.claim(level.getBlockEntity(event.getPos()), player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }

        String blockId = blockId(event.getState());
        PlayerPlacedBlockData provenance = PlayerPlacedBlockData.get(player.server);
        boolean sugarCaneFromPlacedColumn = isSugarCane(blockId)
                && sugarCaneColumnHasPlacedSource(event.getLevel(), event.getPos(), provenance);
        boolean playerPlaced = provenance.consumePlaced(event.getLevel(), event.getPos());
        if (event.isCanceled()) return;
        boolean naturalSource = !playerPlaced && !sugarCaneFromPlacedColumn;
        boolean log = technology().matches(blockId, "#logs");
        if (playerPlaced && technology().matches(blockId, "#reviewed_crop_blocks") && isMatureCrop(event.getState())
                && CultivatedYieldService.roll(player)) {
            for (ItemEntity drop : event.getDrops()) CultivatedYieldService.apply(drop.getItem());
        }

        if (!log || naturalSource) {
            record(player, "mine_block", blockId, 1.0D);
        }
        if (log && naturalSource) {
            record(player, "chop_log", blockId, 1.0D);
        }
        if ((technology().matches(blockId, "#reviewed_crop_blocks") || technology().matches(blockId, "#crops"))
                && isMatureCrop(event.getState())
                ) {
            record(player, "harvest_crop", blockId, 1.0D);
            record(player, "harvest_mature_crop", blockId, 1.0D);
        }

        if (!event.isCanceled()) {
            boolean cropHarvest = ((technology().matches(blockId, "#reviewed_crop_blocks") || technology().matches(blockId, "#crops")) && isMatureCrop(event.getState()))
                    || (naturalSource && technology().matches(blockId, "#reviewed_wild_crops"));
            boolean relevantNaturalSource = naturalSource;
            if (relevantNaturalSource || cropHarvest) {
                boolean first = true;
                for (ItemEntity drop : event.getDrops()) {
                    tagSource(drop, player, cropHarvest ? "harvest" : "block", blockId);
                    drop.getPersistentData().putBoolean(BLOCK_CREDIT, naturalSource && first);
                    first = false;
                }
            }
            if (naturalSource && technology().matches(blockId, "#reviewed_wild_crops")
                    && ProgressService.hasNodeAccess(player, "foraging_i")
                    && event.getLevel().random.nextFloat() < 0.05F) {
                for (ItemEntity drop : event.getDrops()) {
                    String id = itemId(drop.getItem());
                    if (technology().matches(id, "#reviewed_crop_items")) {
                        drop.getItem().grow(drop.getItem().getCount());
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack crafted = event.getCrafting();
        double amount = crafted.getCount();
        record(player, "craft_item", itemId(crafted), amount);
        record(player, "craft_any_item", itemId(crafted), amount);
        if (itemId(crafted).equals("minecraft:bone_meal")) {
            boolean bones = false;
            for (int i = 0; i < event.getInventory().getContainerSize(); i++) {
                String id = itemId(event.getInventory().getItem(i));
                bones |= id.equals("minecraft:bone") || id.equals("minecraft:bone_block");
            }
            if (bones) record(player, "produce_bonemeal", "minecraft:bone_meal", amount);
        }
    }

    // FurnaceProcessMixin records successful production for the actual operator.
    // Vanilla ItemSmeltedEvent fires on output extraction, even for another player's
    // old output or inserted items, so it is deliberately not a progress source.

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean caughtFish = false;
        for (ItemStack drop : event.getDrops()) {
            if (drop.isEmpty()) {
                continue;
            }
            String itemId = itemId(drop);
            if (BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString().equals("minecraft:fishing_rod")
                    || BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString().equals("minecraft:fishing_rod")) {
                record(player, "fish_caught", itemId, drop.getCount());
                record(player, "distinct_fish", itemId, 1);
            }
            recordSourceItem(player, drop);
            caughtFish |= technology().matches(itemId, "#fish");
        }
        if (caughtFish) {
            record(player, "hunt_animals_or_fish", "minecraft:fishing", 1.0D);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity target = event.getEntity();
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
        record(player, "kill_entity", entityId, 1.0D);
        if (target instanceof Animal) {
            record(player, "kill_animal", entityId, 1.0D);
        }
        if (target instanceof Animal || target instanceof AbstractFish) {
            record(player, "hunt_animals_or_fish", entityId, 1.0D);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean eligibleFood = false;
        // Farmer's Delight can produce smoked ham directly from burning, knife-killed pigs.
        // Raw ham remains public; the new preservation technology protects only the cooked result.
        event.getDrops().removeIf(drop -> itemId(drop.getItem()).equals("farmersdelight:smoked_ham")
                && GateService.denyCraftOutput("farmersdelight:smoked_ham", player, true));
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            tagSource(drop, player, "animal", "");
            eligibleFood |= technology().matches(itemId(stack), "#reviewed_raw_food");
        }
        if (!event.isCanceled() && eligibleFood) record(player, "kill_food_animal", "minecraft:eligible_food_animal", 1);
    }

    @SubscribeEvent
    public static void onItemTossed(ItemTossEvent event) {
        LootSourceService.clear(event.getEntity().getItem());
        event.getEntity().getPersistentData().putBoolean(PLAYER_TOSSED_TAG, true);
        event.getEntity().getPersistentData().remove(SOURCE_KIND);
        event.getEntity().getPersistentData().remove(BLOCK_CREDIT);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemPickedUp(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack original = event.getOriginalStack();
        int pickedUp = Math.max(0, original.getCount() - event.getCurrentStack().getCount());
        if (pickedUp == 0) {
            return;
        }
        String itemId = itemId(original);
        var source = event.getItemEntity().getPersistentData();
        boolean tossed = source.getBoolean(PLAYER_TOSSED_TAG);
        boolean owned = source.hasUUID(SOURCE_OWNER) && source.getUUID(SOURCE_OWNER).equals(player.getUUID());
        String kind = source.getString(SOURCE_KIND);
        if (!tossed && (owned || kind.equals("passive_egg"))) {
            record(player, "source_obtain_item", itemId, pickedUp);
            if (kind.equals("animal") || kind.equals("passive_egg")
                    || (kind.equals("block") && technology().matches(itemId, "#reviewed_eggs")))
                record(player, "animal_obtain_item", itemId, pickedUp);
            if (kind.equals("harvest")) record(player, "harvest_obtain_item", itemId, pickedUp);
            if (source.getBoolean(BLOCK_CREDIT)) {
                record(player, "source_mine_block", source.getString(SOURCE_BLOCK), 1);
                source.putBoolean(BLOCK_CREDIT, false);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated() || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getItemAbility().equals(ItemAbilities.AXE_STRIP)) {
            record(player, "strip_log", blockId(event.getState()), 1.0D);
        } else if (event.getItemAbility().equals(ItemAbilities.HOE_TILL)) {
            BlockState finalState = event.getFinalState();
            if (finalState != null) {
                record(player, "till_soil", blockId(finalState), 1.0D);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                ) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        String blockId = blockId(state);
        if ((technology().matches(blockId, "#reviewed_crop_blocks") || technology().matches(blockId, "#crops")) && isMatureCrop(state))
            HarvestDropService.expect(player, event.getPos(), blockId);
        record(player, "interact_block", blockId, 1.0D);
        if (state.getBlock() instanceof ComposterBlock
                && state.getValue(ComposterBlock.LEVEL) == ComposterBlock.READY) {
            PENDING_COMPOSTERS
                    .computeIfAbsent(player.server, ignored -> new ConcurrentLinkedQueue<>())
                    .add(new PendingComposter(player, level, event.getPos().immutable()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTickPost(ServerTickEvent.Post event) {
        Queue<PendingComposter> pending = PENDING_COMPOSTERS.remove(event.getServer());
        if (pending == null) {
            return;
        }
        PendingComposter interaction;
        while ((interaction = pending.poll()) != null) {
            BlockState state = interaction.level().getBlockState(interaction.pos());
            if (interaction.player().server == event.getServer()
                    && state.getBlock() instanceof ComposterBlock
                    && state.getValue(ComposterBlock.LEVEL) == 0) {
                record(interaction.player(), "composter_bonemeal", "minecraft:bone_meal", 1.0D);
                record(interaction.player(), "produce_bonemeal", "minecraft:bone_meal", 1.0D);
            }
        }
    }

    private static void recordSourceItem(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty()) {
            record(player, "source_obtain_item", itemId(stack), stack.getCount());
        }
    }

    public static void tagSource(ItemEntity drop, ServerPlayer player, String kind, String blockId) {
        var tag = drop.getPersistentData();
        tag.putUUID(SOURCE_OWNER, player.getUUID());
        tag.putString(SOURCE_KIND, kind);
        tag.putString(SOURCE_BLOCK, blockId);
    }

    private static void record(ServerPlayer player, String action, String target, double amount) {
        if (amount > 0.0D) {
            ServerSync.progress(player, ProgressService.recordAction(player, action, target, amount));
        }
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static boolean isMatureCrop(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        return state.getProperties().stream()
                .filter(property -> property instanceof IntegerProperty && property.getName().equals("age"))
                .map(property -> (IntegerProperty) property)
                .max(Comparator.comparingInt(property -> state.getValue(property)))
                .map(property -> state.getValue(property).equals(
                        property.getPossibleValues().stream().max(Integer::compareTo).orElse(Integer.MAX_VALUE)))
                .orElse(true);
    }

    private static boolean isSourceSensitiveBlock(String blockId) {
        // Generic collection may target any block drop, including website-added items.
        // Keep placement provenance for every block so placed-and-rebroken materials
        // cannot masquerade as new naturally gathered resources.
        return true;
    }

    private static TechRegistry technology() {
        return TechnologyManager.current();
    }

    private static List<String> sourceBlockMatchers() {
        return technology().nodes().values().stream()
                .flatMap(node -> node.requirements().stream())
                .filter(requirement -> requirement.action().equals("source_mine_block")
                        || requirement.action().equals("chop_log"))
                .flatMap(requirement -> requirement.targets().stream())
                .distinct()
                .toList();
    }

    private static boolean isSugarCane(String blockId) {
        return blockId.equals("minecraft:sugar_cane") || blockId.endsWith(":sugar_cane");
    }

    private static boolean sugarCaneColumnHasPlacedSource(
            ServerLevel level,
            BlockPos brokenPos,
            PlayerPlacedBlockData provenance
    ) {
        if (provenance.isPlaced(level, brokenPos)) {
            return true;
        }
        BlockPos.MutableBlockPos cursor = brokenPos.mutable().move(0, -1, 0);
        while (cursor.getY() >= level.getMinBuildHeight()
                && isSugarCane(blockId(level.getBlockState(cursor)))) {
            if (provenance.isPlaced(level, cursor)) {
                return true;
            }
            cursor.move(0, -1, 0);
        }
        return false;
    }

    private record PendingComposter(ServerPlayer player, ServerLevel level, BlockPos pos) {
    }
}
