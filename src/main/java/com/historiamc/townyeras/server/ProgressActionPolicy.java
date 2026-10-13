package com.historiamc.townyeras.server;

import java.util.Collection;
import java.util.Set;

/** Generic collection can only be satisfied by verified personal source/production events. */
final class ProgressActionPolicy {
    private static final Set<String> PERSONAL_ACQUISITIONS = Set.of(
            "source_obtain_item", "loot_obtain_item", "craft_item", "smelt_item", "produce_bonemeal");

    private ProgressActionPolicy() {}

    static boolean matches(String requirement, String event, Collection<String> targets) {
        // Legacy inventory-difference acquisition is never evidence of personal work.
        if (event.equals("obtain_item") || event.equals("fiber_gathered")) return false;
        if (requirement.equals("obtain_item")) return PERSONAL_ACQUISITIONS.contains(event);
        // Cordage explicitly accepts string from personally opened generated loot.
        if (requirement.equals("source_obtain_item") && event.equals("loot_obtain_item"))
            return targets.contains("minecraft:string");
        return requirement.equals(event);
    }
}
