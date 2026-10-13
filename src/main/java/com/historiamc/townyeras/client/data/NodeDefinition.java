package com.historiamc.townyeras.client.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record NodeDefinition(
        ResourceLocation id,
        String title,
        String description,
        String eraId,
        String classId,
        String className,
        ResourceLocation icon,
        int graphX,
        int graphY,
        List<ResourceLocation> prerequisites,
        List<String> requirements,
        List<String> unlocks
) {
    public NodeDefinition {
        prerequisites = List.copyOf(prerequisites);
        requirements = List.copyOf(requirements);
        unlocks = List.copyOf(unlocks);
    }

    public NodeDefinition(
            ResourceLocation id,
            String title,
            String description,
            ResourceLocation icon,
            int graphX,
            int graphY,
            List<ResourceLocation> prerequisites,
            List<String> requirements,
            List<String> unlocks
    ) {
        this(
                id,
                title,
                description,
                "ancient",
                "universal",
                "Universal",
                icon,
                graphX,
                graphY,
                prerequisites,
                requirements,
                unlocks
        );
    }
}
