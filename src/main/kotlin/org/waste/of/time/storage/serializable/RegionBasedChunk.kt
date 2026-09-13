package org.waste.of.time.storage.serializable

import net.minecraft.SharedConstants
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.Fluid
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongArrayTag
import net.minecraft.nbt.NbtOps
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.MutableComponent
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.levelgen.BelowZeroRetrogen
import net.minecraft.world.level.chunk.Strategy
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.chunk.storage.SerializableChunkData
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.blending.BlendingData
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.ticks.SavedTick
import org.waste.of.time.WorldTools.LOG
import org.waste.of.time.WorldTools.TIMESTAMP_KEY
import org.waste.of.time.WorldTools.config
import org.waste.of.time.extension.IPalettedContainerExtension
import org.waste.of.time.manager.MessageManager.translateHighlight
import org.waste.of.time.manager.StatisticManager
import org.waste.of.time.storage.Cacheable
import org.waste.of.time.storage.CustomRegionBasedStorage
import org.waste.of.time.storage.RegionBased
import org.waste.of.time.storage.cache.HotCache
import it.unimi.dsi.fastutil.longs.LongOpenHashSet


open class RegionBasedChunk(
    val chunk: LevelChunk,
) : RegionBased(chunk.pos, chunk.level, "region"), Cacheable {
    // storing a reference to the block entities in the chunk to prevent them from being unloaded
    val cachedBlockEntities = mutableMapOf<BlockPos, BlockEntity>()

    init {
        cachedBlockEntities.putAll(chunk.blockEntities)

        cachedBlockEntities.values.associateWith { fresh ->
            HotCache.scannedBlockEntities[fresh.pos]
        }.forEach { (fresh, lastEntry) ->
            if (lastEntry == null) return@forEach
            cachedBlockEntities[fresh.pos] = lastEntry
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

    private val stateIdContainer = PalettedContainer.createPalettedContainerCodec(
        BlockState.CODEC,
        Strategy.forBlockStates(Block.FLUID_STATE_REGISTRY),
        Blocks.AIR.defaultFluidState
    )


    override fun cache() {
        HotCache.chunks[chunkPos] = this
    
        // track saved chunks per-dimension
        val dim = level.registryKey
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
        session: LevelStorageSource.Session,
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
        if (chunk.isEmpty) return
        super.writeToStorage(session, storage, cachedStorages)
    }

    /**
     * See [net.minecraft.world.ChunkSerializer.serialize]
     */
    override fun compound() = CompoundTag().apply {
        if (config.level.metadata.captureTimestamp) {
            putLong(TIMESTAMP_KEY, System.currentTimeMs())
        }

        putInt("DataVersion", SharedConstants.getLaunchedVersion().dataVersion().id())
        putInt(SerializableChunkData.X_POS_TAG, chunk.pos.x)
        putInt("yPos", chunk.bottomSectionCoord)
        putInt(SerializableChunkData.Z_POS_TAG, chunk.pos.z)
        putLong("LastUpdate", chunk.level.time)
        putLong("InhabitedTime", chunk.inhabitedTime)
        putString("Status", BuiltInRegistries.CHUNK_STATUS.getId(chunk.status).toString())

        genBackwardsCompat(chunk)

        if (!chunk.upgradeData.isDone) {
            put("UpgradeData", chunk.upgradeData.toNbt())
        }

        put(SerializableChunkData.SECTIONS_TAG, generateSections(chunk))

        if (chunk.isLightCorrect) {
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
        cachedBlockEntities.tags.map { (_, blockEntity) ->
            blockEntity.createNbtWithIdentifyingData(level.registryAccess).apply {
                putBoolean("keepPacked", false)
            }
        }.apply {
            addAll(this)
        }
    }

    private fun generateSections(chunk: LevelChunk) = ListTag().apply {
        val biomes = chunk.level.registryAccess.getOptional(Registries.BIOME).orElse(null) ?: return@apply
        val defaultValue = biomes.getValueSampler(biomes.get(Biomes.PLAINS)!!) ?: return@apply
        val biomeCodec = PalettedContainer.createReadableContainerCodec(
            biomes.entryCodec,
            Strategy.forBiomes(biomes.indexedEntries),
            defaultValue
        )
        val lightEngine = chunk.level.chunkSource.lightEngine

        (lightEngine.bottomY until lightEngine.topY).forEach { y ->
            val sectionCoord = chunk.getSectionIndexFromSectionY(y)
            val inSection = sectionCoord in (0 until chunk.sections.entryCount)
            val blockLightSection =
                lightEngine[LightLayer.BLOCK].getDataLayerData(SectionPos.from(chunk.pos, y))
            val skyLightSection =
                lightEngine[LightLayer.SKY].getDataLayerData(SectionPos.from(chunk.pos, y))

            if (!inSection && blockLightSection == null && skyLightSection == null) return@forEach

            add(CompoundTag().apply {
                if (inSection) {
                    val chunkSection = chunk.sections[sectionCoord]
                    /**
                     * Mods like Bobby may also try serializing chunk data concurrently on separate threads
                     * PalettedContainer contains a lock that is acquired during read/write operations
                     *
                     * Force disabling checking the lock's status here as it should be safe to
                     * read here, no write operations should happen after the chunk is unloaded
                     */
                    (chunkSection.states as IPalettedContainerExtension).setWTIgnoreLock(true)
                    (chunkSection.biomes as IPalettedContainerExtension).setWTIgnoreLock(true)
                    put(
                        "block_states",
                        stateIdContainer.encodeStart(NbtOps.INSTANCE, chunkSection.states).getOrThrow()
                    )
                    put(
                        "biomes",
                        biomeCodec.encodeStart(NbtOps.INSTANCE, chunkSection.biomes).getOrThrow()
                    )
                    (chunkSection.states as IPalettedContainerExtension).setWTIgnoreLock(false)
                    (chunkSection.biomes as IPalettedContainerExtension).setWTIgnoreLock(false)
                }
                if (blockLightSection != null && !blockLightSection.isUninitialized) {
                    putByteArray(SerializableChunkData.BLOCK_LIGHT_TAG, blockLightSection.asByteArray())
                }
                if (skyLightSection != null && !skyLightSection.isUninitialized) {
                    putByteArray(SerializableChunkData.SKY_LIGHT_TAG, skyLightSection.asByteArray())
                }
                if (isEmpty) return@forEach
                putByte("Y", y.toByte())
            })
        }
    }

    private fun CompoundTag.genBackwardsCompat(chunk: LevelChunk) {
        chunk.blendingData?.let { bleedingData ->
            BlendingData.Serialized.CODEC.encodeStart(NbtOps.INSTANCE, bleedingData.toSerialized()).resultOrPartial {
                LOG.error(it)
            }.ifPresent {
                put("blending_data", it)
            }
        }

        chunk.belowZeroRetrogen?.let { belowZeroRetrogen ->
            BelowZeroRetrogen.CODEC.encodeStart(NbtOps.INSTANCE, belowZeroRetrogen).resultOrPartial {
                LOG.error(it)
            }.ifPresent {
                put("below_zero_retrogen", it)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun CompoundTag.getTickSchedulers(chunk: LevelChunk) {
        val time = chunk.level.levelData.time
        val tickSchedulers = chunk.getTickSchedulers(time)

        val blockTickCodec = SavedTick.createCodec(BuiltInRegistries.BLOCK.entryCodec).listOf()
        val fluidTickCodec = SavedTick.createCodec(BuiltInRegistries.FLUID.entryCodec).listOf()

        // Cast needed because tickSchedulers returns SavedTick<Block> but codec expects SavedTick<RegistryEntry<Block>>
        val neighborBlockTicks = tickSchedulers.blocks as List<SavedTick<*>>
        val neighborFluidTicks = tickSchedulers.fluids as List<SavedTick<*>>

        (blockTickCodec as com.mojang.serialization.Codec<List<SavedTick<*>>>).encodeStart(NbtOps.INSTANCE, neighborBlockTicks).result().ifPresent {
            put("block_ticks", it)
        }
        (fluidTickCodec as com.mojang.serialization.Codec<List<SavedTick<*>>>).encodeStart(NbtOps.INSTANCE, neighborFluidTicks).result().ifPresent {
            put("fluid_ticks", it)
        }
    }

    private fun CompoundTag.genPostProcessing(chunk: LevelChunk) {
        put("PostProcessing", SerializableChunkData.toNbt(chunk.postProcessing))

        put(SerializableChunkData.HEIGHTMAPS_TAG, CompoundTag().apply {
            chunk.heightmaps.filter {
                chunk.status.heightmapTypes.contains(it.key)
            }.forEach { (key, value) ->
                put(key.name, LongArrayTag(value.asLongArray()))
            }
        })
    }
}
