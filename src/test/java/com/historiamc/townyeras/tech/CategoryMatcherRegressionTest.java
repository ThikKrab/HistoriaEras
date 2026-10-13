package com.historiamc.townyeras.tech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryMatcherRegressionTest {
    private final TechRegistry registry = TechRegistry.load();

    @Test
    void troddenObsidianIsNotAFishingRod() {
        assertFalse(registry.matches("chipped:trodden_obsidian", "#fishing_rods"));
        assertFalse(registry.matches("chipped:trodden_crying_obsidian", "#fishing_rods"));
        assertTrue(registry.gatesFor("craft", "chipped:trodden_obsidian").stream()
                .noneMatch(gate -> gate.nodeId().equals("fishing")));
        assertTrue(registry.gatesFor("use_item", "chipped:trodden_obsidian").stream()
                .noneMatch(gate -> gate.nodeId().equals("fishing")));
    }

    @Test
    void actualFishingRodsRemainMatched() {
        assertTrue(registry.matches("minecraft:fishing_rod", "#fishing_rods"));
        assertTrue(registry.matches("aquaculture:iron_fishing_rod", "#fishing_rods"));
        assertTrue(registry.matches("examplemod:reinforced_fishing_rod_with_bobber", "#fishing_rods"));
    }
}
