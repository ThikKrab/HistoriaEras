# HistoriaEras

Server-authoritative historical technology progression for HistoriaMC.
Minecraft **1.21.1**, NeoForge **21.1.241**, Java **21**.
Current source version: **0.7.1-alpha**.

## Features

- 77 technologies: 51 Ancient and 26 Classical, with class-specific progression.
- Universal technologies plus two selected classes per player.
- Server-tracked objectives, source attribution and anti-trade progress rules.
- Recipe, workstation and supported interaction restrictions.
- Technology map, unlock icons, search, tracking, draggable details scrollbar,
  remembered page and Getting Started tutorial.
- Historical calendar, admin mode and persistent node grants.
- Website-managed technology synchronization when configured on the server.

The player-facing name is HistoriaEras. The old `townyeras` mod ID, internal
package, resource namespaces and save/config names are intentionally retained
for compatibility with existing worlds and settings. The main command is
`/historiaeras`; `/townyeras` remains a compatibility alias.

## Build

Install JDK 21, then run from this repository's root:

Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```sh
chmod +x gradlew
./gradlew clean build
```

The wrapper downloads Gradle and development dependencies. The first build needs
internet access and may take substantially longer than later builds. A system
Gradle installation is not required.

Built files:

- `build/libs/historiaeras-0.7.1-alpha.jar` — installable mod.
- `build/libs/historiaeras-0.7.1-alpha-sources.jar` — source archive, not a mod to install.

## Tests

```powershell
.\gradlew.bat test
```

The 57 automated checks cover progression, graph geometry, public utility rules,
website payload handling and the HistoriaEras rename. The included GitHub Actions
workflow builds and runs these tests on pushes and pull requests. It does not
launch Minecraft or connect to a live server.

Development runs:

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runUiTestClient
```

The UI test is opt-in, creates/uses an isolated local test world and exits after
verification. It is NOT a headless CI test. Its full gameplay audit requires the
third-party mods described in `docs/DEVELOPMENT.md`, which are not included here.
Never point development runs at a live server world.

## Install

Back up the server world/configuration, stop the server, and replace its old
TownyEras JAR with the new HistoriaEras JAR. Replace the JAR in client modpacks as
well. Do not install both: they share the same mod ID. Existing progress is kept.
See `docs/RELEASE_NOTES_0.7.1-alpha.md`.

## Website configuration

The safe template is `townyeras-website.properties.example`. On a dedicated server,
copy it to `config/townyeras-website.properties`, then supply the server's actual
bridge secret privately. Never commit that configured file or distribute the
secret with a client modpack. Website integration requires a compatible backend;
the website/backend itself is not part of this mod repository.

## Source layout

- `src/main/java/` — gameplay, UI, networking, persistence, commands and mixins.
- `src/main/resources/` — language text, technology graph, recipes and tags.
- `src/main/templates/` — generated NeoForge metadata template.
- `src/test/java/` — automated tests.
- `gradle/`, `gradlew`, `gradlew.bat` — reproducible build wrapper.
- `.github/workflows/build.yml` — build/test automation.
- `docs/` — Classical catalog, release notes, development notes and setup guide.

The effective technology graph is
`src/main/resources/data/townyeras/townyeras/technology.json`.
Changing it requires validating the graph, actual installed item/recipe IDs and
server enforcement; editing a display label alone does not implement an ability.

## Known limits

Ancient and Classical are the playable eras; later-era metadata is reserved for
future work. Compatibility with every menu, machine, plugin and automation path
in the complete live modpack still needs in-server testing. Anonymous machines
and hoppers do not always provide an acting player's identity. Bukkit-to-mod
player-data migration is not implemented. The mod does not bundle third-party
mods, server worlds, website code or live configuration.

## License

HistoriaMC code is **All Rights Reserved**, as in `LICENSE.txt`. Publishing this
repository does not make it open-source. The original NeoForge template remains
subject to `TEMPLATE_LICENSE.txt`; both notices are included in release JARs.
