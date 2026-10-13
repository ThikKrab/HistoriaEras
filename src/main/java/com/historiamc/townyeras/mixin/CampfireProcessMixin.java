package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.GateService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CampfireBlockEntity.class)
public abstract class CampfireProcessMixin {
    @Inject(method = "placeFood", at = @At("HEAD"), cancellable = true)
    private void townyeras$gateFood(LivingEntity actor, ItemStack input, int time, CallbackInfoReturnable<Boolean> callback) {
        CampfireBlockEntity self = (CampfireBlockEntity) (Object) this;
        if (actor instanceof ServerPlayer player) {
            var recipe = self.getCookableRecipe(input);
            if (recipe.isPresent() && GateService.denyProcess("minecraft:campfire", recipe.get(), player, true))
                callback.setReturnValue(false);
        } else if (self.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && com.historiamc.townyeras.server.ServerSessionPolicy.isActive(level.getServer())) {
            callback.setReturnValue(false);
        }
    }
}
