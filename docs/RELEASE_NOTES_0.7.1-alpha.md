# HistoriaEras 0.7.1-alpha

## Rename

TownyEras is now named HistoriaEras throughout the mod's visible interface:
NeoForge mod list, dashboard, navigation logo, tutorial, keybind settings,
reminders, chat messages, technology-denial messages and diagnostic log text.
The navigation logo uses HISTORIA / ERAS, with fitted text on small displays
and HE for the compact sidebar. Java classes and release artifacts also use
HistoriaEras.

The primary command is `/historiaeras`. All existing subcommands and permission
checks are unchanged. `/townyeras` redirects to the same command tree so existing
server scripts keep working. The dashboard now sends the new command name.

## Compatibility

This is a branding release, not a progress migration. All 77 technology nodes,
prerequisites, progression counters, grants, classes, dates and gameplay rules
remain unchanged. The following technical identities deliberately retain the
old lowercase name:

- NeoForge mod ID `townyeras` and resource/recipe/network namespaces.
- The `townyeras_progress.dat` save and existing config/preference filenames.
- Localization keys, saved keybind identity and website source identifiers.
- The internal Java package and mixin configuration filename.

These are compatibility identifiers, not player-facing branding. Renaming them
would require coordinated migration of server data, client settings and website
integration; this release avoids that disruption.

## Install

1. Back up the server world and configuration, then stop the server normally.
2. Remove the previous TownyEras JAR from the server's mods folder.
3. Install `historiaeras-0.7.1-alpha.jar` instead.
4. Replace the old JAR with the same new JAR in each client's mods folder.
5. Restart the server and client. Existing progress/configs should load directly.

Do not install old and new JARs together: they intentionally share one mod ID.
No progress reset, website edit, era change or config rename is required.
This package does not automatically install itself on the live server/client.

## Verification

The release is checked by the automated progression suite plus branding checks
for localized names, preserved identities, absence of old visible branding and
the fitted navigation logo. The isolated Minecraft smoke test additionally checks
new commands and the legacy redirect, admin permissions, all nodes, production
gates and the dashboard UI. See the packaged test report for final results.
