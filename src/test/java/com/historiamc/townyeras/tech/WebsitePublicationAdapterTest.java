package com.historiamc.townyeras.tech;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebsitePublicationAdapterTest {
    @Test
    void olderEditorPayloadPreservesProcessingAndSharedRouteMetadata() {
        var base = TechRegistry.load();
        var gson = new com.google.gson.Gson();
        JsonObject desired = new JsonObject();
        desired.addProperty("schemaVersion", 2);
        JsonArray nodes = new JsonArray();
        for (var original : base.nodes().values()) {
            JsonObject node = new JsonObject();
            node.addProperty("id", original.id());
            node.addProperty("name", original.displayName());
            node.addProperty("era", original.era());
            node.addProperty("classId", original.classId());
            node.addProperty("icon", original.icon());
            node.add("legacyBindings", gson.toJsonTree(original.legacyBindings()));
            node.add("prerequisites", gson.toJsonTree(original.prerequisites()));
            node.add("requirements", gson.toJsonTree(original.requirements()));
            node.add("unlockRecipes", gson.toJsonTree(original.unlockRecipes()));
            node.add("unlockActions", gson.toJsonTree(original.unlockActions()));
            nodes.add(node);
        }
        desired.add("nodes", nodes);
        var published = WebsitePublicationAdapter.apply(base, desired);
        assertEquals(base.node("early_enclosures").sharedRoutes(), published.node("early_enclosures").sharedRoutes());
        assertEquals(base.node("fermentation").unlocks().processRecipes(), published.node("fermentation").unlocks().processRecipes());
        assertEquals(77, published.nodes().size());
    }
    @Test
    void transfersLegacyGameplayBindingsToAWebsiteNode() {
        TechRegistry published = WebsitePublicationAdapter.apply(TechRegistry.load(), graph("website_crafting"));

        assertEquals(1, published.nodes().size());
        assertEquals("Website Crafting", published.node("website_crafting").displayName());
        assertEquals(321, published.node("website_crafting").graphX());
        assertTrue(published.stations().values().stream()
                .filter(station -> station.nodeId().equals("website_crafting"))
                .anyMatch(station -> station.nodeName().equals("Website Crafting")));
    }

    @Test
    void rejectsDuplicateGameplayBindings() {
        JsonObject graph = graph("first");
        JsonObject duplicate = graph("second").getAsJsonArray("nodes").get(0).getAsJsonObject();
        graph.getAsJsonArray("nodes").add(duplicate);
        assertThrows(IllegalArgumentException.class,
                () -> WebsitePublicationAdapter.apply(TechRegistry.load(), graph));
    }

    private static JsonObject graph(String id) {
        JsonObject desired = new JsonObject();
        desired.addProperty("schemaVersion", 2);
        JsonObject node = new JsonObject();
        node.addProperty("id", id);
        node.addProperty("name", "Website Crafting");
        node.addProperty("era", "ancient");
        node.addProperty("classId", "universal");
        node.addProperty("icon", "minecraft:crafting_table");
        node.addProperty("slot", 10);
        JsonObject position = new JsonObject();
        position.addProperty("x", 321);
        position.addProperty("y", 123);
        node.add("position", position);
        node.add("legacyBindings", strings("craftsmanship"));
        node.add("prerequisites", new JsonArray());
        JsonObject requirement = new JsonObject();
        requirement.addProperty("id", "craft");
        requirement.addProperty("action", "craft_item");
        requirement.addProperty("display", "Craft an item");
        requirement.addProperty("amount", 1);
        requirement.add("targets", strings("minecraft:stick"));
        JsonArray requirements = new JsonArray();
        requirements.add(requirement);
        node.add("requirements", requirements);
        node.add("unlockRecipes", strings("minecraft:stick"));
        node.add("unlockActions", new JsonArray());
        JsonArray nodes = new JsonArray();
        nodes.add(node);
        desired.add("nodes", nodes);
        return desired;
    }

    private static JsonArray strings(String... values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }
}
