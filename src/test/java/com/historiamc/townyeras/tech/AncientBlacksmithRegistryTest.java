package com.historiamc.townyeras.tech;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AncientBlacksmithRegistryTest {
    private final TechRegistry registry = TechRegistry.load();

    @Test
    void loadsTheRevisedBlacksmithProgression() {
        assertEquals(List.of("furnace_basic_ore"), registry.node("ore_smelting").prerequisites());
        assertEquals(List.of("ore_smelting"), registry.node("copper_working").prerequisites());
        assertEquals(List.of("copper_working"), registry.node("copper_age").prerequisites());
        assertEquals(List.of("copper_age"), registry.node("forge_construction").prerequisites());
        assertEquals(List.of("forge_construction"), registry.node("iron_smelting").prerequisites());
        assertEquals(List.of("iron_smelting"), registry.node("early_iron_links").prerequisites());
    }

    @Test
    void copperAgeUsesOnlyTheAuditedCopperEquipmentSet() {
        TechNode node = registry.node("copper_age");
        assertNotNull(node);
        assertEquals(33, node.unlockRecipes().size());
        assertTrue(node.unlockRecipes().contains("create_sa:copper_sword"));
        assertTrue(node.unlockRecipes().contains("create_sa:copper_chestplate"));
        assertTrue(node.unlockRecipes().contains("pangeaarmor:roman_copper_chestplate"));
        assertFalse(node.unlockRecipes().stream().anyMatch(recipe -> recipe.startsWith("tfmg:")));
        assertFalse(node.unlockRecipes().stream().anyMatch(recipe -> recipe.startsWith("createaddition:")));
        assertFalse(node.unlockRecipes().stream().anyMatch(recipe -> recipe.startsWith("createdieselgenerators:")));
    }

    @Test
    void furnaceAndMetalOutputsHaveServerRules() {
        assertTrue(registry.stations().values().stream()
                .anyMatch(station -> station.id().equals("furnace")
                        && station.nodeId().equals("furnace_basic_ore")));
        assertTrue(registry.gatesFor("furnace_output", "minecraft:charcoal").stream()
                .anyMatch(gate -> gate.nodeId().equals("furnace_basic_ore")));
        assertTrue(registry.gatesFor("furnace_output", "minecraft:copper_ingot").stream()
                .anyMatch(gate -> gate.nodeId().equals("ore_smelting")));
        assertTrue(registry.gatesFor("furnace_output", "minecraft:iron_ingot").stream()
                .anyMatch(gate -> gate.nodeId().equals("iron_smelting")));
    }
}
