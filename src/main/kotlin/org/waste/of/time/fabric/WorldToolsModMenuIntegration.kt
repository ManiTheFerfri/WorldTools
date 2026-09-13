package org.waste.of.time.fabric

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigManager
import me.shedaniel.autoconfig.gui.ConfigScreenProvider
import me.shedaniel.autoconfig.gui.registry.DefaultGuiRegistryAccess
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.screens.Screen
import org.waste.of.time.config.WorldToolsConfig

@Environment(EnvType.CLIENT)
class WorldToolsModMenuIntegration : ModMenuApi {

    override fun getModConfigScreenFactory(): ConfigScreenFactory<Screen?> =
        ConfigScreenFactory { parent: Screen? ->
            ConfigScreenProvider(
                AutoConfig.getConfigHolder(WorldToolsConfig::class.java) as ConfigManager<WorldToolsConfig>,
                DefaultGuiRegistryAccess(),
                parent
            ).get()
        }
}
