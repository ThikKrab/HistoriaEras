package com.historiamc.townyeras.client.data;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TechnologyNodeVisibilityTest {
    @Test void adminGrantedCompletionRevealsItselfEvenWithoutPrerequisites() {
        assertTrue(TechnologyNodeVisibility.revealed(true, List.of("missing"), id -> false));
    }
    @Test void successorsUseAccessNotThePrerequisiteClass() {
        assertTrue(TechnologyNodeVisibility.revealed(false, List.of("agriculture"), "agriculture"::equals));
        assertFalse(TechnologyNodeVisibility.revealed(false, List.of("agriculture"), id -> false));
    }
    @Test void allMergePrerequisitesMustBeActiveAndRootsAreVisible() {
        assertFalse(TechnologyNodeVisibility.revealed(false, List.of("a", "b"), Set.of("a")::contains));
        assertTrue(TechnologyNodeVisibility.revealed(false, List.of("a", "b"), Set.of("a", "b")::contains));
        assertTrue(TechnologyNodeVisibility.revealed(false, List.of(), id -> false));
    }
}
