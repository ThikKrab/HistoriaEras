package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.GateService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.jusipat.astikorcartsredux.entity.AbstractDrawnEntity", remap = false)
public abstract class CartPullGateMixin {
    @Inject(method = "setPulling", at = @At("HEAD"), cancellable = true)
    private void townyeras$gatePull(Entity pulling, CallbackInfo callback) {
        // Null is a detach; never prevent releasing a cart.
        ServerPlayer player = pulling instanceof ServerPlayer direct ? direct
                : pulling != null && pulling.getControllingPassenger() instanceof ServerPlayer rider ? rider : null;
        Entity cart = (Entity) (Object) this;
        if (player != null && GateService.deny("entity_interact",
                BuiltInRegistries.ENTITY_TYPE.getKey(cart.getType()).toString(), player, true)) callback.cancel();
    }
}
