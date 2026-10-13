package com.historiamc.townyeras.tech;

import java.util.List;
import java.util.Objects;

/** Named matcher category referenced with a leading {@code #}. */
public record TechCategory(String id, List<String> exact, List<String> patterns) {
    public TechCategory {
        Objects.requireNonNull(id, "id");
        exact = List.copyOf(Objects.requireNonNull(exact, "exact"));
        patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
    }
}
