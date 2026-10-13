package com.historiamc.townyeras.tech;

import com.historiamc.townyeras.HistoriaEras;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** One process-wide, atomically replaceable technology definition set. */
public final class TechnologyManager {
    private static final AtomicReference<TechRegistry> ACTIVE = new AtomicReference<>(TechRegistry.load());
    private static volatile String revision = revision(ACTIVE.get().toNormalizedJson());

    private TechnologyManager() {
    }

    public static TechRegistry current() {
        return ACTIVE.get();
    }

    public static String revision() {
        return revision;
    }

    public static synchronized void install(TechRegistry registry) {
        installInternal(registry);
        HistoriaEras.onTechnologyChanged(registry);
    }

    public static synchronized void installFromNetwork(TechRegistry registry) {
        installInternal(registry);
    }

    /** Discards server-published definitions when the client session ends. */
    public static synchronized void restoreBundledForClient() {
        installInternal(TechRegistry.load());
    }

    private static void installInternal(TechRegistry registry) {
        TechRegistry next = Objects.requireNonNull(registry, "registry");
        ACTIVE.set(next);
        revision = revision(next.toNormalizedJson());
    }

    private static String revision(String json) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
