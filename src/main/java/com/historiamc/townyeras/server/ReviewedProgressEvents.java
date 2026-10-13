package com.historiamc.townyeras.server;

import com.historiamc.townyeras.HistoriaEras;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Objectives needing successful breeding, inventory acquisitions or verified personal movement. */
@EventBusSubscriber(modid = HistoriaEras.MOD_ID)
public final class ReviewedProgressEvents {
    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();
    private static final java.util.Queue<PendingBirth> BIRTHS = new java.util.ArrayDeque<>();
    private ReviewedProgressEvents() {}

    @SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        SAMPLES.clear();
        BIRTHS.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreeding(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player) || !ServerSessionPolicy.isActive(player.server)) return;
        if (!AdminModeService.isEnabled(player) && !ProgressService.hasNodeAccess(player, "animal_husbandry")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void countBreeding(BabyEntitySpawnEvent event) {
        if (event.getChild() != null && event.getCausedByPlayer() instanceof ServerPlayer player) {
            // Verify next tick that the proposed child actually joined the level.
            var child = event.getChild();
            BIRTHS.add(new PendingBirth(player, child, event));
        }
    }

    @SubscribeEvent
    public static void teleport(EntityTeleportEvent event) {
        SAMPLES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SAMPLES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!ServerSessionPolicy.isActive(event.getServer())) return;
        PendingBirth birth;
        while ((birth = BIRTHS.poll()) != null) {
            if (!birth.event().isCanceled() && birth.child().isAddedToLevel() && !birth.child().isRemoved()) {
                record(birth.player(), "breed_animal", BuiltInRegistries.ENTITY_TYPE.getKey(birth.child().getType()).toString(), 1);
            }
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) LootSourceService.acquired(player, stack);
            }
            Sample now = new Sample(player.getX(), player.getZ(), player.level().dimension().location().toString(),
                    player.isInWater());
            Sample before = SAMPLES.put(player.getUUID(), now);
            if (before == null) continue;
            // Inventory changes, ordinary container transfers and trades never award progress.
            if (player.isSpectator() || player.isFallFlying() || player.getAbilities().flying
                    || !before.dimension().equals(now.dimension())) continue;
            double distance = Math.hypot(now.x() - before.x(), now.z() - before.z());
            // Sample every tick; reject discontinuities rather than counting teleports as travel.
            if (!Double.isFinite(distance) || distance <= 0 || distance > 2.0) continue;
            if (!player.isPassenger()) {
                record(player, "travel_personal", "minecraft:personal_movement", distance);
                if (before.water() && now.water()) {
                    record(player, "travel_water_personal", "minecraft:swimming", distance);
                    record(player, "travel_water_driver", "minecraft:swimming", distance);
                }
            } else if (player.getVehicle() instanceof Boat boat && boat.getControllingPassenger() == player
                    && boat.isInWater() && ProgressService.hasNodeAccess(player, "early_seafaring")) {
                record(player, "travel_water_driver", "minecraft:boat_driver", distance);
            }
        }
    }

    private static void record(ServerPlayer player, String action, String target, double amount) {
        ServerSync.progress(player, ProgressService.recordAction(player, action, target, amount));
    }
    private record Sample(double x, double z, String dimension, boolean water) {}
    private record PendingBirth(ServerPlayer player, net.minecraft.world.entity.AgeableMob child, BabyEntitySpawnEvent event) {}
}
