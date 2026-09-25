# WorldTools 1.2.8+26.2 — Minecraft 26.2 Support

This release brings WorldTools to **Minecraft 26.2 on Fabric**. It is the first 26.x build of WorldTools anywhere —
upstream development ended at 1.21.11, so every change below is novel work by [@Promptt001](https://github.com/Promptt001)
(22 commits, `1bf69b3..d0cce08`).

## Highlights

- **Full migration to Minecraft 26.2**, including a complete move from Yarn to Mojang (official) mappings and a
  single-project Fabric Loom restructure.
- **Captured worlds now round-trip correctly on 26.2.** Minecraft 26.2 changed the world save format in several
  undocumented ways; this release contains novel fixes for each of them, verified by capturing a live server world
  and reopening it in singleplayer with all chunks, entities, containers, players, statistics, advancements and
  metadata intact.

## Changed

- **Mappings: Yarn → Mojang (official).** Minecraft is unobfuscated from 26.1 onward and Fabric Loom no longer
  remaps through Yarn; the entire codebase (classes, methods, fields, mixin annotations, access widener) was
  migrated to official names. The access widener now uses the `official` namespace.
- **Project restructure**: the old multi-project common/fabric/forge layout was replaced by a single-project
  Fabric Loom layout (Forge support was already retired upstream).
- **Toolchain**: Gradle 9.5.1, Fabric Loom 1.17.20, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2,
  Fabric Language Kotlin 1.14.1, Java 25.
- **All systems ported to 26.2 APIs**: chunk storage (`RegionBasedChunk`, `CustomRegionBasedStorage`,
  `StorageFlow`), world metadata (`LevelDataStoreable`, `MetadataStoreable`), container data injection, capture
  manager and events, GUI screens, boss bar HUD, debug renderer, and all mixins.
- **Game rules**: the `doFireTick` rule no longer exists in 26.2; the capture setting is now mapped to its
  replacement `FIRE_SPREAD_RADIUS_AROUND_PLAYER` (0 = fire spread off).

## Fixed — 26.2 save-format changes (all novel, previously undocumented)

- **Captured chunks were silently ignored on load** (`65229ea`): Minecraft 26.2 stores every dimension —
  including the overworld — under `dimensions/<namespace>/<path>/` instead of at the save root. Captures written
  to the old layout were never read, and vanilla silently regenerated natural terrain over the captured area.
  WorldTools now writes to the new layout.
- **Captured chunks failed to decode** (`c5888b7`): the chunk `Status` tag must be a registry Identifier
  (e.g. `minecraft:full`); writing the raw integer registry id made vanilla reject every captured chunk
  (`Unknown registry key ... minecraft:11`) and run worldgen over the captured terrain.
- **"Invalid player data" kick on first join** (`43e5448`): player data files (advancements, statistics) must
  carry the current integer `DataVersion`; without it vanilla assumes pre-1.12 data and runs the entire datafix
  pipeline, corrupting the load. The files are now stamped correctly.
- **Captured worlds would not open at all** (`91ccc7c`): 26.2 moved world gen settings and game rules out of
  `level.dat` into separate saved-data files. WorldTools now writes `data/minecraft/world_gen_settings.dat`
  (including the renamed `generate_structures` key) and `data/minecraft/game_rules.dat`.
- **Statistics round-trip** (`65229ea`): statistics JSON keys were written as raw integer registry ids, which
  vanilla discarded on load; they are now proper identifiers.

## Fixed — runtime issues found during the migration

- **Capture-stop deadlock** (`1048411`): manual palette-container locking around codec encode self-deadlocked on
  26.2's non-reentrant `ThreadingDetector` semaphore, leaving the capture in a hung state.
- **Crash on opening any singleplayer world** (`5b2a25a`): the backup-prompt mixin call site moved to a different
  method in 26.2; retargeted.
- **Empty configuration screen** (`d479e9f`): the old cloth-config API is a no-op stub in 26.2; the config screen
  (General / Entities / Advanced tabs, via Mod Menu and the manager GUI) renders correctly again.
- **Out-of-bounds crash at capture start** (`91ccc7c`): the client chunk cache sizing math changed in 26.2
  (`viewRange` is already the diameter).
- **`IllegalAccessError` on world join** (`f44c6e2`): the access widener is now declared in `fabric.mod.json` so
  the loader applies it at runtime, not just at compile time.
- Removed the obsolete `PalettedContainer` lock mixin — 26.2 exposes public `acquire()`/`release()`.

## Added in 26.2.1 — custom-dimension and player-state fixes

These four issues were originally identified and fixed in the
[Ronal-SHEN/WorldTools-update](https://github.com/Ronal-SHEN/WorldTools-update) fork (GPL, shared upstream lineage);
ported here and adapted and re-verified for 26.2.

- **Custom-dimension servers now round-trip correctly** (`d0cce08`): `mc.connection.levels()` is empty on servers
  with non-standard dimensions (e.g. `play.hollowcube.net`), which left the level.dat dimensions registry empty
  and made captured worlds fail to load with "Overworld settings missing". The registry is now sourced from what
  was actually captured (the player's dimension and every dimension with saved chunks/entities), and a synthetic
  `minecraft:overworld` entry is added when the server has none.
- **Player no longer spawns in an empty overworld** (`d0cce08`): the vanilla entity save omits the `Dimension`
  tag; in singleplayer the integrated server loads `playerdata/<uuid>.dat` in preference to `level.dat`, so on
  custom-dimension servers the player was dropped into the default (often empty) overworld. The `Dimension` tag
  is now written explicitly in playerdata.
- **Respawn now lands on the captured build** (`d0cce08`): 26.1+ reads the world spawn from a `spawn` compound
  (flat `{dimension, pos, yaw, pitch}`) instead of the legacy `SpawnX/Y/Z` fields, which are now ignored. The
  spawn is anchored to the captured player's position and dimension.
- **New "Modify Player Behavior" option** (`d0cce08`, World → Player Behavior, default off): optionally strips
  server-locked player state (`abilities` with flySpeed 0, `mayBuild` false, spectator/adventure game type) so
  the downloaded world stays usable in every game mode. Disabled by default to preserve captured state verbatim.
- Localization: config entries for the new option added for all 14 supported languages.

## Requirements

- Minecraft 26.2, Fabric Loader ≥ 0.19.5, Java ≥ 25
- [Fabric API](https://modrinth.com/mod/fabric-api), [fabric-language-kotlin](https://modrinth.com/mod/fabric-language-kotlin), [Cloth Config](https://modrinth.com/mod/cloth-config), [Mod Menu](https://modrinth.com/mod/modmenu)

**Full changelog (vs. `WorldTools_1.21.11`)**: https://github.com/Promptt001/WorldTools/compare/WorldTools_1.21.11...26.2.1
