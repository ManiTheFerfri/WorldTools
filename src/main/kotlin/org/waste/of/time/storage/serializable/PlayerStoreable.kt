package org.waste.of.time.storage.serializable

import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.NbtIo
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.ProblemReporter
import net.minecraft.util.Util
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess
import org.waste.of.time.Utils.asString
import org.waste.of.time.WorldTools
import org.waste.of.time.WorldTools.config
import org.waste.of.time.manager.MessageManager.translateHighlight
import org.waste.of.time.manager.StatisticManager
import org.waste.of.time.storage.Cacheable
import org.waste.of.time.storage.CustomRegionBasedStorage
import org.waste.of.time.storage.Storeable
import org.waste.of.time.storage.cache.HotCache
import java.io.File
import java.nio.file.Path

data class PlayerStoreable(
    val player: Player
) : Cacheable, Storeable() {
    override fun shouldStore() = config.general.capture.players

    override val verboseInfo: MutableComponent
        get() = translateHighlight(
            "worldtools.capture.saved.player",
            player.name,
            player.position.debugInfo(),
            player.level.registryKey.value.path
        )

    override val anonymizedInfo: MutableComponent
        get() = translateHighlight(
            "worldtools.capture.saved.player.anonymized",
            player.name,
            player.level.registryKey.value.path
        )

    override fun cache() {
        HotCache.players.add(this)
    }

    override fun flush() {
        HotCache.players.remove(this)
    }

    override fun store(session: LevelStorageAccess, cachedStorages: MutableMap<String, CustomRegionBasedStorage>) {
        savePlayerData(player, session)
        session.createPlayerStorage()
        StatisticManager.players++
        StatisticManager.dimensions.add(player.level.registryKey.value.path)
    }

    private fun savePlayerData(player: Player, session: LevelStorageAccess) {
        try {
            val playerDir = session.getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile()
            playerDir.mkdirs()

            val writeView = TagValueOutput.create(ProblemReporter.DISCARDING)
            player.writeData(writeView)
            val playerNbt = writeView.output.apply {
                if (config.entity.censor.lastDeathLocation) {
                    remove("LastDeathLocation")
                }
            }
            
            val newPlayerFile = File.createTempFile(player.stringUUID + "-", ".dat", playerDir).toPath()
            NbtIo.writeCompressed(playerNbt, newPlayerFile)
            val currentFile = File(playerDir, player.stringUUID + ".dat").toPath()
            val tempFile = File(playerDir, player.stringUUID + ".dat_old").toPath()
            Util.backupAndReplace(currentFile, newPlayerFile, tempFile)
        } catch (e: Exception) {
            WorldTools.LOG.warn("Failed to save player data for {}", player.name.text)
        }
    }
}
