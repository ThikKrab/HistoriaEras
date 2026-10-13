package com.historiamc.townyeras.tech;

import java.util.function.Predicate;

/** A workstation whitelist never overrides a narrower technology output gate. */
public final class RecipeOutputPolicy {
    private RecipeOutputPolicy() {}

    public static boolean allowed(TechRegistry registry, String output, Predicate<String> access) {
        if (PublicUtilityPolicy.publicLadder(output)) return true;
        boolean restricted = false;
        for (TechGate gate : registry.gatesFor("craft", output)) {
            restricted = true;
            if (access.test(gate.nodeId())) return true;
        }
        for (TechNode node : registry.nodes().values()) {
            if (node.unlockRecipes().stream().anyMatch(matcher -> registry.matches(output, matcher))) {
                restricted = true;
                if (access.test(node.id())) return true;
            }
        }
        return !restricted;
    }

    public static boolean declaredAndAccessible(TechRegistry registry, String output, Predicate<String> access) {
        return registry.nodes().values().stream().anyMatch(node -> access.test(node.id())
                && node.unlockRecipes().stream().anyMatch(matcher -> registry.matches(output, matcher)));
    }
}
