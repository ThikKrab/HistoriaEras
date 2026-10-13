package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.NodeProgressEvents;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Do not mix verified personal drops with another player's or unverified item entities. */
@Mixin(ItemEntity.class)
public abstract class SourceItemMergeMixin {
    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
    private void townyeras$preserveSource(ItemEntity other, CallbackInfo callback) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getPersistentData().contains(NodeProgressEvents.SOURCE_KIND)
                || other.getPersistentData().contains(NodeProgressEvents.SOURCE_KIND)) callback.cancel();
    }
}
