package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.GateService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** These vanilla shift-click methods do not themselves ask the result slot's permission. */
@Mixin({StonecutterMenu.class, CraftingMenu.class, InventoryMenu.class})
public abstract class RecipeMenuQuickMoveMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void townyeras$protectQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> callback) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        int result = menu instanceof StonecutterMenu ? 1 : 0;
        if (index != result || !(player instanceof ServerPlayer serverPlayer)) return;
        ItemStack output = menu.getSlot(index).getItem();
        if (!output.isEmpty() && GateService.denyCraftOutput(
                BuiltInRegistries.ITEM.getKey(output.getItem()).toString(), serverPlayer, true)) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }
}
