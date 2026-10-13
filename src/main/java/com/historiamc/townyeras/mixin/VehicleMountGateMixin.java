package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.GateService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class VehicleMountGateMixin {
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true)
    private void townyeras$gateMount(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerPlayer player && GateService.deny("entity_interact",
                BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString(), player, true)) callback.setReturnValue(false);
    }
}
