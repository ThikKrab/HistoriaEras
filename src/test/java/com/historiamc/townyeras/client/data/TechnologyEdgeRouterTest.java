package com.historiamc.townyeras.client.data;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TechnologyEdgeRouterTest {
    @Test void straightChainsHaveNoExtraSegments() {
        var a = new TechnologyEdgeRouter.Point(0, 66);
        var b = new TechnologyEdgeRouter.Point(180, 66);
        assertEquals(List.of(a, b), TechnologyEdgeRouter.route(a, b, List.of()));
    }

    @Test void longConnectionsAvoidInterveningNodeAndLabel() {
        var boxes = List.of(new TechnologyEdgeRouter.Box(116, 46, 244, 146));
        var a = new TechnologyEdgeRouter.Point(0, 66);
        var b = new TechnologyEdgeRouter.Point(360, 66);
        var path = TechnologyEdgeRouter.route(a, b, boxes);
        assertEquals(a, path.getFirst());
        assertEquals(b, path.getLast());
        assertTrue(TechnologyEdgeRouter.clear(path, boxes));
        assertTrue(path.stream().anyMatch(p -> p.y() < 46 || p.y() > 146));
    }

    @Test void branchesUseGutterNotNeighboringIcons() {
        var boxes = List.of(new TechnologyEdgeRouter.Box(116, 46, 244, 146));
        var path = TechnologyEdgeRouter.route(new TechnologyEdgeRouter.Point(0, 226),
                new TechnologyEdgeRouter.Point(180, 386), boxes);
        assertTrue(TechnologyEdgeRouter.clear(path, boxes));
        assertTrue(path.stream().allMatch(p -> p.y() >= 226 && p.y() <= 386));
    }
}
