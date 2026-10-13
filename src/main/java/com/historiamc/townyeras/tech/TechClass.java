package com.historiamc.townyeras.tech;

import java.util.Objects;

/** Immutable metadata for an enabled HistoriaEras class. */
public record TechClass(
        String id,
        String displayName,
        boolean enabled,
        String icon,
        String description,
        String unavailableMessage
) {
    public TechClass {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(icon, "icon");
        description = description == null ? "" : description;
        Objects.requireNonNull(unavailableMessage, "unavailableMessage");
    }
}
