package com.historiamc.townyeras.server;

import net.minecraft.server.MinecraftServer;

/** Determines whether this process is allowed to host an active HistoriaEras session. */
public final class ServerSessionPolicy {
    private static final boolean DEVELOPMENT_SESSION =
            Boolean.getBoolean("townyeras.prototype.visualTest")
                    || Boolean.getBoolean("townyeras.developmentSession");

    private ServerSessionPolicy() {
    }

    public static boolean isActive(MinecraftServer server) {
        return server != null && (server.isDedicatedServer() || DEVELOPMENT_SESSION);
    }
}
