package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.GateService;
import com.historiamc.townyeras.server.ProgressService;
import com.historiamc.townyeras.server.ServerSync;
import com.historiamc.townyeras.server.ServerSessionPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity", remap = false)
public abstract class CuttingBoardProcessMixin {
    @Unique private String townyeras$successfulRecipe = "";

    @Inject(method = "processStoredItemUsingTool", at = @At("HEAD"))
    private void townyeras$start(ItemStack tool, Player player, CallbackInfoReturnable<Boolean> callback) {
        townyeras$successfulRecipe = "";
    }

    @Inject(method = "getMatchingRecipe", at = @At("RETURN"), cancellable = true)
    private void townyeras$verify(ItemStack tool, Player player, CallbackInfoReturnable<Optional<RecipeHolder<?>>> callback) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && ServerSessionPolicy.isActive(level.getServer()) && callback.getReturnValue().isPresent()) {
            RecipeHolder<?> recipe = callback.getReturnValue().get();
            if (!(player instanceof ServerPlayer operator) || GateService.denyProcess("farmersdelight:cutting_board", recipe, operator, true)) {
                callback.setReturnValue(Optional.empty());
            } else townyeras$successfulRecipe = recipe.id().toString();
        }
    }

    @Inject(method = "processStoredItemUsingTool", at = @At("RETURN"))
    private void townyeras$count(ItemStack tool, Player player, CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue()) && player instanceof ServerPlayer operator
                && !townyeras$successfulRecipe.isEmpty()) {
            ServerSync.progress(operator, ProgressService.recordAction(operator, "butcher_raw_input", townyeras$successfulRecipe, 1));
            if (townyeras$successfulRecipe.equals("farmersdelight:cutting/mutton"))
                ServerSync.progress(operator, ProgressService.recordAction(operator, "butcher_mutton_chops", "farmersdelight:mutton_chops", 1));
        }
        townyeras$successfulRecipe = "";
    }
}
