package com.historiamc.townyeras.tech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts the website's schema-v2 editable graph into the mod's normalized registry. */
public final class WebsitePublicationAdapter {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private WebsitePublicationAdapter() {
    }

    public static TechRegistry apply(TechRegistry base, JsonObject desired) {
        if (desired == null || desired.get("schemaVersion") == null
                || desired.get("schemaVersion").getAsInt() != 2
                || !desired.has("nodes") || !desired.get("nodes").isJsonArray()) {
            throw new IllegalArgumentException("Expected a schemaVersion 2 technology graph");
        }

        JsonObject normalized = PRETTY.fromJson(base.toNormalizedJson(), JsonObject.class);
        JsonArray publishedNodes = desired.getAsJsonArray("nodes");
        if (publishedNodes.isEmpty()) {
            throw new IllegalArgumentException("The website graph may not be empty");
        }

        JsonArray nodes = new JsonArray();
        Map<String, String> bindingOwners = new HashMap<>();
        Set<String> nodeIds = new HashSet<>();
        int index = 0;
        for (JsonElement element : publishedNodes) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Every published technology must be an object");
            }
            JsonObject source = element.getAsJsonObject();
            String id = requiredString(source, "id");
            if (!id.equals(normalizeId(id)) || !nodeIds.add(id)) {
                throw new IllegalArgumentException("Technology IDs must be unique normalized identifiers: " + id);
            }

            JsonObject node = new JsonObject();
            node.addProperty("id", id);
            node.addProperty("displayName", plainText(requiredString(source, "name"), "technology name"));
            node.addProperty("era", normalizeId(requiredString(source, "era")));
            node.addProperty("classId", normalizeId(requiredString(source, "classId")));
            node.addProperty("icon", requiredString(source, "icon"));
            node.addProperty("slot", source.has("slot") ? source.get("slot").getAsInt() : -1);
            node.add("prerequisites", requiredArray(source, "prerequisites").deepCopy());
            node.add("requirements", requiredArray(source, "requirements").deepCopy());

            JsonObject unlocks = new JsonObject();
            unlocks.add("recipes", requiredArray(source, "unlockRecipes").deepCopy());
            unlocks.add("actions", requiredArray(source, "unlockActions").deepCopy());
            TechNode original = base.node(id);
            if (original == null && source.has("legacyBindings")) {
                for (JsonElement binding : source.getAsJsonArray("legacyBindings")) {
                    if (base.node(binding.getAsString()) != null) {
                        original = base.node(binding.getAsString());
                        break;
                    }
                }
            }
            unlocks.add("processRecipes", source.has("processRecipes") ? source.get("processRecipes").deepCopy()
                    : PRETTY.toJsonTree(original == null ? java.util.List.of() : original.unlocks().processRecipes()));
            node.add("sharedRoutes", source.has("sharedRoutes") ? source.get("sharedRoutes").deepCopy()
                    : PRETTY.toJsonTree(original == null ? java.util.Map.of() : original.sharedRoutes()));
            node.add("unlocks", unlocks);

            JsonObject position = source.has("position") && source.get("position").isJsonObject()
                    ? source.getAsJsonObject("position") : null;
            node.addProperty("graphX", position == null ? 40 + (index % 5) * 250 : position.get("x").getAsInt());
            node.addProperty("graphY", position == null ? 40 + (index / 5) * 130 : position.get("y").getAsInt());
            JsonArray legacy = requiredArray(source, "legacyBindings");
            if (legacy.isEmpty()) {
                legacy.add(id);
            }
            JsonArray normalizedBindings = new JsonArray();
            for (JsonElement bindingElement : legacy) {
                String binding = normalizeId(bindingElement.getAsString());
                String previous = bindingOwners.putIfAbsent(binding, id);
                if (previous != null) {
                    throw new IllegalArgumentException("Gameplay binding " + binding + " belongs to more than one node");
                }
                normalizedBindings.add(binding);
            }
            node.add("legacyBindings", normalizedBindings);
            nodes.add(node);
            index++;
        }
        normalized.add("nodes", nodes);
        JsonArray classes = normalized.getAsJsonArray("classes");
        normalized.add("gates", remapRules(
                normalized.getAsJsonArray("gates"), bindingOwners, nodes, classes, "nodeId", "nodeName"));
        normalized.add("stations", remapRules(
                normalized.getAsJsonArray("stations"), bindingOwners, nodes, classes, "nodeId", "nodeName"));
        normalized.add("counts", counts(normalized));
        return TechRegistry.fromNormalizedJson(PRETTY.toJson(normalized));
    }

    private static JsonArray remapRules(
            JsonArray existing,
            Map<String, String> owners,
            JsonArray nodes,
            JsonArray classes,
            String idField,
            String nameField
    ) {
        Map<String, String> names = new HashMap<>();
        Map<String, String> nodeClasses = new HashMap<>();
        Map<String, String> classNames = new HashMap<>();
        classNames.put("universal", "Universal");
        for (JsonElement classElement : classes) {
            JsonObject classDefinition = classElement.getAsJsonObject();
            classNames.put(classDefinition.get("id").getAsString(),
                    classDefinition.get("displayName").getAsString());
        }
        for (JsonElement nodeElement : nodes) {
            JsonObject node = nodeElement.getAsJsonObject();
            names.put(node.get("id").getAsString(), node.get("displayName").getAsString());
            nodeClasses.put(node.get("id").getAsString(), node.get("classId").getAsString());
        }
        JsonArray remapped = new JsonArray();
        for (JsonElement element : existing) {
            JsonObject rule = element.getAsJsonObject().deepCopy();
            String oldId = normalizeId(rule.get(idField).getAsString());
            String owner = owners.get(oldId);
            if (owner == null) {
                continue;
            }
            rule.addProperty(idField, owner);
            rule.addProperty(nameField, names.get(owner));
            rule.addProperty("className", classNames.getOrDefault(nodeClasses.get(owner), "Universal"));
            remapped.add(rule);
        }
        return remapped;
    }

    private static JsonObject counts(JsonObject normalized) {
        JsonObject counts = new JsonObject();
        JsonArray nodes = normalized.getAsJsonArray("nodes");
        int requirements = 0;
        int recipes = 0;
        int actions = 0;
        for (JsonElement element : nodes) {
            JsonObject node = element.getAsJsonObject();
            requirements += node.getAsJsonArray("requirements").size();
            recipes += node.getAsJsonObject("unlocks").getAsJsonArray("recipes").size();
            actions += node.getAsJsonObject("unlocks").getAsJsonArray("actions").size();
        }
        counts.addProperty("nodes", nodes.size());
        counts.addProperty("requirements", requirements);
        counts.addProperty("recipeEntries", recipes);
        counts.addProperty("unlockActions", actions);
        counts.addProperty("categories", normalized.getAsJsonArray("categories").size());
        counts.addProperty("gates", normalized.getAsJsonArray("gates").size());
        counts.addProperty("stations", normalized.getAsJsonArray("stations").size());
        counts.addProperty("eras", normalized.getAsJsonArray("eras").size());
        counts.addProperty("classes", normalized.getAsJsonArray("classes").size());
        return counts;
    }

    private static JsonArray requiredArray(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonArray()) {
            throw new JsonParseException("Missing array " + key);
        }
        return value.getAsJsonArray(key);
    }

    private static String requiredString(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonPrimitive()) {
            throw new JsonParseException("Missing string " + key);
        }
        String result = value.get(key).getAsString();
        if (result.isBlank()) {
            throw new JsonParseException("Blank string " + key);
        }
        return result;
    }

    private static String plainText(String value, String label) {
        if (value.indexOf('\u00a7') >= 0 || value.matches(".*&[0-9a-fk-orA-FK-OR].*")) {
            throw new IllegalArgumentException("Use plain text for " + label);
        }
        return value;
    }

    private static String normalizeId(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
