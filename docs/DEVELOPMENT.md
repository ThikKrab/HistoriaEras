# Development notes

## Build inputs

Java 21, the committed Gradle wrapper, NeoForge 21.1.241, Minecraft 1.21.1 and the
versions declared in `gradle.properties` / `build.gradle`. Gradle resolves the
development dependencies; no files from the original Codex workspace are required.

The repository includes all production Java/resources/templates and all unit
tests. Helper files used to assemble earlier proposals/releases are not required
for compilation and are not included. Old work folders and build caches are excluded.

## Optional isolated in-game audit

The previous successful 0.7.1 audit used these third-party files in
`run-ui-test/mods/`, in addition to the development mod:

- `Aquaculture-1.21.1-2.7.21.jar`
- `astikorcartsredux-1.2.3.jar`
- `BrewinAndChewin-neoforge-4.5.0+1.21.1.jar`
- `FarmersDelight-1.21.1-1.3.2.jar`
- `mcw-bridges-3.1.2-mc1.21.1neoforge.jar`
- `mcw-mcwpaths-1.1.1-mc1.21.1neoforge.jar`
- `moonlight-neoforge-1.21.1-3.1.1.jar`
- `supplementaries-neoforge-1.21.1-3.8.2.jar`

These are recorded test inputs, not bundled redistributables or a claim that they
are the newest versions. Obtain them separately through their authorized sources.
The full runtime audit is not expected to pass without its required integration
mods. Regular `clean build` and `test` do not require them.

Never commit those JARs or the generated test world. The UI test enables its own
development session, opens a graphical client and writes screenshots/a success
marker under `run-ui-test/`. Those are development artifacts, not server data.

## Compatibility and progression rules

- Keep the `townyeras` compatibility namespace unless implementing a coordinated
  migration of world data, network/resource IDs, client settings and website data.
- Keep source attribution strict; transferred items must not advance collection
  objectives. Simplified display labels must not weaken tracking.
- Only current/past eras are available to ordinary players. Future-era details
  remain concealed; admin mode follows its existing permission checks.
- Utility use of ladders, wooden storage, carts, boats and maps remains public.
  Manufacturing restrictions are distinct from use restrictions.
- Validate real item and recipe IDs against the installed modpack before adding
  nodes. Prerequisites must make the objectives achievable without circular gates.
- Preserve existing node IDs and player progress. Shared nodes have one identity
  with eligible alternative class routes, not duplicate progress records.

The bundled graph and tests are the source of truth. The Classical catalog in
`docs/CLASSICAL_NODE_CATALOG.md` records the shipped Classical node definitions.
