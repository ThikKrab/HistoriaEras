package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.NodeProgressEvents;
import com.historiamc.townyeras.server.ServerSessionPolicy;
import com.historiamc.townyeras.tech.TechnologyManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Proves eggs were spawned by an animal, rather than trusting item names or proximity. */
@Mixin(Entity.class)
public abstract class NaturalAnimalDropMixin {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"))
    private void townyeras$naturalDrop(ItemStack stack, float offset, CallbackInfoReturnable<ItemEntity> callback) {
        Entity self = (Entity) (Object) this;
        ItemEntity drop = callback.getReturnValue();
        if (drop != null && self instanceof Animal && self.level() instanceof ServerLevel level
                && ServerSessionPolicy.isActive(level.getServer())
                && TechnologyManager.current().matches(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "#reviewed_eggs")) {
            drop.getPersistentData().putString(NodeProgressEvents.SOURCE_KIND, "passive_egg");
        }
    }
}
