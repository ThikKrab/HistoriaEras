package com.historiamc.townyeras.client.data;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class TechnologyPresentationTest {
    @Test void wildCropYieldHasPercentageLabel() {
        assertEquals("5% Increase in Wild Crop Yields", TechnologyPresentation.readable("wild_seed_yield_5"));
    }
    @Test void campfireCookingHasSimpleDisplayNameWithoutChangingPermissionId() {
        var entry = TechnologyPresentation.groups(List.of("Action: campfire_cooking_whitelist"))
                .getFirst().entries().getFirst();
        assertEquals("Campfire Cooking", entry.name());
        assertEquals("campfire_cooking_whitelist", entry.id());
    }
    @Test void strictObjectivesHavePlainLabels() {
        assertEquals("Collect raw meat", TechnologyPresentation.objectiveLabel("Personally collect direct animal raw-meat drops"));
        assertEquals("Collect wool", TechnologyPresentation.objectiveLabel("Personally collect natural wool drops"));
        assertEquals("Collect eggs", TechnologyPresentation.objectiveLabel("Naturally collect eligible eggs"));
        assertEquals("Craft leather canteens", TechnologyPresentation.objectiveLabel("Personally craft toughasnails:empty_leather_canteen"));
        assertEquals("Collect string", TechnologyPresentation.objectiveLabel("Obtain minecraft:string (trade permitted)"));
        assertEquals("Craft planks", TechnologyPresentation.objectiveLabel("Personally craft generic planks"));
    }
    @Test void groupingPreservesEveryExactIdAndType() {
        var entries = List.of("Recipe: minecraft:chest", "Recipe: biomesoplenty:fir_stairs",
                "Recipe: mod:recipes/special_output", "Action: crafting_table_use", "Process: mod:stew");
        var groups = TechnologyPresentation.groups(entries);
        var flattened = groups.stream().flatMap(g -> g.entries().stream())
                .map(e -> e.kind() + ": " + e.id()).collect(Collectors.toSet());
        assertEquals(Set.copyOf(entries), flattened);
        assertEquals(entries.size(), groups.stream().mapToInt(g -> g.entries().size()).sum());
        assertTrue(groups.stream().anyMatch(g -> g.name().equals("Permitted interactions")));
        assertTrue(groups.stream().anyMatch(g -> g.name().equals("Station processes")));
    }
    @Test void searchAcceptsReadableNamesAndExactIds() {
        assertTrue(TechnologyPresentation.matches("Craftsmanship", List.of("Recipe: minecraft:chest"), "CRAFT"));
        assertTrue(TechnologyPresentation.matches("Craftsmanship", List.of("Recipe: minecraft:oak_stairs"), "oak stairs"));
        assertTrue(TechnologyPresentation.matches("Craftsmanship", List.of("Recipe: minecraft:oak_stairs"), "minecraft:oak"));
        assertFalse(TechnologyPresentation.matches("Craftsmanship", List.of("Recipe: minecraft:chest"), "copper"));
    }
    @Test void duplicateRecipesAreNotSilentlyRemoved() {
        var groups = TechnologyPresentation.groups(List.of("Recipe: minecraft:chest", "Recipe: minecraft:chest"));
        assertEquals(2, groups.getFirst().entries().size());
    }
}
