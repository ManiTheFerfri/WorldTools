package org.waste.of.time.storage.serializable

import net.minecraft.SharedConstants
import net.minecraft.nbt.*
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.ProblemReporter
import net.minecraft.util.Util
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess
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
            Util.backupAndReplace(current, newFile, backup)
            LOG.info("Saved level data.")
        } catch (exception: IOException) {
            MessageManager.sendError(
                "worldtools.log.error.failed_to_save_level",
                resultingFile.path,
                exception.localizedMessage
            )
        }
    }

    /**
     * See [net.minecraft.world.level.LevelProperties.updateProperties]
     */
    private fun serializeLevelData() = CompoundTag().apply {
        val player = CaptureManager.lastPlayer ?: mc.player ?: return@apply

        mc.connection?.brand?.let {
            put("ServerBrands", ListTag().apply {
                add(StringTag.of(it))
            })
        }

        putBoolean("WasModded", false)

        // skip removed features

        put("Version", CompoundTag().apply {
            putString("Name", SharedConstants.getCurrentVersion().name())
            putInt("Id", SharedConstants.getCurrentVersion().dataVersion().id())
            putBoolean("Snapshot", !SharedConstants.getCurrentVersion().stable())
            putString("Series", SharedConstants.getCurrentVersion().dataVersion().series())
        })

        NbtUtils.putDataVersion(this)

        put("WorldGenSettings", generatorMockNbt())
        mc.connection?.listedPlayers?.find {
            it.profile.id == player.uuid
        }?.let {
            putInt("GameType", it.gameMode.getIndex())
        } ?: putInt("GameType", player.level.server?.gameType?.getIndex() ?: 0)

        putInt("SpawnX", player.level.levelData.getRespawnData().getPos().x)
        putInt("SpawnY", player.level.levelData.getRespawnData().getPos().y)
        putInt("SpawnZ", player.level.levelData.getRespawnData().getPos().z)
        putFloat("SpawnAngle", player.level.levelData.getRespawnData().yaw())
        putLong("Time", player.level.time)
        putLong("DayTime", player.level.dayTime)
        putLong("LastPlayed", System.currentTimeMs())
        putString("LevelName", currentLevelName)
        putInt("version", 19133)
        putInt("clearWeatherTime", 0) // not sure
        putInt("rainTime", 0) // not sure
        putBoolean("raining", player.level.isRaining)
        putBoolean("thundering", player.level.isThundering)
        putBoolean("hardcore", player.level.server?.hardcore ?: false)
        putInt("thunderTime", 0) // not sure
        putBoolean("allowCommands", true) // not sure
        putBoolean("initialized", true) // not sure

        val worldBorderNbt = WorldBorder.CODEC.encodeStart(NbtOps.INSTANCE, player.level.worldBorder)
            .getOrThrow { error -> IllegalStateException("Failed to encode world border: $error") }
        put("WorldBorder", worldBorderNbt)

        putByte("Difficulty", player.level.levelData.difficulty.id.toByte())
        putBoolean("DifficultyLocked", false) // not sure

        // ToDo: Seems that the client side game rules were removed. Now only works for single player :/
        // Game rules need to be serialized using the CODEC now
        val gameRules = player.level.server?.worldData?.getGameRules()
        val rulesNbt = if (gameRules != null) {
            val codec = net.minecraft.world.rule.GameRules.createCodec(player.level.server!!.worldData.dataConfiguration.enabledFeatures)
            codec.encodeStart(NbtOps.INSTANCE, gameRules).getOrThrow { error -> IllegalStateException("Failed to encode game rules: $error") } as CompoundTag
        } else {
            CompoundTag()
        }
        put("GameRules", rulesNbt)
        val playerWriteView = TagValueOutput.create(ProblemReporter.DISCARDING)
        player.writeData(playerWriteView)
        put("Player", playerWriteView.output.apply {
            remove("LastDeathLocation") // can contain sensitive information
            putString("Dimension", "minecraft:${player.level.registryKey.value.path}")
        })

        put("DragonFight", CompoundTag()) // not sure
        put("CustomBossEvents", CompoundTag()) // not sure
        put("ScheduledEvents", ListTag()) // not sure
        putInt("WanderingTraderSpawnDelay", 0) // not sure
        putInt("WanderingTraderSpawnChance", 0) // not sure

        // skip wandering trader id
    }

    private fun GameRules.genGameRules() = CompoundTag().also { output ->
        this.streamRules().forEach { rule ->
            output.putString(rule.id.path, this.getRuleValueName(rule))
        }
    }.apply {
        val roomDefinition = config.world.gameRules
        if (!roomDefinition.modifyGameRules) return@apply

        putString(GameRules.SPAWN_WARDENS.id.path, roomDefinition.doWardenSpawning.toString())
        putString("doFireTick", roomDefinition.doFireTick.toString())
        putString(GameRules.SPREAD_VINES.id.path, roomDefinition.doVinesSpread.toString())
        putString(GameRules.SPAWN_MOBS.id.path, roomDefinition.doMobSpawning.toString())
        putString(GameRules.ADVANCE_TIME.id.path, roomDefinition.doDaylightCycle.toString())
        putString(GameRules.KEEP_INVENTORY.id.path, roomDefinition.keepInventory.toString())
        putString(GameRules.MOB_GRIEFING.id.path, roomDefinition.doMobGriefing.toString())
        putString(GameRules.SPAWN_WANDERING_TRADERS.id.path, roomDefinition.doTraderSpawning.toString())
        putString(GameRules.SPAWN_PATROLS.id.path, roomDefinition.doPatrolSpawning.toString())
        putString(GameRules.ADVANCE_WEATHER.id.path, roomDefinition.doWeatherCycle.toString())
    }

    private fun generatorMockNbt() = CompoundTag().apply {
        putByte("bonus_chest", config.world.worldGenerator.generateBonusChest.toByte())
        putLong("seed", config.world.worldGenerator.seed)
        putByte("generate_features", config.world.worldGenerator.generateFeatures.toByte())

        put("dimensions", CompoundTag().apply {
            CaptureManager.lastWorldKeys.forEach { key ->
                put("minecraft:${key.value.path}", CompoundTag().apply {
                    put("generator", generateGenerator(key.value.path))

                    when (key.value.path) {
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
                add(StringTag.of("minecraft:strongholds"))
                add(StringTag.of("minecraft:villages"))
            })
        })
        putString("type", "minecraft:flat")
    }
}
