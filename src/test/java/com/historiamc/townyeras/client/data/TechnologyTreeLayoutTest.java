package com.historiamc.townyeras.client.data;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class TechnologyTreeLayoutTest {
    @Test void longChainKeepsItsLaneWhenSideBranchesEnd() {
        var layout = TechnologyTreeLayout.layout(List.of(
                new TechnologyTreeLayout.Vertex("root", List.of(), 0),
                new TechnologyTreeLayout.Vertex("main", List.of("root"), 0),
                new TechnologyTreeLayout.Vertex("side", List.of("root"), 100),
                new TechnologyTreeLayout.Vertex("next", List.of("main"), 0),
                new TechnologyTreeLayout.Vertex("last", List.of("next"), 0)));
        assertEquals((layout.get("main").y() + layout.get("side").y()) / 2, layout.get("root").y());
        assertEquals(layout.get("main").y(), layout.get("next").y());
        assertEquals(layout.get("next").y(), layout.get("last").y());
        assertTrue(Math.abs(layout.get("side").y() - layout.get("main").y()) >= 120);
    }
    @Test void chainHasNoInventedBranchesAndProgressesRightward() {
        var vertices = List.of(new TechnologyTreeLayout.Vertex("a", List.of("external"), 0),
                new TechnologyTreeLayout.Vertex("b", List.of("a"), 0),
                new TechnologyTreeLayout.Vertex("c", List.of("b"), 0));
        var layout = TechnologyTreeLayout.layout(vertices);
        assertEquals(0, layout.get("a").x());
        assertEquals(180, layout.get("b").x());
        assertEquals(360, layout.get("c").x());
        assertEquals(layout.get("a").y(), layout.get("c").y());
    }
    @Test void siblingBranchesHaveDistinctSpacedLanes() {
        var vertices = List.of(new TechnologyTreeLayout.Vertex("a", List.of(), 0),
                new TechnologyTreeLayout.Vertex("b", List.of("a"), 0),
                new TechnologyTreeLayout.Vertex("c", List.of("a"), 150),
                new TechnologyTreeLayout.Vertex("d", List.of("b", "c"), 0));
        var layout = TechnologyTreeLayout.layout(vertices);
        assertEquals(vertices.size(), new HashSet<>(layout.values()).size());
        assertEquals(160, layout.get("c").y() - layout.get("b").y());
        assertEquals(360, layout.get("d").x());
        assertEquals(layout, TechnologyTreeLayout.layout(vertices.reversed()));
    }
    @Test void invalidCyclesFailInsteadOfRecursingForever() {
        assertThrows(IllegalArgumentException.class, () -> TechnologyTreeLayout.layout(List.of(
                new TechnologyTreeLayout.Vertex("a", List.of("b"), 0),
                new TechnologyTreeLayout.Vertex("b", List.of("a"), 0))));
    }
}
