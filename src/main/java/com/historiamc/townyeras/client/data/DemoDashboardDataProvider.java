package com.historiamc.townyeras.client.data;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DemoDashboardDataProvider implements DashboardDataProvider {
    private static ResourceLocation nodeId(String path) {
        return ResourceLocation.fromNamespaceAndPath("townyeras", path);
    }

    private static ResourceLocation itemId(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    @Override
    public DashboardSnapshot snapshot(LocalPlayer player) {
        ResourceLocation craftsmanship = nodeId("craftsmanship");
        ResourceLocation stoneTools = nodeId("stone_tools");
        ResourceLocation firemaking = nodeId("firemaking");
        ResourceLocation furnaceBasics = nodeId("furnace_basic_ore");
        ResourceLocation earlyAgriculture = nodeId("early_agriculture");
        ResourceLocation woodworking = nodeId("woodworking");

        List<NodeDefinition> nodes = List.of(
                new NodeDefinition(
                        craftsmanship,
                        "Craftsmanship",
                        "The first universal technology and the foundation of practical work.",
                        itemId("crafting_table"),
                        0, 66,
                        List.of(),
                        List.of("Craft 32 planks", "Chop 16 logs", "Craft 16 sticks"),
                        List.of("Crafting tables", "Wooden tools", "Vertical planks")
                ),
                new NodeDefinition(
                        stoneTools,
                        "Stone Tools",
                        "Shape durable stone implements for mining and construction.",
                        itemId("stone_pickaxe"),
                        76, 66,
                        List.of(craftsmanship),
                        List.of("Mine 50 stone"),
                        List.of("Stone tool recipes")
                ),
                new NodeDefinition(
                        firemaking,
                        "Firemaking",
                        "Master controlled fire for heat, light, and charcoal.",
                        itemId("campfire"),
                        152, 66,
                        List.of(stoneTools),
                        List.of("Collect 30 flint", "Mine 30 coal", "Craft 30 sticks"),
                        List.of("Campfires", "Soul campfires", "Charcoal recipe")
                ),
                new NodeDefinition(
                        furnaceBasics,
                        "Furnace Basics",
                        "Build hotter fires capable of transforming ore and clay.",
                        itemId("furnace"),
                        228, 66,
                        List.of(firemaking),
                        List.of("Mine 128 stone", "Mine 32 coal", "Mine 16 copper ore or clay"),
                        List.of("Furnace use", "Basic metal ingots", "Clay smelting")
                ),
                new NodeDefinition(
                        earlyAgriculture,
                        "Early Agriculture",
                        "Begin deliberate cultivation and food storage.",
                        itemId("wheat"),
                        76, 0,
                        List.of(craftsmanship),
                        List.of("Till 32 soil", "Collect 20 wild crops"),
                        List.of("Hay bales", "Composters", "Bonemeal and composter use")
                ),
                new NodeDefinition(
                        woodworking,
                        "Woodworking",
                        "Refine timber into stronger and more expressive structures.",
                        itemId("oak_log"),
                        76, 132,
                        List.of(craftsmanship),
                        List.of("Craft 200 planks", "Chop 100 logs"),
                        List.of("Wooden stairs and slabs", "Timber frames", "Log stripping")
                )
        );

        Map<ResourceLocation, NodeProgress> progress = new LinkedHashMap<>();
        progress.put(craftsmanship, new NodeProgress(NodeStatus.COMPLETED, 1, 1));
        progress.put(stoneTools, new NodeProgress(NodeStatus.COMPLETED, 1, 1));
        progress.put(firemaking, new NodeProgress(NodeStatus.IN_PROGRESS, 18, 30));
        progress.put(furnaceBasics, new NodeProgress(NodeStatus.LOCKED, 0, 128));
        progress.put(earlyAgriculture, new NodeProgress(NodeStatus.AVAILABLE, 0, 32));
        progress.put(woodworking, new NodeProgress(NodeStatus.AVAILABLE, 0, 200));

        return new DashboardSnapshot(
                player.getUUID(),
                player.getGameProfile().getName(),
                "Ancient Era",
                "January 1, 3100 BCE",
                "Texas",
                List.of("Farmer", "Carpenter"),
                3,
                2,
                nodes,
                progress,
                firemaking
        );
    }
}
