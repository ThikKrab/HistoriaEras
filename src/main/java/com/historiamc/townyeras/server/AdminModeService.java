package com.historiamc.townyeras.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Server-authoritative, persistent safety switch for HistoriaEras operator powers. */
public final class AdminModeService {
    public static final int REQUIRED_PERMISSION_LEVEL = 3;

    private AdminModeService() {
    }

    /** Admin Mode never elevates a player who is not currently an operator. */
    public static boolean isEnabled(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return player.createCommandSourceStack().hasPermission(REQUIRED_PERMISSION_LEVEL)
                && HistoriaErasSavedData.get(player.server).player(player.getUUID()).adminMode();
    }

    /** Console/command-block sources retain access; players must explicitly enable Admin Mode. */
    public static boolean canManage(CommandSourceStack source) {
        if (!source.hasPermission(REQUIRED_PERMISSION_LEVEL)) {
            return false;
        }
        ServerPlayer player = source.getPlayer();
        return player == null || isEnabled(player);
    }

    public static boolean setEnabled(ServerPlayer player, boolean enabled) {
        Objects.requireNonNull(player, "player");
        if (!player.createCommandSourceStack().hasPermission(REQUIRED_PERMISSION_LEVEL)) {
            return false;
        }
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());
        if (progress.adminMode() != enabled) {
            progress.setAdminMode(enabled);
            data.setDirty();
        }
        return true;
    }
}
