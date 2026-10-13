package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.ItemAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Credit actual full-hive harvesting, not donated items or inventory movement. */
@Mixin(BeehiveBlock.class)
public abstract class BeehiveHarvestMixin {
    @Unique private static final ThreadLocal<String> townyeras$harvest = new ThreadLocal<>();

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void townyeras$beforeHarvest(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit,
            CallbackInfoReturnable<ItemInteractionResult> callback) {
        townyeras$harvest.remove();
        if (!(player instanceof ServerPlayer serverPlayer) || !ServerSessionPolicy.isActive(serverPlayer.server)
                || state.getValue(BeehiveBlock.HONEY_LEVEL) < 5) return;
        String output = stack.canPerformAction(ItemAbilities.SHEARS_HARVEST) ? "minecraft:honeycomb"
                : stack.is(Items.GLASS_BOTTLE) ? "minecraft:honey_bottle" : "";
        if (output.isEmpty()) return;
        if (!AdminModeService.isEnabled(serverPlayer) && !ProgressService.hasNodeAccess(serverPlayer, "apiculture")) {
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("HistoriaEras: Unlock Apiculture first."), true);
            callback.setReturnValue(ItemInteractionResult.FAIL);
            return;
        }
        townyeras$harvest.set(output);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void townyeras$afterHarvest(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit,
            CallbackInfoReturnable<ItemInteractionResult> callback) {
        String output = townyeras$harvest.get();
        townyeras$harvest.remove();
        BlockState after = level.getBlockState(pos);
        if (output == null || !(player instanceof ServerPlayer serverPlayer)
                || !after.hasProperty(BeehiveBlock.HONEY_LEVEL) || after.getValue(BeehiveBlock.HONEY_LEVEL) != 0) return;
        var update = ProgressService.recordAction(serverPlayer, "bee_harvest", output, output.endsWith("honeycomb") ? 3 : 1);
        ServerSync.progress(serverPlayer, update);
    }
}
