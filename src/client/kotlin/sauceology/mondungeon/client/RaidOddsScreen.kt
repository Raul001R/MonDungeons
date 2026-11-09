package sauceology.mondungeon.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import sauceology.mondungeon.RaidOddsPayload
import sauceology.mondungeon.RaidStartPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import java.text.DecimalFormat
import kotlin.math.ceil

class RaidOddsScreen(private val odds: RaidOddsPayload) : Screen(Component.translatable("screen.mondungeons.raid")) {
    private var page = 0
    private val rowsPerPage get() = ((height - 136) / 20).coerceAtLeast(1)
    private val pageCount get() = ceil(odds.rewards.size.toDouble() / rowsPerPage).toInt().coerceAtLeast(1)
    private val panelWidth get() = (width - 24).coerceAtMost(440)
    private val left get() = (width - panelWidth) / 2
    private val percent = DecimalFormat("0.###'%'")
    private lateinit var previous: Button
    private lateinit var next: Button

    override fun init() {
        page = page.coerceIn(0, pageCount - 1)
        previous = addRenderableWidget(Button.builder(Component.literal("<")) { page--; updatePages() }
            .bounds(left + 8, height - 62, 30, 20).build())
        next = addRenderableWidget(Button.builder(Component.literal(">")) { page++; updatePages() }
            .bounds(left + panelWidth - 38, height - 62, 30, 20).build())
        val start = addRenderableWidget(Button.builder(Component.translatable("screen.mondungeons.start")) { button ->
            button.active = false
            ClientPlayNetworking.send(RaidStartPayload(odds.startToken))
            onClose()
        }
            .bounds(width / 2 - 104, height - 32, 100, 20).build())
        start.active = ClientPlayNetworking.canSend(RaidStartPayload.TYPE)
        addRenderableWidget(Button.builder(Component.translatable("gui.done")) { onClose() }
            .bounds(width / 2 + 4, height - 32, 100, 20).build())
        updatePages()
    }

    private fun updatePages() {
        previous.active = page > 0
        next.active = page + 1 < pageCount
    }

    override fun isPauseScreen() = false

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        graphics.fill(left, 12, left + panelWidth, height - 40, 0xE018202C.toInt())
        graphics.drawCenteredString(font, title, width / 2, 22, 0xFFE3AE)
        val tierName = odds.tier.replaceFirstChar { it.uppercase() }
        graphics.drawCenteredString(font, Component.translatable("screen.mondungeons.key", tierName), width / 2, 38, 0xFFFFFF)
        graphics.drawString(font, Component.translatable("screen.mondungeons.reward"), left + 12, 58, 0xAAAAAA)
        val chanceTitle = Component.translatable("screen.mondungeons.chance")
        graphics.drawString(font, chanceTitle, left + panelWidth - 12 - font.width(chanceTitle), 58, 0xAAAAAA)
        var tooltip: String? = null
        odds.rewards.drop(page * rowsPerPage).take(rowsPerPage).forEachIndexed { index, reward ->
            val y = 76 + index * 20
            if (index % 2 == 0) graphics.fill(left + 6, y - 4, left + panelWidth - 6, y + 14, 0x442F4358)
            val labelWidth = (panelWidth - 100).coerceAtLeast(1)
            val label = if (font.width(reward.label) > labelWidth)
                font.plainSubstrByWidth(reward.label, (labelWidth - font.width("...")).coerceAtLeast(0)) + "..."
                else reward.label
            graphics.drawString(font, label, left + 12, y, 0xFFFFFF)
            val chance = if (reward.probability > 0 && reward.probability < 0.00001) "<0.001%"
                else percent.format(reward.probability * 100)
            graphics.drawString(font, chance, left + panelWidth - 12 - font.width(chance), y, 0xFFE3AE)
            if (mouseX in left + 8 until left + panelWidth - 8 && mouseY in y - 4 until y + 14) {
                tooltip = reward.label + " — " + reward.probability.times(100).toString() + "%"
            }
        }
        graphics.drawCenteredString(font, Component.translatable("screen.mondungeons.page", page + 1, pageCount), width / 2, height - 56, 0xAAAAAA)
        super.render(graphics, mouseX, mouseY, partialTick)
        tooltip?.let { graphics.renderTooltip(font, Component.literal(it), mouseX, mouseY) }
    }
}
