package org.waste.of.time.gui

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigManager
import me.shedaniel.autoconfig.gui.ConfigScreenProvider
import me.shedaniel.autoconfig.gui.registry.DefaultGuiRegistryAccess
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.network.chat.Component
import org.waste.of.time.WorldTools.MAX_LEVEL_NAME_LENGTH
import org.waste.of.time.config.WorldToolsConfig
import org.waste.of.time.manager.CaptureManager
import org.waste.of.time.manager.CaptureManager.currentLevelName
import org.waste.of.time.manager.CaptureManager.levelName

object ManagerScreen : Screen(Component.translatable("worldtools.gui.manager.title")) {
    private lateinit var worldNameTextEntryWidget: EditBox
    private lateinit var titleWidget: StringWidget
    private lateinit var downloadButton: Button
    private lateinit var configButton: Button
    private lateinit var cancelButton: Button
    private const val BUTTON_WIDTH = 90
    private const val BUTTON_HEIGHT = 20
    private const val SPACING = 4

    override fun init() {
        val centerX = this.width / 2
        val titleY = (this.height * 0.01f).toInt()

        // Title centered at the top
        titleWidget = StringWidget(Component.translatable("worldtools.gui.manager.title"), font)
        val titleWidth = font.width(titleWidget.message)
        titleWidget.setRectangle(titleWidth, 20, centerX - titleWidth / 2, titleY)
        addRenderableWidget(titleWidget)

        // Entry row: text field (250) + download button (90), centered, below the title
        val rowWidth = 250 + BUTTON_WIDTH + SPACING
        val rowX = centerX - rowWidth / 2
        val rowY = titleY + 20 + SPACING * 4

        worldNameTextEntryWidget = EnterTextField(
            font, rowX, rowY, 250, 20, Component.literal(levelName), minecraft
        ).apply {
            setHint(Component.translatable("worldtools.gui.manager.world_name_placeholder", levelName))
            setMaxLength(MAX_LEVEL_NAME_LENGTH)
        }
        addRenderableWidget(worldNameTextEntryWidget)

        downloadButton = createButton("worldtools.gui.manager.button.start_download", rowX + 250 + SPACING, rowY) {
            if (CaptureManager.capturing) {
                minecraft?.gui?.setScreen(null)
                CaptureManager.stop()
            } else {
                minecraft?.gui?.setScreen(null)
                CaptureManager.start(worldNameTextEntryWidget.value)
            }
        }
        addRenderableWidget(downloadButton)

        // Bottom row: config + cancel buttons, centered near the bottom
        val bottomWidth = BUTTON_WIDTH * 2 + SPACING
        val bottomX = centerX - bottomWidth / 2
        val bottomY = (this.height * 0.95f).toInt() - BUTTON_HEIGHT

        configButton = createButton("worldtools.gui.manager.button.config", bottomX, bottomY) {
            minecraft?.gui?.setScreen(
                ConfigScreenProvider(
                    AutoConfig.getConfigHolder(WorldToolsConfig::class.java) as ConfigManager<WorldToolsConfig>,
                    DefaultGuiRegistryAccess(),
                    this
                ).get()
            )
        }
        addRenderableWidget(configButton)

        cancelButton = createButton("worldtools.gui.manager.button.cancel", bottomX + BUTTON_WIDTH + SPACING, bottomY) {
            minecraft?.gui?.setScreen(null)
        }
        addRenderableWidget(cancelButton)
    }

    override fun tick() {
        if (CaptureManager.capturing) {
            downloadButton.message = Component.translatable("worldtools.gui.manager.button.stop_download")
            worldNameTextEntryWidget.setHint(Component.literal(currentLevelName))
            worldNameTextEntryWidget.setEditable(false)
        } else {
            downloadButton.message = Component.translatable("worldtools.gui.manager.button.start_download")
            worldNameTextEntryWidget.setEditable(true)
        }
        super.tick()
    }

    private fun createButton(textKey: String, x: Int, y: Int, onClick: (Button) -> Unit) =
        Button.builder(Component.translatable(textKey), onClick).pos(x, y).width(BUTTON_WIDTH).build()
}
