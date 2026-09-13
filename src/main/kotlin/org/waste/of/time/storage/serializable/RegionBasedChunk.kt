package org.waste.of.time.storage.serializable

import net.minecraft.SharedConstants
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongArrayTag
import net.minecraft.nbt.NbtOps
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.chunk.DataLayer
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.chunk.PalettedContainerFactory
import net.minecraft.world.level.chunk.UpgradeData
import net.minecraft.world.level.chunk.storage.SerializableChunkData
import net.minecraft.world.level.levelgen.BelowZeroRetrogen
import net.minecraft.world.level.levelgen.blending.BlendingData
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.ticks.SavedTick
import org.waste.of.time.WorldTools.LOG
import org.waste.of.time.WorldTools.TIMESTAMP_KEY
import org.waste.of.time.WorldTools.config
import org.waste.of.time.manager.MessageManager.translateHighlight
import org.waste.of.time.manager.StatisticManager
import org.waste.of.time.storage.Cacheable
import org.waste.of.time.storage.CustomRegionBasedStorage
import org.waste.of.time.storage.RegionBased
import org.waste.of.time.storage.cache.HotCache
import it.unimi.dsi.fastutil.longs.LongOpenHashSet


open class RegionBasedChunk(
    val chunk: LevelChunk,
) : RegionBased(chunk.getPos(), chunk.getLevel(), "region"), Cacheable {
    // storing a reference to the block entities in the chunk to prevent them from being unloaded
    val cachedBlockEntities = mutableMapOf<BlockPos, net.minecraft.world.level.block.entity.BlockEntity>()

    init {
        cachedBlockEntities.putAll(chunk.getBlockEntities())

        cachedBlockEntities.values.associateWith { fresh ->
            HotCache.scannedBlockEntities[fresh.blockPos]
        }.forEach { (fresh, lastEntry) ->
            if (lastEntry == null) return@forEach
            cachedBlockEntities[fresh.blockPos] = lastEntry
        }
    }

    override fun shouldStore() = config.general.capture.chunks

    override val verboseInfo: MutableComponent
        get() = translateHighlight(
            "worldtools.capture.saved.chunks",
            chunkPos,
            dimension
        )

    override val anonymizedInfo: MutableComponent
        get() = translateHighlight(
            "worldtools.capture.saved.chunks.anonymized",
            dimension
        )

    private val containerFactory = PalettedContainerFactory.create(level.registryAccess())


    override fun cache() {
        HotCache.chunks[chunkPos] = this

        // track saved chunks per-dimension
        val dim = level.dimension()
        val set = HotCache.savedDimensionChunks.computeIfAbsent(dim) { LongOpenHashSet() }

        synchronized(set) {
            set.add(chunkPos.pack())
        }
    }

    override fun flush() {
        HotCache.chunks.remove(chunkPos)
    }

    override fun incrementStats() {
        StatisticManager.chunks++
        StatisticManager.dimensions.add(dimension)
    }

    override fun writeToStorage(
        session: LevelStorageSource.LevelStorageAccess,
        storage: CustomRegionBasedStorage,
        cachedStorages: MutableMap<String, CustomRegionBasedStorage>
    ) {
        // avoiding `emit` here due to flow order issues when capture is stopped
        // i.e., if EndFlow is emitted before this,
        // these are not written because they're behind it in the flow
        HotCache.getEntitySerializableForChunk(chunkPos, level)
            ?.store(session, cachedStorages)
            ?: run {
                // remove any previously stored entities in this chunk in case there are no entities to store
                RegionBasedEntities(chunkPos, emptySet(), level).store(session, cachedStorages)
        }
        if (chunk.isEmpty()) return
        super.writeToStorage(session, storage, cachedStorages)
    }

    /**
     * See [net.minecraft.world.level.chunk.storage.SerializableChunkData.write]
     */
    override fun compound() = CompoundTag().apply {
        if (config.world.metadata.captureTimestamp) {
            putLong(TIMESTAMP_KEY, System.currentTimeMillis())
        }

        putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version())
        putInt(SerializableChunkData.X_POS_TAG, chunk.getPos().x)
        putInt("yPos", chunk.getMinSectionY())
        putInt(SerializableChunkData.Z_POS_TAG, chunk.getPos().z)
        putLong("LastUpdate", level.getLevelData().getGameTime())
        putLong("InhabitedTime", chunk.getInhabitedTime())
        putString("Status", BuiltInRegistries.CHUNK_STATUS.getId(chunk.getPersistedStatus()).toString())

        genBackwardsCompat(chunk)

        if (!chunk.getUpgradeData().isEmpty()) {
            put("UpgradeData", chunk.getUpgradeData().write())
        }

        put(SerializableChunkData.SECTIONS_TAG, generateSections(chunk))

        if (chunk.isLightCorrect()) {
            putBoolean(SerializableChunkData.IS_LIGHT_ON_TAG, true)
        }

        put("block_entities", ListTag().apply {
            upsertBlockEntities()
        })

        getTickSchedulers(chunk)
        genPostProcessing(chunk)

        // skip structures
        if (config.debug.logSavedChunks)
            LOG.info("Chunk saved: $chunkPos ($dimension)")
    }

    private fun ListTag.upsertBlockEntities() {
        cachedBlockEntities.entries.map { (_, blockEntity) ->
            blockEntity.saveWithFullMetadata(level.registryAccess()).apply {
                putBoolean("keepPacked", false)
            }
        }.apply {
            addAll(this)
        }
    }

    private fun generateSections(chunk: LevelChunk) = ListTag().apply {
        val blockStatesCodec = containerFactory.blockStatesContainerCodec()
        val biomeCodec = containerFactory.biomeContainerCodec()
        val lightEngine = level.getLightEngine()

        (level.getMinSectionY()..level.getMaxSectionY()).forEach { y ->
            val sectionCoord = chunk.getSectionIndexFromSectionY(y)
            val inSection = sectionCoord in (0 until chunk.getSections().size)
            val blockLightSection = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunk.getPos().x, y, chunk.getPos().z))
            val skyLightSection = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunk.getPos().x, y, chunk.getPos().z))

            if (!inSection && blockLightSection == null && skyLightSection == null) return@forEach

            add(CompoundTag().apply {
                if (inSection) {
                    val chunkSection = chunk.getSection(sectionCoord)
                    // PalettedContainer contains a lock that is acquired during read/write operations.
                    // Use acquire()/release() for safe concurrent reads; captured chunks are no longer written to.
                    (chunkSection.getStates() as PalettedContainer<*>).acquire()
                    (chunkSection.getBiomes() as PalettedContainer<*>).acquire()
                    put(
                        "block_states",
                        blockStatesCodec.encodeStart(NbtOps.INSTANCE, chunkSection.getStates()).getOrThrow()
                    )
                    put(
                        "biomes",
                        biomeCodec.encodeStart(NbtOps.INSTANCE, chunkSection.getBiomes()).getOrThrow()
                    )
                    (chunkSection.getStates() as PalettedContainer<*>).release()
                    (chunkSection.getBiomes() as PalettedContainer<*>).release()
                }
                if (blockLightSection != null && !blockLightSection.isEmpty()) {
                    putByteArray(SerializableChunkData.BLOCK_LIGHT_TAG, blockLightSection.getData())
                }
                if (skyLightSection != null && !skyLightSection.isEmpty()) {
                    putByteArray(SerializableChunkData.SKY_LIGHT_TAG, skyLightSection.getData())
                }
                if (isEmpty) return@forEach
                putByte("Y", y.toByte())
            })
        }
    }

    private fun CompoundTag.genBackwardsCompat(chunk: LevelChunk) {
        chunk.getBlendingData()?.let { blendingData ->
            BlendingData.Packed.CODEC.encodeStart(NbtOps.INSTANCE, blendingData.pack()).resultOrPartial {
                LOG.error(it)
            }.ifPresent {
                put("blending_data", it)
            }
        }

        chunk.getBelowZeroRetrogen()?.let { belowZeroRetrogen ->
            BelowZeroRetrogen.CODEC.encodeStart(NbtOps.INSTANCE, belowZeroRetrogen).resultOrPartial {
                LOG.error(it)
            }.ifPresent {
                put("below_zero_retrogen", it)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun CompoundTag.getTickSchedulers(chunk: LevelChunk) {
        val time = level.getLevelData().getGameTime()
        val tickSchedulers = chunk.getTicksForSerialization(time)

        val blockTickCodec = SavedTick.codec(BuiltInRegistries.BLOCK.byNameCodec()).listOf()
        val fluidTickCodec = SavedTick.codec(BuiltInRegistries.FLUID.byNameCodec()).listOf()

        val neighborBlockTicks = tickSchedulers.blocks() as List<SavedTick<*>>
        val neighborFluidTicks = tickSchedulers.fluids() as List<SavedTick<*>>

        (blockTickCodec as com.mojang.serialization.Codec<List<SavedTick<*>>>).encodeStart(NbtOps.INSTANCE, neighborBlockTicks).result().ifPresent {
            put("block_ticks", it)
        }
        (fluidTickCodec as com.mojang.serialization.Codec<List<SavedTick<*>>>).encodeStart(NbtOps.INSTANCE, neighborFluidTicks).result().ifPresent {
            put("fluid_ticks", it)
        }
    }

    private fun CompoundTag.genPostProcessing(chunk: LevelChunk) {
        put("PostProcessing", ListTag().apply {
            chunk.getPostProcessing().forEach { shortList ->
                add(ListTag().apply { shortList?.forEach { add(net.minecraft.nbt.ShortTag.valueOf(it)) } })
            }
        })

        put(SerializableChunkData.HEIGHTMAPS_TAG, CompoundTag().apply {
            chunk.getHeightmaps().filter {
                chunk.getPersistedStatus().heightmapsAfter().contains(it.key)
            }.forEach { (key, value) ->
                put(key.name, LongArrayTag(value.getRawData()))
            }
        })
    }
}
