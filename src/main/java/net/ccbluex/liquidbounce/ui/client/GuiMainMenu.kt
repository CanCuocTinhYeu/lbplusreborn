package net.ccbluex.liquidbounce.ui.client

import net.ccbluex.liquidbounce.ui.client.altmanager.GuiAltManager
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.Translate
import net.minecraft.client.gui.*
import java.awt.Color

class GuiMainMenu : GuiScreen(), GuiYesNoCallback {
    private var translate: Translate? = null
    private var hue = 0.0f

    override fun initGui() {
        val defaultHeight = this.height / 4 + 30
        val defaultWidth = this.width / 2 - 60
        val buttonWidth = 120
        val buttonHeight = 20
        this.buttonList.add(GuiButton(0, defaultWidth, defaultHeight, buttonWidth, buttonHeight, "Single Player"))
        this.buttonList.add(GuiButton(1, defaultWidth, defaultHeight + 25, buttonWidth, buttonHeight, "Multi Player"))
        this.buttonList.add(GuiButton(2, defaultWidth, defaultHeight + 50, buttonWidth, buttonHeight, "Alt Manager"))
        this.buttonList.add(GuiButton(3, defaultWidth, defaultHeight + 75, buttonWidth, buttonHeight, "Script Manager"))
        this.buttonList.add(GuiButton(4, defaultWidth, defaultHeight + 100, buttonWidth, buttonHeight, "Back Ground"))
        this.buttonList.add(GuiButton(5, defaultWidth, defaultHeight + 125, buttonWidth, buttonHeight, "Game Options"))
        this.buttonList.add(GuiButton(6, defaultWidth, defaultHeight + 150, buttonWidth, buttonHeight, "Quit Game"))
        translate = Translate(0f, 0f)

        super.initGui()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)
        hue += 1f
        if (hue > 255.0f) {
            hue = 0.0f
        }
        translate?.interpolate(width.toFloat(), height.toFloat(), 4.0)

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(GuiSelectWorld(this))
            1 -> mc.displayGuiScreen(GuiMultiplayer(this))
            2 -> mc.displayGuiScreen(GuiAltManager(this))
            3 -> mc.displayGuiScreen(GuiScripts(this))
            4 -> mc.displayGuiScreen(GuiBackground(this))
            5 -> mc.displayGuiScreen(GuiOptions(this, mc.gameSettings))
            6 -> mc.shutdown()
        }
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        when (typedChar) {
            's' -> mc.displayGuiScreen(GuiSelectWorld(this))
            'm' -> mc.displayGuiScreen(GuiMultiplayer(this))
            'a' -> mc.displayGuiScreen(GuiAltManager(this))
            'o' -> mc.displayGuiScreen(GuiOptions(this, mc.gameSettings))
            'b' -> mc.displayGuiScreen(GuiBackground(this))
        }
    }
}
