package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.HarvestDropService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class HarvestResourceMixin {
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private static void townyeras$begin(Level level, BlockPos pos, ItemStack stack, CallbackInfo callback) {
        HarvestDropService.begin(level, pos);
    }
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private static void townyeras$end(Level level, BlockPos pos, ItemStack stack, CallbackInfo callback) {
        HarvestDropService.end();
    }
}
