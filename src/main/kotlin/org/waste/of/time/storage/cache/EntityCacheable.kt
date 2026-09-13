package org.waste.of.time.storage.cache

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.util.ProblemReporter
import net.minecraft.util.datafix.DataFixTypes
import org.waste.of.time.Utils.toByte
import org.waste.of.time.WorldTools.TIMESTAMP_KEY
import org.waste.of.time.WorldTools.config
import org.waste.of.time.storage.Cacheable

data class EntityCacheable(
    val entity: Entity
) : Cacheable {
    fun compound() = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.level().registryAccess()).let { writeView ->
        // saveSelfNbt has a check for RemovalReason.DISCARDED
        EntityType.getKey(entity.type)?.let { writeView.putString(Entity.TAG_ID, it.toString()) }
        entity.saveWithoutId(writeView)

        if (config.entity.damageCalculator.modifyEntityBehavior) {
            writeView.putByte("NoAI", config.entity.damageCalculator.noAI.toByte())
            writeView.putByte("NoGravity", config.entity.damageCalculator.hoversInPlace.toByte())
            writeView.putByte("Invulnerable", config.entity.damageCalculator.invulnerable.toByte())
            writeView.putByte("Silent", config.entity.damageCalculator.silent.toByte())
        }

        if (config.entity.metadata.captureTimestamp) {
            writeView.putLong(TIMESTAMP_KEY, System.currentTimeMillis())
        }
        
        writeView.buildResult()
    }

    override fun cache() {
        HotCache.entities.computeIfAbsent(entity.chunkPosition()) { mutableSetOf() }.apply {
            // Remove the entity if it already exists to update it
            removeIf { it.entity.uuid == entity.uuid }
            add(this@EntityCacheable)
        }
    }

    override fun flush() {
        val chunkPos = entity.chunkPosition()
        HotCache.entities[chunkPos]?.let { list ->
            list.remove(this)
            if (list.isEmpty()) {
                HotCache.entities.remove(chunkPos)
            }
        }
    }

    override fun equals(second: Any?): Boolean {
        if (second !is EntityCacheable) return super.equals(second)
        return entity.uuid == second.entity.uuid
    }

    override fun hashCode() = entity.uuid.hashCode()
}
