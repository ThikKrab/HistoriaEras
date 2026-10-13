package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.StationOperatorService;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity", remap = false)
public abstract class CookingPotProcessMixin {
    @Inject(method = "getMatchingRecipe", at = @At("RETURN"), cancellable = true)
    private void townyeras$verify(@Coerce Object input, CallbackInfoReturnable<Optional<RecipeHolder<?>>> callback) {
        if (callback.getReturnValue().isPresent()
                && StationOperatorService.denied((BlockEntity) (Object) this, callback.getReturnValue().get()))
            callback.setReturnValue(Optional.empty());
    }
}
