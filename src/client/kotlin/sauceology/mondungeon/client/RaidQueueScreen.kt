package sauceology.mondungeon.client

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import sauceology.mondungeon.RaidQueuePromptPayload
import sauceology.mondungeon.RaidQueueReplyPayload

/** This screen can only answer a server invitation. It never chooses a slot or starts a run locally. */
class RaidQueueScreen(private val invitation: RaidQueuePromptPayload, private val preview: Boolean = false) :
    Screen(Component.translatable("screen.mondungeons.queue_ready")) {
    private var elapsed = 0
    private var answered = false
    private val panelWidth get() = (width - 20).coerceAtMost(410)

    override fun init() {
        val left = (width - panelWidth) / 2
        val buttonWidth = (panelWidth - 34) / 2
        addRenderableWidget(RaidOddsScreen.MenuButton(left + 12, height / 2 + 46, buttonWidth, 24,
            Component.translatable("screen.mondungeons.queue_yes"), primary = true) { answer(true) })
        addRenderableWidget(RaidOddsScreen.MenuButton(width / 2 + 5, height / 2 + 46, buttonWidth, 24,
            Component.translatable("screen.mondungeons.queue_no")) { answer(false) })
    }

    override fun tick() { if (!preview && ++elapsed >= invitation.seconds * 20) answer(false) }
    override fun isPauseScreen() = false
    override fun onClose() = answer(false)

    private fun answer(accept: Boolean) {
        if (answered) return
        answered = true
        if (!preview && ClientPlayNetworking.canSend(RaidQueueReplyPayload.TYPE)) {
            ClientPlayNetworking.send(RaidQueueReplyPayload(invitation.token, accept))
        }
        minecraft?.setScreen(null)
    }

    fun dismiss(token: String) {
        if (token != invitation.token) return
        answered = true
        minecraft?.setScreen(null)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fillGradient(0, 0, width, height, 0xC9080710.toInt(), 0xE0080710.toInt())
        val left = (width - panelWidth) / 2
        graphics.fill(left, height / 2 - 94, left + panelWidth, height / 2 + 86, 0xFF393154.toInt())
        graphics.fill(left + 1, height / 2 - 93, left + panelWidth - 1, height / 2 + 85, 0xFF15121E.toInt())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val center = height / 2
        graphics.drawCenteredString(font, "MONDUNGEONS", width / 2, center - 77, 0xC6AEFF)
        graphics.drawCenteredString(font, title, width / 2, center - 58, 0xFFD76A)
        val question = Component.translatable("screen.mondungeons.queue_question")
        font.split(question, panelWidth - 24).forEachIndexed { i, line ->
            graphics.drawCenteredString(font, line, width / 2, center - 35 + i * 11, 0xF1ECFA)
        }
        val run = Component.translatable("screen.mondungeons.queue_run", invitation.keyName, invitation.rounds)
        font.split(run, panelWidth - 24).take(2).forEachIndexed { i, line ->
            graphics.drawCenteredString(font, line, width / 2, center - 5 + i * 11, 0xB4A6C8)
        }
        graphics.drawCenteredString(font, Component.translatable("screen.mondungeons.queue_timeout",
            (invitation.seconds - elapsed / 20).coerceAtLeast(0)), width / 2, center + 27, 0xA79FC0)
    }
}
