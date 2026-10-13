package com.historiamc.townyeras;

import com.historiamc.townyeras.network.HistoriaErasNetwork;
import com.historiamc.townyeras.server.GameplayGateEvents;
import com.historiamc.townyeras.server.GateService;
import com.historiamc.townyeras.server.ProgressService;
import com.historiamc.townyeras.server.ServerSync;
import com.historiamc.townyeras.server.ServerSessionPolicy;
import com.historiamc.townyeras.server.SereneSeasonsTimelineBridge;
import com.historiamc.townyeras.server.TimelineService;
import com.historiamc.townyeras.server.TimelineState;
import com.historiamc.townyeras.server.WebsiteTechnologySync;
import com.historiamc.townyeras.tech.TechRegistry;
import com.historiamc.townyeras.tech.TechnologyManager;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(HistoriaEras.MOD_ID)
public final class HistoriaEras {
    // Compatibility identity: existing saves, recipe IDs and server integrations depend on this.
    public static final String MOD_ID = "townyeras";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static long synchronizationTick;

    public HistoriaEras(IEventBus modEventBus) {
        TimelineState.validateModel();
        modEventBus.addListener(HistoriaErasNetwork::register);

        onTechnologyChanged(technology());
        HistoriaErasNetwork.installServerHandlers(
                ServerSync::sync,
                (player, nodeId) -> {
                    if (!ProgressService.trackNode(player, nodeId)) {
                        player.displayClientMessage(
                                Component.literal("HistoriaEras: unknown technology.")
                                        .withStyle(ChatFormatting.RED),
                                true
                        );
                    }
                    ServerSync.sync(player);
                }
        );

        NeoForge.EVENT_BUS.addListener(GameplayGateEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(GameplayGateEvents::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(GameplayGateEvents::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(GameplayGateEvents::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(GameplayGateEvents::onBlockToolModification);
        NeoForge.EVENT_BUS.addListener(HistoriaEras::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(HistoriaEras::onServerTick);

        LOGGER.info(
                "HistoriaEras loaded {} technologies, {} objectives, {} gates, and {} functional stations",
                technology().nodes().size(),
                technology().nodes().values().stream().mapToInt(node -> node.requirements().size()).sum(),
                technology().gates().size(),
                technology().stations().size()
        );
    }

    public static TechRegistry technology() {
        return TechnologyManager.current();
    }

    /** Rebinds all server services after a validated website publication is installed. */
    public static void onTechnologyChanged(TechRegistry registry) {
        GateService.configure(registry, ProgressService::hasSelectedClass, ProgressService::hasNodeAccess);
        ServerSync.configure(registry);
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ServerSessionPolicy.isActive(player.server)) {
            ServerSync.forget(player);
            SereneSeasonsTimelineBridge.sync(player.server);
            ServerSync.sync(player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!ServerSessionPolicy.isActive(event.getServer())) {
            return;
        }
        TimelineService.TimelineAdvance advance = TimelineService.tick(event.getServer());
        WebsiteTechnologySync.tick(event.getServer());
        SereneSeasonsTimelineBridge.sync(event.getServer());
        synchronizationTick++;
        if (advance.changed() || synchronizationTick % 200L == 0L) {
            ServerSync.syncAll(event.getServer());
        }
        ServerSync.flushPending(event.getServer());
    }
}
