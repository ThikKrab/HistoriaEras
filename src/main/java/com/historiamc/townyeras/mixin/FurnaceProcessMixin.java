package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.StationOperatorService;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Checks before fuel or input is consumed, including hopper-driven furnaces and smokers. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceProcessMixin {
    @Inject(method = "burn", at = @At("RETURN"))
    private static void townyeras$recordProduction(RegistryAccess access, RecipeHolder<?> recipe,
                                                  NonNullList<ItemStack> items, int size, AbstractFurnaceBlockEntity furnace,
                                                  CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) StationOperatorService.produced(furnace, recipe);
    }

    @Inject(method = "canBurn", at = @At("HEAD"), cancellable = true)
    private static void townyeras$gateProcess(RegistryAccess access, RecipeHolder<?> recipe,
                                             NonNullList<ItemStack> items, int size, AbstractFurnaceBlockEntity furnace,
                                             CallbackInfoReturnable<Boolean> callback) {
        if (StationOperatorService.denied(furnace, recipe)) callback.setReturnValue(false);
    }
}
