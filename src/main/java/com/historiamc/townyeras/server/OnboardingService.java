package com.historiamc.townyeras.server;

import com.historiamc.townyeras.HistoriaEras;
import com.historiamc.townyeras.network.GettingStartedReminderPayload;
import com.historiamc.townyeras.network.OnboardingPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.HashMap;
import java.util.UUID;

@EventBusSubscriber(modid = HistoriaEras.MOD_ID)
public final class OnboardingService {
    private static final HashMap<UUID, Long> LAST_SAMPLE = new HashMap<>();
    private OnboardingService() {}

    public static void receive(ServerPlayer player, OnboardingPayload payload) {
        if (!ServerSessionPolicy.isActive(player.server)) return;
        var data = HistoriaErasSavedData.get(player.server);
        var state = data.player(player.getUUID()).onboarding();
        if ((!payload.treeViewed() || state.treeViewed()) && (!payload.tutorialDismissed() || state.tutorialDismissed())) return;
        // Monotonic acknowledgments affect only the sender's onboarding, never research/admin state.
        if (payload.treeViewed()) state.markTreeViewed();
        if (payload.tutorialDismissed()) state.dismissTutorial();
        data.setDirty();
        ServerSync.sync(player);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ServerSessionPolicy.isActive(event.getServer())) return;
        long now = System.nanoTime();
        for (var player : event.getServer().getPlayerList().getPlayers()) sample(player, now, true);
    }
    private static void sample(ServerPlayer player, long now, boolean notify) {
        Long previous = LAST_SAMPLE.putIfAbsent(player.getUUID(), now);
        if (previous == null || now - previous < 1_000_000_000L) return;
        LAST_SAMPLE.put(player.getUUID(), now);
        var data = HistoriaErasSavedData.get(player.server);
        var state = data.player(player.getUUID()).onboarding();
        if (state.treeViewed()) return;
        boolean due = state.advance((now - previous) / 1_000_000L);
        data.setDirty();
        if (due && notify && NetworkRegistry.hasChannel(player.connection, GettingStartedReminderPayload.TYPE.id()))
            PacketDistributor.sendToPlayer(player, new GettingStartedReminderPayload(true));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ServerSessionPolicy.isActive(player.server))
            sample(player, System.nanoTime(), false);
        LAST_SAMPLE.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LAST_SAMPLE.clear(); }
}
