package com.historiamc.townyeras.client.data;

import java.util.Collection;
import java.util.function.Predicate;

/** Completed nodes are never concealed; successors use authoritative active access. */
public final class TechnologyNodeVisibility {
    private TechnologyNodeVisibility() {}
    public static <T> boolean revealed(boolean completed, Collection<T> prerequisites, Predicate<T> accessible) {
        return completed || prerequisites.stream().allMatch(accessible);
    }
}
