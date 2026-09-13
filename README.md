<p align="center">
  <img src="src/main/resources/assets/worldtools/WorldTools.png?raw=true" alt="WorldTools" width="256" height="256" style="display: block; margin-left: auto; margin-right: auto;">
</p>

# WorldTools: World Downloader (Fabric)

[![CurseForge Downloads](https://cf.way2muchnoise.eu/worldtools.svg?badge_style=for_the_badge)](https://www.curseforge.com/minecraft/mc-mods/worldtools)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/FlFKBOIX?style=for-the-badge&logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/mod/worldtools)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-lime?style=for-the-badge&link=https://www.minecraft.net/)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue?style=for-the-badge&link=https://www.gnu.org/licenses/gpl-3.0.en.html)](https://www.gnu.org/licenses/gpl-3.0.en.html)

WorldTools is a powerful Minecraft mod that allows you to capture and save high-detail snapshots of server worlds
locally.
It empowers you to download comprehensive information, including chunks, entities,
chests, players, statistics, advancements, and detailed metadata.
WorldTools ensures that you can retain an accurate and unaltered representation of the server's world for analysis,
sharing, or backup purposes on your local machine.

> **Note:** This fork targets **Minecraft 26.2 on Fabric**. The Forge port has been retired upstream; this repository
> is Fabric-only.

## About this fork

This repository is a fork of [Avanatiker/WorldTools](https://github.com/Avanatiker/WorldTools), maintained by
[Promptt001](https://github.com/Promptt001). Upstream development stopped at Minecraft 1.21.11 and contains no 26.x
branch, so the 26.2 support in this fork is **novel work, not an upstream port**.

### What Promptt001 contributed

All 21 commits between upstream's `WorldTools_1.21.11` release and this fork's `26.2` release were authored by
Promptt001. In summary, the work falls into three layers:

**1. Full migration to Minecraft 26.2 and its new Mojang-official toolchain**

- Migrated the entire codebase from Yarn to **Mojang (official) mappings** — required because 26.1+ Minecraft is
  unobfuscated and Fabric Loom no longer remaps through Yarn. This touched every class, method, field, and mixin
  annotation in the mod.
- Restructured the project from the old multi-project (common/fabric/forge) layout to a **single-project
  Fabric Loom layout**, and rebuilt the build config for Gradle 9.5.1, Loom 1.17.20, Loader 0.19.5,
  Fabric API 0.160.0+26.2, Fabric Language Kotlin 1.14.1, and Java 25.
- Ported every storage engine component (`RegionBasedChunk`, `CustomRegionBasedStorage`, `LevelDataStoreable`,
  `MetadataStoreable`, `DataInjectionHandler`, `StorageFlow`, ...) and every GUI, mixin, and event handler to the
  26.2 APIs.

**2. Novel fixes for undocumented 26.2 save-format changes**

Minecraft 26.2 changed the world save format in ways that break a naive port; each of these was diagnosed from
runtime logs and verified against vanilla bytecode, and none are documented in any changelog:

- Captured chunks were silently ignored and vanilla regenerated terrain over them — fixed by writing chunks to
  the new `dimensions/<namespace>/<path>/` save layout (26.2 moved **every** dimension, including the overworld,
  off the save root).
- Captured chunks failed decode (`Unknown registry key ... minecraft:11`) — fixed the chunk `Status` tag, which
  must be an Identifier, not the raw integer registry id.
- First join on a captured world kicked the player with "Invalid player data" — fixed by stamping the current
  integer `DataVersion` into the advancements (and statistics) JSON so vanilla's datafix pipeline does not
  misinterpret the files as pre-1.12 data.
- Captured worlds were unreadable in 26.2 — world gen settings and game rules moved out of `level.dat` into
  separate saved-data files (`world_gen_settings.dat`, `game_rules.dat`); the mod now writes both, including
  the renamed `generate_structures` key and the `FIRE_SPREAD_RADIUS_AROUND_PLAYER` rule that replaced
  `doFireTick`.
- Fixed a capture-stop deadlock caused by manual palette-container locking around codec encode (26.2's
  `acquire()/release()` are the raw non-reentrant `ThreadingDetector` lock).
- Fixed the world-open crash from a moved mixin call site, an empty configuration screen (cloth-config 26.2
  API change), an out-of-bounds in the initial chunk cache sync, statistics JSON keys, and an access widener
  that compiled but was not applied at runtime.

**3. Verified end-to-end**

The full capture round-trip — join a server, capture chunks/entities/containers, stop, save, and reopen the
world in singleplayer with all captured content intact — has been tested and confirmed working on 26.2.

### Credits

- **Original mod:** [Constructor](https://github.com/Constructor), [P529](https://github.com/P529)
  and [rfresh](https://github.com/rfresh) — see the
  [upstream repository](https://github.com/Avanatiker/WorldTools).
- **26.2 migration and maintenance:** [Promptt001](https://github.com/Promptt001)

## Features

- **World Download (_default keybind:_ `F12`)**:
  Initiate a quick download by hitting the `F12` key, which can be altered in the keybind settings.
  Alternatively, you can access the GUI (_default keybind:_ `F10`) via the escape menu.
  The GUI allows you to tailor the capture process according to your requirements.
  WorldTools facilitates the capture of a wide range of crucial elements, ensuring no detail is missed.
    - Chunks: Terrain, biomes and structures
    - Entities: Inventories and attributes of most entities
    - Containers: Contents of all tile entities like chests, shulkers, hoppers, furnaces, brewing stands, droppers,
      dispensers etc...
    - Players: Player positions and inventories
    - Statistics: Full personal player statistics
    - Advancements: Player advancements and progress
    - Special Objects: Maps, Lecterns and Banners
    - Detailed Metadata: Exhaustive capture details like motd, server version, timestamps, and more

- **Easy Access to Saved Worlds**: Your locally captured world save can be found in the single-player worlds list,
  allowing you to load and explore it conveniently.

- **Advanced Configuration**: WorldTools provides a wide range of settings to customize the capture process to your
  needs.
  Select elements to capture, modify game rules, alter entity NBT data, and configure the capture process in detail.

## Getting Started

1. **Installation**:
    - Install Fabric for Minecraft 26.2 by following the [Fabric Installation Guide](https://fabricmc.net/wiki/install).
    - Download the latest WorldTools JAR from the
      [releases page](https://github.com/Promptt001/WorldTools/releases)
      (for Minecraft 26.2, grab the `+26.2` artifact).
    - Place the mod JAR file in the `mods` folder of your Fabric installation.

2. **Prerequisites**: Make sure you have the following mods installed:
    - [Fabric API](https://modrinth.com/mod/fabric-api)
    - [fabric-language-kotlin](https://modrinth.com/mod/fabric-language-kotlin)
    - [Cloth Config API](https://modrinth.com/mod/cloth-config)
    - [Mod Menu](https://modrinth.com/mod/modmenu)

   Java 25 or newer is required.

### Usage

1. **Download**:
    - Enable capture mode: hit `F12`, use the GUI on the ESC menu, or run `/worldtools capture` to start capturing
      data.
    - Play the game normally while WorldTools downloads all data. You need to open containers like chests to capture
      their contents.
    - Save captured data: hit `F12`, use the GUI, or run `/worldtools capture` again to stop capturing and save the
      world.
2. **Access Downloaded World**: Your downloaded world can be found in the single-player worlds list.

### File Structure

After capturing data, WorldTools creates the following files in the world directory's folder:

- `Capture Metadata.md`: Contains detailed information about the capture process itself.

- `Dimension Tree.txt`: Provides a tree of all dimension folder paths of the server, not just the downloaded ones.

- `Player Entry List.csv`: Lists all players that were online during the capture including all known metadata.

## Supported Languages

For the best user experience, WorldTools is available in the following languages:

- German
- English (Pirate)
- English (United States)
- French (Canada)
- French (France)
- Dutch (Belgium)
- Dutch (Netherlands)
- Nynorsk (Norwegian)
- Norwegian (Norway)
- Portuguese (Brazil)
- Portuguese (Portugal)
- Russian
- Chinese (Simplified)
- Chinese (Traditional)

## Contributing

Contributions are welcome! Fork the repository, create a feature branch, and open a Pull Request against the
relevant version branch of this fork.

## Building

1. Clone the repository and run `./gradlew build`.
2. The mod JAR can be found in `build/libs/WorldTools-<version>+26.2.jar`.

Requires Java 25 and an active internet connection on the first run (dependencies are fetched from the
Fabric maven).

## License

WorldTools is distributed under
the [GNU General Public License v3.0](LICENSE.md).

---

**Disclaimer:** WorldTools is not affiliated with Mojang Studios. Minecraft is a registered trademark of Mojang
Studios. Use of the WorldTools software is subject to the terms outlined in the license agreement.
