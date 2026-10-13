package com.historiamc.townyeras.server;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProgressActionPolicyTest {
    @Test void unverifiedAcquisitionsNeverCount() {
        for (String requirement : List.of("obtain_item", "source_obtain_item", "animal_obtain_item",
                "harvest_obtain_item", "loot_obtain_item", "fiber_gathered", "craft_item")) {
            assertFalse(ProgressActionPolicy.matches(requirement, "obtain_item", List.of("minecraft:leather")));
            assertFalse(ProgressActionPolicy.matches(requirement, "fiber_gathered", List.of("minecraft:string")));
        }
    }
    @Test void personalGatheringAndProductionSatisfyGenericCollection() {
        for (String event : List.of("source_obtain_item", "loot_obtain_item", "craft_item", "smelt_item", "produce_bonemeal"))
            assertTrue(ProgressActionPolicy.matches("obtain_item", event, List.of("minecraft:coal")));
    }
    @Test void narrowerObjectivesRemainStrict() {
        assertFalse(ProgressActionPolicy.matches("animal_obtain_item", "craft_item", List.of("minecraft:white_wool")));
        assertFalse(ProgressActionPolicy.matches("harvest_obtain_item", "loot_obtain_item", List.of("minecraft:wheat")));
        assertFalse(ProgressActionPolicy.matches("source_obtain_item", "loot_obtain_item", List.of("minecraft:leather")));
        assertTrue(ProgressActionPolicy.matches("source_obtain_item", "loot_obtain_item", List.of("minecraft:string")));
        assertTrue(ProgressActionPolicy.matches("animal_obtain_item", "animal_obtain_item", List.of("minecraft:leather")));
    }
}
