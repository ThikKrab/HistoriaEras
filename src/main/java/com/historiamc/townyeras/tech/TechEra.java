package com.historiamc.townyeras.tech;

import java.util.Objects;

/** Immutable historical era boundary and calendar rate configuration. */
public record TechEra(
        String id,
        String displayName,
        TechDate startDate,
        TechDate endDate,
        double calendarDaysPerRealDay
) {
    public TechEra {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
    }
}
