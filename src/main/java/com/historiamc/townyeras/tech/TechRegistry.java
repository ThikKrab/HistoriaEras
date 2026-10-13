package com.historiamc.townyeras.tech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Loads and queries the bundled, normalized HistoriaEras technology definitions. */
public final class TechRegistry {
    public static final String RESOURCE_PATH = "/data/townyeras/townyeras/technology.json";

    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final int EXPECTED_NODES = 77;
    private static final int EXPECTED_REQUIREMENTS = 224;
    private static final int EXPECTED_RECIPE_ENTRIES = 1160;
    private static final int EXPECTED_UNLOCK_ACTIONS = 45;
    private static final int EXPECTED_CATEGORIES = 75;
    private static final int EXPECTED_GATES = 29;
    private static final int EXPECTED_STATIONS = 13;
    private static final int EXPECTED_ERAS = 9;
    private static final int EXPECTED_ENABLED_CLASSES = 6;
    private static final Gson GSON = new GsonBuilder().create();
    private static final Set<String> REQUIREMENT_ACTIONS = Set.of(
            "butcher_mutton_chops", "chop_log", "composter_bonemeal", "craft_any_item", "craft_item",
            "fiber_gathered", "fish_caught", "harvest_crop", "harvest_mature_crop",
            "hunt_animals_or_fish", "interact_block", "kill_animal", "kill_entity", "mine_block",
            "obtain_item", "place_block", "plant_crop", "smelt_item", "source_mine_block", "source_obtain_item",
            "strip_log", "till_soil", "harvest_obtain_item", "animal_obtain_item", "kill_food_animal",
            "breed_animal", "produce_bonemeal", "butcher_raw_input", "distinct_fish",
            "travel_water_personal", "travel_water_driver", "travel_personal", "loot_obtain_item", "bee_harvest"
    );
    private static final Set<String> BUILT_IN_CATEGORY_IDS = Set.of(
            "logs", "leaves", "planks", "stone", "natural_stone", "decorative_stone",
            "basic_ore_sources", "ores", "fuel", "seeds", "crops", "animals", "animal_feed",
            "fences", "fence_gates", "shears", "bonded_leather", "leather_canteens",
            "rain_collectors", "water_purifiers", "campfires", "fire_pits", "straw_blocks", "wool",
            "dyes", "dyed_blocks", "forage", "dye_plants", "fiber", "ropecraft_fiber",
            "wooden_stairs", "wooden_slabs", "wooden_doors", "wooden_trapdoors", "vertical_planks",
            "timber_frame_recipes", "ladders_chutes", "animal_products", "animal_loot", "raw_food",
            "grains", "filter_materials", "heat_sources", "ink_materials", "wax_materials",
            "storage_blocks", "raw_meat", "cooked_meat", "raw_fish", "fish", "cooked_fish",
            "soups_stews", "cooking_stations", "row_boats", "boats", "fishing_gear", "fish_foods",
            "composters", "enclosure_blocks", "wool_goods", "leather_goods", "future_tech",
            "wood_parts", "trapdoors", "signs", "stone_parts", "stone_tools", "wood_tools",
            "basic_stone_parts", "candles", "copper_goods", "basic_tools", "knives", "hay_straw",
            "stick_stacks", "smokers", "blast_furnaces", "hoes", "axes", "dirt", "grass"
    );

    private final Map<String, TechNode> nodes;
    private final Map<String, TechCategory> categories;
    private final List<TechGate> gates;
    private final Map<String, TechStation> stations;
    private final Map<String, TechEra> eras;
    private final Map<String, TechClass> classes;

    private final DataFile data;

    private TechRegistry(DataFile data, boolean requireBundledCounts) {
        if (data.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported HistoriaEras technology schema "
                    + data.schemaVersion() + "; expected " + SUPPORTED_SCHEMA_VERSION);
        }

        this.data = data;
        nodes = immutableIndex(data.nodes(), TechNode::id, "node");
        categories = immutableIndex(data.categories(), TechCategory::id, "category");
        gates = Objects.requireNonNull(data.gates(), "gates").stream()
                .filter(gate -> !PublicUtilityPolicy.RETIRED_GATES.contains(gate.id())).toList();
        stations = immutableIndex(data.stations().stream()
                .filter(station -> !station.id().equals("wooden_storage"))
                .toList(), TechStation::id, "station");
        eras = immutableIndex(data.eras(), TechEra::id, "era");
        classes = immutableIndex(data.classes(), TechClass::id, "class");

        validateCounts(data.counts(), requireBundledCounts);
        validateReferences();
        validateMatcherSemantics();
    }

    /** Loads a new immutable registry from the bundled normalized JSON resource. */
    public static TechRegistry load() {
        try (InputStream stream = TechRegistry.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled HistoriaEras technology resource " + RESOURCE_PATH);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                DataFile data = GSON.fromJson(reader, DataFile.class);
                if (data == null) {
                    throw new IllegalStateException("Empty HistoriaEras technology resource " + RESOURCE_PATH);
                }
                return new TechRegistry(data, true);
            }
        } catch (IOException | JsonParseException exception) {
            throw new IllegalStateException("Could not load HistoriaEras technology resource " + RESOURCE_PATH, exception);
        }
    }

    /** Loads a validated normalized registry produced by the website publication adapter. */
    public static TechRegistry fromNormalizedJson(String json) {
        try (Reader reader = new StringReader(Objects.requireNonNull(json, "json"))) {
            DataFile data = GSON.fromJson(reader, DataFile.class);
            if (data == null) {
                throw new IllegalStateException("Empty HistoriaEras technology JSON");
            }
            return new TechRegistry(data, false);
        } catch (IOException | JsonParseException exception) {
            throw new IllegalStateException("Could not load HistoriaEras technology JSON", exception);
        }
    }

    /** Returns the stable normalized form used for crash-safe publication persistence. */
    public String toNormalizedJson() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(data);
    }

    public Map<String, TechNode> nodes() {
        return nodes;
    }

    /** Returns the node for a case-insensitive ID, or {@code null} when it is unknown. */
    public TechNode node(String id) {
        return id == null ? null : nodes.get(normalizeId(id));
    }

    public Map<String, TechCategory> categories() {
        return categories;
    }

    public List<TechGate> gates() {
        return gates;
    }

    public Map<String, TechStation> stations() {
        return stations;
    }

    public Map<String, TechEra> eras() {
        return eras;
    }

    public Map<String, TechClass> classes() {
        return classes;
    }

    /**
     * Matches a namespaced target against an exact ID, a {@code *} glob, or a
     * data-defined {@code #category} matcher.
     */
    public boolean matches(String target, String matcher) {
        if (matcher == null) {
            return false;
        }
        String normalizedTarget = target == null ? "minecraft:air" : normalizeMatcher(target);
        String normalizedMatcher = normalizeMatcher(matcher);
        if (normalizedMatcher.equals("*") || normalizedMatcher.equals(normalizedTarget)) {
            return true;
        }
        if (normalizedMatcher.indexOf('*') >= 0 && globMatches(normalizedTarget, normalizedMatcher)) {
            return true;
        }
        if (!normalizedMatcher.startsWith("#")) {
            return false;
        }

        TechCategory category = categories.get(normalizedMatcher.substring(1));
        if (category != null) {
            if (category.exact().contains(normalizedTarget)) {
                return true;
            }
            return category.patterns().stream().anyMatch(pattern -> globMatches(normalizedTarget, pattern));
        }
        return matchesBuiltInCategory(normalizedTarget, normalizedMatcher.substring(1));
    }

    /** Returns every configured gate of {@code type} whose target matcher accepts {@code target}. */
    public List<TechGate> gatesFor(String type, String target) {
        if (type == null) {
            return List.of();
        }
        String normalizedType = normalizeId(type);
        return gates.stream()
                .filter(gate -> gate.type().equals(normalizedType))
                .filter(gate -> gate.targets().stream().anyMatch(matcher -> matches(target, matcher)))
                .toList();
    }

    /*
     * The original plugin intentionally keeps common Minecraft families in code and
     * consults them only when categories.yml does not define the requested category.
     * Keeping that precedence is important: the data-defined category is authoritative.
     */
    private boolean matchesBuiltInCategory(String key, String tag) {
        String bare = bareName(key);
        return switch (tag) {
            case "logs" -> key.equals("#logs") || bare.equals("log") || bare.equals("wood")
                    || bare.endsWith("_log") || bare.endsWith("_logs") || bare.endsWith("_wood")
                    || bare.endsWith("_woods") || bare.endsWith("_stem") || bare.endsWith("_stems")
                    || bare.endsWith("_hyphae") || bare.contains("log_") || bare.contains("_log_");
            case "leaves" -> isLeafLike(key);
            case "planks" -> bare.endsWith("_planks") && !isVerticalPlanks(key);
            case "stone" -> bare.equals("stone") || bare.equals("deepslate") || bare.endsWith("stone");
            case "natural_stone" -> bare.equals("stone") || bare.equals("deepslate") || bare.equals("tuff")
                    || bare.equals("calcite") || bare.endsWith("_stone") || bare.endsWith("stone")
                    || bare.endsWith("_deepslate");
            case "decorative_stone" -> bare.equals("andesite") || bare.equals("granite") || bare.equals("diorite")
                    || bare.equals("tuff") || bare.equals("calcite") || bare.contains("limestone")
                    || bare.contains("marble") || bare.contains("slate") || bare.contains("travertine");
            case "basic_ore_sources" -> bare.equals("copper_ore") || bare.equals("deepslate_copper_ore")
                    || bare.equals("clay") || bare.endsWith(":clay");
            case "ores" -> bare.endsWith("_ore") || bare.contains("raw_");
            case "fuel" -> bare.equals("coal") || bare.equals("charcoal") || bare.endsWith("_coal")
                    || bare.contains("coal") || bare.contains("charcoal");
            case "seeds" -> bare.endsWith("_seeds") || bare.endsWith("_seed") || bare.equals("wheat_seeds")
                    || bare.contains("seed") || bare.equals("carrot") || bare.equals("carrots")
                    || bare.equals("potato") || bare.equals("potatoes") || bare.equals("beetroot")
                    || bare.equals("beetroots") || bare.equals("cabbage") || bare.equals("cabbages")
                    || bare.equals("tomato") || bare.equals("tomatoes") || bare.equals("onion")
                    || bare.equals("onions") || bare.equals("rice") || bare.equals("corn")
                    || bare.equals("maize") || bare.equals("flax");
            case "crops" -> bare.equals("wheat") || bare.equals("carrots") || bare.equals("potatoes")
                    || bare.equals("beetroots") || bare.contains("crop") || bare.contains("cabbage")
                    || bare.contains("tomato") || bare.contains("onion") || bare.contains("rice")
                    || bare.contains("corn") || bare.contains("sugar_cane");
            case "animals" -> true;
            case "animal_feed" -> bare.equals("wheat") || bare.equals("wheat_seeds") || bare.endsWith("_seeds")
                    || bare.endsWith("_seed") || bare.equals("carrot") || bare.equals("potato")
                    || bare.equals("beetroot") || bare.equals("apple") || bare.equals("golden_carrot")
                    || bare.equals("hay_block") || bare.contains("animal_feed") || bare.contains("fodder");
            case "fences" -> bare.endsWith("_fence") && !bare.endsWith("_fence_gate");
            case "fence_gates" -> bare.endsWith("_fence_gate");
            case "shears" -> bare.equals("shears") || bare.endsWith("_shears") || bare.contains("shears");
            case "bonded_leather" -> bare.equals("bonded_leather") || bare.endsWith("_bonded_leather")
                    || bare.contains("bonded_leather");
            case "leather_canteens" -> bare.equals("leather_canteen") || bare.endsWith("_leather_canteen")
                    || bare.contains("leather_canteen") || bare.contains("canteen");
            case "rain_collectors" -> bare.equals("rain_collector") || bare.endsWith("_rain_collector")
                    || bare.contains("rain_collector");
            case "water_purifiers" -> bare.equals("water_purifier") || bare.endsWith("_water_purifier")
                    || bare.contains("water_purifier") || bare.contains("water_filter");
            case "campfires" -> bare.equals("campfire") || bare.equals("soul_campfire")
                    || bare.endsWith("_campfire") || bare.contains("campfire");
            case "fire_pits" -> bare.equals("fire_pit") || bare.endsWith("_fire_pit") || bare.contains("fire_pit");
            case "straw_blocks" -> bare.contains("straw_block") || bare.contains("straw_bale")
                    || bare.contains("straw_bales") || bare.contains("thatch");
            case "wool" -> bare.endsWith("_wool") || bare.equals("wool");
            case "dyes" -> bare.endsWith("_dye") || bare.equals("dye");
            case "dyed_blocks" -> bare.contains("terracotta") || bare.contains("wool")
                    || bare.contains("candle") || bare.contains("concrete");
            case "forage" -> bare.contains("berries") || bare.contains("mushroom") || bare.contains("roots")
                    || bare.contains("wild") || bare.contains("apple") || bare.contains("nut");
            case "dye_plants" -> isFlowerOrCactus(key);
            case "fiber" -> isLegacyFiber(key);
            case "ropecraft_fiber" -> isRopecraftFiber(key);
            case "wooden_stairs" -> isWoodenStair(key);
            case "wooden_slabs" -> isWoodenSlab(key);
            case "wooden_doors" -> isWoodenDoor(key);
            case "wooden_trapdoors" -> isWoodenTrapdoor(key);
            case "vertical_planks" -> isVerticalPlanks(key);
            case "timber_frame_recipes" -> isSupplementariesTimberFrame(key);
            case "ladders_chutes" -> bare.equals("ladder") || bare.endsWith("_ladder") || bare.contains("ladder")
                    || bare.equals("chute") || bare.endsWith("_chute") || bare.contains("chute");
            case "animal_products" -> bare.contains("egg") || bare.contains("milk") || bare.endsWith("_wool")
                    || bare.equals("wool") || bare.equals("leather");
            case "animal_loot" -> matches(key, "#raw_meat") || bare.equals("leather")
                    || bare.endsWith(":leather") || bare.contains("egg") || bare.contains("milk")
                    || bare.endsWith("_wool") || bare.equals("wool");
            case "raw_food" -> matches(key, "#raw_meat") || matches(key, "#raw_fish")
                    || matches(key, "#fish") || matches(key, "#forage") || matches(key, "#seeds")
                    || bare.contains("mushroom") || bare.contains("carrot") || bare.contains("potato")
                    || bare.contains("beetroot") || bare.contains("cabbage") || bare.contains("onion")
                    || bare.contains("tomato") || bare.contains("rice") || bare.contains("corn")
                    || bare.contains("wheat");
            case "grains" -> bare.equals("wheat") || bare.contains("rice") || bare.contains("oat")
                    || bare.contains("barley") || bare.contains("rye") || bare.contains("grain")
                    || bare.contains("corn") || bare.contains("maize");
            case "filter_materials" -> bare.equals("sand") || bare.equals("gravel") || bare.equals("charcoal")
                    || bare.equals("coal") || bare.contains("sand") || bare.contains("gravel")
                    || bare.contains("charcoal");
            case "heat_sources" -> bare.equals("campfire") || bare.equals("soul_campfire")
                    || bare.equals("furnace") || bare.equals("smoker") || bare.contains("fire_pit")
                    || bare.contains("stove") || bare.contains("kiln") || bare.contains("oven");
            case "ink_materials" -> bare.equals("ink_sac") || bare.equals("black_dye")
                    || bare.equals("charcoal") || bare.contains("ink") || bare.contains("soot");
            case "wax_materials" -> bare.equals("honeycomb") || bare.contains("wax")
                    || bare.contains("tallow") || bare.contains("fat");
            case "storage_blocks" -> isStorageLike(bare);
            case "raw_meat" -> bare.equals("beef") || bare.equals("porkchop") || bare.equals("mutton")
                    || bare.equals("chicken") || bare.equals("rabbit") || bare.contains("raw_")
                    || bare.contains("venison") || bare.contains("goat") || bare.contains("duck")
                    || bare.contains("ribs");
            case "cooked_meat" -> bare.startsWith("cooked_") || bare.equals("steak");
            case "raw_fish", "fish" -> bare.contains("cod") || bare.contains("salmon") || bare.contains("fish")
                    || bare.contains("catfish") || bare.contains("blobfish") || bare.contains("trout")
                    || bare.contains("tuna") || bare.contains("perch") || bare.contains("bass")
                    || bare.contains("herring") || bare.contains("gar") || bare.contains("carp")
                    || bare.contains("minnow") || bare.contains("muskellunge") || bare.contains("arapaima")
                    || bare.contains("bayad") || bare.contains("boulti") || bare.contains("capitaine")
                    || bare.contains("piranha") || bare.contains("pollock") || bare.contains("tambaqui")
                    || bare.contains("synodontis") || bare.contains("bluegill") || bare.contains("fillet")
                    || bare.contains("jellyfish") || bare.contains("goldfish") || bare.contains("halibut")
                    || bare.contains("grouper") || bare.contains("blackfish") || bare.contains("shrooma")
                    || bare.contains("leech") || bare.contains("sushi") || bare.contains("fish_bones");
            case "cooked_fish" -> bare.equals("cooked_cod") || bare.equals("cooked_salmon")
                    || (bare.startsWith("cooked_") && matches("minecraft:" + bare.substring("cooked_".length()), "#fish"))
                    || bare.contains("cooked_fish") || bare.contains("fish_fillet_cooked");
            case "soups_stews" -> bare.contains("stew") || bare.contains("soup") || bare.contains("broth")
                    || bare.contains("chowder") || bare.contains("curry") || bare.contains("stock")
                    || bare.contains("porridge") || bare.contains("gumbo") || bare.contains("cabbage_roll");
            case "cooking_stations" -> bare.equals("campfire") || bare.equals("soul_campfire")
                    || bare.equals("smoker") || bare.contains("cooking_pot") || bare.contains("stove")
                    || bare.contains("skillet") || bare.contains("cuisine") || bare.contains("kitchen");
            case "row_boats" -> (bare.endsWith("_boat") && !bare.endsWith("_chest_boat"))
                    || (bare.endsWith("_raft") && !bare.endsWith("_chest_raft"));
            case "boats" -> bare.endsWith("_boat") || bare.endsWith("_boats") || bare.endsWith("_chest_boat")
                    || bare.endsWith("_chest_boats") || bare.contains("rowboat") || isRaftLike(bare);
            case "fishing_gear" -> bare.contains("fishing_line") || bare.endsWith("_hook")
                    || bare.contains("tackle_box") || bare.contains("bobber") || bare.contains("worm_farm");
            case "fish_foods" -> bare.contains("fish_fillet") || bare.contains("sushi")
                    || bare.contains("turtle_soup") || bare.contains("fish_oil")
                    || (bare.startsWith("cooked_") && matches("minecraft:" + bare.substring("cooked_".length()), "#fish"));
            case "composters" -> bare.equals("composter") || bare.contains("composter") || bare.contains("compost");
            case "enclosure_blocks" -> bare.endsWith("_fence") || bare.endsWith("_fence_gate")
                    || bare.contains("fence");
            case "wool_goods" -> bare.endsWith("_wool") || bare.endsWith("_bed")
                    || bare.endsWith("_carpet") || bare.endsWith("_banner");
            case "leather_goods" -> bare.contains("leather_helmet") || bare.contains("leather_chestplate")
                    || bare.contains("leather_leggings") || bare.contains("leather_boots")
                    || bare.contains("bonded_leather") || bare.contains("leather_canteen");
            case "future_tech" -> isFutureTech(key.contains(":") ? key.substring(0, key.indexOf(':')) : "minecraft", bare);
            case "wood_parts" -> bare.contains("stairs") || bare.contains("slab")
                    || bare.contains("door") || bare.contains("trapdoor");
            case "trapdoors" -> bare.endsWith("_trapdoor");
            case "signs" -> bare.endsWith("_sign") || bare.endsWith("_hanging_sign") || bare.contains("sign_post");
            case "stone_parts" -> bare.contains("stone") || bare.contains("brick") || bare.contains("slab")
                    || bare.contains("stairs") || bare.contains("wall");
            case "stone_tools" -> bare.startsWith("stone_") && isToolBareName(bare);
            case "wood_tools" -> (bare.startsWith("wooden_") || bare.startsWith("wood_")) && isToolBareName(bare);
            case "basic_stone_parts" -> bare.contains("stone")
                    && (bare.contains("slab") || bare.contains("stairs") || bare.contains("wall"));
            case "candles" -> bare.endsWith("_candle") || bare.equals("candle");
            case "copper_goods" -> bare.contains("copper");
            case "basic_tools" -> isToolBareName(bare);
            case "knives" -> bare.equals("knife") || bare.endsWith("_knife")
                    || bare.contains("knife") || bare.contains("fillet_blade");
            case "hay_straw" -> bare.equals("hay_block") || bare.equals("hay_bale")
                    || bare.endsWith("_hay_block") || bare.endsWith("_hay_bale") || bare.equals("straw")
                    || bare.endsWith("_straw") || bare.contains("straw_bale") || bare.contains("straw_block")
                    || bare.contains("thatch") || bare.contains("haystack") || bare.contains("hay_stack");
            case "stick_stacks" -> bare.equals("stick_stack") || bare.endsWith("_stick_stack")
                    || bare.contains("stick_stack") || bare.contains("stacked_stick");
            case "smokers" -> bare.equals("smoker") || bare.endsWith("_smoker") || bare.contains("smoker");
            case "blast_furnaces" -> bare.equals("blast_furnace") || bare.endsWith("_blast_furnace")
                    || bare.contains("blast_furnace");
            case "hoes" -> bare.endsWith("_hoe");
            case "axes" -> bare.endsWith("_axe");
            case "dirt" -> bare.equals("dirt") || bare.equals("coarse_dirt") || bare.equals("rooted_dirt");
            case "grass" -> bare.equals("grass_block") || bare.equals("podzol") || bare.equals("mycelium");
            default -> false;
        };
    }

    private static String bareName(String key) {
        int separator = key.indexOf(':');
        return separator >= 0 ? key.substring(separator + 1) : key;
    }

    private static boolean isSeedLike(String key) {
        String bare = bareName(key);
        return bare.equals("seed") || bare.equals("seeds") || bare.endsWith("_seed")
                || bare.endsWith("_seeds") || bare.contains("seed");
    }

    private static boolean isLegacyFiber(String key) {
        if (isSeedLike(key)) {
            return false;
        }
        String bare = bareName(key);
        return bare.equals("string") || bare.equals("flax") || bare.contains("fiber")
                || bare.contains("fibre") || bare.contains("straw") || bare.contains("hemp")
                || bare.contains("jute") || bare.contains("rope") || bare.contains("cordage");
    }

    private static boolean isRopecraftFiber(String key) {
        if (isSeedLike(key)) {
            return false;
        }
        String bare = bareName(key);
        return bare.equals("string") || bare.equals("flax") || bare.contains("fiber")
                || bare.contains("fibre") || bare.contains("hemp") || bare.contains("jute")
                || bare.contains("cordage");
    }

    private static boolean isLeafLike(String key) {
        String bare = bareName(key);
        return bare.equals("leaves") || bare.endsWith("_leaves") || bare.endsWith("_leaf")
                || bare.contains("leaf_block") || bare.contains("leaves");
    }

    private static boolean isFlowerOrCactus(String key) {
        String bare = bareName(key);
        if (bare.equals("cactus") || bare.equals("cacti") || bare.contains("cactus") || bare.contains("cacti")) {
            return true;
        }
        if (bare.contains("flower") || bare.contains("blossom") || bare.contains("petals")) {
            return true;
        }
        return bare.equals("dandelion") || bare.equals("poppy") || bare.equals("blue_orchid")
                || bare.equals("allium") || bare.equals("azure_bluet") || bare.equals("red_tulip")
                || bare.equals("orange_tulip") || bare.equals("white_tulip") || bare.equals("pink_tulip")
                || bare.equals("oxeye_daisy") || bare.equals("cornflower") || bare.equals("lily_of_the_valley")
                || bare.equals("wither_rose") || bare.equals("sunflower") || bare.equals("lilac")
                || bare.equals("rose_bush") || bare.equals("peony") || bare.equals("torchflower")
                || bare.equals("pitcher_plant") || bare.equals("spore_blossom") || bare.contains("orchid")
                || bare.contains("tulip") || bare.contains("daisy") || bare.contains("bluet")
                || bare.contains("lily") || bare.contains("rose") || bare.contains("lilac")
                || bare.contains("peony") || bare.contains("sunflower") || bare.contains("cornflower");
    }

    private static boolean isWoodenFamily(String bare) {
        return bare.contains("oak") || bare.contains("spruce") || bare.contains("birch")
                || bare.contains("jungle") || bare.contains("acacia") || bare.contains("dark_oak")
                || bare.contains("mangrove") || bare.contains("cherry") || bare.contains("bamboo")
                || bare.contains("crimson") || bare.contains("warped") || bare.contains("fir")
                || bare.contains("redwood") || bare.contains("mahogany") || bare.contains("jacaranda")
                || bare.contains("palm") || bare.contains("willow") || bare.contains("hellbark")
                || bare.contains("azalea") || bare.contains("blossom") || bare.contains("maple")
                || bare.contains("pine") || bare.contains("dead") || bare.contains("magic")
                || bare.contains("umbran") || bare.contains("wood") || bare.contains("plank");
    }

    private static boolean isWoodenStair(String key) {
        String bare = bareName(key);
        return bare.endsWith("_stairs") && isWoodenFamily(bare);
    }

    private static boolean isWoodenSlab(String key) {
        String bare = bareName(key);
        return bare.endsWith("_slab") && isWoodenFamily(bare);
    }

    private static boolean isWoodenDoor(String key) {
        String bare = bareName(key);
        return bare.endsWith("_door") && !bare.endsWith("_trapdoor") && isWoodenFamily(bare);
    }

    private static boolean isWoodenTrapdoor(String key) {
        String bare = bareName(key);
        return bare.endsWith("_trapdoor") && isWoodenFamily(bare);
    }

    private static boolean isVerticalPlanks(String key) {
        String bare = bareName(key);
        return bare.startsWith("vertical_") && bare.endsWith("_planks");
    }

    private static boolean isSupplementariesTimberFrame(String key) {
        return key.equals("supplementaries:timber_frame") || key.equals("supplementaries:timber_brace")
                || key.equals("supplementaries:timber_cross_brace");
    }

    private static boolean isStorageLike(String bare) {
        if (bare.contains("chestplate")) {
            return false;
        }
        return bare.equals("chest") || bare.endsWith("_chest") || bare.endsWith("_trapped_chest")
                || bare.contains("_chest_") || bare.equals("barrel") || bare.endsWith("_barrel")
                || bare.contains("_barrel_") || bare.contains("crate") || bare.contains("cabinet")
                || bare.contains("basket") || bare.contains("storage");
    }

    private static boolean isRaftLike(String bare) {
        return bare.equals("raft") || bare.endsWith("_raft") || bare.endsWith("_rafts")
                || bare.endsWith("_chest_raft") || bare.endsWith("_chest_rafts") || bare.contains("_raft_");
    }

    private static boolean isFutureTech(String namespace, String bare) {
        if (namespace.equals("create") || namespace.equals("tfmg") || namespace.equals("createaddition")
                || namespace.equals("createbigcannons") || namespace.equals("createdeco")) {
            return bare.contains("andesite_alloy") || bare.contains("cogwheel") || bare.contains("shaft")
                    || bare.contains("gearbox") || bare.contains("gearshift") || bare.contains("clutch")
                    || bare.contains("mechanical") || bare.contains("water_wheel") || bare.contains("windmill")
                    || bare.contains("millstone") || bare.contains("encased_fan") || bare.contains("deployer")
                    || bare.contains("basin") || bare.contains("mixer") || bare.contains("press")
                    || bare.contains("belt") || bare.contains("chute") || bare.contains("brass")
                    || bare.contains("casing") || bare.contains("funnel") || bare.contains("tunnel")
                    || bare.contains("chain_conveyor") || bare.contains("bearing")
                    || bare.contains("content_observer") || bare.contains("analog_lever")
                    || bare.contains("attribute_filter") || bare.contains("filter") || bare.contains("toolbox")
                    || bare.contains("valve_handle") || bare.contains("flywheel")
                    || bare.contains("cart_assembler") || bare.contains("minecart_coupling")
                    || bare.contains("cartridge") || bare.contains("cannon") || bare.contains("electron")
                    || bare.contains("engine") || bare.contains("generator") || bare.contains("motor")
                    || bare.contains("dynamo") || bare.contains("crushing_wheel") || bare.contains("crushing")
                    || bare.contains("contraption") || bare.equals("controls") || bare.contains("controller_rail")
                    || bare.contains("backtank") || bare.contains("diving") || bare.contains("copper_sheet")
                    || bare.contains("cardboard_sword");
        }
        return bare.contains("mechanical_") || bare.contains("engine") || bare.contains("motor")
                || bare.contains("generator") || bare.contains("minecart") || bare.contains("flywheel")
                || bare.contains("crushing_wheel");
    }

    private static boolean isToolBareName(String bare) {
        return bare.endsWith("_pickaxe") || bare.endsWith("_axe") || bare.endsWith("_shovel")
                || bare.endsWith("_hoe") || bare.endsWith("_sword");
    }

    private void validateCounts(DeclaredCounts declared, boolean requireBundledCounts) {
        if (!requireBundledCounts) {
            if (nodes.isEmpty() || eras.isEmpty() || classes.isEmpty()) {
                throw new IllegalStateException("Published HistoriaEras technology data may not be empty");
            }
            return;
        }
        Objects.requireNonNull(declared, "counts");
        int requirementCount = nodes.values().stream().mapToInt(node -> node.requirements().size()).sum();
        int recipeEntryCount = nodes.values().stream().mapToInt(node -> node.unlockRecipes().size()).sum();
        int unlockActionCount = nodes.values().stream().mapToInt(node -> node.unlockActions().size()).sum();

        requireCount("nodes", nodes.size(), EXPECTED_NODES, declared.nodes());
        requireCount("requirements", requirementCount, EXPECTED_REQUIREMENTS, declared.requirements());
        requireCount("recipe entries", recipeEntryCount, EXPECTED_RECIPE_ENTRIES, declared.recipeEntries());
        requireCount("unlock actions", unlockActionCount, EXPECTED_UNLOCK_ACTIONS, declared.unlockActions());
        requireCount("categories", categories.size(), EXPECTED_CATEGORIES, declared.categories());
        requireCount("gates", gates.size(), EXPECTED_GATES, declared.gates());
        requireCount("stations", stations.size(), EXPECTED_STATIONS, declared.stations());
        requireCount("eras", eras.size(), EXPECTED_ERAS, declared.eras());
        requireCount("enabled classes", classes.size(), EXPECTED_ENABLED_CLASSES, declared.classes());
        if (classes.values().stream().anyMatch(classDefinition -> !classDefinition.enabled())) {
            throw new IllegalStateException("HistoriaEras technology resource contains disabled class metadata");
        }
    }

    private void validateReferences() {
        for (TechNode node : nodes.values()) {
            if (!eras.containsKey(normalizeId(node.era()))) {
                throw new IllegalStateException("Technology " + node.id() + " references unknown era " + node.era());
            }
            String classId = normalizeId(node.classId());
            if (!classId.equals("universal") && !classes.containsKey(classId)) {
                throw new IllegalStateException("Technology " + node.id() + " references unknown class " + node.classId());
            }

            Set<String> requirementIds = new HashSet<>();
            for (String routeClass : node.sharedRoutes().keySet()) {
                if (!classes.containsKey(routeClass) || node.sharedRoutes().get(routeClass).isEmpty()) {
                    throw new IllegalStateException("Invalid shared route in " + node.id());
                }
            }
            for (String prerequisite : allPrerequisites(node)) {
                String prerequisiteId = normalizeId(prerequisite);
                if (prerequisiteId.equals(normalizeId(node.id())) || !nodes.containsKey(prerequisiteId)) {
                    throw new IllegalStateException("Technology " + node.id()
                            + " has invalid prerequisite " + prerequisite);
                }
            }
            for (TechRequirement requirement : node.requirements()) {
                if (!requirementIds.add(normalizeId(requirement.id()))) {
                    throw new IllegalStateException("Technology " + node.id()
                            + " has duplicate requirement " + requirement.id());
                }
                if (!REQUIREMENT_ACTIONS.contains(normalizeId(requirement.action()))) {
                    throw new IllegalStateException("Technology " + node.id()
                            + " uses unsupported action " + requirement.action());
                }
                if (!Double.isFinite(requirement.amount()) || requirement.amount() <= 0.0D) {
                    throw new IllegalStateException("Technology " + node.id()
                            + " has invalid requirement amount for " + requirement.id());
                }
                if (requirement.targets().isEmpty()) {
                    throw new IllegalStateException("Technology " + node.id()
                            + " has no targets for requirement " + requirement.id());
                }
                validateMatchers("requirement " + node.id() + "/" + requirement.id(), requirement.targets());
            }
            validateMatchers("recipe unlocks for " + node.id(), node.unlockRecipes());
        }

        for (TechGate gate : gates) {
            boolean intentionalFutureLock = normalizeId(gate.id()).equals("future_tech")
                    && normalizeId(gate.nodeId()).equals("future_engineering");
            if (node(gate.nodeId()) == null && !intentionalFutureLock) {
                throw new IllegalStateException("Gate " + gate.id() + " references unknown node " + gate.nodeId());
            }
            String gateClass = normalizeId(gate.className());
            if (!gateClass.equals("universal") && !classes.containsKey(gateClass) && !intentionalFutureLock) {
                throw new IllegalStateException("Gate " + gate.id() + " references unknown class " + gate.className());
            }
            if (gate.targets().isEmpty()) {
                throw new IllegalStateException("Gate " + gate.id() + " has no targets");
            }
            validateMatchers("gate " + gate.id(), gate.targets());
        }
        for (TechStation station : stations.values()) {
            if (node(station.nodeId()) == null) {
                throw new IllegalStateException("Station " + station.id()
                        + " references unknown node " + station.nodeId());
            }
            if (station.targets().isEmpty()) {
                throw new IllegalStateException("Station " + station.id() + " has no targets");
            }
            String stationClass = normalizeId(station.className());
            if (!stationClass.equals("universal") && !classes.containsKey(stationClass)) {
                throw new IllegalStateException("Station " + station.id()
                        + " references unknown class " + station.className());
            }
            validateMatchers("station " + station.id(), station.targets());
        }

        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String nodeId : nodes.keySet()) {
            validateAcyclic(nodeId, visited, visiting);
        }
    }

    private void validateMatcherSemantics() {
        requireMatch("minecraft:oak_log", "#logs", true);
        requireMatch("minecraft:oak_planks", "#planks", true);
        requireMatch("quark:vertical_oak_planks", "#planks", false);
        requireMatch("minecraft:wheat", "#crops", true);
        requireMatch("minecraft:cod", "#fish", true);
        requireMatch("minecraft:stone", "#fish", false);
        requireMatch("minecraft:dandelion", "#dye_plants", true);
        requireMatch("farmersdelight:cooking_pot", "#cooking_stations", true);
    }

    private void requireMatch(String target, String matcher, boolean expected) {
        boolean actual = matches(target, matcher);
        if (actual != expected) {
            throw new IllegalStateException("HistoriaEras matcher self-test failed: " + target + " vs "
                    + matcher + " was " + actual + ", expected " + expected);
        }
    }

    private void validateMatchers(String owner, List<String> matchers) {
        if (matchers == null) {
            throw new IllegalStateException("HistoriaEras " + owner + " has null targets");
        }
        for (String matcher : matchers) {
            if (matcher == null || matcher.isBlank()) {
                throw new IllegalStateException("HistoriaEras " + owner + " has a blank target");
            }
            String normalized = normalizeMatcher(matcher);
            if (normalized.startsWith("#")) {
                String categoryId = normalized.substring(1);
                if (!categories.containsKey(categoryId) && !BUILT_IN_CATEGORY_IDS.contains(categoryId)) {
                    throw new IllegalStateException("HistoriaEras " + owner
                            + " references unknown category #" + categoryId);
                }
            }
        }
    }

    private void validateAcyclic(String nodeId, Set<String> visited, Set<String> visiting) {
        if (visited.contains(nodeId)) {
            return;
        }
        if (!visiting.add(nodeId)) {
            throw new IllegalStateException("HistoriaEras technology prerequisite cycle includes " + nodeId);
        }
        for (String prerequisite : allPrerequisites(nodes.get(nodeId))) {
            validateAcyclic(normalizeId(prerequisite), visited, visiting);
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
    }

    private static void requireCount(String label, int actual, int expected, int declared) {
        if (actual != expected || declared != expected) {
            throw new IllegalStateException("Invalid HistoriaEras " + label + " count: loaded=" + actual
                    + ", declared=" + declared + ", expected=" + expected);
        }
    }

    private static List<String> allPrerequisites(TechNode node) {
        return java.util.stream.Stream.concat(node.prerequisites().stream(),
                node.sharedRoutes().values().stream().flatMap(List::stream)).distinct().toList();
    }

    private static boolean globMatches(String target, String matcher) {
        StringBuilder regex = new StringBuilder("^");
        int offset = 0;
        for (int wildcard = matcher.indexOf('*'); wildcard >= 0; wildcard = matcher.indexOf('*', offset)) {
            regex.append(Pattern.quote(matcher.substring(offset, wildcard))).append(".*");
            offset = wildcard + 1;
        }
        regex.append(Pattern.quote(matcher.substring(offset))).append('$');
        return Pattern.matches(regex.toString(), target);
    }

    private static String normalizeMatcher(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeId(String value) {
        return normalizeMatcher(value).replace(' ', '_').replace('-', '_');
    }

    private static <T> Map<String, T> immutableIndex(
            List<T> values,
            java.util.function.Function<T, String> idFunction,
            String kind
    ) {
        Objects.requireNonNull(values, kind + "s");
        Map<String, T> index = new LinkedHashMap<>();
        for (T value : values) {
            Objects.requireNonNull(value, kind);
            String id = normalizeId(Objects.requireNonNull(idFunction.apply(value), kind + " id"));
            if (index.putIfAbsent(id, value) != null) {
                throw new IllegalStateException("Duplicate HistoriaEras " + kind + " id: " + id);
            }
        }
        return Collections.unmodifiableMap(index);
    }

    private record DataFile(
            int schemaVersion,
            DeclaredCounts counts,
            List<TechNode> nodes,
            List<TechCategory> categories,
            List<TechGate> gates,
            List<TechStation> stations,
            List<TechEra> eras,
            List<TechClass> classes
    ) {
    }

    private record DeclaredCounts(
            int nodes,
            int requirements,
            int recipeEntries,
            int unlockActions,
            int categories,
            int gates,
            int stations,
            int eras,
            int classes
    ) {
    }
}
