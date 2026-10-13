package com.historiamc.townyeras.mixin;

import com.historiamc.townyeras.server.LootSourceService;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomizableContainer.class)
public interface LootSourceMixin {
    @Inject(method = "unpackLootTable", at = @At("HEAD"))
    default void townyeras$begin(Player player, CallbackInfo callback) {
        LootSourceService.begin((RandomizableContainer) this, player);
    }
    @Inject(method = "unpackLootTable", at = @At("RETURN"))
    default void townyeras$end(Player player, CallbackInfo callback) {
        LootSourceService.end((RandomizableContainer) this, player);
    }
}
