package com.historiamc.townyeras.client.data;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic layered geometry; no progress, names or unlocks influence the hidden topology. */
public final class TechnologyTreeLayout {
    private TechnologyTreeLayout() {}
    public record Vertex(String id, List<String> prerequisites, int order) {}
    public record Position(int x, int y) {}

    public static Map<String, Position> layout(List<Vertex> vertices) {
        Map<String, Vertex> byId = new LinkedHashMap<>();
        vertices.forEach(v -> byId.put(v.id(), v));
        Map<String, Integer> depths = new HashMap<>();
        vertices.forEach(v -> depth(v.id(), byId, depths, new HashSet<>()));
        Map<String, Integer> remaining = new HashMap<>();
        vertices.stream().sorted(Comparator.comparingInt((Vertex v) -> depths.get(v.id())).reversed()).forEach(v -> {
            int height = vertices.stream().filter(child -> child.prerequisites().contains(v.id()))
                    .mapToInt(child -> remaining.getOrDefault(child.id(), 0) + 1).max().orElse(0);
            remaining.put(v.id(), height);
        });
        Comparator<Vertex> priority = Comparator.comparingInt((Vertex v) -> remaining.get(v.id())).reversed()
                .thenComparingInt(Vertex::order).thenComparing(Vertex::id);
        // A tidy spanning forest reserves a whole band for each subtree, rather than
        // independently packing columns. Additional prerequisites remain real edges.
        Map<String, List<Vertex>> children = new HashMap<>();
        List<Vertex> roots = new java.util.ArrayList<>();
        for (Vertex vertex : vertices) {
            Vertex parent = vertex.prerequisites().stream().filter(byId::containsKey).map(byId::get)
                    .sorted(Comparator.comparingInt((Vertex v) -> depths.get(v.id())).reversed()
                            .thenComparing(priority)).findFirst().orElse(null);
            if (parent == null) roots.add(vertex);
            else children.computeIfAbsent(parent.id(), unused -> new java.util.ArrayList<>()).add(vertex);
        }
        children.values().forEach(list -> list.sort(priority));
        roots.sort(priority);
        Map<String, Position> positions = new LinkedHashMap<>();
        int[] nextLane = {66};
        for (Vertex root : roots) {
            place(root, children, depths, positions, nextLane);
            nextLane[0] += 40; // Separate independent technology families.
        }
        return Map.copyOf(positions);
    }

    private static int place(Vertex vertex, Map<String, List<Vertex>> children, Map<String, Integer> depths,
                             Map<String, Position> positions, int[] nextLane) {
        List<Vertex> descendants = children.getOrDefault(vertex.id(), List.of());
        int y;
        if (descendants.isEmpty()) {
            y = nextLane[0];
            nextLane[0] += 160;
        } else {
            int first = place(descendants.getFirst(), children, depths, positions, nextLane);
            int last = first;
            for (Vertex child : descendants.subList(1, descendants.size()))
                last = place(child, children, depths, positions, nextLane);
            y = (first + last) / 2;
        }
        positions.put(vertex.id(), new Position(depths.get(vertex.id()) * 180, y));
        return y;
    }

    private static int depth(String id, Map<String, Vertex> vertices, Map<String, Integer> depths, Set<String> visiting) {
        if (depths.containsKey(id)) return depths.get(id);
        if (!visiting.add(id)) throw new IllegalArgumentException("Cyclic technology graph");
        int value = vertices.get(id).prerequisites().stream().filter(vertices::containsKey)
                .mapToInt(p -> depth(p, vertices, depths, visiting) + 1).max().orElse(0);
        visiting.remove(id);
        depths.put(id, value);
        return value;
    }
}
