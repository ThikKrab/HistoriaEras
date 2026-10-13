package com.historiamc.townyeras.client.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechnologyBranchVisibilityTest {
    @Test
    void ordinaryPlayersSeeOnlyUniversalAndSelectedClasses() {
        List<String> selected = List.of("farmer", "mason");

        assertTrue(TechnologyBranchVisibility.allowed(false, selected, "universal"));
        assertTrue(TechnologyBranchVisibility.allowed(false, selected, "farmer"));
        assertTrue(TechnologyBranchVisibility.allowed(false, selected, "mason"));
        assertFalse(TechnologyBranchVisibility.allowed(false, selected, "chef"));
        assertFalse(TechnologyBranchVisibility.allowed(false, selected, "blacksmith"));
    }

    @Test
    void adminModeMayInspectEveryClassBranch() {
        assertTrue(TechnologyBranchVisibility.allowed(true, List.of("farmer", "mason"), "chef"));
        assertTrue(TechnologyBranchVisibility.allowed(true, List.of(), "blacksmith"));
    }
}
