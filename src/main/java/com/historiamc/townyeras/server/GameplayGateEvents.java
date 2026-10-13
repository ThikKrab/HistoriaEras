package com.historiamc.townyeras.server;

import com.historiamc.townyeras.tech.TechnologyManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Cancellable, server-authoritative workstation, item-use, and action gates. */
public final class GameplayGateEvents {
    private GameplayGateEvents() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = event.getItemStack();
        String blockId = BuiltInRegistries.BLOCK.getKey(
                event.getLevel().getBlockState(event.getPos()).getBlock()
        ).toString();
        if (ServerSessionPolicy.isActive(player.server) && !AdminModeService.isEnabled(player)
                && held.getItem() instanceof BlockItem cropItem
                && TechnologyManager.current().matches(BuiltInRegistries.BLOCK.getKey(cropItem.getBlock()).toString(), "#reviewed_fiber_blocks")
                && !ProgressService.hasNodeAccess(player, "fiber_crops")
                && !(ProgressService.hasSelectedClass(player, "farmer") && ProgressService.prerequisitesMet(player, "fiber_crops"))) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "HistoriaEras: Fiber Crops must be available before planting flax or cotton."), true);
            return;
        }
        boolean farmerButcheryTraining = isFarmerButcheryTraining(blockId, held, player);

        // Protect the clicked workstation before considering the held item. The old
        // block-item branch returned early and could bypass this station gate.
        if (!farmerButcheryTraining && GateService.denyStation(blockId, player, true)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        if (GateService.deny("interact", blockId, player, true)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        boolean deniedPlacement = held.getItem() instanceof BlockItem
                && GateService.deny("place", itemId(held), player, true);
        boolean deniedUse = !farmerButcheryTraining
                && !held.isEmpty()
                && GateService.deny("use_item", itemId(held), player, true);
        if (deniedPlacement || deniedUse) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        } else {
            StationOperatorService.claim(event.getLevel().getBlockEntity(event.getPos()), player);
        }
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!held.isEmpty() && GateService.deny("use_item", itemId(held), player, true)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        gateEntityItem(event, event.getItemStack());
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        gateEntityItem(event, event.getItemStack());
    }

    private static void gateEntityItem(PlayerInteractEvent event, ItemStack held) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        net.minecraft.world.entity.Entity target = event instanceof PlayerInteractEvent.EntityInteract interaction
                ? interaction.getTarget() : ((PlayerInteractEvent.EntityInteractSpecific) event).getTarget();
        boolean animalFeeding = target instanceof net.minecraft.world.entity.animal.Animal animal
                && !held.isEmpty() && animal.isFood(held)
                && ServerSessionPolicy.isActive(player.server) && !AdminModeService.isEnabled(player)
                && !ProgressService.hasNodeAccess(player, "animal_husbandry");
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
        boolean vehicleDenied = GateService.deny("entity_interact", entityId, player, true);
        if (!animalFeeding && !vehicleDenied && (held.isEmpty() || !GateService.deny("use_item", itemId(held), player, true))) {
            return;
        }
        if (animalFeeding) player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "HistoriaEras: Animal Husbandry is required to feed or breed animals."), true);
        if (event instanceof PlayerInteractEvent.EntityInteract entityInteract) {
            entityInteract.setCancellationResult(InteractionResult.FAIL);
            entityInteract.setCanceled(true);
        } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific specific) {
            specific.setCancellationResult(InteractionResult.FAIL);
            specific.setCanceled(true);
        }
    }

    public static void onBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated()
                || !event.getItemAbility().equals(ItemAbilities.AXE_STRIP)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
        if (GateService.deny("action", blockId, player, true)) {
            event.setCanceled(true);
        }
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static boolean isFarmerButcheryTraining(String blockId, ItemStack held, ServerPlayer player) {
        if (!TechnologyManager.current().matches(blockId, "#cutting_stations")
                || !ProgressService.hasSelectedClass(player, "farmer")
                || !ProgressService.prerequisitesMet(player, "shearing")
                || held.isEmpty()) {
            return false;
        }
        String heldId = itemId(held);
        return heldId.equals("minecraft:mutton") || TechnologyManager.current().matches(heldId, "#knives");
    }
}
