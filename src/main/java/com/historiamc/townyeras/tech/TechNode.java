package com.historiamc.townyeras.tech;

import java.util.List;
import java.util.Objects;

/** Immutable technology-tree node in source order. */
public record TechNode(
        String id,
        String displayName,
        String era,
        String classId,
        String icon,
        int slot,
        List<String> prerequisites,
        List<TechRequirement> requirements,
        TechUnlocks unlocks,
        Integer graphX,
        Integer graphY,
        List<String> legacyBindings,
        java.util.Map<String, List<String>> sharedRoutes
) {
    public TechNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(era, "era");
        Objects.requireNonNull(classId, "classId");
        Objects.requireNonNull(icon, "icon");
        prerequisites = List.copyOf(Objects.requireNonNull(prerequisites, "prerequisites"));
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        Objects.requireNonNull(unlocks, "unlocks");
        graphX = graphX == null ? Math.floorMod(slot, 9) * 76 : graphX;
        graphY = graphY == null ? Math.floorDiv(slot, 9) * 66 : graphY;
        legacyBindings = legacyBindings == null || legacyBindings.isEmpty()
                ? List.of(id)
                : List.copyOf(legacyBindings);
        sharedRoutes = sharedRoutes == null ? java.util.Map.of() : sharedRoutes.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())));
    }

    public List<String> unlockRecipes() {
        return unlocks.recipes();
    }

    public List<String> unlockActions() {
        return unlocks.actions();
    }

    public boolean classEligible(java.util.Collection<String> selected) {
        return classId.equals("universal") || selected.contains(classId)
                || sharedRoutes.keySet().stream().anyMatch(selected::contains);
    }

    public boolean dependsOnRevoked(java.util.Collection<String> selected, java.util.Set<String> revoked) {
        return dependsOnRevoked(selected, revoked, id -> true);
    }

    public boolean dependsOnRevoked(java.util.Collection<String> selected, java.util.Set<String> revoked,
                                    java.util.function.Predicate<String> completed) {
        if (sharedRoutes.isEmpty()) return prerequisites.stream().anyMatch(revoked::contains);
        var routes = sharedRoutes.entrySet().stream().filter(entry -> selected.contains(entry.getKey()))
                .map(java.util.Map.Entry::getValue).toList();
        if (routes.isEmpty()) routes = java.util.List.copyOf(sharedRoutes.values());
        // Revoking one branch must not revoke the shared node when the other route remains.
        return routes.stream().anyMatch(route -> route.stream().anyMatch(revoked::contains))
                && routes.stream().noneMatch(route -> route.stream().allMatch(id -> completed.test(id) && !revoked.contains(id)));
    }

    /** One shared identity, with an OR between the selected classes' routes. */
    public List<String> effectivePrerequisites(java.util.Collection<String> selected,
                                             java.util.function.Predicate<String> completed) {
        if (sharedRoutes.isEmpty()) return prerequisites;
        return sharedRoutes.entrySet().stream().filter(entry -> selected.contains(entry.getKey()))
                .map(java.util.Map.Entry::getValue)
                .min(java.util.Comparator.<List<String>>comparingInt(route ->
                        (int) route.stream().filter(id -> !completed.test(id)).count())
                        .thenComparingInt(List::size))
                .orElse(prerequisites);
    }

    public String visibleClass(java.util.Collection<String> selected, java.util.function.Predicate<String> completed) {
        if (sharedRoutes.isEmpty()) return classId;
        var route = effectivePrerequisites(selected, completed);
        if (selected.contains(classId) && sharedRoutes.getOrDefault(classId, List.of()).equals(route)) return classId;
        return sharedRoutes.entrySet().stream().filter(entry -> selected.contains(entry.getKey())
                && entry.getValue().equals(route)).map(java.util.Map.Entry::getKey).findFirst().orElse(classId);
    }
}
