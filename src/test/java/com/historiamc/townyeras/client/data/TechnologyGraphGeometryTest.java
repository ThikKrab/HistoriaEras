package com.historiamc.townyeras.client.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TechnologyGraphGeometryTest {
    @Test void everyShippedClassAndEraHasClearEdgesAndSeparatedNodes() throws Exception {
        var nodes = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/townyeras/townyeras/technology.json"))).getAsJsonObject().getAsJsonArray("nodes");
        Map<String, List<TechnologyTreeLayout.Vertex>> branches = new TreeMap<>();
        for (var element : nodes) {
            var node = element.getAsJsonObject();
            List<String> prerequisites = new ArrayList<>();
            node.getAsJsonArray("prerequisites").forEach(p -> prerequisites.add(p.getAsString()));
            branches.computeIfAbsent(node.get("era").getAsString() + "/" + node.get("classId").getAsString(),
                    unused -> new ArrayList<>()).add(new TechnologyTreeLayout.Vertex(node.get("id").getAsString(),
                    prerequisites, branches.values().stream().mapToInt(List::size).sum()));
        }
        for (var branch : branches.entrySet()) {
            var layout = TechnologyTreeLayout.layout(branch.getValue());
            for (var a : layout.entrySet()) for (var b : layout.entrySet()) {
                if (a.getKey().equals(b.getKey())) continue;
                if (a.getValue().x() == b.getValue().x())
                    assertTrue(Math.abs(a.getValue().y() - b.getValue().y()) >= 160, branch.getKey());
            }
            for (var vertex : branch.getValue()) for (String prerequisite : vertex.prerequisites()) {
                if (!layout.containsKey(prerequisite)) continue;
                var a = layout.get(prerequisite);
                var b = layout.get(vertex.id());
                for (double zoom : List.of(0.08, 0.12, 0.20, 0.40, 0.60, 0.78, 1.0, 1.45)) {
                    int size = Math.max(8, Math.min(Math.max(21, (int) Math.round(29 * zoom)), (int) Math.floor(100 * zoom)));
                    int radius = Math.max(20, (int) Math.ceil(size / 2 / zoom) + 4);
                    var boxes = layout.entrySet().stream()
                            .filter(e -> !e.getKey().equals(prerequisite) && !e.getKey().equals(vertex.id()))
                            .map(e -> new TechnologyEdgeRouter.Box(e.getValue().x() - 64, e.getValue().y() - radius,
                                    e.getValue().x() + 64, e.getValue().y() + (zoom >= .6 ? 80 : radius))).toList();
                    var path = TechnologyEdgeRouter.route(new TechnologyEdgeRouter.Point(a.x(), a.y()),
                            new TechnologyEdgeRouter.Point(b.x(), b.y()), boxes);
                    assertTrue(TechnologyEdgeRouter.clear(path, boxes), branch.getKey() + " at " + zoom);
                }
            }
        }
    }
}
