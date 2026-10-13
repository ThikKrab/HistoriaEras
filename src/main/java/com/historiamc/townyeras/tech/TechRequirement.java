package com.historiamc.townyeras.tech;

import java.util.List;
import java.util.Objects;

/** A single, immutable progress requirement for a technology node. */
public record TechRequirement(
        String id,
        String action,
        String display,
        double amount,
        List<String> targets
) {
    public TechRequirement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(display, "display");
        display = display.replace(" (trade permitted)", "");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }
}
