package com.historiamc.townyeras.tech;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ReviewedAncientTreeTest {
    private final TechRegistry tree = TechRegistry.load();
    @Test void farmingCartsUnlockCraftingNotPublicOperation() {
        assertTrue(tree.node("farming_carts").unlockActions().isEmpty());
        assertTrue(tree.node("farming_carts").unlockRecipes().contains("astikorcartsredux:oak_plow"));
        assertTrue(tree.node("farming_carts").unlockRecipes().contains("astikorcartsredux:oak_animal_cart"));
    }

    @Test void reviewedAncientBranchesRemainPresentWithClassicalExpansion() {
        assertEquals(77, tree.nodes().size());
        assertEquals(51, tree.nodes().values().stream().filter(n -> n.era().equals("ancient")).count());
        assertEquals(26, tree.nodes().values().stream().filter(n -> n.era().equals("classical")).count());
        assertEquals("Farming Carts", tree.node("farming_carts").displayName());
        assertEquals("Rain Collector", tree.node("water_treatment").displayName());
        assertEquals("Boatbuilding", tree.node("early_seafaring").displayName());
    }

    @Test void sharedEnclosuresUseOneIdentityAndEitherEligibleRoute() {
        var node = tree.node("early_enclosures");
        assertTrue(node.classEligible(List.of("carpenter", "mason")));
        assertFalse(node.classEligible(List.of("chef", "mason")));
        assertEquals(List.of("woodworking"), node.effectivePrerequisites(List.of("carpenter"), id -> false));
        var pair = List.of("farmer", "carpenter");
        assertEquals(List.of("early_agriculture"), node.effectivePrerequisites(pair, "early_agriculture"::equals));
        assertEquals("farmer", node.visibleClass(pair, "early_agriculture"::equals));
        assertFalse(node.dependsOnRevoked(pair, Set.of("woodworking")));
        assertTrue(node.dependsOnRevoked(pair, Set.of("early_agriculture"), "early_agriculture"::equals));
        assertFalse(node.dependsOnRevoked(pair, Set.of("early_agriculture"), id -> true));
        assertTrue(node.dependsOnRevoked(pair, Set.of("woodworking", "early_agriculture")));
        assertTrue(node.dependsOnRevoked(List.of("carpenter"), Set.of("woodworking")));
    }

    @Test void approvedUnlocksAndProcessingAreExact() {
        assertTrue(tree.node("craftsmanship").unlockRecipes().contains("minecraft:chest"));
        assertTrue(tree.node("craftsmanship").unlockRecipes().contains("moredelight:wooden_knife"));
        assertTrue(tree.node("stone_tools").unlockRecipes().contains("moredelight:stone_knife"));
        assertTrue(tree.node("ropecraft").unlocks().processRecipes().contains("townyeras:straw_from_wheat"));
        assertTrue(tree.node("textiles").unlockRecipes().contains("brewinandchewin:coaster"));
        assertFalse(tree.node("farming_carts").unlockRecipes().contains("astikorcartsredux:wheel"));
        assertTrue(tree.node("the_wheel").unlockRecipes().contains("astikorcartsredux:wheel"));
        assertEquals(List.of("stone_tools"), tree.node("leather_canteen").prerequisites());
        assertTrue(tree.node("firemaking").prerequisites().contains("leather_canteen"));
    }

    @Test void carpenterAdvancesThroughActualConstructionPrerequisites() {
        assertEquals(List.of("woodworking"), tree.node("joinery").prerequisites());
        assertEquals(List.of("joinery"), tree.node("doors").prerequisites());
        assertEquals(List.of("doors"), tree.node("ancient_storage").prerequisites());
        assertEquals(List.of("ancient_storage"), tree.node("the_wheel").prerequisites());
        assertEquals(List.of("the_wheel"), tree.node("carts_wagons").prerequisites());
        assertEquals(List.of("doors"), tree.node("chutes_ladders").prerequisites());
        assertEquals(List.of("joinery"), tree.node("archery").prerequisites());
    }

    @Test void personalObjectivesDoNotBecomeGenericPickupRequirements() {
        assertTrue(tree.matches("minecraft:wheat_seeds", "#reviewed_crop_items"));
        assertTrue(tree.matches("minecraft:potato", "#reviewed_crop_items"));
        assertTrue(tree.node("early_agriculture").requirements().stream().anyMatch(r -> r.action().equals("harvest_obtain_item")));
        assertTrue(tree.node("animal_husbandry").requirements().stream().allMatch(r -> r.action().equals("animal_obtain_item")));
        assertTrue(tree.node("angling").requirements().stream().anyMatch(r -> r.action().equals("distinct_fish") && r.amount() == 8));
        assertTrue(tree.node("early_seafaring").requirements().stream().anyMatch(r -> r.action().equals("travel_water_personal") && r.amount() == 2500));
        assertTrue(tree.node("sailmaking").requirements().stream().anyMatch(r -> r.action().equals("travel_water_driver") && r.amount() == 5000));
        assertTrue(tree.node("foraging_i").requirements().stream().anyMatch(r -> r.action().equals("source_mine_block") && r.amount() == 250));
    }
}
