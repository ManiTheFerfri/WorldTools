package org.waste.of.time.storage

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.ListTag
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.util.ProblemReporter
import net.minecraft.resources.Identifier
import net.minecraft.util.ExceptionCollector
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.storage.RegionFile
import net.minecraft.world.level.chunk.storage.RegionStorageInfo
import org.waste.of.time.WorldTools.ANVIL_EXTENSION
import org.waste.of.time.WorldTools.MOD_NAME
import org.waste.of.time.WorldTools.mc
import java.io.DataOutput
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path


open class CustomRegionBasedStorage internal constructor(
    private val externalFileDir: Path,
    private val sync: Boolean
) : AutoCloseable {
    private val regionCache: Long2ObjectLinkedOpenHashMap<RegionFile?> = Long2ObjectLinkedOpenHashMap()

    companion object {
        // Seems to only be used for MC's profiler
        // simpler to just use a default key instead of wiring this all in here
        val defaultStorageKey: RegionStorageInfo = RegionStorageInfo(MOD_NAME, Level.OVERWORLD, "chunk")
    }

    @Throws(IOException::class)
    fun getRegionFile(worldPosition: ChunkPos): RegionFile {
        val longPos = ChunkPos.pack(worldPosition.getRegionX(), worldPosition.getRegionZ())
        regionCache.getAndMoveToFirst(longPos)?.let { return it }

        if (regionCache.size >= 256) {
            regionCache.removeLast()?.close()
        }

        Files.createDirectories(externalFileDir)
        val path = externalFileDir.resolve("r." + worldPosition.getRegionX() + "." + worldPosition.getRegionZ() + ANVIL_EXTENSION)
        val regionFile = RegionFile(defaultStorageKey, path, externalFileDir, sync)
        regionCache.putAndMoveToFirst(longPos, regionFile)
        return regionFile
    }

    @Throws(IOException::class)
    fun write(worldPosition: ChunkPos, input: CompoundTag?) {
        val regionFile = getRegionFile(worldPosition)
        if (input == null) {
            regionFile.clear(worldPosition)
        } else {
            regionFile.getChunkDataOutputStream(worldPosition).use { dataOutputStream ->
                NbtIo.write(input, dataOutputStream as DataOutput)
            }
        }
    }

    private fun getNbtAt(chunkPos: ChunkPos) =
        getRegionFile(chunkPos).getChunkDataInputStream(chunkPos)?.use { dataInputStream ->
            NbtIo.read(dataInputStream, NbtAccounter.EMPTY)
        }

    fun getBlockEntities(chunkPos: ChunkPos): List<BlockEntity> {
        val input = getNbtAt(chunkPos) ?: return emptyList()
        val blockEntitiesList = input.getList("block_entities").orElse(null) ?: return emptyList()
        
        return blockEntitiesList.filterIsInstance<CompoundTag>().mapNotNull { compoundTag ->
            val blockPos = BlockPos(
                compoundTag.getInt("x").orElse(0),
                compoundTag.getInt("y").orElse(0),
                compoundTag.getInt("z").orElse(0)
            )
            val blockStateIdentifier = Identifier.parse(compoundTag.getString("id").orElse(""))
            val level = mc.level ?: return@mapNotNull null

            runCatching {
                val block = BuiltInRegistries.BLOCK.get(blockStateIdentifier).orElse(null) ?: return@mapNotNull null
                BuiltInRegistries.BLOCK_ENTITY_TYPE
                    .get(blockStateIdentifier)
                    .orElse(null)
                    ?.create(blockPos, block.defaultState)?.apply {
                        val readView = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess, compoundTag)
                        loadCustomOnly(readView)
                    }
            }.getOrNull()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        val throwableDeliverer = ExceptionCollector<IOException>()

        regionCache.values.filterNotNull().forEach { regionFile ->
            try {
                regionFile.close()
            } catch (iOException: IOException) {
                throwableDeliverer.add(iOException)
            }
        }

        throwableDeliverer.deliver()
    }
}
