package org.waste.of.time.storage.cache

import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.*
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.*
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper
import net.minecraft.world.entity.vehicle.ContainerEntity
import net.minecraft.world.inventory.PlayerEnderChestContainer
import net.minecraft.world.SimpleContainer
import org.waste.of.time.WorldTools.mc
import org.waste.of.time.storage.cache.HotCache.markScanned
import org.waste.of.time.storage.cache.HotCache.scannedBlockEntities

object DataInjectionHandler {
    fun onScreenRemoved(screen: Screen) {
        HotCache.lastInteractedBlockEntity?.let {
            handleBlockEntity(screen, it)
        }
        HotCache.lastInteractedEntity?.let {
            handleEntity(screen, it)
        }
    }

    private fun handleEntity(screen: Screen, entity: Entity) {
        when (screen) {
            is AbstractContainerScreen<*> -> {
                (entity as? ContainerEntity)?.dataToVehicle(screen)
            }
            is HopperScreen -> {
                (entity as? MinecartHopper)?.dataToHopperMinecart(screen)
            }
        }

        entity.markScanned()
    }

    private fun ContainerEntity.dataToVehicle(screen: AbstractContainerScreen<*>) {
        screen.getContainerSlots().forEach {
            setItem(it.index, it.item)
        }
    }

    private fun MinecartHopper.dataToHopperMinecart(screen: HopperScreen) {
        screen.getContainerSlots().forEach {
            setItem(it.index, it.item)
        }
    }

    private fun handleBlockEntity(screen: Screen, blockEntity: BlockEntity) {
        when (screen) {
            is AbstractContainerScreen<*> -> {
                when (blockEntity) {
                    is ChestBlockEntity -> blockEntity.dataToChest(screen)
                    is BarrelBlockEntity -> blockEntity.dataToBarrelBlock(screen)
                    is EnderChestBlockEntity -> dataToEnderChest(screen)
                }
            }

            is DispenserScreen -> {
                (blockEntity as? DispenserBlockEntity)?.dataToDispenserOrDropper(screen)
            }

            is AbstractFurnaceScreen<*> -> {
                (blockEntity as? AbstractFurnaceBlockEntity)?.dataToFurnace(screen)
            }

            is BrewingStandScreen -> {
                (blockEntity as? BrewingStandBlockEntity)?.dataToBrewingStand(screen)
            }

            is HopperScreen -> {
                (blockEntity as? HopperBlockEntity)?.dataToHopper(screen)
            }

            is ShulkerBoxScreen -> {
                (blockEntity as? ShulkerBoxBlockEntity)?.dataToShulkerBox(screen)
            }

            is LecternScreen -> {
                (blockEntity as? LecternBlockEntity)?.dataToLectern(screen)
            }

            is CrafterScreen -> {
                (blockEntity as? CrafterBlockEntity)?.dataToCrafter(screen)
            }
        }

        // ToDo: Add support for entity containers like chest boat and minecart

        blockEntity.markScanned()
    }

    private fun dataToEnderChest(screen: AbstractContainerScreen<*>) {
        if (mc.isLocalServer) return
        val inventory = screen.menu.inventory as? SimpleContainer ?: return
        if (inventory.containerSize != 27) return
        mc.player?.enderChestInventory = PlayerEnderChestContainer().apply {
            for (i in 0 until inventory.containerSize) {
                setItem(i, inventory.getItem(i))
            }
        }
    }

    private fun AbstractFurnaceBlockEntity.dataToFurnace(screen: AbstractFurnaceScreen<*>) {
        screen.getContainerSlots().forEach {
            setItem(it.index, it.item)
        }
    }

    private fun BarrelBlockEntity.dataToBarrelBlock(screen: AbstractContainerScreen<*>) {
        screen.getContainerSlots().forEach {
            setItem(it.index, it.item)
        }
    }

    private fun BrewingStandBlockEntity.dataToBrewingStand(screen: BrewingStandScreen) {
        screen.getContainerSlots().forEach {
            setItem(it.index, it.item)
        }
    }

    private fun ChestBlockEntity.dataToChest(screen: AbstractContainerScreen<*>) {
        val facing = blockState[ChestBlock.FACING] ?: return
        val type = blockState[ChestBlock.TYPE] ?: return
        val containerSlots = screen.getContainerSlots()
        val inventories = containerSlots.partition { it.index < 27 }

        when (type) {
            ChestType.SINGLE -> {
                containerSlots.forEach {
                    setItem(it.index, it.item)
                }
            }

            ChestType.LEFT -> {
                val position = worldPosition.offset(facing.getClockWise())
                val otherChest = level?.getBlockEntity(position)
                if (otherChest !is ChestBlockEntity) return

                inventories.first.forEach {
                    otherChest.setItem(it.index, it.item)
                }
                inventories.second.forEach {
                    setItem(it.index - 27, it.item)
                }

                scannedBlockEntities[otherChest.worldPosition] = otherChest
            }

            ChestType.RIGHT -> {
                val position = worldPosition.offset(facing.getCounterClockWise())
                val otherChest = level?.getBlockEntity(position)
                if (otherChest !is ChestBlockEntity) return

                inventories.first.forEach {
                    setItem(it.index, it.item)
                }
                inventories.second.forEach {
                    otherChest.setItem(it.index - 27, it.item)
                }

                scannedBlockEntities[otherChest.worldPosition] = otherChest
            }
        }
    }

    private fun DispenserBlockEntity.dataToDispenserOrDropper(screen: DispenserScreen) {
        screen.getContainerSlots().forEach {
            setItem(it.index, it.item)
        }
    }

    private fun HopperBlockEntity.dataToHopper(screen: HopperScreen) {
        screen.getContainerSlots().forEach {
            setItem(it.index, it.item)
        }
    }

    private fun ShulkerBoxBlockEntity.dataToShulkerBox(screen: ShulkerBoxScreen) {
        screen.getContainerSlots().forEach {
            setItem(it.index, it.item)
        }
    }

    private fun LecternBlockEntity.dataToLectern(screen: LecternScreen) {
        setBook(screen.menu.book)
    }

    private fun CrafterBlockEntity.dataToCrafter(screen: CrafterScreen) {
        screen.getContainerSlots().forEach {
            setItem(it.index, it.item)
            setSlotState(it.index, !isSlotDisabled(it.index))
        }
    }

    private fun AbstractContainerScreen<*>.getContainerSlots() = menu.slots.filter { it.container !is Inventory }
}
