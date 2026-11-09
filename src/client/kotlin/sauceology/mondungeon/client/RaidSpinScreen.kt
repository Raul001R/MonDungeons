package sauceology.mondungeon.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import sauceology.mondungeon.RaidPayoutPayload
import kotlin.math.pow
import kotlin.math.floor
import kotlin.math.sqrt

/** Cosmetic only. Closing this screen neither cancels nor claims the server's payout. */
class RaidSpinScreen(private val payout: RaidPayoutPayload) : Screen(Component.translatable("screen.mondungeons.spin")) {
    private var elapsed = 0
    override fun tick() { elapsed++ }
    override fun isPauseScreen() = false

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fillGradient(0, 0, width, height, 0xC9080710.toInt(), 0xE0080710.toInt())
        val w = (width - 24).coerceAtMost(430)
        val x = (width - w) / 2
        val y = height / 2 - 90
        rounded(graphics, x, y, w, 180, 7, 0xFF3A2E50.toInt())
        rounded(graphics, x + 1, y + 1, w - 2, 178, 6, 0xFF15121E.toInt())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val center = height / 2
        val spinning = payout.state == "spinning"
        val progress = if (spinning) ((elapsed + partialTick) / payout.ticks.coerceAtLeast(1)).coerceIn(0f, 1f) else 1f
        graphics.drawCenteredString(font, "MONDUNGEONS", width / 2, center - 72, 0xC6AEFF)
        graphics.drawCenteredString(font, title, width / 2, center - 55, 0xF2EDFF)
        val reelWidth = (width - 48).coerceAtMost(404)
        val reelLeft = (width - reelWidth) / 2
        val position = if (spinning) 24 * (1 - (1 - progress).pow(3)) else 24f
        // Fixed cosmetic reel: its final card is the prize already selected by the server.
        graphics.enableScissor(reelLeft, center - 32, reelLeft + reelWidth, center + 26)
        for (index in floor(position).toInt() - 2..floor(position).toInt() + 2) {
            val x = (width / 2 + (index - position) * 138 - 64).toInt()
            val selected = index == 24
            rounded(graphics, x, center - 30, 128, 54, 5, if (selected) 0xFF80633A.toInt() else 0xFF352B45.toInt())
            rounded(graphics, x + 1, center - 29, 126, 52, 4, 0xFF211B2D.toInt())
            val label = if (selected || payout.candidates.isEmpty()) payout.label else payout.candidates[Math.floorMod(index, payout.candidates.size)]
            val lines = font.split(Component.literal(label), 114).take(3)
            lines.forEachIndexed { i, line -> graphics.drawCenteredString(font, line, x + 64,
                center - lines.size * 5 + i * 11, if (selected) 0xFFD76A else 0xD3C8E5) }
        }
        graphics.disableScissor()
        graphics.fill(width / 2 - 3, center - 36, width / 2 + 3, center - 32, 0xFFFFD76A.toInt())
        graphics.fill(width / 2 - 3, center + 26, width / 2 + 3, center + 30, 0xFFFFD76A.toInt())
        val status = when (payout.state) {
            "granted" -> "granted"
            "failed" -> "payout_failed"
            "error" -> "payout_error"
            else -> "spinning"
        }
        graphics.drawCenteredString(font, Component.translatable("screen.mondungeons.$status"), width / 2, center + 43, 0xB4A6C8)
        if (spinning) graphics.drawCenteredString(font, Component.translatable("screen.mondungeons.spin_wait"), width / 2, center + 59, 0xB4A6C8)
        else graphics.drawCenteredString(font, Component.translatable("screen.mondungeons.close_hint"), width / 2, center + 65, 0xB4A6C8)
    }

    private fun rounded(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, radius: Int, color: Int) {
        for (row in 0 until h) {
            val edge = minOf(row, h - row - 1)
            val inset = if (edge < radius) (radius - sqrt((radius * radius - (radius - edge - 1) * (radius - edge - 1)).toDouble())).toInt() else 0
            graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, color)
        }
    }
}
