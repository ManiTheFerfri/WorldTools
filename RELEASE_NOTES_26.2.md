# WorldTools 1.2.8+26.2 — Minecraft 26.2 Support

This release brings WorldTools to **Minecraft 26.2 on Fabric**. It is the first 26.x build of WorldTools anywhere —
upstream development ended at 1.21.11, so every change below is novel work by [@Promptt001](https://github.com/Promptt001)
(21 commits, `1bf69b3..c5888b7`).

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

## Requirements

- Minecraft 26.2, Fabric Loader ≥ 0.19.5, Java ≥ 25
- [Fabric API](https://modrinth.com/mod/fabric-api), [fabric-language-kotlin](https://modrinth.com/mod/fabric-language-kotlin), [Cloth Config](https://modrinth.com/mod/cloth-config), [Mod Menu](https://modrinth.com/mod/modmenu)

**Full changelog (vs. `WorldTools_1.21.11`)**: https://github.com/Promptt001/WorldTools/compare/WorldTools_1.21.11...26.2
