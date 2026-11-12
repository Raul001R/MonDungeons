package sauceology.mondungeon.client

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import sauceology.mondungeon.RaidLeaderboardEntry
import sauceology.mondungeon.RaidLeaderboardPayload
import sauceology.mondungeon.RaidLeaderboardRequest

class RaidLeaderboardScreen(private val parent: RaidOddsScreen, private val preview: Boolean = false) :
    Screen(Component.translatable("screen.mondungeons.leaderboard")), RaidMenuState {
    private var result: RaidLeaderboardPayload? = null
    private var waiting = false
    private lateinit var previous: Button
    private lateinit var next: Button
    private val panelWidth get() = (width - 20).coerceAtMost(460)
    private val panelHeight get() = (height - 20).coerceAtMost(340)
    private val left get() = (width - panelWidth) / 2
    private val top get() = (height - panelHeight) / 2
    private val bottom get() = top + panelHeight

    override fun init() {
        addRenderableWidget(RaidOddsScreen.MenuButton(left + 12, bottom - 34, 70, 22,
            Component.translatable("screen.mondungeons.back")) { onClose() })
        previous = addRenderableWidget(RaidOddsScreen.MenuButton(left + panelWidth - 78, bottom - 34, 28, 22,
            Component.literal("<")) { request((result?.page ?: 0) - 1) })
        next = addRenderableWidget(RaidOddsScreen.MenuButton(left + panelWidth - 42, bottom - 34, 28, 22,
            Component.literal(">")) { request((result?.page ?: 0) + 1) })
        buttons()
        if (result == null && !waiting) request(0)
    }

    private fun request(page: Int) {
        waiting = true
        buttons()
        if (preview) {
            accept(RaidLeaderboardPayload(0, 8, 3, listOf(
                RaidLeaderboardEntry("Alex", 24), RaidLeaderboardEntry("Steve", 17), RaidLeaderboardEntry("Kai", 9))))
        } else ClientPlayNetworking.send(RaidLeaderboardRequest(page))
    }

    fun accept(payload: RaidLeaderboardPayload) {
        result = payload
        waiting = false
        buttons()
    }

    private fun buttons() {
        val data = result
        if (::previous.isInitialized) previous.active = !waiting && data != null && data.page > 0
        if (::next.isInitialized) next.active = !waiting && data != null && (data.page + 1) * data.pageSize < data.totalPlayers
    }

    override fun setOccupied(value: Boolean) = parent.setOccupied(value)
    override fun isPauseScreen() = false
    override fun onClose() { minecraft?.setScreen(parent) }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fillGradient(0, 0, width, height, 0xA9080710.toInt(), 0xD0080710.toInt())
        graphics.fill(left, top, left + panelWidth, bottom, 0xFF393154.toInt())
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, bottom - 1, 0xFF1D1829.toInt())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(font, title, width / 2, top + 14, 0xF4C25A)
        graphics.drawCenteredString(font, Component.translatable("screen.mondungeons.leaderboard_note"), width / 2, top + 30, 0xA79FC0)
        graphics.drawString(font, Component.translatable("screen.mondungeons.leaderboard_player"), left + 16, top + 51, 0xA79FC0)
        val clears = Component.translatable("screen.mondungeons.leaderboard_clears")
        graphics.drawString(font, clears, left + panelWidth - 16 - font.width(clears), top + 51, 0xA79FC0)
        val data = result
        if (data == null || data.entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(if (data == null)
                "screen.mondungeons.leaderboard_loading" else "screen.mondungeons.leaderboard_empty"), width / 2, top + 82, 0xF1ECFA)
        } else {
            val rowHeight = ((panelHeight - 118) / data.entries.size).coerceAtMost(22)
            data.entries.forEachIndexed { index, entry ->
                val y = top + 67 + index * rowHeight
                graphics.fill(left + 12, y - 3, left + panelWidth - 12, y + rowHeight - 4,
                    if (index % 2 == 0) 0xFF241D33.toInt() else 0xFF1D1829.toInt())
                val rank = data.page * data.pageSize + index + 1
                graphics.drawString(font, "$rank. ${entry.name}", left + 17, y, if (rank == 1) 0xF4C25A else 0xF1ECFA)
                val total = entry.clears.toString()
                graphics.drawString(font, total, left + panelWidth - 17 - font.width(total), y, 0xF1ECFA)
            }
        }
        val pageCount = if (data == null) 1 else ((data.totalPlayers - 1).coerceAtLeast(0)) / data.pageSize + 1
        graphics.drawCenteredString(font, Component.translatable("screen.mondungeons.page", (data?.page ?: 0) + 1, pageCount),
            width / 2, bottom - 27, 0xA79FC0)
    }
}
