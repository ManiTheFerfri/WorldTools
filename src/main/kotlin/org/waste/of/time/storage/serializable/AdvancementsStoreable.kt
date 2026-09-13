package org.waste.of.time.storage.serializable

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import net.minecraft.server.PlayerAdvancements
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.storage.LevelStorageSource
import org.waste.of.time.WorldTools.CURRENT_VERSION
import org.waste.of.time.WorldTools.GSON
import org.waste.of.time.WorldTools.LOG
import org.waste.of.time.WorldTools.config
import org.waste.of.time.WorldTools.mc
import org.waste.of.time.manager.MessageManager.translateHighlight
import org.waste.of.time.storage.CustomRegionBasedStorage
import org.waste.of.time.storage.Storeable
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class AdvancementsStoreable : Storeable() {
    override fun shouldStore() = config.general.capture.advancements

    override val verboseInfo: MutableComponent
        get() = translateHighlight(
            "worldtools.capture.saved.advancements",
            mc.player?.name ?: "Unknown"
        )

    override val anonymizedInfo: MutableComponent
        get() = verboseInfo

    private val codec =
        DataFixTypes.ADVANCEMENTS.createDataFixingCodec(
            PlayerAdvancements.ProgressMap.CODEC, mc.fixerUpper, CURRENT_VERSION
        )

    override fun store(
        session: LevelStorageSource.LevelStorageAccess,
        cachedStorages: MutableMap<String, CustomRegionBasedStorage>
    ) {
        val uuid = mc.player?.uuid ?: return
        val progress = mc.player
            ?.connection
            ?.advancements
            ?.progress ?: return
        val progressMap = progress.entries
            .filter { it.value.isAnyObtained }
            .associate {
                it.key.id to it.value
            }
        val jsonElement =
            codec.encodeStart(
                JsonOps.INSTANCE,
                PlayerAdvancements.ProgressMap(progressMap)
            ).getOrThrow() as JsonElement


        val advancements = session.getLevelPath(LevelResource.ADVANCEMENTS)
        Files.createDirectories(advancements)
        Files.newBufferedWriter(
            advancements.resolve("$uuid.json"),
            StandardCharsets.UTF_8
        ).increaseUses { writer ->
            GSON.serializePackets(jsonElement, writer)
        }

        LOG.info("Saved ${progressMap.size} advancements.")
    }
}
