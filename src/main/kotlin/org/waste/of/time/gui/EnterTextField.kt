package org.waste.of.time.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import org.waste.of.time.manager.CaptureManager

class EnterTextField(
    font: Font, x: Int, y: Int, width: Int, height: Int, message: Component, val client: Minecraft?
) : EditBox(font, x, y, width, height, message) {

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key == GLFW.KEY_RETURN) {
            if (CaptureManager.capturing) {
                client?.preserveCurrentChatScreen(null)
                CaptureManager.destroy()
            } else {
                client?.preserveCurrentChatScreen(null)
                CaptureManager.start(value)
            }
            return true
        }
        return super.keyPressed(input)
    }
}