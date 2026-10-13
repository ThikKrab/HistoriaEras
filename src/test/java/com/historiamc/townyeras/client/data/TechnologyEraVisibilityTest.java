package com.historiamc.townyeras.client.data;

import com.historiamc.townyeras.tech.TechDate;
import com.historiamc.townyeras.tech.TechEra;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TechnologyEraVisibilityTest {
    private final Map<String, TechEra> eras = Map.of(
            "ancient", era("ancient", -4000), "classical", era("classical", -500),
            "medieval", era("medieval", 500));

    private static TechEra era(String id, int year) {
        return new TechEra(id, id, new TechDate(year, 1, 1), new TechDate(year + 100, 1, 1), 1);
    }

    @Test void ordinaryPlayersCanBrowseCurrentAndPastOnly() {
        assertTrue(TechnologyEraVisibility.allowed(false, "classical", "ancient", eras));
        assertTrue(TechnologyEraVisibility.allowed(false, "classical", "classical", eras));
        assertFalse(TechnologyEraVisibility.allowed(false, "classical", "medieval", eras));
        assertFalse(TechnologyEraVisibility.allowed(false, "ancient", "classical", eras));
    }

    @Test void adminModeCanBrowseFutureButTurningItOffRemovesAccess() {
        assertTrue(TechnologyEraVisibility.allowed(true, "ancient", "medieval", eras));
        assertFalse(TechnologyEraVisibility.allowed(false, "ancient", "medieval", eras));
    }

    @Test void unknownErasFailClosed() {
        assertFalse(TechnologyEraVisibility.allowed(false, "missing", "ancient", eras));
        assertFalse(TechnologyEraVisibility.allowed(true, "ancient", "missing", eras));
    }
}
