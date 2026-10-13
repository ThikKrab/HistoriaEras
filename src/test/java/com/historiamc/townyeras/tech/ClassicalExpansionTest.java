package com.historiamc.townyeras.tech;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ClassicalExpansionTest {
    private final TechRegistry tree = TechRegistry.load();

    @Test void nineteenAdditionsAreValidAndHaveNoFutureMachinery() {
        var additions = tree.nodes().values().stream().filter(n -> n.id().startsWith("classical_")).toList();
        assertEquals(19, additions.size());
        for (var node : additions) {
            assertEquals("classical", node.era());
            assertEquals(3, node.requirements().size());
            assertTrue(node.unlockActions().isEmpty(), node.id());
            assertFalse(node.unlockRecipes().stream().anyMatch(id -> id.startsWith("create:")), node.id());
            node.prerequisites().forEach(id -> assertNotNull(tree.node(id), id));
        }
    }
    @Test void yieldBonusHasExactProbabilityBoundaryAndDoesNotDuplicateSeeds() {
        assertFalse(CultivatedYieldPolicy.bonus(false, 0));
        assertTrue(CultivatedYieldPolicy.bonus(true, 0));
        assertTrue(CultivatedYieldPolicy.bonus(true, 0.04999F));
        assertFalse(CultivatedYieldPolicy.bonus(true, 0.05F));
        assertFalse(CultivatedYieldPolicy.bonus(true, Float.NaN));
        assertTrue(CultivatedYieldPolicy.eligible(tree, "minecraft:wheat"));
        assertTrue(CultivatedYieldPolicy.eligible(tree, "minecraft:carrot"));
        assertFalse(CultivatedYieldPolicy.eligible(tree, "minecraft:wheat_seeds"));
        assertFalse(CultivatedYieldPolicy.eligible(tree, "farmersdelight:tomato_seeds"));
        assertFalse(CultivatedYieldPolicy.eligible(tree, "minecraft:stone"));
        assertTrue(tree.node("stonecutting").unlocks().processRecipes().contains("minecraft:stone"));
        assertTrue(tree.node("classical_roadworks").unlocks().processRecipes().contains("minecraft:smooth_stone"));
    }
    @Test void pastEraResearchRemainsAvailableButFutureAndUnknownDoNot() {
        assertTrue(ResearchEraPolicy.available(tree.eras(), "classical", "ancient"));
        assertTrue(ResearchEraPolicy.available(tree.eras(), "classical", "classical"));
        assertFalse(ResearchEraPolicy.available(tree.eras(), "ancient", "classical"));
        assertFalse(ResearchEraPolicy.available(tree.eras(), "unknown", "ancient"));
        assertFalse(ResearchEraPolicy.available(tree.eras(), "classical", "unknown"));
    }
    @Test void narrowProductionGatesAllowOwnedTechnologyAndNotEarlierWhitelists() {
        assertFalse(RecipeOutputPolicy.allowed(tree, "farmersdelight:smoked_ham", Set.of("basic_butchery")::contains));
        assertTrue(RecipeOutputPolicy.allowed(tree, "farmersdelight:smoked_ham", "classical_meat_preservation"::equals));
        assertTrue(RecipeOutputPolicy.allowed(tree, "farmersdelight:ham", id -> false));
        assertFalse(RecipeOutputPolicy.allowed(tree, "minecraft:glass_pane", "glassmaking"::equals));
        assertTrue(RecipeOutputPolicy.allowed(tree, "minecraft:glass_pane", "classical_window_glass"::equals));
        assertFalse(RecipeOutputPolicy.allowed(tree, "minecraft:iron_axe", "iron_smelting"::equals));
        assertTrue(RecipeOutputPolicy.allowed(tree, "minecraft:iron_axe", "classical_iron_tools"::equals));
        assertFalse(RecipeOutputPolicy.allowed(tree, "mcwbridges:stone_brick_bridge", "stonecutting"::equals));
        assertTrue(RecipeOutputPolicy.allowed(tree, "mcwbridges:stone_brick_bridge", "classical_stone_bridges"::equals));
    }
    @Test void correctedFeastsAndPlainGlassAreExact() {
        assertEquals(List.of("farmersdelight:roast_chicken_block", "farmersdelight:grilled_salmon",
                "farmersdelight:stuffed_pumpkin_block", "farmersdelight:honey_glazed_ham_block"),
                tree.node("classical_banquet").unlockRecipes());
        assertEquals(List.of("minecraft:glass_pane"), tree.node("classical_window_glass").unlockRecipes());
        assertEquals(List.of("minecraft:iron_helmet", "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots"),
                tree.node("classical_iron_armor").unlockRecipes());
    }
    @Test void signalsUseOneNodeWithTwoAlternativeClassRoutes() {
        var signals = tree.node("classical_signals");
        assertEquals(List.of("classical_chartmaking"), signals.effectivePrerequisites(List.of("mariner"), id -> false));
        assertEquals(List.of("classical_forged_utilities"), signals.effectivePrerequisites(List.of("blacksmith"), id -> false));
        assertFalse(signals.classEligible(List.of("farmer", "chef")));
    }
    @Test void earlierTechnologiesCannotBypassAnyNewOutput() {
        var earlier = tree.nodes().values().stream().filter(n -> !n.id().startsWith("classical_"))
                .map(TechNode::id).collect(java.util.stream.Collectors.toSet());
        tree.nodes().values().stream().filter(n -> n.id().startsWith("classical_")).forEach(node ->
                node.unlockRecipes().forEach(output -> {
                    assertFalse(RecipeOutputPolicy.allowed(tree, output, earlier::contains), output);
                    assertTrue(RecipeOutputPolicy.allowed(tree, output, node.id()::equals), output);
                }));
    }
}
