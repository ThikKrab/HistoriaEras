package com.historiamc.townyeras.client.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DashboardSnapshot(
        UUID playerId,
        String displayName,
        String eraId,
        String eraName,
        String calendarDate,
        String nationName,
        List<String> classes,
        int jobTokens,
        int completedNodeCount,
        List<NodeDefinition> nodes,
        Map<ResourceLocation, NodeProgress> progress,
        ResourceLocation trackedNodeId,
        List<String> selectedClassIds,
        TimelineView timeline,
        boolean operator,
        boolean timelineAdmin,
        List<String> onlinePlayerNames
) {
    public DashboardSnapshot {
        classes = List.copyOf(classes);
        nodes = List.copyOf(nodes);
        progress = Map.copyOf(progress);
        selectedClassIds = List.copyOf(selectedClassIds);
        onlinePlayerNames = List.copyOf(onlinePlayerNames);
    }

    public NodeDefinition node(ResourceLocation id) {
        return nodes.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
    }

    public NodeProgress progress(ResourceLocation id) {
        if (id == null) {
            return new NodeProgress(NodeStatus.LOCKED, 0, 0);
        }
        return progress.getOrDefault(id, new NodeProgress(NodeStatus.LOCKED, 0, 0));
    }

    public DashboardSnapshot(
            UUID playerId,
            String displayName,
            String eraName,
            String calendarDate,
            String nationName,
            List<String> classes,
            int jobTokens,
            int completedNodeCount,
            List<NodeDefinition> nodes,
            Map<ResourceLocation, NodeProgress> progress,
            ResourceLocation trackedNodeId
    ) {
        this(
                playerId,
                displayName,
                "ancient",
                eraName,
                calendarDate,
                nationName,
                classes,
                jobTokens,
                completedNodeCount,
                nodes,
                progress,
                trackedNodeId,
                classes.stream().map(value -> value.toLowerCase(java.util.Locale.ROOT)).toList(),
                TimelineView.demonstration(),
                false,
                false,
                List.of(displayName)
        );
    }
}
