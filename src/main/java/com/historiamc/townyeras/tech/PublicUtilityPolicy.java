package com.historiamc.townyeras.tech;

import java.util.Set;

/** Retired utility restrictions stay retired even in older website publications. */
public final class PublicUtilityPolicy {
    private PublicUtilityPolicy() {}
    public static final Set<String> RETIRED_ACTIONS = Set.of("ladder_hoist_use", "wooden_storage_use",
            "cart_operation", "wooden_boat_operation", "chart_use", "sails_are_decorative_not_boat_propulsion");
    public static final Set<String> RETIRED_GATES = Set.of("boat_place", "boat_use", "boats_entity",
            "cart_place_farmer", "cart_place_carpenter", "carts_farmer", "carts_carpenter",
            "storage_place", "charts_use");

    public static boolean publicLadder(String id) {
        return "minecraft:ladder".equals(id) || (id != null && id.startsWith("quark:") && id.endsWith("_ladder"));
    }
}
