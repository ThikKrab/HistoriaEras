# TownyEras 0.7.0-alpha — Classical core expansion

## Quick summary

19 new Classical technologies, taking the tree to 77 unique nodes: 51 Ancient and 26 Classical. The seven existing Classical nodes remain, alongside the new additions. Existing node IDs, objective IDs, requirements, and saved contributions are retained. No player-progress reset is needed.

Earlier-era research now remains available after the calendar advances. Future-era research still cannot advance, and normal players still cannot browse future-era nodes or their details. Universal plus the player's two selected classes remains the normal browsing scope.

This release is a local build and installation package. It has not replaced the live server JAR, changed the live website, installed itself in the HistoriaMC client profile, or advanced the real server calendar.

## Added technologies

| Class | New technologies |
|---|---|
| Universal | Bookmaking |
| Farmer | Fodder and Tack; Beeswax and Honey Products |
| Chef | Meat Preservation; Banquet Cookery; Pastry Baking |
| Mariner | Chartmaking; Specialized Fishing Tackle |
| Carpenter | Timber Bridges; Crossbow Making; Libraries |
| Mason | Paved Roads; Stone Bridges; Window Glass |
| Blacksmith | Iron Tools; Iron Weaponry; Forged Iron Utilities; Iron Armor |
| Shared | Harbor and Civic Signals: Mariner through Chartmaking **or** Blacksmith through Forged Iron Utilities; one node and one saved completion |

The generated `CLASSICAL_NODE_CATALOG.md` contains every requirement, prerequisite, exact output, and processing recipe ID for all 26 Classical nodes. `technology.json` is the complete normalized registry; `website-schema-v2.json` is an editable-graph seed, not a command to overwrite an existing customized website publication.

## Progress and permission rules

- Only current and earlier eras can receive new research contributions. A node still needs an eligible class and its prerequisites before its requirements count.
- Completing a prerequisite during one action does not also credit its newly available child with that same action.
- Direct collection objectives require verified personal animal drops, harvested crops, natural block sources, or the specifically allowed source event. Moving items, donated items, inventory changes, trades, and tossing/recollecting them do not satisfy those collection objectives.
- Crafting objectives require the player to craft the specified output. They may obtain ingredients through normal cooperation; receiving the completed output does not count as crafting it. This preserves the existing distinction between trading goods and earning research progress.
- Production permissions protect ordinary crafting and vanilla stonecutter output, including shift-click crafting. Cooking-pot and smoker whitelists cannot bypass a later technology's narrower output restriction.
- An administrator's persistent grants remain usable when Admin Mode is switched off. Authorized offline station operators retain those grants for processing.
- Public-use rules are retained: ladders, storage, boats, maps, and carts do not acquire new operating restrictions. Their existing specialized manufacturing restrictions remain where applicable. Town/claim protections remain independent.

## Supporting behavior implemented

### Honey and beeswax

Actual successful full-hive harvesting is tracked. Shearing a full hive credits three honeycomb; bottling a full hive credits one honey bottle. The hive must actually reset its honey level. Unsuccessful interactions, empty hives, inventory changes, and donated honey do not count. Apiculture permits harvesting; without it the hive and shears are preserved and a technology message is shown.

Beeswax and Honey Products unlocks the plain candle, honeycomb block, and honey block. Colored candles were not added to this release.

### Crop yields

The existing Yield I technology now applies a five-percent chance of doubling eligible mature cultivated crop drops from player-planted crops. Supported crop breaking and attributed right-click harvest drops use the same policy. Separate seed-named drops are not doubled; edible planting items such as carrots and potatoes remain eligible. This is a five-percent *chance of a doubled harvest*, not a guaranteed extra item on every harvest. It stays separate from the existing wild Foraging bonus.

### Preservation and feasts

Meat Preservation protects smoked ham manufacture, including the Farmer's Delight burning-pig knife-loot shortcut. Raw ham stays public. Banquet Cookery uses the actual installed feast IDs: roast chicken block, grilled salmon, stuffed pumpkin block, and honey-glazed ham block. Stuffed pumpkin's real cooking-pot recipe and result extraction are both supported.

### Masonry ingredient flow

Stone Dressing gains the supporting `minecraft:stone` furnace process so already-unlocked stone bricks can be produced from ordinary cobblestone without depending on Silk Touch or rare loot. Its existing requirements are unchanged. Paved Roads supplies the later smooth-stone process and smooth-stone slabs.

The installed Macaw recipes supply 58 wooden bridge outputs, 40 stone bridge outputs, and 21 stone/brick path surfaces. Exact output IDs—not a broad namespace unlock—are used. The Classical coordinates published to the website use the same layered geometry as the mod rather than giving every new node one overlapping default location. In-game layouts remain automatic.

### Signals

The shared signals node unlocks lanterns and bells. A new shaped bell recipe produces one bell from six copper ingots and one iron ingot:

```text
C C C
C I C
  C
```

`C` is `minecraft:copper_ingot`; `I` is `minecraft:iron_ingot`. Bell/lantern use stays public.

## Deliberately excluded

- Create waterwheel/mill progression and other optional machinery.
- Colored glass and stained panes; Window Glass grants clear panes only.
- Custom craftable iron horse armor.
- A nonexistent `moredelight:iron_knife`: that installed mod provides wooden and stone knives, not an iron knife. Iron Tools uses the verified Minecraft, Farmer's Delight, and Aquaculture iron tools instead.
- New gold smelting. The optional gold hook retains Aquaculture's existing gold-nugget ingredient recipe; its gold can come from existing loot or ingredient trade. The tackle box itself does not require gold. Neither source grants research progress merely for trading it.

## Install safely

1. Choose a maintenance window. Back up the world, current TownyEras JAR, and `config/townyeras-*` files. Preserve any customized website graph/publication too.
2. Stop the server and close Minecraft. Replace the old TownyEras JAR in the server's `mods` folder and each client's `mods` folder with `townyeras-0.7.0-alpha.jar`. Keep only one active TownyEras JAR per folder. Keep the same Minecraft 1.21.1 / NeoForge modpack dependencies.
3. Restart. Do not reset players or delete their progress files. Verify the active tree contains 77 nodes before making further website edits. A pending older website publication can replace the complete graph; reconcile it before applying it.
4. With website synchronization configured, `/townyeras admin website status` checks its status and `/townyeras admin website publish` queues a fresh graph for the editor. In-game operator commands require Admin Mode. No shared secrets are included in this package.
5. Keep the real server in Ancient until you intend the transition. For an isolated test server only, an authorized administrator can use `/townyeras admin time era classical`. This sets that server's era/calendar and is not needed simply to install the update.

The existing website cache revision check prevents an older bundled-graph cache from silently restoring itself on startup. This does not merge arbitrary pending website edits: review those separately rather than automatically overwriting them with the included seed.

## Verification

54 automated tests pass. The isolated Minecraft test loads the installed Farmer's Delight, Aquaculture, Macaw Bridges, Macaw Paths, Supplementaries, Moonlight, Brewin' and Chewin', and AstikorCarts Redux JARs alongside TownyEras.

Checks cover all 19 new nodes' counters/completion and persistence, future-era denial, Ancient catch-up, registered items and recipes for every new output, actual honey harvesting, stonecutter pickup and shift-click input preservation, stone ingredient processing, smoked ham, offline grants, banquet cooking/result extraction, and the bell recipe. The existing 77-node admin-grant audit, no-trade checks, public utility rules, workstation checks, tutorial, scrollbar, remembered tabs, and era-visibility UI tests also pass.

These are local, isolated checks, not a certification of the full live server's plugins, claims, world configuration, or every possible third-party automation system. Test this package on a staging copy before rolling it out broadly.
