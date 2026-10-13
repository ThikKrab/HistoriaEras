package com.historiamc.townyeras.server;

import com.historiamc.townyeras.HistoriaEras;
import com.historiamc.townyeras.tech.TechnologyManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Only invoked by the opt-in, isolated development-world test, never by ordinary gameplay. */
public final class ReviewedNodeSmokeTest {
    private ReviewedNodeSmokeTest() {}
    public static boolean run(ServerPlayer player) {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) return false;
        var data = HistoriaErasSavedData.get(player.server);
        var progress = data.player(player.getUUID());
        progress.setAdminMode(false);
        ProgressService.resetEverything(player);
        try {
            verifyClassicalExpansion(player);
            ProgressService.resetEverything(player);
            verifyAdminGrants(player);
            ProgressService.resetEverything(player);
            verifyPublicFarmingCarts(player);
            verifyNoTradeProgress(player);
            ProgressService.resetEverything(player);
            verifyPersonalSmelting(player);
            ProgressService.resetEverything(player);
            verifyFirePitRecipe(player);
            verifyCuttingBoardFeedback(player);
            ProgressService.resetEverything(player);
            var onboardingProbe = new PlayerProgress();
            onboardingProbe.onboarding().advance(45_000);
            var restoredProbe = PlayerProgress.load(onboardingProbe.save());
            require(restoredProbe.onboarding().pendingMillis() == 45_000, "Onboarding time save/reload");
            restoredProbe.onboarding().markTreeViewed();
            restoredProbe.onboarding().dismissTutorial();
            restoredProbe.clearEverything();
            var persistedProbe = PlayerProgress.load(restoredProbe.save());
            require(persistedProbe.onboarding().treeViewed() && persistedProbe.onboarding().tutorialDismissed(),
                    "Research reset must not reset onboarding history");
            ProgressService.adminSetClasses(player, "farmer", "carpenter");
            ProgressService.adminGrantNode(player, "craftsmanship");
            ProgressService.adminGrantNode(player, "woodworking");
            require(ProgressService.prerequisitesMet(player, "early_enclosures"), "Carpenter shared route");
            ProgressService.adminGrantNode(player, "early_agriculture");
            ProgressService.adminGrantNode(player, "early_enclosures");
            ProgressService.adminRevokeNode(player, "woodworking");
            require(ProgressService.hasNodeAccess(player, "early_enclosures"), "Shared node survives other valid route");
            require(GateService.denyCraftOutput("astikorcartsredux:wheel", player, false), "Locked wheel crafting");
            require(!GateService.deny("place", "minecraft:furnace", player, false), "Unrestricted furnace placement");
            require(GateService.denyStation("minecraft:furnace", player, false), "Locked furnace opening");
            require(GateService.deny("place", "minecraft:campfire", player, false), "Locked campfire placement");
            require(!GateService.deny("use_item", "toughasnails:empty_leather_canteen", player, false), "Canteen use stays unrestricted");

            var farming = TechnologyManager.current().node("early_agriculture");
            ProgressService.adminRevokeNode(player, "early_agriculture");
            ProgressService.recordAction(player, "obtain_item", "minecraft:wheat_seeds", 32);
            var harvest = farming.requirements().stream().filter(r -> r.action().equals("harvest_obtain_item")).findFirst().orElseThrow();
            require(progress.contribution(farming.id(), harvest.id()) == 0, "Trade cannot satisfy personal harvest");
            ProgressService.recordAction(player, "harvest_obtain_item", "minecraft:wheat_seeds", 32);
            require(progress.contribution(farming.id(), harvest.id()) == 32, "Personal harvest credit");

            ProgressService.resetEverything(player);
            ProgressService.adminSetClasses(player, "mariner", "chef");
            ProgressService.adminGrantNode(player, "fishing");
            var distinct = TechnologyManager.current().node("angling").requirements().stream()
                    .filter(r -> r.action().equals("distinct_fish")).findFirst().orElseThrow();
            ProgressService.recordAction(player, "distinct_fish", "minecraft:cod", 1);
            ProgressService.recordAction(player, "distinct_fish", "minecraft:cod", 1);
            ProgressService.recordAction(player, "distinct_fish", "minecraft:salmon", 1);
            require(progress.contribution("angling", distinct.id()) == 2, "Fish species deduplication");
            var loaded = PlayerProgress.load(progress.save());
            require(!loaded.addDistinct("angling", distinct.id(), "minecraft:cod"), "Fish species survive save/reload");

            var beef = player.server.getRecipeManager().byKey(ResourceLocation.parse("minecraft:cooked_beef_from_campfire_cooking")).orElseThrow();
            require(GateService.denyProcess("minecraft:campfire", beef, player, false), "Locked campfire recipe");
            ProgressService.adminGrantNode(player, "firemaking");
            require(!GateService.denyProcess("minecraft:campfire", beef, player, false), "Unlocked campfire recipe");
            ProgressService.adminGrantNode(player, "furnace_basic_ore");
            var charcoal = player.server.getRecipeManager().byKey(ResourceLocation.parse("minecraft:charcoal")).orElseThrow();
            require(!GateService.denyProcess("minecraft:furnace", charcoal, player, false), "Universal charcoal");
            var iron = player.server.getRecipeManager().byKey(ResourceLocation.parse("minecraft:iron_ingot_from_smelting_iron_ore")).orElseThrow();
            require(GateService.denyProcess("minecraft:furnace", iron, player, false), "Ore smelting remains locked");
            if (net.neoforged.fml.ModList.get().isLoaded("farmersdelight")) {
                var wheatDough = player.server.getRecipeManager().byKey(ResourceLocation.parse("farmersdelight:wheat_dough_from_water")).orElseThrow();
                ProgressService.adminGrantNode(player, "simmering");
                require(GateService.denyCraftOutput("farmersdelight:wheat_dough", player, false), "Pot access does not unlock grain preparation");
                ProgressService.adminGrantNode(player, "grain_preparation");
                require(!GateService.denyProcess("farmersdelight:cooking_pot", wheatDough, player, false), "Explicit dough process");
            }
            HistoriaEras.LOGGER.info("Reviewed Ancient node smoke tests passed: shared routes, harvest provenance actions, distinct fish persistence, workstation and process permissions");
            return true;
        } catch (RuntimeException failure) {
            HistoriaEras.LOGGER.error("Reviewed node smoke test failed", failure);
            return false;
        } finally {
            ProgressService.resetEverything(player);
            progress.setAdminMode(false);
            data.setDirty();
        }
    }
    private static void require(boolean result, String message) {
        if (!result) throw new IllegalStateException(message);
    }

    private static void verifyClassicalExpansion(ServerPlayer player) {
        var data = HistoriaErasSavedData.get(player.server);
        var oldTimeline = data.timeline().save();
        var tree = TechnologyManager.current();
        try {
            for (var node : tree.nodes().values()) {
                if (!node.id().startsWith("classical_")) continue;
                ProgressService.resetEverything(player);
                if (!node.classId().equals("universal"))
                    ProgressService.adminSetClasses(player, node.classId(), node.classId().equals("farmer") ? "chef" : "farmer");
                for (var parent : node.prerequisites()) ProgressService.adminGrantNode(player, parent);
                data.timeline().startEra("ancient");
                require(ProgressService.nodeStatus(player, node.id()) == ProgressService.NodeStatus.LOCKED_ERA, "Future era locked " + node.id());
                for (var req : node.requirements()) {
                    ProgressService.recordAction(player, req.action(), req.targets().getFirst(), req.amount());
                    require(data.player(player.getUUID()).contribution(node.id(), req.id()) == 0, "Future progress denied " + node.id());
                }
                data.timeline().startEra("classical");
                for (var req : node.requirements())
                    ProgressService.recordAction(player, req.action(), req.targets().getFirst(), req.amount());
                require(ProgressService.hasNodeAccess(player, node.id()), "Natural completion " + node.id());
                require(PlayerProgress.load(data.player(player.getUUID()).save()).completedNodesView().contains(node.id()),
                        "Completion persists " + node.id());
                for (String output : node.unlockRecipes()) {
                    require(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(output)), "Registered unlock " + output);
                    require(player.server.getRecipeManager().getRecipes().stream().anyMatch(recipe -> output.equals(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(recipe.value().getResultItem(player.registryAccess()).getItem()).toString())),
                            "Recipe exists " + output);
                    require(!GateService.denyCraftOutput(output, player, false), "Completion unlocks output " + output);
                }
            }
            ProgressService.resetEverything(player);
            data.timeline().startEra("classical");
            require(ProgressService.nodeStatus(player, "craftsmanship") == ProgressService.NodeStatus.AVAILABLE, "Ancient catch-up status");
            var craft = tree.node("craftsmanship");
            for (var req : craft.requirements()) ProgressService.recordAction(player, req.action(), req.targets().getFirst(), req.amount());
            require(ProgressService.hasNodeAccess(player, "craftsmanship"), "Ancient catch-up completion");
            verifyClassicalStonecutter(player);
            verifyBeeHarvest(player);
            verifyClassicalProcessing(player);
            HistoriaEras.LOGGER.info("Classical expansion passed: 19 nodes, future lock, natural completion, persistence, item/recipe registries, Ancient catch-up, stonecutter and honey harvesting");
        } finally {
            data.timeline().startEra(oldTimeline.getString("eraId"));
            data.timeline().setDate(oldTimeline.getInt("historicalYear"), oldTimeline.getInt("dayOfYear"));
            data.timeline().setPaused(oldTimeline.getBoolean("paused"));
            ProgressService.resetEverything(player);
        }
    }

    private static void verifyClassicalStonecutter(ServerPlayer player) {
        var position = player.blockPosition().offset(6, 1, 5);
        var level = player.serverLevel();
        var original = level.getBlockState(position);
        var originalMenu = player.containerMenu;
        try {
            ProgressService.resetEverything(player);
            level.setBlock(position, net.minecraft.world.level.block.Blocks.STONECUTTER.defaultBlockState(), 3);
            var menu = new net.minecraft.world.inventory.StonecutterMenu(71, player.getInventory(),
                    net.minecraft.world.inventory.ContainerLevelAccess.create(level, position));
            player.containerMenu = menu;
            menu.getSlot(0).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SMOOTH_STONE, 4));
            int selection = -1;
            for (int i = 0; i < menu.getRecipes().size(); i++)
                if (menu.getRecipes().get(i).value().getResultItem(player.registryAccess()).is(net.minecraft.world.item.Items.SMOOTH_STONE_SLAB)) selection = i;
            require(selection >= 0 && menu.clickMenuButton(player, selection), "Select stonecutting slab");
            require(!menu.getSlot(1).mayPickup(player), "Stonecutter normal pickup denied");
            require(menu.quickMoveStack(player, 1).isEmpty() && menu.getSlot(0).getItem().getCount() == 4, "Stonecutter shift click preserves inputs");
            ProgressService.adminGrantNode(player, "classical_roadworks");
            require(menu.getSlot(1).mayPickup(player), "Stonecutter grant allows pickup");
            require(!menu.quickMoveStack(player, 1).isEmpty() && menu.getSlot(0).getItem().getCount() == 3, "Stonecutter grant allows actual shift craft");
        } finally {
            player.containerMenu = originalMenu;
            level.setBlock(position, original, 3);
        }
    }

    private static void verifyBeeHarvest(ServerPlayer player) {
        ProgressService.resetEverything(player);
        ProgressService.adminSetClasses(player, "farmer", "chef");
        var pos = player.blockPosition().offset(7, 1, 5);
        var level = player.serverLevel();
        var original = level.getBlockState(pos);
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var oldHand = player.getItemInHand(hand).copy();
        var full = net.minecraft.world.level.block.Blocks.BEEHIVE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL, 5);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        try {
            level.setBlock(pos, full, 3);
            var shears = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHEARS);
            player.setItemInHand(hand, shears);
            full.useItemOn(shears, level, player, hand, hit);
            require(level.getBlockState(pos).getValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL) == 5 && shears.getDamageValue() == 0,
                    "Locked hive harvest preserves honey and shears");
            ProgressService.adminGrantNode(player, "apiculture");
            full.useItemOn(shears, level, player, hand, hit);
            require(HistoriaErasSavedData.get(player.server).player(player.getUUID()).contribution("classical_beeswax", "honeycomb") == 3,
                    "Actual shearing credits three honeycomb");
            level.setBlock(pos, full, 3);
            var bottle = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE);
            player.setItemInHand(hand, bottle);
            full.useItemOn(bottle, level, player, hand, hit);
            require(player.getItemInHand(hand).is(net.minecraft.world.item.Items.HONEY_BOTTLE), "Actual honey bottle produced");
            var progress = HistoriaErasSavedData.get(player.server).player(player.getUUID());
            require(progress.contribution("classical_beeswax", "honey") == 1, "Actual bottle credits one");
            ProgressService.recordAction(player, "obtain_item", "minecraft:honey_bottle", 64);
            require(progress.contribution("classical_beeswax", "honey") == 1, "Trade does not count honey");
        } finally {
            player.setItemInHand(hand, oldHand);
            level.setBlock(pos, original, 3);
        }
    }

    private static void verifyClassicalProcessing(ServerPlayer player) {
        ProgressService.resetEverything(player);
        ProgressService.adminSetClasses(player, "mason", "chef");
        ProgressService.adminGrantNode(player, "furnace_basic_ore");
        var recipes = player.server.getRecipeManager();
        var stone = recipes.byKey(ResourceLocation.parse("minecraft:stone")).orElseThrow();
        require(GateService.denyProcess("minecraft:furnace", stone, player, false), "Stone needs Stone Dressing");
        ProgressService.adminGrantNode(player, "stonecutting");
        require(!GateService.denyProcess("minecraft:furnace", stone, player, false), "Stone Dressing supplies stone-brick ingredient");
        var smooth = recipes.byKey(ResourceLocation.parse("minecraft:smooth_stone")).orElseThrow();
        require(GateService.denyProcess("minecraft:furnace", smooth, player, false), "Smooth stone needs Paved Roads");
        ProgressService.adminGrantNode(player, "classical_roadworks");
        require(!GateService.denyProcess("minecraft:furnace", smooth, player, false), "Paved Roads supplies smooth stone");
        ProgressService.adminGrantNode(player, "basic_butchery");
        var ham = recipes.byKey(ResourceLocation.parse("farmersdelight:smoked_ham")).orElseThrow();
        require(GateService.denyProcess("minecraft:smoker", ham, player, false), "Ancient smoking cannot bypass Classical ham gate");
        ProgressService.adminGrantNode(player, "classical_meat_preservation");
        require(!GateService.denyProcess("minecraft:smoker", ham, player, false), "Meat Preservation permits ham smoking");
        var offlineId = java.util.UUID.randomUUID();
        var offline = HistoriaErasSavedData.get(player.server).player(offlineId);
        offline.mutableCompletedNodes().addAll(java.util.List.of("basic_butchery", "classical_meat_preservation"));
        offline.mutableGrantedNodes().addAll(java.util.List.of("basic_butchery", "classical_meat_preservation"));
        require(!GateService.denyOfflineProcess("minecraft:smoker", ham, player.server, offlineId), "Offline granted operator can preserve meat");
        HistoriaErasSavedData.get(player.server).removePlayer(offlineId);
        ProgressService.adminGrantNode(player, "simmering");
        var pumpkin = recipes.byKey(ResourceLocation.parse("farmersdelight:cooking/stuffed_pumpkin_block")).orElseThrow();
        require(GateService.denyProcess("farmersdelight:cooking_pot", pumpkin, player, false), "Simmering cannot bypass Banquet Cookery");
        ProgressService.adminGrantNode(player, "classical_banquet");
        require(!GateService.denyProcess("farmersdelight:cooking_pot", pumpkin, player, false), "Banquet cooking process allowed");
        require(!GateService.denyOutput("farmersdelight:cooking_pot", "farmersdelight:stuffed_pumpkin_block", player, false), "Banquet pot extraction allowed");
        ProgressService.adminGrantNode(player, "classical_signals");
        var bell = (net.minecraft.world.item.crafting.CraftingRecipe) recipes.byKey(ResourceLocation.parse("townyeras:classical_bell")).orElseThrow().value();
        var copper = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT);
        var iron = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        require(bell.matches(net.minecraft.world.item.crafting.CraftingInput.of(3, 3,
                java.util.List.of(copper.copy(), copper.copy(), copper.copy(), copper.copy(), iron.copy(), copper.copy(),
                        net.minecraft.world.item.ItemStack.EMPTY, copper.copy(), net.minecraft.world.item.ItemStack.EMPTY)), player.level()), "Bell custom recipe shape");
        HistoriaEras.LOGGER.info("Classical production passed: stone ingredients, smoking, offline grants, banquet cooking/extraction and bell recipe");
    }

    private static void verifyPublicFarmingCarts(ServerPlayer player) {
        require(!AdminModeService.isEnabled(player), "Cart test has Admin Mode off");
        for (String wood : java.util.List.of("", "oak_", "spruce_", "birch_", "jungle_", "acacia_", "dark_oak_",
                "mangrove_", "cherry_", "bamboo_", "crimson_", "warped_")) {
            for (String cart : java.util.List.of("animal_cart", "plow", "hand_cart", "supply_cart", "seed_drill", "reaper")) {
                require(!GateService.deny("entity_interact", "astikorcartsredux:" + wood + cart, player, false),
                        "Public cart " + wood + cart);
                require(!GateService.deny("use_item", "astikorcartsredux:" + wood + cart, player, false),
                        "Public cart placement " + wood + cart);
            }
        }
        require(GateService.denyCraftOutput("astikorcartsredux:oak_plow", player, false), "Plow crafting remains locked");
        require(GateService.denyCraftOutput("astikorcartsredux:oak_animal_cart", player, false), "Animal cart crafting remains locked");
        require(!GateService.deny("entity_interact", "minecraft:boat", player, false), "Public boat operation");
        require(!GateService.deny("entity_interact", "minecraft:chest_boat", player, false), "Public chest-boat storage");
        for (var node : TechnologyManager.current().nodes().values()) {
            for (String item : node.unlockRecipes()) {
                if (node.id().equals("early_seafaring") || node.id().equals("ancient_storage")) {
                    require(!GateService.deny("place", item, player, false), "Public utility placement " + item);
                    require(!GateService.deny("use_item", item, player, false), "Public utility use " + item);
                    require(!GateService.denyStation(item, player, false), "Public wooden storage " + item);
                }
            }
        }
        for (String map : java.util.List.of("minecraft:map", "minecraft:filled_map"))
            require(!GateService.deny("use_item", map, player, false), "Public map viewing " + map);
        for (String ladder : java.util.List.of("minecraft:ladder", "quark:spruce_ladder", "quark:iron_ladder")) {
            require(!GateService.denyCraftOutput(ladder, player, false), "Public ladder crafting " + ladder);
            require(!GateService.deny("place", ladder, player, false), "Public ladder placement " + ladder);
        }
        require(GateService.denyCraftOutput("minecraft:oak_boat", player, false), "Boat crafting remains locked");
        require(GateService.denyCraftOutput("minecraft:barrel", player, false), "Storage crafting remains locked");
        require(GateService.denyCraftOutput("astikorcartsredux:oak_supply_cart", player, false), "Cart crafting remains locked");
        require(GateService.denyStation("farmersdelight:cutting_board", player, false), "Cutting-board restriction preserved");
        var cart = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.parse("astikorcartsredux:animal_cart")).create(player.serverLevel());
        require(cart != null, "Actual Astikor animal cart is registered");
        cart.setPos(player.position());
        try {
            require(player.startRiding(cart, true), "Actual animal-cart mount mixin permits a player with no nodes");
        } finally {
            player.stopRiding();
            cart.discard();
        }
        HistoriaEras.LOGGER.info("Public utility audit passed: carts, boats, maps, wooden storage and ladders are usable without nodes; specialized crafting/workstations stay gated");
    }

    private static void verifyNoTradeProgress(ServerPlayer player) {
        var registry = TechnologyManager.current();
        var progress = HistoriaErasSavedData.get(player.server).player(player.getUUID());
        int checked = 0;
        for (var node : registry.nodes().values()) {
            for (var requirement : node.requirements()) {
                if (!requirement.action().equals("obtain_item")) continue;
                ProgressService.resetEverything(player);
                if (!node.classId().equals("universal"))
                    ProgressService.adminSetClasses(player, node.classId(), node.classId().equals("farmer") ? "chef" : "farmer");
                for (String ancestor : node.prerequisites()) ProgressService.adminGrantNode(player, ancestor);
                String item = requirement.targets().getFirst();
                if (item.startsWith("#")) item = "minecraft:oak_log";
                ProgressService.recordAction(player, "obtain_item", item, requirement.amount());
                require(progress.contribution(node.id(), requirement.id()) == 0, "Trade blocked: " + node.id());
                ProgressService.recordAction(player, "source_obtain_item", item, 1);
                require(progress.contribution(node.id(), requirement.id()) == 1, "Source collection accepted: " + node.id());
                checked++;
            }
        }
        HistoriaEras.LOGGER.info("Verified no-trade acquisition policy for all {} collection objectives", checked);
        ProgressService.resetEverything(player);
        ProgressService.adminGrantNode(player, "stone_tools");
        var leather = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER);
        var entity = new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), leather.copy());
        entity.setItem(net.minecraft.world.item.ItemStack.EMPTY);
        var pickup = new net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post(player, entity, leather);
        NodeProgressEvents.onItemPickedUp(pickup);
        require(progress.contribution("leather_canteen", "leather") == 0, "Unverified/player trade pickup ignored");
        NodeProgressEvents.tagSource(entity, player, "animal", "");
        entity.getPersistentData().putUUID(NodeProgressEvents.SOURCE_OWNER, java.util.UUID.randomUUID());
        NodeProgressEvents.onItemPickedUp(pickup);
        require(progress.contribution("leather_canteen", "leather") == 0, "Another player's source drop ignored");
        NodeProgressEvents.tagSource(entity, player, "animal", "");
        NodeProgressEvents.onItemTossed(new net.neoforged.neoforge.event.entity.item.ItemTossEvent(entity, player));
        NodeProgressEvents.onItemPickedUp(pickup);
        require(progress.contribution("leather_canteen", "leather") == 0, "Toss and repick cannot recycle credit");
        var own = new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), leather.copy());
        own.setItem(net.minecraft.world.item.ItemStack.EMPTY);
        NodeProgressEvents.tagSource(own, player, "animal", "");
        NodeProgressEvents.onItemPickedUp(new net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post(player, own, leather));
        require(progress.contribution("leather_canteen", "leather") == 1, "Own fresh leather drop credits exactly once");
        HistoriaEras.LOGGER.info("Pickup provenance passed: unverified trades, other owners, toss/re-pick denied; own leather accepted");
    }

    private static void verifyFirePitRecipe(ServerPlayer player) {
        if (!net.neoforged.fml.ModList.get().isLoaded("supplementaries")) return;
        var recipes = player.server.getRecipeManager();
        var recipe = recipes.byKey(ResourceLocation.parse("supplementaries:fire_pit")).orElseThrow();
        require(recipes.byKey(ResourceLocation.parse("supplementaries:fire_pit_2")).isEmpty(), "Old coal fire pit disabled");
        var copper = net.minecraft.world.item.Items.COPPER_INGOT;
        var campfire = net.minecraft.world.item.Items.CAMPFIRE;
        var stacks = java.util.List.of(new net.minecraft.world.item.ItemStack(copper),
                new net.minecraft.world.item.ItemStack(campfire), new net.minecraft.world.item.ItemStack(copper),
                new net.minecraft.world.item.ItemStack(copper), new net.minecraft.world.item.ItemStack(copper),
                new net.minecraft.world.item.ItemStack(copper));
        var shaped = (net.minecraft.world.item.crafting.CraftingRecipe) recipe.value();
        require(shaped.matches(net.minecraft.world.item.crafting.CraftingInput.of(3, 2, stacks), player.level()),
                "Five copper and campfire match fire pit recipe");
        var old = new java.util.ArrayList<>(stacks);
        old.set(1, new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("supplementaries:lumisene_bottle"))));
        require(!shaped.matches(net.minecraft.world.item.crafting.CraftingInput.of(3, 2, old), player.level()),
                "Old bottle recipe cannot craft fire pit");
        long outputs = recipes.getRecipes().stream().filter(r ->
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(r.value().getResultItem(player.registryAccess()).getItem())
                        .equals(ResourceLocation.parse("supplementaries:fire_pit"))).count();
        require(outputs == 1, "Exactly one fire pit recipe remains");
        HistoriaEras.LOGGER.info("Fire pit replacement verified: five copper plus campfire; old bottle/coal recipes removed");
    }

    private static void verifyPersonalSmelting(ServerPlayer player) {
        ProgressService.adminSetClasses(player, "mason", "farmer");
        ProgressService.adminGrantNode(player, "firemaking");
        ProgressService.adminGrantNode(player, "clay_working");
        ProgressService.adminGrantNode(player, "furnace_basic_ore");
        var progress = HistoriaErasSavedData.get(player.server).player(player.getUUID());
        var node = TechnologyManager.current().node("pottery");
        var fuel = node.requirements().stream().filter(r -> r.id().equals("fuel")).findFirst().orElseThrow();
        var level = player.serverLevel();
        var position = player.blockPosition().offset(5, 1, 4);
        var original = level.getBlockState(position);
        try {
            level.setBlock(position, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState(), 3);
            var furnace = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) level.getBlockEntity(position);
            StationOperatorService.claim(furnace, player);
            furnace.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHARCOAL, 32));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemSmeltedEvent(
                    player, furnace.removeItem(2, 32)));
            require(progress.contribution("pottery", fuel.id()) == 0, "Donated furnace output cannot award collection");
            furnace.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG));
            furnace.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL));
            for (int tick = 0; tick < 205; tick++)
                net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(level, position, furnace.getBlockState(), furnace);
            require(progress.contribution("pottery", fuel.id()) == 1, "Actual personal charcoal production counts once");
            HistoriaEras.LOGGER.info("Furnace provenance verified: donated output ignored, real charcoal production credited once");
        } finally {
            level.setBlock(position, original, 3);
        }
    }

    private static void verifyCuttingBoardFeedback(ServerPlayer player) {
        require(GateService.lockedTechnologyMessage("Butchery").getString()
                .equals("HistoriaEras: Unlock Butchery first."), "Readable cutting feedback");
        require(GateService.lockedTechnologyMessage("").getString()
                .equals("HistoriaEras: Unlock the required technology first."), "Readable fallback feedback");
        var level = player.serverLevel();
        var position = player.blockPosition().offset(4, 1, 4);
        var previous = level.getBlockState(position);
        try {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                    ResourceLocation.parse("farmersdelight:cutting_board"));
            require(block != net.minecraft.world.level.block.Blocks.AIR, "Farmer's Delight installed for cutting test");
            level.setBlock(position, block.defaultBlockState(), 3);
            var board = level.getBlockEntity(position);
            var type = board.getClass();
            var stackType = net.minecraft.world.item.ItemStack.class;
            var knife = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("farmersdelight:flint_knife")));
            var meat = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEEF);
            type.getMethod("addItem", stackType).invoke(board, meat);
            var process = type.getMethod("processStoredItemUsingTool", stackType,
                    net.minecraft.world.entity.player.Player.class);
            require(!(boolean) process.invoke(board, knife, player), "Locked beef cutting rejected by actual mixin");
            var remaining = (net.minecraft.world.item.ItemStack) type.getMethod("getStoredItem").invoke(board);
            require(remaining.is(net.minecraft.world.item.Items.BEEF) && remaining.getCount() == 1,
                    "Denied cutting preserves beef");
            require(knife.getDamageValue() == 0, "Denied cutting preserves knife durability");
            ProgressService.adminGrantNode(player, "basic_cutting");
            require((boolean) process.invoke(board, knife, player), "Granted Butchery permits actual beef cutting");
            HistoriaEras.LOGGER.info("Actual Farmer's Delight beef cutting passed locked/unlocked checks and feedback wording");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cutting-board integration test failed", failure);
        } finally {
            level.setBlock(position, previous, 3);
        }
    }

    private static void verifyAdminGrants(ServerPlayer player) {
        var registry = TechnologyManager.current();
        var data = HistoriaErasSavedData.get(player.server);
        var progress = data.player(player.getUUID());
        try {
            var dispatcher = player.server.getCommands().getDispatcher();
            require(dispatcher.getRoot().getChild("townyeras").getRedirect()
                    == dispatcher.getRoot().getChild("historiaeras"), "Legacy command redirects to HistoriaEras");
            require(dispatcher.execute("townyeras" + " status", player.createCommandSourceStack()) == 1,
                    "Legacy status command still works");
            var previousOp = player.server.getPlayerList().getOps().get(player.getGameProfile());
            try {
                player.server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(
                        player.getGameProfile(), 4, false));
                progress.setAdminMode(false);
                boolean modeOffDenied = false;
                try {
                    player.server.getCommands().getDispatcher().execute("historiaeras admin node grantself early_agriculture",
                            player.createCommandSourceStack());
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { modeOffDenied = true; }
                require(modeOffDenied, "Operator grant requires Admin Mode");
                require(AdminModeService.setEnabled(player, true), "Operator enables Admin Mode");
                require(player.server.getCommands().getDispatcher().execute("historiaeras admin node grantself early_agriculture",
                        player.createCommandSourceStack()) == 1, "Operator self-grant command");
                AdminModeService.setEnabled(player, false);
                require(ProgressService.hasNodeAccess(player, "early_agriculture"), "Self-grant works with Admin Mode off");
            } finally {
                progress.setAdminMode(false);
                if (previousOp == null) player.server.getPlayerList().getOps().remove(player.getGameProfile());
                else player.server.getPlayerList().getOps().add(previousOp);
            }
            // Execute the registered command, not only the implementation helper.
            for (var node : registry.nodes().values()) {
                ProgressService.resetEverything(player);
                require(player.server.getCommands().getDispatcher().execute("historiaeras admin node grant "
                        + player.getGameProfile().getName() + " " + node.id(), player.server.createCommandSourceStack()) == 1,
                        "Grant command " + node.id());
                require(ProgressService.hasNodeAccess(player, node.id()), "Class-independent grant " + node.id());
                var wire = ProgressService.createSnapshot(player);
                require(wire.getList("accessibleNodes", net.minecraft.nbt.Tag.TAG_STRING).stream()
                        .anyMatch(tag -> tag.getAsString().equals(node.id())), "Granted snapshot access " + node.id());
                var restored = PlayerProgress.load(progress.save());
                require(restored.grantedNodesView().contains(node.id()) && restored.completedNodesView().contains(node.id()),
                        "Grant save/reload " + node.id());
                for (var child : registry.nodes().values()) if (child.prerequisites().contains(node.id()))
                    require(!ProgressService.missingPrerequisites(player, child.id()).contains(node.id()),
                            "Successor accepts granted prerequisite " + child.id());
                ProgressService.adminRevokeNode(player, node.id());
                require(!ProgressService.hasNodeAccess(player, node.id()) && !progress.grantedNodesView().contains(node.id()),
                        "Revoke removes override " + node.id());
            }
            ProgressService.resetEverything(player);
            progress.mutableCompletedNodes().add("woodworking");
            require(!ProgressService.hasNodeAccess(player, "woodworking"), "Natural old-class progress stays inactive");
            for (var node : registry.nodes().values()) ProgressService.adminGrantNode(player, node.id());
            int concreteRecipes = 0;
            for (var node : registry.nodes().values()) for (String recipe : node.unlockRecipes()) {
                if (recipe.startsWith("#") || recipe.contains("*")) continue;
                require(!GateService.denyCraftOutput(recipe, player, false), "Granted concrete recipe " + recipe);
                concreteRecipes++;
            }
            for (var gate : registry.gates()) for (String target : gate.targets()) {
                if (registry.node(gate.nodeId()) != null && !target.startsWith("#") && !target.contains("*"))
                    require(!GateService.deny(gate.type(), target, player, false), "Granted gate " + gate.id());
            }
            for (var station : registry.stations().values()) for (String target : station.targets()) {
                if (registry.node(station.nodeId()) != null && !target.startsWith("#") && !target.contains("*"))
                    require(!GateService.denyStation(target, player, false), "Granted workstation " + station.id());
            }
            require(GateService.deny("interact", "minecraft:blast_furnace", player, false),
                    "Unimplemented future engineering stays locked even after all defined nodes are granted");
            // A non-operator cannot execute the same command.
            boolean denied = false;
            try {
                player.server.getCommands().getDispatcher().execute("historiaeras admin node grant "
                        + player.getGameProfile().getName() + " craftsmanship", player.createCommandSourceStack().withPermission(0));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            require(denied, "Non-operator grant command is denied");
            ProgressService.resetProgress(player);
            require(progress.grantedNodesView().isEmpty(), "Reset clears all grant overrides");
            HistoriaEras.LOGGER.info("Admin grant audit passed: {} nodes, commands/access/successors/save/reload/revoke/reset; {} concrete unlock entries, gates and workstations; non-operator denied",
                    registry.nodes().size(), concreteRecipes);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new IllegalStateException("Registered admin grant command failed", failure);
        }
    }
}
