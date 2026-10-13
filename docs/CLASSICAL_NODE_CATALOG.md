# TownyEras 0.7.0-alpha — Exact Classical node catalog

Generated directly from the shipped technology registry. 26 Classical nodes: 7 retained plus 19 new. Requirements are ALL required. An array of targets is pooled: any matching target contributes to the same counter, not a separate quota per item.

Collection objectives require verified personal sources, never item transfers. Natural mining objectives count only unplaced source blocks mined by the player and picked up. Crafting/smelting counters come from actual personal production. Honey counters come from successful full-hive harvesting. Movement counts the player, not a passenger. Prerequisites must already be complete before an action counts. Past-era catch-up remains possible; future-era contributions are blocked.

## universal

### Everyday Cookery — `everyday_cookery`

Status: Retained. Icon: `farmersdelight:cooking_pot`.

Prerequisites: None

Requirements:

- 100 — Mine coal; counter `coal`; action `source_mine_block`; targets: `minecraft:coal_ore`, `minecraft:deepslate_coal_ore`.
- 100 — Smelt items; counter `smelt`; action `smelt_item`; targets: `*`.
- 30 — Hunt animals or fish; counter `hunt`; action `hunt_animals_or_fish`; targets: `*`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:baked_potato`
- `minecraft:cooked_cod`
- `minecraft:cooked_salmon`
- `farmersdelight:cooked_bacon`
- `farmersdelight:beef_patty`
- `aquaculture:fish_fillet_cooked`
- `farmersdelight:cooked_salmon_slice`
- `delightful:cooked_venison_chops`
- `farmersdelight:cooked_chicken_cuts`
- `farmersdelight:cooked_mutton_chops`
- `farmersdelight:cooked_cod_slice`
- `culturaldelights:cooked_calamari`
- `farmersdelight:cooked_rice`
- `minecraft:mushroom_stew`
- `minecraft:rabbit_stew`
- `farmersdelight:beef_stew`
- `farmersdelight:fish_stew`
- `farmersdelight:baked_cod_stew`
- `aquaculturedelight:bass_stew`
- `delightful:venison_stew`
- `fruitsdelight:fig_chicken_stew`

Unlocked processing recipe IDs:

- None separately declared.

Actions: furnace_fish_cooking, cooked_meat_and_fish_cuts, baked_potatoes, limited_cooking_pot_use, basic_stews

### Simple Bread — `simple_bread`

Status: Retained. Icon: `minecraft:bread`.

Prerequisites: None

Requirements:

- 50 — Harvest wheat; counter `wheat`; action `harvest_crop`; targets: `minecraft:wheat`.
- 50 — Harvest corn; counter `corn`; action `harvest_crop`; targets: `corn_delight:corn_crop`.
- 50 — Craft items; counter `craft`; action `craft_any_item`; targets: `*`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:bread`
- `corn_delight:cornbread_batter`
- `corn_delight:cornbread`

Unlocked processing recipe IDs:

- None separately declared.

Actions: bread_recipe, cornbread_batter_recipe, furnace_cornbread

### Rough Stone Building — `rough_stone_building`

Status: Retained. Icon: `minecraft:cobblestone_wall`.

Prerequisites: None

Requirements:

- 500 — Mine blocks; counter `blocks`; action `mine_block`; targets: `*`.
- 300 — Mine stone; counter `stone`; action `source_mine_block`; targets: `minecraft:stone`.
- 10 — Craft furnaces; counter `furnaces`; action `craft_item`; targets: `minecraft:furnace`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:cobblestone_wall`
- `minecraft:cobblestone_slab`
- `minecraft:cobblestone_stairs`

Unlocked processing recipe IDs:

- None separately declared.

Actions: crafting_table_rough_stone

### Bookmaking — `classical_codices`

Status: New. Icon: `minecraft:writable_book`.

Prerequisites: Early Recordkeeping (`early_recordkeeping`)

Requirements:

- 64 — Craft paper; counter `paper`; action `craft_item`; targets: `minecraft:paper`.
- 16 — Collect leather; counter `leather`; action `animal_obtain_item`; targets: `minecraft:leather`.
- 12 — Collect ink sacs; counter `ink`; action `animal_obtain_item`; targets: `minecraft:ink_sac`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:book`
- `minecraft:writable_book`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

## farmer

### Soil Enrichment — `soil_enrichment`

Status: Retained. Icon: `farmersdelight:rich_soil`.

Prerequisites: None

Requirements:

- 500 — Till dirt; counter `till`; action `till_soil`; targets: `minecraft:farmland`.
- 300 — Plant crops; counter `plant`; action `plant_crop`; targets: `*`.
- 200 — Collect bone meal from composters; counter `compost_bonemeal`; action `composter_bonemeal`; targets: `minecraft:bone_meal`.

Unlocked item outputs (each has its registered item icon):

- `farmersdelight:organic_compost`
- `farmersdelight:rich_soil`
- `farmersdelight:rich_soil_farmland`

Unlocked processing recipe IDs:

- None separately declared.

Actions: organic_compost, rich_soil, rich_soil_farmland

### Apiculture — `apiculture`

Status: Retained. Icon: `minecraft:beehive`.

Prerequisites: Soil Enrichment (`soil_enrichment`)

Requirements:

- 500 — Craft planks; counter `planks`; action `craft_item`; targets: `#planks`.
- 100 — Craft organic compost; counter `compost`; action `craft_item`; targets: `farmersdelight:organic_compost`.
- 10 — Craft campfires for apiaries; counter `apiary_campfires`; action `craft_item`; targets: `minecraft:campfire`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:beehive`

Unlocked processing recipe IDs:

- None separately declared.

Actions: bee_breeding, honey_harvesting, honeycomb_harvesting

### Shearing — `shearing`

Status: Retained. Icon: `minecraft:shears`.

Prerequisites: Soil Enrichment (`soil_enrichment`)

Requirements:

- 50 — Kill sheep; counter `sheep`; action `kill_entity`; targets: `minecraft:sheep`.
- 50 — Cook mutton; counter `mutton`; action `smelt_item`; targets: `minecraft:cooked_mutton`.
- 50 — Butcher mutton into chops; counter `mutton_chops`; action `butcher_mutton_chops`; targets: `farmersdelight:mutton_chops`, `farmersdelight:cooked_mutton_chops`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:shears`

Unlocked processing recipe IDs:

- None separately declared.

Actions: shear_use

### Yield I — `yield_i`

Status: Retained. Icon: `minecraft:wheat`.

Prerequisites: Apiculture (`apiculture`), Shearing (`shearing`)

Requirements:

- 1500 — Harvest mature crops; counter `crops`; action `harvest_mature_crop`; targets: `#crops`.

Unlocked item outputs (each has its registered item icon):

- None.

Unlocked processing recipe IDs:

- None separately declared.

Actions: cultivated_crop_yield_5

### Fodder and Tack — `classical_fodder`

Status: New. Icon: `minecraft:hay_block`.

Prerequisites: Soil Enrichment (`soil_enrichment`), Animal Husbandry (`animal_husbandry`)

Requirements:

- 96 — Harvest wheat; counter `wheat`; action `harvest_obtain_item`; targets: `minecraft:wheat`.
- 24 — Breed animals; counter `breeding`; action `breed_animal`; targets: `*`.
- 16 — Collect leather; counter `leather`; action `animal_obtain_item`; targets: `minecraft:leather`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:hay_block`
- `minecraft:leather_horse_armor`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

### Beeswax and Honey Products — `classical_beeswax`

Status: New. Icon: `minecraft:candle`.

Prerequisites: Apiculture (`apiculture`)

Requirements:

- 4 — Craft beehives; counter `hives`; action `craft_item`; targets: `minecraft:beehive`.
- 24 — Harvest honeycomb; counter `honeycomb`; action `bee_harvest`; targets: `minecraft:honeycomb`.
- 8 — Harvest honey bottles; counter `honey`; action `bee_harvest`; targets: `minecraft:honey_bottle`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:candle`
- `minecraft:honeycomb_block`
- `minecraft:honey_block`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

## chef

### Meat Preservation — `classical_meat_preservation`

Status: New. Icon: `farmersdelight:smoked_ham`.

Prerequisites: Smoking & Drying (`basic_butchery`)

Requirements:

- 32 — Butcher pork; counter `butcher`; action `butcher_raw_input`; targets: `farmersdelight:cutting/porkchop`.
- 48 — Collect raw pork; counter `pork`; action `animal_obtain_item`; targets: `minecraft:porkchop`.
- 64 — Chop logs; counter `logs`; action `chop_log`; targets: `#logs`.

Unlocked item outputs (each has its registered item icon):

- `farmersdelight:smoked_ham`

Unlocked processing recipe IDs:

- `farmersdelight:smoked_ham`
- `farmersdelight:cutting/smoked_ham`

Actions: No new use restriction; manufacturing only.

### Banquet Cookery — `classical_banquet`

Status: New. Icon: `farmersdelight:roast_chicken_block`.

Prerequisites: Pot Cookery (`simmering`), Everyday Cookery (`everyday_cookery`)

Requirements:

- 48 — Collect raw chicken; counter `chicken`; action `animal_obtain_item`; targets: `minecraft:chicken`.
- 32 — Craft bowls; counter `bowls`; action `craft_item`; targets: `minecraft:bowl`.
- 64 — Harvest vegetables; counter `vegetables`; action `harvest_obtain_item`; targets: `minecraft:carrot`, `minecraft:potato`, `farmersdelight:onion`, `farmersdelight:cabbage`.

Unlocked item outputs (each has its registered item icon):

- `farmersdelight:roast_chicken_block`
- `farmersdelight:grilled_salmon`
- `farmersdelight:stuffed_pumpkin_block`
- `farmersdelight:honey_glazed_ham_block`

Unlocked processing recipe IDs:

- `farmersdelight:cooking/stuffed_pumpkin_block`

Actions: No new use restriction; manufacturing only.

### Pastry Baking — `classical_pastries`

Status: New. Icon: `farmersdelight:apple_pie`.

Prerequisites: Breadmaking (`grainery`)

Requirements:

- 32 — Craft dough; counter `dough`; action `craft_item`; targets: `farmersdelight:wheat_dough`.
- 64 — Harvest wheat; counter `wheat`; action `harvest_obtain_item`; targets: `minecraft:wheat`.
- 16 — Collect eggs; counter `eggs`; action `animal_obtain_item`; targets: `minecraft:egg`.

Unlocked item outputs (each has its registered item icon):

- `farmersdelight:pie_crust`
- `farmersdelight:apple_pie`

Unlocked processing recipe IDs:

- `farmersdelight:cutting/apple_pie`

Actions: No new use restriction; manufacturing only.

## mariner

### Chartmaking — `classical_chartmaking`

Status: New. Icon: `minecraft:cartography_table`.

Prerequisites: Coastal Navigation (`coastal_navigation`)

Requirements:

- 4 — Craft maps; counter `maps`; action `craft_item`; targets: `minecraft:map`.
- 64 — Craft paper; counter `paper`; action `craft_item`; targets: `minecraft:paper`.
- 4000 — Swim or sail; counter `water`; action `travel_water_driver`; targets: `*`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:cartography_table`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

### Specialized Fishing Tackle — `classical_tacklework`

Status: New. Icon: `aquaculture:tackle_box`.

Prerequisites: Angling (`angling`)

Requirements:

- 80 — Catch fish; counter `fish`; action `fish_caught`; targets: `#reviewed_fish`.
- 4 — Craft iron hooks; counter `hooks`; action `craft_item`; targets: `aquaculture:iron_hook`.
- 8 — Craft chests; counter `chests`; action `craft_item`; targets: `minecraft:chest`.

Unlocked item outputs (each has its registered item icon):

- `aquaculture:tackle_box`
- `aquaculture:gold_hook`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

### Harbor and Civic Signals — `classical_signals`

Status: New. Icon: `minecraft:lantern`.

Shared node: either eligible route is sufficient; it appears once, not once per class.

- mariner: Chartmaking (`classical_chartmaking`)
- blacksmith: Forged Iron Utilities (`classical_forged_utilities`)

Requirements:

- 32 — Craft torches; counter `torches`; action `craft_item`; targets: `minecraft:torch`.
- 24 — Mine coal ore; counter `coal`; action `source_mine_block`; targets: `minecraft:coal_ore`, `minecraft:deepslate_coal_ore`.
- 48 — Chop logs; counter `logs`; action `chop_log`; targets: `#logs`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:lantern`
- `minecraft:bell`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

## carpenter

### Timber Bridges — `classical_timber_bridges`

Status: New. Icon: `mcwbridges:oak_log_bridge_middle`.

Prerequisites: Doors & Shutters (`doors`), Wooden Enclosures (`early_enclosures`)

Requirements:

- 128 — Craft wooden slabs; counter `slabs`; action `craft_item`; targets: `#wooden_slabs`.
- 64 — Collect string; counter `string`; action `source_obtain_item`; targets: `minecraft:string`.
- 128 — Chop logs; counter `logs`; action `chop_log`; targets: `#logs`.

Unlocked item outputs (each has its registered item icon):

- `mcwbridges:acacia_bridge_pier`
- `mcwbridges:acacia_log_bridge_middle`
- `mcwbridges:acacia_log_bridge_stair`
- `mcwbridges:acacia_rope_bridge_stair`
- `mcwbridges:andesite_bridge_pier`
- `mcwbridges:asian_red_bridge_pier`
- `mcwbridges:bamboo_bridge_pier`
- `mcwbridges:birch_bridge_pier`
- `mcwbridges:birch_log_bridge_middle`
- `mcwbridges:birch_log_bridge_stair`
- `mcwbridges:birch_rope_bridge_stair`
- `mcwbridges:cherry_bridge_pier`
- `mcwbridges:cherry_log_bridge_middle`
- `mcwbridges:cherry_log_bridge_stair`
- `mcwbridges:cherry_rope_bridge_stair`
- `mcwbridges:crimson_bridge_pier`
- `mcwbridges:crimson_log_bridge_middle`
- `mcwbridges:crimson_log_bridge_stair`
- `mcwbridges:crimson_rope_bridge_stair`
- `mcwbridges:dark_oak_bridge_pier`
- `mcwbridges:dark_oak_log_bridge_middle`
- `mcwbridges:dark_oak_log_bridge_stair`
- `mcwbridges:dark_oak_rope_bridge_stair`
- `mcwbridges:deepslate_tile_bridge_pier`
- `mcwbridges:diorite_bridge_pier`
- `mcwbridges:dry_bamboo_bridge_pier`
- `mcwbridges:glass_bridge_pier`
- `mcwbridges:granite_bridge_pier`
- `mcwbridges:jungle_bridge_pier`
- `mcwbridges:jungle_log_bridge_middle`
- `mcwbridges:jungle_log_bridge_stair`
- `mcwbridges:jungle_rope_bridge_stair`
- `mcwbridges:mangrove_bridge_pier`
- `mcwbridges:mangrove_log_bridge_middle`
- `mcwbridges:mangrove_log_bridge_stair`
- `mcwbridges:mangrove_rope_bridge_stair`
- `mcwbridges:oak_bridge_pier`
- `mcwbridges:oak_log_bridge_middle`
- `mcwbridges:oak_log_bridge_stair`
- `mcwbridges:oak_rope_bridge_stair`
- `mcwbridges:rope_acacia_bridge`
- `mcwbridges:rope_birch_bridge`
- `mcwbridges:rope_cherry_bridge`
- `mcwbridges:rope_crimson_bridge`
- `mcwbridges:rope_dark_oak_bridge`
- `mcwbridges:rope_jungle_bridge`
- `mcwbridges:rope_mangrove_bridge`
- `mcwbridges:rope_oak_bridge`
- `mcwbridges:rope_spruce_bridge`
- `mcwbridges:rope_warped_bridge`
- `mcwbridges:spruce_bridge_pier`
- `mcwbridges:spruce_log_bridge_middle`
- `mcwbridges:spruce_log_bridge_stair`
- `mcwbridges:spruce_rope_bridge_stair`
- `mcwbridges:warped_bridge_pier`
- `mcwbridges:warped_log_bridge_middle`
- `mcwbridges:warped_log_bridge_stair`
- `mcwbridges:warped_rope_bridge_stair`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

### Crossbow Making — `classical_crossbows`

Status: New. Icon: `minecraft:crossbow`.

Prerequisites: Bowmaking (`archery`)

Requirements:

- 12 — Craft bows; counter `bows`; action `craft_item`; targets: `minecraft:bow`.
- 96 — Craft arrows; counter `arrows`; action `craft_item`; targets: `minecraft:arrow`.
- 48 — Collect string; counter `string`; action `source_obtain_item`; targets: `minecraft:string`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:crossbow`
- `minecraft:tripwire_hook`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

### Libraries — `classical_libraries`

Status: New. Icon: `minecraft:bookshelf`.

Prerequisites: Wooden Storage (`ancient_storage`), Bookmaking (`classical_codices`)

Requirements:

- 16 — Craft books; counter `books`; action `craft_item`; targets: `minecraft:book`.
- 96 — Craft planks; counter `planks`; action `craft_item`; targets: `#planks`.
- 64 — Craft wooden slabs; counter `slabs`; action `craft_item`; targets: `#wooden_slabs`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:bookshelf`
- `minecraft:chiseled_bookshelf`
- `minecraft:lectern`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

## mason

### Paved Roads — `classical_roadworks`

Status: New. Icon: `minecraft:smooth_stone_slab`.

Prerequisites: Stone Dressing (`stonecutting`), Rough Stone Building (`rough_stone_building`)

Requirements:

- 200 — Mine stone; counter `stone`; action `source_mine_block`; targets: `minecraft:stone`, `minecraft:granite`, `minecraft:diorite`, `minecraft:andesite`.
- 128 — Craft cobblestone slabs; counter `slabs`; action `craft_item`; targets: `minecraft:cobblestone_slab`.
- 64 — Craft stone bricks; counter `bricks`; action `craft_item`; targets: `minecraft:stone_bricks`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:smooth_stone`
- `minecraft:smooth_stone_slab`
- `mcwpaths:andesite_flagstone_path`
- `mcwpaths:andesite_running_bond_path`
- `mcwpaths:andesite_strewn_rocky_path`
- `mcwpaths:brick_flagstone_path`
- `mcwpaths:brick_running_bond_path`
- `mcwpaths:brick_strewn_rocky_path`
- `mcwpaths:diorite_flagstone_path`
- `mcwpaths:diorite_running_bond_path`
- `mcwpaths:diorite_strewn_rocky_path`
- `mcwpaths:granite_flagstone_path`
- `mcwpaths:granite_running_bond_path`
- `mcwpaths:granite_strewn_rocky_path`
- `mcwpaths:red_sandstone_flagstone_path`
- `mcwpaths:red_sandstone_running_bond_path`
- `mcwpaths:red_sandstone_strewn_rocky_path`
- `mcwpaths:sandstone_flagstone_path`
- `mcwpaths:sandstone_running_bond_path`
- `mcwpaths:sandstone_strewn_rocky_path`
- `mcwpaths:stone_flagstone_path`
- `mcwpaths:stone_running_bond_path`
- `mcwpaths:stone_strewn_rocky_path`

Unlocked processing recipe IDs:

- `minecraft:smooth_stone`

Actions: No new use restriction; manufacturing only.

### Stone Bridges — `classical_stone_bridges`

Status: New. Icon: `mcwbridges:stone_brick_bridge`.

Prerequisites: Stone Dressing (`stonecutting`), Paved Roads (`classical_roadworks`)

Requirements:

- 128 — Craft stone bricks; counter `bricks`; action `craft_item`; targets: `minecraft:stone_bricks`.
- 48 — Craft stone-brick walls; counter `walls`; action `craft_item`; targets: `minecraft:stone_brick_wall`.
- 200 — Mine stone; counter `stone`; action `source_mine_block`; targets: `minecraft:stone`, `minecraft:granite`, `minecraft:diorite`, `minecraft:andesite`.

Unlocked item outputs (each has its registered item icon):

- `mcwbridges:balustrade_bricks_bridge`
- `mcwbridges:balustrade_cobblestone_bridge`
- `mcwbridges:balustrade_mossy_cobblestone_bridge`
- `mcwbridges:balustrade_mossy_stone_bricks_bridge`
- `mcwbridges:balustrade_mud_bricks_bridge`
- `mcwbridges:balustrade_nether_bricks_bridge`
- `mcwbridges:balustrade_orange_sandstone_bridge`
- `mcwbridges:balustrade_prismarine_bricks_bridge`
- `mcwbridges:balustrade_sandstone_bridge`
- `mcwbridges:balustrade_stone_bricks_bridge`
- `mcwbridges:brick_bridge`
- `mcwbridges:brick_bridge_pier`
- `mcwbridges:brick_bridge_stair`
- `mcwbridges:cobblestone_bridge`
- `mcwbridges:cobblestone_bridge_pier`
- `mcwbridges:cobblestone_bridge_stair`
- `mcwbridges:mossy_cobblestone_bridge`
- `mcwbridges:mossy_cobblestone_bridge_pier`
- `mcwbridges:mossy_cobblestone_bridge_stair`
- `mcwbridges:mossy_stone_brick_bridge`
- `mcwbridges:mossy_stone_bridge_pier`
- `mcwbridges:mossy_stone_bridge_stair`
- `mcwbridges:mud_brick_bridge`
- `mcwbridges:mud_brick_bridge_pier`
- `mcwbridges:mud_brick_bridge_stair`
- `mcwbridges:nether_bricks_bridge`
- `mcwbridges:nether_bricks_bridge_pier`
- `mcwbridges:nether_bricks_bridge_stair`
- `mcwbridges:orange_sandstone_bridge`
- `mcwbridges:prismarine_bricks_bridge`
- `mcwbridges:prismarine_bricks_bridge_pier`
- `mcwbridges:prismarine_bricks_bridge_stair`
- `mcwbridges:red_sandstone_bridge_pier`
- `mcwbridges:red_sandstone_bridge_stair`
- `mcwbridges:sandstone_bridge`
- `mcwbridges:sandstone_bridge_pier`
- `mcwbridges:sandstone_bridge_stair`
- `mcwbridges:stone_brick_bridge`
- `mcwbridges:stone_brick_bridge_stair`
- `mcwbridges:stone_bridge_pier`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

### Window Glass — `classical_window_glass`

Status: New. Icon: `minecraft:glass_pane`.

Prerequisites: Glassmaking (`glassmaking`)

Requirements:

- 64 — Smelt glass; counter `glass`; action `smelt_item`; targets: `minecraft:glass`.
- 100 — Mine sand; counter `sand`; action `source_mine_block`; targets: `minecraft:sand`, `minecraft:red_sand`, `biomesoplenty:orange_sand`, `biomesoplenty:black_sand`.
- 12 — Craft glass bottles; counter `bottles`; action `craft_item`; targets: `minecraft:glass_bottle`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:glass_pane`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

## blacksmith

### Iron Tools — `classical_iron_tools`

Status: New. Icon: `minecraft:iron_pickaxe`.

Prerequisites: Iron Smelting (`iron_smelting`)

Requirements:

- 128 — Mine iron ore; counter `ore`; action `source_mine_block`; targets: `minecraft:iron_ore`, `minecraft:deepslate_iron_ore`.
- 64 — Smelt iron ingots; counter `iron`; action `smelt_item`; targets: `minecraft:iron_ingot`.
- 64 — Craft sticks; counter `handles`; action `craft_item`; targets: `minecraft:stick`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:iron_pickaxe`
- `minecraft:iron_axe`
- `minecraft:iron_shovel`
- `minecraft:iron_hoe`
- `farmersdelight:iron_knife`
- `aquaculture:iron_fillet_knife`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

### Iron Weaponry — `classical_iron_weaponry`

Status: New. Icon: `minecraft:iron_sword`.

Prerequisites: Iron Tools (`classical_iron_tools`)

Requirements:

- 8 — Craft iron axes; counter `axes`; action `craft_item`; targets: `minecraft:iron_axe`.
- 48 — Smelt iron ingots; counter `iron`; action `smelt_item`; targets: `minecraft:iron_ingot`.
- 64 — Craft planks; counter `planks`; action `craft_item`; targets: `#planks`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:iron_sword`
- `minecraft:shield`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

### Forged Iron Utilities — `classical_forged_utilities`

Status: New. Icon: `minecraft:cauldron`.

Prerequisites: Early Iron Links (`early_iron_links`)

Requirements:

- 32 — Craft chains; counter `chains`; action `craft_item`; targets: `minecraft:chain`.
- 48 — Smelt iron ingots; counter `iron`; action `smelt_item`; targets: `minecraft:iron_ingot`.
- 16 — Craft iron bars; counter `bars`; action `craft_item`; targets: `minecraft:iron_bars`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:iron_door`
- `minecraft:iron_trapdoor`
- `minecraft:cauldron`
- `minecraft:bucket`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

### Iron Armor — `classical_iron_armor`

Status: New. Icon: `minecraft:iron_chestplate`.

Prerequisites: Iron Tools (`classical_iron_tools`), Forged Iron Utilities (`classical_forged_utilities`)

Requirements:

- 96 — Smelt iron ingots; counter `iron`; action `smelt_item`; targets: `minecraft:iron_ingot`.
- 8 — Craft iron blocks; counter `blocks`; action `craft_item`; targets: `minecraft:iron_block`.
- 32 — Craft chains; counter `chains`; action `craft_item`; targets: `minecraft:chain`.

Unlocked item outputs (each has its registered item icon):

- `minecraft:iron_helmet`
- `minecraft:iron_chestplate`
- `minecraft:iron_leggings`
- `minecraft:iron_boots`

Unlocked processing recipe IDs:

- None separately declared.

Actions: No new use restriction; manufacturing only.

## Referenced category membership

### `#crops`

Built-in semantic category; see the complete registry and matcher definitions. The server also resolves its supported tags. This is not a made-up registry ID.

### `#logs`

Built-in semantic category; see the complete registry and matcher definitions. The server also resolves its supported tags. This is not a made-up registry ID.

### `#planks`

Built-in semantic category; see the complete registry and matcher definitions. The server also resolves its supported tags. This is not a made-up registry ID.

### `#reviewed_fish`

- `minecraft:cod`
- `minecraft:salmon`
- `minecraft:tropical_fish`
- `minecraft:pufferfish`
- `aquaculture:atlantic_cod`
- `aquaculture:blackfish`
- `aquaculture:pacific_halibut`
- `aquaculture:atlantic_halibut`
- `aquaculture:atlantic_herring`
- `aquaculture:pink_salmon`
- `aquaculture:pollock`
- `aquaculture:rainbow_trout`
- `aquaculture:bayad`
- `aquaculture:boulti`
- `aquaculture:capitaine`
- `aquaculture:synodontis`
- `aquaculture:smallmouth_bass`
- `aquaculture:bluegill`
- `aquaculture:brown_trout`
- `aquaculture:carp`
- `aquaculture:catfish`
- `aquaculture:gar`
- `aquaculture:muskellunge`
- `aquaculture:perch`
- `aquaculture:arapaima`
- `aquaculture:piranha`
- `aquaculture:tambaqui`
- `aquaculture:brown_shrooma`
- `aquaculture:red_shrooma`
- `aquaculture:red_grouper`
- `aquaculture:tuna`
- `farmersdelight:cod_slice`
- `farmersdelight:salmon_slice`
- `rusticdelight:calamari`
- `rusticdelight:calamari_slice`
- `culturaldelights:squid`
- `culturaldelights:glow_squid`
- `culturaldelights:raw_calamari`
- `miners_delight:squid`
- `miners_delight:glow_squid`
- `miners_delight:tentacles`

### `#wooden_slabs`

Built-in semantic category; see the complete registry and matcher definitions. The server also resolves its supported tags. This is not a made-up registry ID.

