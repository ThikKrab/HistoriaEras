package com.historiamc.townyeras.tech;

import java.util.List;
import java.util.Objects;

/** Recipe matchers and named actions granted by a completed technology node. */
public record TechUnlocks(List<String> recipes, List<String> actions, List<String> processRecipes) {
    public TechUnlocks {
        recipes = Objects.requireNonNull(recipes, "recipes").stream()
                .filter(recipe -> !PublicUtilityPolicy.publicLadder(recipe)).toList();
        actions = Objects.requireNonNull(actions, "actions").stream()
                .filter(action -> !PublicUtilityPolicy.RETIRED_ACTIONS.contains(action)).toList();
        processRecipes = processRecipes == null ? List.of() : List.copyOf(processRecipes);
    }

    public TechUnlocks(List<String> recipes, List<String> actions) {
        this(recipes, actions, List.of());
    }
}
