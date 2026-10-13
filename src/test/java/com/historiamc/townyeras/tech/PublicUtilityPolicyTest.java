package com.historiamc.townyeras.tech;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PublicUtilityPolicyTest {
    @Test void retiredActionsAndRulesAreAbsent() {
        var tree = TechRegistry.load();
        assertTrue(tree.nodes().values().stream().flatMap(node -> node.unlockActions().stream())
                .noneMatch(PublicUtilityPolicy.RETIRED_ACTIONS::contains));
        assertTrue(tree.gates().stream().noneMatch(g -> PublicUtilityPolicy.RETIRED_GATES.contains(g.id())));
        assertFalse(tree.stations().containsKey("wooden_storage"));
        assertTrue(tree.stations().containsKey("cutting_board"));
        assertTrue(tree.stations().containsKey("furnace"));
    }

    @Test void obsoleteWebsiteActionsCannotComeBack() {
        var unlocks = new TechUnlocks(List.of("minecraft:ladder", "quark:birch_ladder", "minecraft:barrel"),
                List.of("chart_use", "wooden_storage_use", "sails_are_decorative_not_boat_propulsion", "sail_block_dyeing"));
        assertEquals(List.of("minecraft:barrel"), unlocks.recipes());
        assertEquals(List.of("sail_block_dyeing"), unlocks.actions());
    }

    @Test void oldWebsiteRulesAreRetiredOnLoad() {
        var json = com.google.gson.JsonParser.parseString(TechRegistry.load().toNormalizedJson()).getAsJsonObject();
        for (String id : PublicUtilityPolicy.RETIRED_GATES) {
            var gate = json.getAsJsonArray("gates").get(0).getAsJsonObject().deepCopy();
            gate.addProperty("id", id);
            json.getAsJsonArray("gates").add(gate);
        }
        var station = json.getAsJsonArray("stations").get(0).getAsJsonObject().deepCopy();
        station.addProperty("id", "wooden_storage");
        json.getAsJsonArray("stations").add(station);
        var restored = TechRegistry.fromNormalizedJson(json.toString());
        assertTrue(restored.gates().stream().noneMatch(g -> PublicUtilityPolicy.RETIRED_GATES.contains(g.id())));
        assertFalse(restored.stations().containsKey("wooden_storage"));
    }

    @Test void craftingProgressionIsPreservedExceptLadders() {
        var tree = TechRegistry.load();
        assertTrue(tree.node("early_seafaring").unlockRecipes().contains("minecraft:oak_boat"));
        assertTrue(tree.node("ancient_storage").unlockRecipes().contains("minecraft:barrel"));
        assertTrue(tree.node("carts_wagons").unlockRecipes().contains("astikorcartsredux:oak_supply_cart"));
        assertTrue(tree.node("chutes_ladders").unlockRecipes().contains("minecraft:scaffolding"));
        assertTrue(tree.node("chutes_ladders").unlockRecipes().stream().noneMatch(PublicUtilityPolicy::publicLadder));
        assertFalse(PublicUtilityPolicy.publicLadder("minecraft:scaffolding"));
    }
}
