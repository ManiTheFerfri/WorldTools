package org.waste.of.time.storage.serializable

import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.storage.LevelStorageSource
import org.waste.of.time.WorldTools
import org.waste.of.time.WorldTools.LOG
import org.waste.of.time.WorldTools.config
import org.waste.of.time.WorldTools.mc
import org.waste.of.time.manager.CaptureManager
import org.waste.of.time.manager.MessageManager
import org.waste.of.time.storage.CustomRegionBasedStorage
import org.waste.of.time.storage.Storeable
import org.waste.of.time.storage.cache.HotCache
import kotlin.io.path.exists

class MapDataStoreable : Storeable() {
    override fun shouldStore() = config.general.capture.maps
    override val verboseInfo: MutableComponent
        get() = MessageManager.translateHighlight(
            "worldtools.capture.saved.mapData",
            CaptureManager.currentLevelName
        )
    override val anonymizedInfo: MutableComponent
        get() = verboseInfo

    override fun store(
        session: LevelStorageSource.LevelStorageAccess,
        cachedStorages: MutableMap<String, CustomRegionBasedStorage>
    ) {
        // this map doesn't seem to be cleared until the world closes
        val dataDirectory = session.getLevelPath(LevelResource.ROOT).resolve("data")
        if (!dataDirectory.toFile().exists()) {
            dataDirectory.toFile().mkdirs()
        }

        mc.level?.let { world ->
            world.getAllMapData().filter { (mapId, _) ->
                HotCache.mapIDs.contains(mapId.id())
            }.forEach { (mapId, mapState) ->
                val id = mapId.id()
                CompoundTag().apply {
                    val mapNbt = MapItemSavedData.CODEC.encodeStart(NbtOps.INSTANCE, mapState)
                        .getOrThrow { error -> IllegalStateException("Failed to encode map state: $error") }
                    put("data", mapNbt)
                    NbtUtils.addCurrentDataVersion(this)
                    val mapFile = dataDirectory.resolve("map_$id${WorldTools.DAT_EXTENSION}")
                    if (!mapFile.exists()) {
                        mapFile.toFile().createNewFile()
                    }
                    NbtIo.writeCompressed(this, mapFile)
                    if (config.debug.logSavedMaps) {
                        LOG.info("Map data saved: $id")
                    }
                }
            }
        }
    }
}
