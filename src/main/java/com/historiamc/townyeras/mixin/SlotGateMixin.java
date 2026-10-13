package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.GateService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops gated output at the pickup boundary. Inputs remain untouched, including shift-clicks.
 */
@Mixin(Slot.class)
public abstract class SlotGateMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void townyeras$gateOutput(Player player, CallbackInfoReturnable<Boolean> callback) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Slot slot = (Slot) (Object) this;
        if (serverPlayer.containerMenu instanceof net.minecraft.world.inventory.StonecutterMenu menu
                && slot == menu.getSlot(1) && !slot.getItem().isEmpty()
                && GateService.denyCraftOutput(BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString(), serverPlayer, true)) {
            callback.setReturnValue(false);
            return;
        }
        if (serverPlayer.containerMenu instanceof net.minecraft.world.inventory.LoomMenu loom
                && slot == loom.getResultSlot() && !slot.getItem().isEmpty()
                && GateService.denyLoomOutput(serverPlayer, slot.getItem())) {
            callback.setReturnValue(false);
            return;
        }
        if (slot instanceof ResultSlot) {
            ItemStack result = slot.getItem();
            if (!result.isEmpty() && GateService.denyCraftOutput(
                    BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),
                    serverPlayer,
                    true
            )) {
                callback.setReturnValue(false);
            }
            return;
        }
        if (slot instanceof FurnaceResultSlot && slot.container instanceof AbstractFurnaceBlockEntity furnace) {
            if (!slot.getItem().isEmpty() && GateService.denyOutput(
                    BuiltInRegistries.BLOCK.getKey(furnace.getBlockState().getBlock()).toString(),
                    BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString(),
                    serverPlayer,
                    true
            )) {
                callback.setReturnValue(false);
            }
            return;
        }
        String slotKind = slot.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if ((slotKind.contains("result") || slotKind.contains("output"))
                && slot.container instanceof ResultContainer
                && !slot.getItem().isEmpty()
                && GateService.denyCraftOutput(
                        BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString(),
                        serverPlayer,
                        true
                )) {
            callback.setReturnValue(false);
            return;
        }
        if ((slotKind.contains("result") || slotKind.contains("output"))
                && slot.container instanceof BlockEntity blockEntity
                && !slot.getItem().isEmpty()
                && GateService.denyOutput(
                        BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()).toString(),
                        BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString(),
                        serverPlayer,
                        true
                )) {
            callback.setReturnValue(false);
        }
    }
}
