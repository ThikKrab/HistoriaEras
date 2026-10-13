package com.historiamc.townyeras.server;

import com.historiamc.townyeras.tech.TechGate;
import com.historiamc.townyeras.tech.TechNode;
import com.historiamc.townyeras.tech.TechRegistry;
import com.historiamc.townyeras.tech.TechStation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.Util;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/** Resolves data-defined gates and produces one consistent denial explanation. */
public final class GateService {
    private static final long MESSAGE_COOLDOWN_MILLIS = 1_500L;
    private static final Map<String, Long> MESSAGE_COOLDOWNS = new ConcurrentHashMap<>();

    private static volatile TechRegistry registry;
    private static volatile BiPredicate<ServerPlayer, String> classAccess = (player, classId) -> false;
    private static volatile BiPredicate<ServerPlayer, String> nodeAccess = (player, nodeId) -> false;

    private GateService() {
    }

    public static void configure(
            TechRegistry technology,
            BiPredicate<ServerPlayer, String> selectedClass,
            BiPredicate<ServerPlayer, String> completedNode
    ) {
        registry = Objects.requireNonNull(technology);
        classAccess = Objects.requireNonNull(selectedClass);
        nodeAccess = Objects.requireNonNull(completedNode);
    }

    /** Returns {@code true} when a configured rule denied the attempted operation. */
    public static boolean deny(String type, String target, ServerPlayer player, boolean notify) {
        TechRegistry technology = registry;
        if (!isActive(player) || technology == null || AdminModeService.isEnabled(player)) {
            return false;
        }
        List<TechGate> gates = technology.gatesFor(type, target);
        if (gates.isEmpty()) {
            return false;
        }
        if (gates.stream().anyMatch(gate -> hasAccess(rule(gate, target), player))) return false;
        return deny(rule(gates.getFirst(), target), player, notify);
    }

    /**
     * Enforces every recipe declared by a technology node, in addition to the
     * older explicit craft gates. Multiple technologies that unlock the same
     * result are alternatives: access to any one of them permits the craft.
     */
    public static boolean denyCraftOutput(String outputId, ServerPlayer player, boolean notify) {
        if (com.historiamc.townyeras.tech.PublicUtilityPolicy.publicLadder(outputId)) return false;
        TechRegistry technology = registry;
        if (!isActive(player) || technology == null || AdminModeService.isEnabled(player)) {
            return false;
        }
        String output = outputId == null ? "minecraft:air" : outputId.toLowerCase(Locale.ROOT);
        LinkedHashMap<String, AccessRule> rules = new LinkedHashMap<>();

        for (TechGate gate : technology.gatesFor("craft", output)) {
            AccessRule rule = rule(gate, output);
            rules.putIfAbsent(rule.nodeId(), rule);
        }
        for (TechNode node : technology.nodes().values()) {
            boolean unlocksOutput = node.unlockRecipes().stream()
                    .anyMatch(matcher -> technology.matches(output, matcher));
            if (!unlocksOutput) {
                continue;
            }
            String className = technology.classes().containsKey(node.classId())
                    ? technology.classes().get(node.classId()).displayName()
                    : node.classId();
            rules.putIfAbsent(node.id(), new AccessRule(
                    "recipe_" + node.id(), "craft", className, node.id(), node.displayName(), output
            ));
        }

        if (rules.isEmpty() || rules.values().stream().anyMatch(rule -> hasAccess(rule, player))) {
            return false;
        }
        AccessRule explanation = rules.values().stream()
                .filter(rule -> hasRequiredClass(rule, player))
                .findFirst()
                .orElse(rules.values().iterator().next());
        if (notify) {
            notifyDenied(player, explanation, hasRequiredClass(explanation, player));
        }
        return true;
    }

    /** Returns {@code true} when an explicit functional workstation denied interaction. */
    public static boolean denyStation(String blockId, ServerPlayer player, boolean notify) {
        TechRegistry technology = registry;
        if (!isActive(player) || technology == null || AdminModeService.isEnabled(player)) {
            return false;
        }
        for (TechStation station : technology.stations().values()) {
            if (station.targets().stream().noneMatch(target -> technology.matches(blockId, target))) {
                continue;
            }
            return deny(new AccessRule(
                    station.gateId(),
                    "interact",
                    station.className(),
                    station.nodeId(),
                    station.nodeName(),
                    blockId
            ), player, notify);
        }
        return false;
    }

    /** Applies both configured processing gates and the original output-specific exceptions. */
    public static boolean denyOutput(String blockId, String outputId, ServerPlayer player, boolean notify) {
        TechRegistry technology = registry;
        if (!isActive(player) || technology == null || AdminModeService.isEnabled(player)) {
            return false;
        }
        String block = blockId == null ? "minecraft:air" : blockId.toLowerCase(Locale.ROOT);
        String output = outputId == null ? "minecraft:air" : outputId.toLowerCase(Locale.ROOT);

        if (denyCraftOutput(output, player, notify)) return true;
        if (deny("extract_output", block, player, notify)) {
            return true;
        }

        if (isPlainFurnace(block)) {
            if (deny("furnace_output", output, player, notify)) {
                return true;
            }
            if (output.equals("farmersdelight:straw") && !nodeAccess.test(player, "ropecraft")) {
                return denySpecial(player, "furnace_straw", "Farmer: Ropecraft", notify);
            }
            if (technology.matches(output, "#cooked_fish")
                    && !nodeAccess.test(player, "fishing")
                    && !nodeAccess.test(player, "everyday_cookery")) {
                return denySpecial(player, "furnace_fish", "Mariner: Fishing or Everyday Cookery", notify);
            }
            if (isEverydayCookeryFurnaceOutput(output)
                    && !nodeAccess.test(player, "everyday_cookery")) {
                return denySpecial(player, "everyday_furnace", "Everyday Cookery", notify);
            }
            if (output.equals("corn_delight:cornbread") && !nodeAccess.test(player, "simple_bread")) {
                if (!nodeAccess.test(player, "grainery"))
                    return denySpecial(player, "furnace_cornbread", "Breadmaking or Simple Bread", notify);
            }
        }

        if (technology.matches(block, "#cooking_pots")) {
            if (com.historiamc.townyeras.tech.RecipeOutputPolicy.declaredAndAccessible(
                    technology, output, id -> nodeAccess.test(player, id))) return false;
            TechNode cookery = technology.node("simmering");
            if (nodeAccess.test(player, "simmering") && cookery != null && cookery.unlockRecipes().contains(output)) {
                return false;
            }
            if (nodeAccess.test(player, "everyday_cookery") && isEverydayCookeryPotOutput(output)) {
                return false;
            }
            String requirement = isEverydayCookeryPotOutput(output)
                    ? "Everyday Cookery or Chef: Simmering"
                    : "Chef: Simmering";
            return denySpecial(player, "cooking_pot_output", requirement, notify);
        }
        return false;
    }

    private static boolean deny(AccessRule rule, ServerPlayer player, boolean notify) {
        boolean hasClass = hasRequiredClass(rule, player);

        // Classical Everyday Cookery explicitly grants limited cooking-pot operation.
        if (rule.type().equals("interact")
                && rule.gateId().contains("cooking_pot")
                && nodeAccess.test(player, "everyday_cookery")) {
            return false;
        }

        if (hasAccess(rule, player)) {
            return false;
        }
        if (notify) {
            notifyDenied(player, rule, hasClass);
        }
        return true;
    }

    private static boolean hasAccess(AccessRule rule, ServerPlayer player) {
        // Node access already applies class eligibility and persistent admin overrides.
        return nodeAccess.test(player, rule.nodeId());
    }

    private static boolean hasRequiredClass(AccessRule rule, ServerPlayer player) {
        TechNode node = registry.node(rule.nodeId());
        if (node != null && !node.sharedRoutes().isEmpty()) {
            return node.sharedRoutes().keySet().stream().anyMatch(id -> classAccess.test(player, id));
        }
        String classId = normalize(rule.className());
        boolean classRequired = !classId.isBlank()
                && !classId.equals("universal")
                && !classId.equals("unknown");
        return !classRequired || classAccess.test(player, classId);
    }

    private static void notifyDenied(ServerPlayer player, AccessRule rule, boolean hasClass) {
        String cooldownKey = player.getUUID() + "|" + rule.type() + "|" + rule.gateId();
        long now = Util.getMillis();
        Long previous = MESSAGE_COOLDOWNS.get(cooldownKey);
        if (previous != null && now - previous < MESSAGE_COOLDOWN_MILLIS) {
            return;
        }

        MESSAGE_COOLDOWNS.put(cooldownKey, now);
        player.displayClientMessage(lockedTechnologyMessage(rule.nodeName()), true);
    }

    static Component lockedTechnologyMessage(String technology) {
        String name = technology == null || technology.isBlank() ? "the required technology" : technology;
        return Component.literal("HistoriaEras: Unlock ").withStyle(ChatFormatting.RED)
                .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" first.").withStyle(ChatFormatting.RED));
    }

    private static boolean denySpecial(ServerPlayer player, String gateId, String requirement, boolean notify) {
        if (!notify) {
            return true;
        }
        String cooldownKey = player.getUUID() + "|output|" + gateId;
        long now = Util.getMillis();
        Long previous = MESSAGE_COOLDOWNS.get(cooldownKey);
        if (previous == null || now - previous >= MESSAGE_COOLDOWN_MILLIS) {
            MESSAGE_COOLDOWNS.put(cooldownKey, now);
            player.displayClientMessage(lockedTechnologyMessage(requirement), true);
        }
        return true;
    }

    private static boolean isPlainFurnace(String blockId) {
        return (blockId.equals("minecraft:furnace") || blockId.endsWith(":furnace"))
                && !blockId.contains("blast_furnace")
                && !blockId.contains("smoker");
    }

    private static boolean isEverydayCookeryFurnaceOutput(String itemId) {
        if (itemId.equals("minecraft:baked_potato")) {
            return true;
        }
        String bare = bareName(itemId);
        return List.of(
                "cooked_bacon", "beef_patty", "cooked_minced_beef", "fish_fillet_cooked",
                "cooked_fish_fillet", "cooked_salmon_slice", "cooked_venison_chops",
                "venison_chops_cooked", "cooked_chicken_cuts", "cooked_mutton_chops",
                "cooked_cod_slice", "cooked_calamari", "smoked_calamari"
        ).contains(bare);
    }

    private static boolean isEverydayCookeryPotOutput(String itemId) {
        return List.of(
                "farmersdelight:cooked_rice", "minecraft:mushroom_stew", "minecraft:rabbit_stew",
                "farmersdelight:beef_stew", "farmersdelight:fish_stew", "farmersdelight:baked_cod_stew",
                "aquaculturedelight:bass_stew", "delightful:venison_stew",
                "fruitsdelight:fig_chicken_stew"
        ).contains(itemId);
    }

    private static String bareName(String id) {
        int separator = id.indexOf(':');
        return separator < 0 ? id : id.substring(separator + 1);
    }

    private static AccessRule rule(TechGate gate, String target) {
        return new AccessRule(
                gate.id(),
                gate.type(),
                gate.className(),
                gate.nodeId(),
                gate.nodeName(),
                target
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record AccessRule(
            String gateId,
            String type,
            String className,
            String nodeId,
            String nodeName,
            String target
    ) {
    }

    private static boolean isActive(ServerPlayer player) {
        return player != null && ServerSessionPolicy.isActive(player.server);
    }

    /** Reject arbitrary mod processing recipes, even when their workstation is unlocked. */
    public static boolean denyProcess(String station, net.minecraft.world.item.crafting.RecipeHolder<?> recipe,
                                      ServerPlayer player, boolean notify) {
        if (!isActive(player) || AdminModeService.isEnabled(player) || registry == null) return false;
        String id = recipe.id().toString();
        if (station.equals("farmersdelight:cutting_board") && id.equals("farmersdelight:cutting/mutton")
                && classAccess.test(player, "farmer") && ProgressService.prerequisitesMet(player, "shearing")) return false;
        if (denyStation(station, player, notify)) return true;
        String output = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                recipe.value().getResultItem(player.registryAccess()).getItem()).toString();
        if (denyCraftOutput(output, player, notify)) return true;
        if (processAllowed(station, id, output, node -> nodeAccess.test(player, node))) return false;
        String technology = registry.nodes().values().stream()
                .filter(node -> node.unlocks().processRecipes().contains(id))
                .map(TechNode::displayName).distinct().collect(java.util.stream.Collectors.joining(" or "));
        return denySpecial(player, "process_" + id, technology, notify);
    }

    static boolean denyOfflineProcess(String station, net.minecraft.world.item.crafting.RecipeHolder<?> recipe,
                                      net.minecraft.server.MinecraftServer server, java.util.UUID operator) {
        if (registry == null || recipe == null) return true;
        java.util.function.Predicate<String> access = id -> ProgressService.hasNodeAccess(server, operator, id);
        for (TechStation rule : registry.stations().values()) {
            if (rule.targets().stream().anyMatch(target -> registry.matches(station, target))
                    && !access.test(rule.nodeId())
                    && !(station.equals("farmersdelight:cooking_pot") && access.test("everyday_cookery"))) return true;
        }
        String output = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                recipe.value().getResultItem(server.registryAccess()).getItem()).toString();
        return !processAllowed(station, recipe.id().toString(), output, access);
    }

    static boolean processAllowed(String station, String id, String output,
                                  java.util.function.Predicate<String> access) {
        if (!com.historiamc.townyeras.tech.RecipeOutputPolicy.allowed(registry, output, access)) return false;
        boolean explicit = registry.nodes().values().stream().anyMatch(node ->
                node.unlocks().processRecipes().contains(id) && access.test(node.id()));
        if (explicit) return true;
        if (station.equals("farmersdelight:cooking_pot")) {
            if (com.historiamc.townyeras.tech.RecipeOutputPolicy.declaredAndAccessible(registry, output, access)) return true;
            TechNode cookery = registry.node("simmering");
            boolean allowed = cookery != null && cookery.unlockRecipes().contains(output)
                    && access.test("simmering");
            allowed |= access.test("everyday_cookery") && isEverydayCookeryPotOutput(output);
            if (allowed) return true;
        }
        if (station.equals("minecraft:smoker") && access.test("basic_butchery")
                && registry.matches(output, "#reviewed_fire_outputs")) return true;
        if (station.equals("minecraft:campfire") && access.test("firemaking")
                && registry.matches(output, "#reviewed_fire_outputs")) return true;
        if (station.equals("minecraft:furnace")) {
            if (output.equals("minecraft:charcoal") && access.test("furnace_basic_ore")) return true;
            if (!registry.gatesFor("furnace_output", output).isEmpty())
                return registry.gatesFor("furnace_output", output).stream().anyMatch(gate -> access.test(gate.nodeId()));
            if (access.test("everyday_cookery")
                    && (isEverydayCookeryFurnaceOutput(output) || registry.matches(output, "#reviewed_fire_outputs"))) return true;
            if (output.equals("corn_delight:cornbread") && access.test("simple_bread")) return true;
        }
        return false;
    }

    public static boolean denyLoomOutput(ServerPlayer player, net.minecraft.world.item.ItemStack output) {
        if (!isActive(player) || AdminModeService.isEnabled(player)) return false;
        if (!nodeAccess.test(player, "pigments"))
            return denySpecial(player, "loom_dye", "Pigments & Dyeing", true);
        var layers = output.get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS);
        // No reviewed Ancient node grants patterned banners. Plain colored banners use crafting.
        return layers != null && !layers.layers().isEmpty()
                && denySpecial(player, "loom_pattern", "a later banner-pattern technology", true);
    }
}
