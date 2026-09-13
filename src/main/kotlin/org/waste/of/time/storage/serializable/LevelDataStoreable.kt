package org.waste.of.time.storage.serializable

import net.minecraft.SharedConstants
import net.minecraft.nbt.*
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.ProblemReporter
import net.minecraft.util.Util
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.gamerules.GameRuleMap
import java.nio.file.Files
import java.nio.file.Path
import org.waste.of.time.Utils.sanitizePlayerForSingleplayer
import org.waste.of.time.Utils.toByte
import org.waste.of.time.WorldTools.DAT_EXTENSION
import org.waste.of.time.WorldTools.LOG
import org.waste.of.time.WorldTools.config
import org.waste.of.time.WorldTools.mc
import org.waste.of.time.config.WorldToolsConfig.World.WorldGenerator.GeneratorType
import org.waste.of.time.manager.CaptureManager
import org.waste.of.time.manager.CaptureManager.currentLevelName
import org.waste.of.time.manager.MessageManager
import org.waste.of.time.manager.MessageManager.translateHighlight
import org.waste.of.time.manager.StatisticManager
import org.waste.of.time.storage.CustomRegionBasedStorage
import org.waste.of.time.storage.Storeable
import java.io.File
import java.io.IOException

class LevelDataStoreable : Storeable() {
    override fun shouldStore() = config.general.capture.levelData

    override val verboseInfo: MutableComponent
        get() = translateHighlight(
            "worldtools.capture.saved.levelData",
            currentLevelName,
            "level${DAT_EXTENSION}"
        )

    override val anonymizedInfo: MutableComponent
        get() = verboseInfo

    /**
     * See [net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess.backupLevelDataFile]
     */
    override fun store(
        session: LevelStorageAccess,
        cachedStorages: MutableMap<String, CustomRegionBasedStorage>
    ) {
        val resultingFile = session.getLevelPath(LevelResource.ROOT).toFile()
        val dataNbt = serializeLevelData()
        // if we save an empty level.dat, clients will crash when opening the SP worlds screen
        if (dataNbt.isEmpty) throw RuntimeException("Failed to serialize level data")
        val levelNbt = CompoundTag().apply {
            put("Data", dataNbt)
        }

        try {
            val newFile = File.createTempFile("level", DAT_EXTENSION, resultingFile).toPath()
            NbtIo.writeCompressed(levelNbt, newFile)
            val backup = session.getLevelPath(LevelResource.OLD_LEVEL_DATA_FILE)
            val current = session.getLevelPath(LevelResource.LEVEL_DATA_FILE)
            Util.safeReplaceFile(current, newFile, backup)
            LOG.info("Saved level data.")
        } catch (exception: IOException) {
            MessageManager.sendError(
                "worldtools.log.error.failed_to_save_level",
                resultingFile.path,
                exception.localizedMessage
            )
        }

        // 26.2 stores world gen settings (and game rules) in separate saved-data files next to
        // level.dat, read via LevelStorageSource.readExistingSavedData(WorldGenSettings.TYPE / GameRuleMap.TYPE).
        // Without data/minecraft/world_gen_settings.dat the world fails to load ("Overworld settings missing").
        try {
            val dataDir = session.getLevelPath(LevelResource.DATA)
            writeSavedDataFile(
                dataDir.resolve("minecraft/world_gen_settings.dat"),
                worldGenSettingsNbt()
            )
            writeSavedDataFile(
                dataDir.resolve("minecraft/game_rules.dat"),
                gameRulesNbt()
            )
            LOG.info("Saved world gen settings and game rules.")
        } catch (exception: IOException) {
            MessageManager.sendError(
                "worldtools.log.error.failed_to_save_level",
                resultingFile.path,
                exception.localizedMessage
            )
        }
    }

    /**
     * Mirrors LevelStorageSource.writeSavedData: compressed {data: <nbt>, DataVersion} file.
     */
    private fun writeSavedDataFile(target: Path, dataNbt: CompoundTag) {
        val wrapper = CompoundTag().apply {
            put("data", dataNbt)
            NbtUtils.addCurrentDataVersion(this)
        }
        Files.createDirectories(target.parent)
        val newFile = Files.createTempFile(target.parent, "saved_data", DAT_EXTENSION)
        try {
            NbtIo.writeCompressed(wrapper, newFile)
            Files.move(newFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(newFile)
        }
    }

    /**
     * See [net.minecraft.world.level.LevelProperties.updateProperties]
     */
    private fun serializeLevelData() = CompoundTag().apply {
        val player = CaptureManager.lastPlayer ?: mc.player ?: return@apply

        mc.connection?.let { listener ->
            // serverBrand is protected in 26.2; read via connection brand channel info
        }
        mc.connection?.serverBrand()?.let {
            put("ServerBrands", ListTag().apply {
                add(StringTag.valueOf(it))
            })
        }

        putBoolean("WasModded", false)

        // skip removed features

        put("Version", CompoundTag().apply {
            putString("Name", SharedConstants.getCurrentVersion().name())
            putInt("Id", SharedConstants.getCurrentVersion().dataVersion().version())
            putBoolean("Snapshot", !SharedConstants.getCurrentVersion().stable())
            putString("Series", SharedConstants.getCurrentVersion().dataVersion().series())
        })

        NbtUtils.addCurrentDataVersion(this)

        put("WorldGenSettings", generatorMockNbt())
        mc.connection?.getOnlinePlayers()?.find {
            it.profile.id == player.uuid
        }?.let {
            putInt("GameType", it.gameMode.getId())
        } ?: putInt("GameType", player.level().getServer()?.defaultGameType?.id ?: 0)

        // Since 26.1 the world spawn is read from a "spawn" compound (LevelData.RespawnData.CODEC:
        // flat {dimension, pos, yaw, pitch}; GlobalPos.MAP_CODEC is inlined without a wrapper key),
        // not the legacy SpawnX/Y/Z fields (which are now ignored, leaving spawn at overworld
        // 0,0,0 -> the void on custom-dimension servers). Anchor it to the captured player's
        // position and dimension so respawns land on the actual build rather than an empty overworld.
        put("spawn", CompoundTag().apply {
            put("pos", IntArrayTag(intArrayOf(player.getBlockX(), player.getBlockY(), player.getBlockZ())))
            putFloat("yaw", player.getYRot())
            putFloat("pitch", player.getXRot())
            putString("dimension", "minecraft:${player.level().dimension().identifier().path}")
        })
        putLong("Time", player.level().getLevelData().getGameTime())
        putLong("DayTime", player.level().getOverworldClockTime())
        putLong("LastPlayed", System.currentTimeMillis())
        putString("LevelName", currentLevelName)
        putInt("version", 19133)
        putInt("clearWeatherTime", 0) // not sure
        putInt("rainTime", 0) // not sure
        putBoolean("raining", player.level().isRaining())
        putBoolean("thundering", player.level().isThundering())
        putBoolean("hardcore", player.level().getLevelData().isHardcore())
        putInt("thunderTime", 0) // not sure
        putBoolean("allowCommands", true) // not sure
        putBoolean("initialized", true) // not sure

        val worldBorderNbt = WorldBorder.CODEC.encodeStart(NbtOps.INSTANCE, player.level().getWorldBorder())
            .getOrThrow { error -> IllegalStateException("Failed to encode world border: $error") }
        put("WorldBorder", worldBorderNbt)

        putByte("Difficulty", player.level().getLevelData().getDifficulty().id.toByte())
        putBoolean("DifficultyLocked", false) // not sure

        // ToDo: Seems that the client side game rules were removed. Now only works for single player :/
        // Game rules need to be serialized using the CODEC now
        val server = player.level().getServer()
        val rulesNbt = if (server != null) {
            val codec = GameRules.codec(server.worldData.dataConfiguration.enabledFeatures)
            codec.encodeStart(NbtOps.INSTANCE, server.overworld().getGameRules()).getOrThrow { error -> IllegalStateException("Failed to encode game rules: $error") } as CompoundTag
        } else {
            CompoundTag()
        }
        put("GameRules", rulesNbt)
        val playerWriteView = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING)
        player.saveWithoutId(playerWriteView)
        put("Player", playerWriteView.buildResult().apply {
            remove("LastDeathLocation") // can contain sensitive information
            putString("Dimension", "minecraft:${player.level().dimension().identifier().path}")
            if (config.world.playerBehavior.modifyPlayerBehavior) {
                sanitizePlayerForSingleplayer()
            }
        })

        put("DragonFight", CompoundTag()) // not sure
        put("CustomBossEvents", CompoundTag()) // not sure
        put("ScheduledEvents", ListTag()) // not sure
        putInt("WanderingTraderSpawnDelay", 0) // not sure
        putInt("WanderingTraderSpawnChance", 0) // not sure

        // skip wandering trader id
    }

    private fun GameRules.genGameRules() = CompoundTag().also { output ->
        this.availableRules().forEach { rule ->
            output.putString(rule.getIdentifier().path, this.getAsString(rule))
        }
    }.apply {
        val roomDefinition = config.world.gameRules
        if (!roomDefinition.modifyGameRules) return@apply

        putString(GameRules.SPAWN_WARDENS.getIdentifier().path, roomDefinition.doWardenSpawning.toString())
        putString("doFireTick", roomDefinition.doFireTick.toString())
        putString(GameRules.SPREAD_VINES.getIdentifier().path, roomDefinition.doVinesSpread.toString())
        putString(GameRules.SPAWN_MOBS.getIdentifier().path, roomDefinition.doMobSpawning.toString())
        putString(GameRules.ADVANCE_TIME.getIdentifier().path, roomDefinition.doDaylightCycle.toString())
        putString(GameRules.KEEP_INVENTORY.getIdentifier().path, roomDefinition.keepInventory.toString())
        putString(GameRules.MOB_GRIEFING.getIdentifier().path, roomDefinition.doMobGriefing.toString())
        putString(GameRules.SPAWN_WANDERING_TRADERS.getIdentifier().path, roomDefinition.doTraderSpawning.toString())
        putString(GameRules.SPAWN_PATROLS.getIdentifier().path, roomDefinition.doPatrolSpawning.toString())
        putString(GameRules.ADVANCE_WEATHER.getIdentifier().path, roomDefinition.doWeatherCycle.toString())
    }

    /**
     * 26.2 reads world gen settings from data/minecraft/world_gen_settings.dat via
     * WorldGenSettings.CODEC: {seed: long, generate_structures: bool, bonus_chest: bool, dimensions: {...}}.
     */
    private fun worldGenSettingsNbt() = generatorMockNbt()

    /**
     * 26.2 reads game rules from data/minecraft/game_rules.dat via GameRuleMap.CODEC
     * (dispatchedMap: rule name -> typed value). Apply config overrides, then encode with the
     * vanilla codec so the value types are always correct.
     */
    private fun gameRulesNbt(): CompoundTag {
        val rules = GameRuleMap.of()
        val roomDefinition = config.world.gameRules
        if (roomDefinition.modifyGameRules) {
            fun boolRule(rule: GameRule<Boolean>, value: Boolean) = rules.set(rule, value)

            boolRule(GameRules.SPAWN_WARDENS, roomDefinition.doWardenSpawning)
            boolRule(GameRules.SPREAD_VINES, roomDefinition.doVinesSpread)
            boolRule(GameRules.SPAWN_MOBS, roomDefinition.doMobSpawning)
            boolRule(GameRules.ADVANCE_TIME, roomDefinition.doDaylightCycle)
            boolRule(GameRules.KEEP_INVENTORY, roomDefinition.keepInventory)
            boolRule(GameRules.MOB_GRIEFING, roomDefinition.doMobGriefing)
            boolRule(GameRules.SPAWN_WANDERING_TRADERS, roomDefinition.doTraderSpawning)
            boolRule(GameRules.SPAWN_PATROLS, roomDefinition.doPatrolSpawning)
            boolRule(GameRules.ADVANCE_WEATHER, roomDefinition.doWeatherCycle)
            // doFireTick (bool) was replaced in 26.2 by FIRE_SPREAD_RADIUS_AROUND_PLAYER (int, 0 = off)
            rules.set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, if (roomDefinition.doFireTick) 1 else 0)
        }
        val codec = GameRules.codec(FeatureFlags.VANILLA_SET)
        @Suppress("UNCHECKED_CAST")
        val gameRules = GameRules(FeatureFlags.VANILLA_SET, rules)
        return codec.encodeStart(NbtOps.INSTANCE, gameRules)
            .getOrThrow { error -> IllegalStateException("Failed to encode game rules: $error") } as CompoundTag
    }

    private fun generatorMockNbt() = CompoundTag().apply {
        putByte("bonus_chest", config.world.worldGenerator.generateBonusChest.toByte())
        putLong("seed", config.world.worldGenerator.seed)
        // 26.2 renamed this WorldOptions key: generate_features -> generate_structures
        putBoolean("generate_structures", config.world.worldGenerator.generateFeatures)

        put("dimensions", CompoundTag().apply {
            // Collect every dimension we actually captured. mc.connection.levels()
            // (lastWorldKeys) is empty on servers with non-standard dimensions
            // (e.g. play.hollowcube.net), which would leave this registry empty,
            // so also include the player's current dimension and every dimension we
            // saved chunks/block entities for.
            val dimensionPaths = linkedSetOf<String>().apply {
                addAll(CaptureManager.lastWorldKeys.map { it.identifier().path })
                (CaptureManager.lastPlayer ?: mc.player)?.let {
                    add(it.level().dimension().identifier().path)
                }
                addAll(StatisticManager.dimensions)
            }

            dimensionPaths.forEach { path ->
                put("minecraft:$path", CompoundTag().apply {
                    put("generator", generateGenerator(path))

                    when (path) {
                        "the_nether" -> {
                            putString("type", "minecraft:the_nether")
                        }
                        "the_end" -> {
                            putString("type", "minecraft:the_end")
                        }
                        else -> {
                            putString("type", "minecraft:overworld")
                        }
                    }
                })
            }

            // Vanilla's WorldDimensions codec requires a minecraft:overworld entry,
            // otherwise loading crashes with "Overworld settings missing". If the server
            // had no overworld, add an empty synthetic one so the captured custom
            // dimensions above still load (the player spawns in their saved dimension).
            if (dimensionPaths.none { it == "overworld" }) {
                put("minecraft:overworld", CompoundTag().apply {
                    put("generator", generateGenerator("overworld"))
                    putString("type", "minecraft:overworld")
                })
            }
        })
    }

    private fun generateGenerator(path: String) = CompoundTag().apply {
        when (config.world.worldGenerator.type) {
            GeneratorType.VOID -> voidGenerator()
            GeneratorType.DEFAULT -> defaultGenerator(path)
            GeneratorType.FLAT -> flatGenerator()
        }
    }

    private fun CompoundTag.voidGenerator() {
        put("settings", CompoundTag().apply {
            putByte("features", 1)
            putString("biome", "minecraft:the_void")
            put("layers", ListTag().apply {
                add(CompoundTag().apply {
                    putString("block", "minecraft:air")
                    putInt("height", 1)
                })
            })
            put("structure_overrides", ListTag())
            putByte("lakes", 0)
        })
        putString("type", "minecraft:flat")
    }

    private fun CompoundTag.defaultGenerator(path: String) {
        when (path) {
            "the_nether" -> {
                put("biome_source", CompoundTag().apply {
                    putString("preset", "minecraft:nether")
                    putString("type", "minecraft:multi_noise")
                })
                putString("settings", "minecraft:nether")
                putString("type", "minecraft:noise")
            }
            "the_end" -> {
                put("biome_source", CompoundTag().apply {
                    putString("type", "minecraft:the_end")
                })
                putString("settings", "minecraft:end")
                putString("type", "minecraft:noise")
            }
            else -> {
                put("biome_source", CompoundTag().apply {
                    putString("preset", "minecraft:overworld")
                    putString("type", "minecraft:multi_noise")
                })
                putString("settings", "minecraft:overworld")
                putString("type", "minecraft:noise")
            }
        }
    }

    private fun CompoundTag.flatGenerator() {
        put("settings", CompoundTag().apply {
            putString("biome", "minecraft:plains")
            putByte("features", 0)
            putByte("lakes", 0)
            put("layers", ListTag().apply {
                add(CompoundTag().apply {
                    putString("block", "minecraft:bedrock")
                    putInt("height", 1)
                })
                add(CompoundTag().apply {
                    putString("block", "minecraft:dirt")
                    putInt("height", 2)
                })
                add(CompoundTag().apply {
                    putString("block", "minecraft:grass_block")
                    putInt("height", 1)
                })
            })
            put("structure_overrides", ListTag().apply {
                add(StringTag.valueOf("minecraft:strongholds"))
                add(StringTag.valueOf("minecraft:villages"))
            })
        })
        putString("type", "minecraft:flat")
    }
}
