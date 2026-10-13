package com.historiamc.townyeras.client.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Presentation only: grouping never creates or broadens an unlock. */
public final class TechnologyPresentation {
    private TechnologyPresentation() {}

    public record Unlock(String kind, String id, String name) {}
    public record Group(String name, List<Unlock> entries) {
        public Group { entries = List.copyOf(entries); }
    }

    public static List<Group> groups(List<String> unlocks) {
        Map<String, List<Unlock>> groups = new LinkedHashMap<>();
        for (String raw : unlocks) {
            int colon = raw.indexOf(": ");
            String kind = colon < 0 ? "Unlock" : raw.substring(0, colon);
            String id = colon < 0 ? raw : raw.substring(colon + 2);
            String path = id.substring(id.indexOf(':') + 1);
            String group = switch (kind) {
                case "Action", "Ability" -> "Permitted interactions";
                case "Process" -> "Station processes";
                case "Recipe" -> path.endsWith("_slab") || path.endsWith("_stairs") ? "Slabs and stairs"
                        : path.contains("knife") || path.endsWith("_axe") || path.endsWith("_hoe")
                        || path.endsWith("_pickaxe") || path.endsWith("_shovel") || path.endsWith("_sword")
                        || path.contains("helmet") || path.contains("chestplate") || path.contains("leggings")
                        || path.contains("boots") ? "Tools and equipment"
                        : path.contains("chest") || path.contains("cabinet") || path.contains("basket")
                        || path.equals("crafting_table") ? "Tables and storage" : "Other recipes";
                default -> "Other unlocks";
            };
            groups.computeIfAbsent(group, unused -> new ArrayList<>()).add(new Unlock(kind, id, readable(path)));
        }
        return groups.entrySet().stream().map(e -> new Group(e.getKey(), e.getValue())).toList();
    }

    public static String readable(String id) {
        if (id.equals("campfire_cooking_whitelist")) return "Campfire Cooking";
        if (id.equals("wild_seed_yield_5")) return "5% Increase in Wild Crop Yields";
        String text = id.replace('/', ' ').replace('_', ' ').trim();
        if (text.isEmpty()) return id;
        StringBuilder result = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    /** Friendly labels only. The requirement's action, targets and amount are never modified. */
    public static String objectiveLabel(String raw) {
        if (raw.startsWith("Obtain minecraft:beef,")) return "Collect raw meat or fish";
        if (raw.startsWith("Obtain minecraft:white_wool,")) return "Collect wool";
        String concise = switch (raw) {
            case "Personally collect direct animal raw-meat drops" -> "Collect raw meat";
            case "Personally collect natural wool drops" -> "Collect wool";
            case "Naturally collect eligible eggs" -> "Collect eggs";
            case "Personally collect crops/seeds from wild or mature crop harvests" -> "Collect crops or seeds";
            case "Personally collect grain from mature or natural wild crops" -> "Harvest grain";
            case "Personally harvest grain/fruit from mature or natural wild crops" -> "Harvest grain or fruit";
            case "Personally break naturally generated wild crops" -> "Harvest wild crops";
            case "Personally butcher raw meat/fish through approved cutting-board recipes" -> "Butcher raw meat or fish";
            case "Personally kill animals that directly drop eligible raw meat/fish" -> "Hunt food animals";
            case "Personally catch eligible fish with a vanilla fishing rod" -> "Catch fish";
            case "Catch distinct eligible raw-fish species" -> "Catch different fish species";
            case "Personally smelt logs into charcoal and collect the output" -> "Make charcoal";
            case "Personally produce bone meal using a composter or bone/bone-block crafting" -> "Produce bone meal";
            case "Personally travel through water without riding" -> "Swim";
            case "Swim or personally pilot a boat/raft through water" -> "Swim or sail";
            case "Travel on foot/swimming; no mounts, vehicles, flight or teleports" -> "Walk or swim";
            case "Mine and collect natural stone" -> "Mine stone";
            case "Collect clay balls from clay" -> "Collect clay balls";
            case "Obtain wool/flax/cotton (trade permitted)" -> "Collect wool, flax or cotton";
            case "Obtain vegetables or eligible raw meat/fish" -> "Collect vegetables, raw meat or fish";
            case "Collect string personally from mobs or loot" -> "Collect string";
            case "Obtain approved Firemaking campfire inputs" -> "Collect campfire materials";
            case "Obtain clay/copper vessel materials" -> "Collect clay or copper";
            case "Personally craft toughasnails:empty_leather_canteen" -> "Craft leather canteens";
            case "Personally craft astikorcartsredux:wheel" -> "Craft wheels";
            case "Obtain supplementaries:flax,rusticdelight:cotton_boll (trade permitted)" -> "Collect flax or cotton";
            default -> raw;
        };
        concise = concise.replaceFirst("^Personally ", "").replaceFirst("^Obtain ", "Collect ")
                .replace(" (trade permitted)", "").replace("eligible ", "").replace("generic ", "")
                .replace("vanilla wool", "wool").replace("crop plants", "crops").replace("#logs", "logs")
                .replace("flax/cotton", "flax or cotton").replace("meat/fish", "meat or fish")
                .replace("dough/batter", "dough or batter").replace("rope/string", "rope or string");
        var matcher = java.util.regex.Pattern.compile("[a-z0-9_]+:([a-z0-9_]+)").matcher(concise);
        StringBuilder friendly = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(friendly,
                java.util.regex.Matcher.quoteReplacement(matcher.group(1).replace('_', ' ')));
        matcher.appendTail(friendly);
        String value = friendly.toString();
        return value.isEmpty() ? raw : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /** Call only after branch/era/concealment checks. No hidden metadata is indexed. */
    public static boolean matches(String title, List<String> unlocks, String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        if (title.toLowerCase(Locale.ROOT).contains(needle)) return true;
        return groups(unlocks).stream().flatMap(g -> g.entries().stream())
                .anyMatch(e -> e.id().toLowerCase(Locale.ROOT).contains(needle)
                        || e.name().toLowerCase(Locale.ROOT).contains(needle));
    }
}
