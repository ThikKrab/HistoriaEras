package com.historiamc.townyeras.client.data;

import com.historiamc.townyeras.network.ClientSnapshotCache;
import com.historiamc.townyeras.tech.TechClass;
import com.historiamc.townyeras.tech.TechNode;
import com.historiamc.townyeras.tech.TechRegistry;
import com.historiamc.townyeras.tech.TechRequirement;
import com.historiamc.townyeras.tech.TechnologyManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds the dashboard exclusively from bundled definitions and server-owned progress. */
public final class SyncedDashboardDataProvider implements DashboardDataProvider {
    private final DashboardDataProvider fallback = new DemoDashboardDataProvider();

    @Override
    public DashboardSnapshot snapshot(LocalPlayer player) {
        if (!ClientSnapshotCache.isSynchronizedWithServer()) {
            return fallback.snapshot(player);
        }

        CompoundTag wire = ClientSnapshotCache.latest();
        TechRegistry technology = TechnologyManager.current();
        CompoundTag timeline = wire.getCompound("timeline");
        String eraId = valueOr(timeline.getString("eraId"), "ancient");
        String eraName = valueOr(timeline.getString("eraName"), displayId(eraId) + " Era");
        int historicalYear = timeline.getInt("historicalYear");
        String historicalLabel = valueOr(
                timeline.getString("historicalYearDisplay"),
                historicalYearLabel(historicalYear)
        );
        String calendarDate = valueOr(timeline.getString("calendarDate"), "January 1");
        String date = calendarDate + ", " + historicalLabel;
        TimelineView timelineView = new TimelineView(
                eraId,
                eraName,
                historicalYear,
                historicalLabel,
                timeline.getInt("dayOfYear"),
                calendarDate,
                valueOr(timeline.getString("seasonName"), "Mid Winter"),
                timeline.getBoolean("sereneSeasonsSynced"),
                timeline.getDouble("eraProgress"),
                timeline.getBoolean("paused"),
                timeline.getBoolean("eraCompleted"),
                timeline.getLong("ticksPerCalendarDay")
        );

        List<String> selectedClassIds = stringList(wire, "classes");
        List<String> selectedClassNames = selectedClassIds.stream().map(this::className).toList();
        Set<String> completed = new HashSet<>(stringList(wire, "completedNodes"));
        Set<String> granted = new HashSet<>(stringList(wire, "grantedNodes"));
        Set<String> accessible = new HashSet<>(stringList(wire, "accessibleNodes"));
        CompoundTag contributions = wire.getCompound("contributions");
        CompoundTag statuses = wire.getCompound("nodeStatuses");

        List<NodeDefinition> definitions = new ArrayList<>();
        Map<ResourceLocation, NodeProgress> progress = new LinkedHashMap<>();
        for (TechNode node : technology.nodes().values()) {
            if (!TechnologyEraVisibility.allowed(wire.getBoolean("timelineAdmin"), eraId, node.era(),
                    technology.eras())) continue;
            ResourceLocation id = nodeId(node.id());
            List<ResourceLocation> prerequisites = node.effectivePrerequisites(selectedClassIds, accessible::contains).stream()
                    .map(SyncedDashboardDataProvider::nodeId)
                    .toList();
            List<String> requirementLabels = node.requirements().stream()
                    .map(requirement -> TechnologyPresentation.objectiveLabel(requirement.display()))
                    .toList();
            List<String> unlocks = new ArrayList<>();
            node.unlockRecipes().forEach(recipe -> unlocks.add("Recipe: " + recipe));
            node.unlockActions().forEach(action -> unlocks.add("Action: " + action));
            node.unlocks().processRecipes().forEach(recipe -> unlocks.add("Process: " + recipe));

            int graphX = node.graphX();
            int graphY = node.graphY();
            String visibleClass = node.visibleClass(selectedClassIds, accessible::contains);
            if (node.id().equals("early_enclosures") && visibleClass.equals("carpenter")) graphY = -300;
            String className = visibleClass.equals("universal") ? "Universal" : className(visibleClass);
            definitions.add(new NodeDefinition(
                    id,
                    node.displayName(),
                    description(node, className),
                    node.era(),
                    visibleClass,
                    className,
                    parseIcon(node.icon()),
                    graphX,
                    graphY,
                    prerequisites,
                    requirementLabels,
                    unlocks
            ));

            List<RequirementProgress> requirements = new ArrayList<>();
            for (TechRequirement requirement : node.requirements()) {
                double current = contributions.getDouble(node.id() + "|" + requirement.id());
                requirements.add(new RequirementProgress(
                        requirement.id(),
                        TechnologyPresentation.objectiveLabel(requirement.display()),
                        requirement.action(),
                        Math.clamp(current, 0.0D, requirement.amount()),
                        requirement.amount()
                ));
            }
            String rawStatus = statuses.getString(node.id());
            NodeStatus status = status(rawStatus, completed.contains(node.id()), requirements);
            // Research may be locked outside the active era, but historical trees remain browsable.
            if (status == NodeStatus.FUTURE_ERA && !node.era().equals(eraId)
                    && TechnologyEraVisibility.allowed(false, eraId, node.era(), technology.eras())) {
                status = NodeStatus.LOCKED;
            }
            if (granted.contains(node.id())) status = NodeStatus.GRANTED;
            progress.put(id, new NodeProgress(status, requirements, reason(node, status, accessible, selectedClassIds),
                    accessible.contains(node.id())));
        }

        String trackedId = wire.getString("trackedNode");
        ResourceLocation tracked = !trackedId.isBlank() && definitions.stream().anyMatch(node -> node.id().equals(nodeId(trackedId)))
                ? nodeId(trackedId) : null;
        return new DashboardSnapshot(
                player.getUUID(),
                valueOr(wire.getString("playerName"), player.getGameProfile().getName()),
                eraId,
                eraName,
                date,
                valueOr(wire.getString("nationName"), "No nation"),
                selectedClassNames.isEmpty() ? List.of("Choose two classes") : selectedClassNames,
                wire.getInt("jobTokens"),
                completed.size(),
                definitions,
                progress,
                tracked,
                selectedClassIds,
                timelineView,
                wire.getBoolean("operator"),
                wire.getBoolean("timelineAdmin"),
                stringList(wire, "onlinePlayers")
        );
    }

    private String className(String classId) {
        TechClass definition = TechnologyManager.current().classes().get(classId);
        return definition == null ? displayId(classId) : definition.displayName();
    }

    private static String description(TechNode node, String className) {
        return node.displayName() + " is a " + className + " technology from the "
                + displayId(node.era()) + " Era. Complete every objective to activate its unlocks.";
    }

    private static String reason(
            TechNode node,
            NodeStatus status,
            Set<String> completed,
            List<String> selectedClasses
    ) {
        return switch (status) {
            case CLASS_LOCKED -> "Select the " + displayId(node.classId()) + " class to make progress.";
            case FUTURE_ERA -> "This technology becomes available in the " + displayId(node.era()) + " Era.";
            case LOCKED -> {
                List<String> missing = node.effectivePrerequisites(selectedClasses, completed::contains).stream()
                        .filter(id -> !completed.contains(id)).toList();
                yield missing.isEmpty() ? "Complete the preceding technologies first."
                        : "Missing: " + String.join(", ", missing.stream().map(SyncedDashboardDataProvider::displayId).toList());
            }
            case AVAILABLE -> "All prerequisites are complete; objectives can now advance.";
            case IN_PROGRESS -> "Objectives are tracked automatically by the server.";
            case COMPLETED -> completed.contains(node.id()) ? "All permissions and recipes from this node are active."
                    : "Completed progress is saved. Select its class to activate these unlocks.";
            case GRANTED -> "Administrator-granted permissions are active.";
        };
    }

    private static NodeStatus status(
            String raw,
            boolean completed,
            List<RequirementProgress> requirements
    ) {
        if (completed || raw.equals("completed")) {
            return NodeStatus.COMPLETED;
        }
        return switch (raw) {
            case "available" -> NodeStatus.AVAILABLE;
            case "in_progress" -> NodeStatus.IN_PROGRESS;
            case "locked_class" -> NodeStatus.CLASS_LOCKED;
            case "locked_era" -> NodeStatus.FUTURE_ERA;
            case "locked_prerequisites" -> NodeStatus.LOCKED;
            default -> requirements.stream().anyMatch(requirement -> requirement.current() > 0.0D)
                    ? NodeStatus.IN_PROGRESS
                    : NodeStatus.LOCKED;
        };
    }

    private static List<String> stringList(CompoundTag parent, String key) {
        ListTag values = parent.getList(key, Tag.TAG_STRING);
        List<String> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            result.add(values.getString(index));
        }
        return List.copyOf(result);
    }

    private static ResourceLocation nodeId(String path) {
        return ResourceLocation.fromNamespaceAndPath("townyeras", path);
    }

    private static ResourceLocation parseIcon(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null ? ResourceLocation.withDefaultNamespace("barrier") : parsed;
    }

    private static String historicalYearLabel(int year) {
        if (year < 0) return Math.abs(year) + " BCE";
        if (year > 0) return year + " CE";
        return "Year 0";
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String displayId(String id) {
        if (id == null || id.isBlank()) return "Unknown";
        String[] words = id.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
