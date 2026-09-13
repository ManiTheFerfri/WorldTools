package org.waste.of.time.gui

import me.shedaniel.autoconfig.AutoConfig
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.*
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

    override fun init() {
        setupTitle()
        setupEntryGrid()
        setupBottomGrid()
    }

    override fun tick() {
        if (CaptureManager.capturing) {
            downloadButton.message = Component.translatable("worldtools.gui.manager.button.stop_download")
            worldNameTextEntryWidget.setPlaceholder(Component.of(currentLevelName))
            worldNameTextEntryWidget.setEditable(false)
        } else {
            downloadButton.message = Component.translatable("worldtools.gui.manager.button.start_download")
            worldNameTextEntryWidget.setEditable(true)
        }
        super.tick()
    }

    private fun setupTitle() {
        titleWidget = StringWidget(Component.translatable("worldtools.gui.manager.title"), font)
        FrameLayout.setBase(titleWidget, 0, 0, width, height, 0.5f, 0.01f)
        addDrawableChild(titleWidget)
    }

    private fun setupEntryGrid() {
        val entryGridWidget = createGridWidget()
        val add = entryGridWidget.createRowHelper(3)

        worldNameTextEntryWidget = EnterTextField(
            font, 0, 0, 250, 20, Component.of(levelName), minecraft
        ).apply {
            setPlaceholder(Component.translatable("worldtools.gui.manager.world_name_placeholder", levelName))
            setMaxLength(MAX_LEVEL_NAME_LENGTH)
        }
        downloadButton = createButton("worldtools.gui.manager.button.start_download") {
            if (CaptureManager.capturing) {
                minecraft?.preserveCurrentChatScreen(null)
                CaptureManager.stop()
            } else {
                minecraft?.preserveCurrentChatScreen(null)
                CaptureManager.start(worldNameTextEntryWidget.text)
            }
        }

        add.add(worldNameTextEntryWidget, 2)
        add.add(downloadButton, 1)

        entryGridWidget.refreshPositions()
        FrameLayout.setBase(entryGridWidget, 0, titleWidget.y, width, height, 0.5f, 0.05f)
        entryGridWidget.visitWidgets(this::addDrawableChild)
    }

    private fun setupBottomGrid() {
        val bottomGridWidget = createGridWidget()
        val bottomAdder = bottomGridWidget.createRowHelper(2)
        configButton = createButton("worldtools.gui.manager.button.config") {
            minecraft?.preserveCurrentChatScreen(AutoConfig.getConfigScreen(WorldToolsConfig::class.java, this).get())
        }
        cancelButton = createButton("worldtools.gui.manager.button.cancel") {
            minecraft?.preserveCurrentChatScreen(null)
        }

        bottomAdder.add(configButton, 1)
        bottomAdder.add(cancelButton, 1)

        bottomGridWidget.refreshPositions()
        FrameLayout.setBase(bottomGridWidget, 0, 0, width, height, 0.5f, .95f)
        bottomGridWidget.visitWidgets(this::addDrawableChild)
    }

    private fun createGridWidget() = GridLayout().apply {
        mainPositioner.padding(4, 4, 4, 4)
    }

    private fun createButton(textKey: String, onClick: (Button) -> Unit) =
        Button.Builder(Component.translatable(textKey), onClick).width(BUTTON_WIDTH).build()
}
