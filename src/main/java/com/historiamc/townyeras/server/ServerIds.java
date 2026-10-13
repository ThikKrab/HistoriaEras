package com.historiamc.townyeras.server;

import java.util.Locale;

final class ServerIds {
    private ServerIds() {
    }

    static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");
    }

    static String normalizeAction(String value) {
        return normalizeId(value);
    }

    static String normalizeTarget(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
