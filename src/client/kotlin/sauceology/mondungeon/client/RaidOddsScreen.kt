package sauceology.mondungeon.client

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import sauceology.mondungeon.RaidOddsPayload
import sauceology.mondungeon.RaidOddsEntry
import sauceology.mondungeon.RaidStartPayload
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.sqrt

/** Layout and palette follow dungeon-ui-spec.md. */
interface RaidMenuState { fun setOccupied(value: Boolean) }

class RaidOddsScreen(private val odds: RaidOddsPayload, private val preview: Boolean = false) :
    Screen(Component.translatable("screen.mondungeons.raid")), RaidMenuState {
    private var occupied = odds.occupied
    private lateinit var start: Button
    private val panelWidth get() = (width - 20).coerceAtMost(460)
    private val panelHeight get() = (height - 20).coerceAtMost(356)
    private val left get() = (width - panelWidth) / 2
    private val top get() = (height - panelHeight) / 2
    private val right get() = left + panelWidth
    private val bottom get() = top + panelHeight
    private val compact get() = panelHeight < 280
    private val keyTop get() = top + if (compact) 30 else 46
    private val keyHeight get() = if (compact) 32 else 52
    private val headingY get() = keyTop + keyHeight + if (compact) 6 else 10
    private val rowTop get() = headingY + if (compact) 12 else 14
    private val gap get() = if (compact) 3 else 6
    private val footer get() = if (odds.roundCount > 0) Component.translatable("screen.mondungeons.footer", odds.roundCount)
        else Component.translatable("screen.mondungeons.footer_unknown")
    private val footerLines get() = font.split(footer, panelWidth - 24)
    private val footerTop get() = bottom - 8 - footerLines.size * 10
    private val buttonHeight get() = if (compact) 22 else 26
    private val buttonY get() = footerTop - buttonHeight - 8
    private val rowHeight get() = ((buttonY - 7 - rowTop - 3 * gap) / 4).coerceAtLeast(16)
    private val keyColor get() = when (odds.tier) {
        "legendary" -> GOLD
        "rare", "epic" -> RARE
        else -> COMMON
    }
    private val rows get() = ORDER.mapNotNull { id -> odds.rewards.find { it.id == id } }
    private val percent = DecimalFormat("0.###'%'")

    override fun init() {
        addRenderableWidget(MenuButton(right - 32, top + 10, 20, 18, Component.literal("X")) { onClose() })
        start = addRenderableWidget(MenuButton(left + 12, buttonY, panelWidth - 24, buttonHeight,
            Component.translatable("screen.mondungeons.start"), primary = true) { button ->
            if (!preview && !occupied) {
                button.active = false
                ClientPlayNetworking.send(RaidStartPayload(odds.startToken))
                onClose()
            }
        })
        updateStart()
        rows.forEachIndexed { index, group ->
            addRenderableWidget(MenuButton(left + 12, rowTop + index * (rowHeight + gap), panelWidth - 24,
                rowHeight, Component.translatable("screen.mondungeons.view_group", group.id), quiet = true) {
                minecraft?.setScreen(RewardDetailsScreen(group))
            })
        }
    }

    override fun setOccupied(value: Boolean) {
        occupied = value
        if (::start.isInitialized) updateStart()
    }

    private fun updateStart() {
        start.message = Component.translatable(if (occupied) "screen.mondungeons.in_use" else "screen.mondungeons.start")
        start.active = !occupied && (preview || (minecraft?.connection != null && ClientPlayNetworking.canSend(RaidStartPayload.TYPE)))
    }

    override fun isPauseScreen() = false

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (preview && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_L) {
            setOccupied(!occupied)
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    // Screen.render calls this once. Never draw a vanilla background over foreground text.
    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fillGradient(0, 0, width, height, 0xA9080710.toInt(), 0xD0080710.toInt())
        rounded(graphics, left + 3, top + 5, panelWidth, panelHeight, 7, 0x66000000)
        rounded(graphics, left, top, panelWidth, panelHeight, 7, BORDER)
        rounded(graphics, left + 1, top + 1, panelWidth - 2, panelHeight - 2, 6, 0xED15121E.toInt())
        graphics.fill(left + 9, top + 1, right - 9, top + 2, HIGHLIGHT)
        rounded(graphics, left + 12, keyTop, panelWidth - 24, keyHeight, 5, INNER)
        rows.forEachIndexed { index, _ ->
            rounded(graphics, left + 12, rowTop + index * (rowHeight + gap), panelWidth - 24, rowHeight, 4, INNER)
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.pose().pushPose()
        graphics.pose().translate((left + 16).toFloat(), (top + if (compact) 8 else 10).toFloat(), 0f)
        graphics.pose().scale(1.15f, 1.15f, 1f)
        graphics.drawString(font, title, 0, 0, TEXT)
        graphics.pose().popPose()
        graphics.drawString(font, Component.translatable("screen.mondungeons.subtitle"), left + 16, top + if (compact) 19 else 22, MUTED)
        val slotSize = keyHeight - 10
        val slotX = left + 17
        val slotY = keyTop + 5
        rounded(graphics, slotX, slotY, slotSize, slotSize, 4, SLOT)
        keyGlyph(graphics, slotX + (slotSize - 22) / 2, slotY + (slotSize - 22) / 2, keyColor)
        val keyTextX = slotX + slotSize + 10
        val keyTextWidth = right - 24 - keyTextX
        val tier = odds.tier.replace('_', ' ').uppercase(Locale.ROOT)
        graphics.drawString(font, Component.translatable("screen.mondungeons.key_label", tier), keyTextX, keyTop + 5, keyColor)
        graphics.drawString(font, shorten(odds.keyName, keyTextWidth), keyTextX, keyTop + 17, TEXT)
        if (keyHeight >= 46) graphics.drawString(font,
            shorten(Component.translatable(if (odds.tier == "common") "screen.mondungeons.key_note_common" else "screen.mondungeons.key_note").string, keyTextWidth),
            keyTextX, keyTop + 31, MUTED)
        graphics.drawString(font, Component.translatable("screen.mondungeons.reward"), left + 18, headingY, FAINT)
        val chanceHeader = Component.translatable("screen.mondungeons.chance")
        graphics.drawString(font, chanceHeader, right - 18 - font.width(chanceHeader), headingY, FAINT)
        var tooltip: Component? = null
        rows.forEachIndexed { index, reward ->
            val y = rowTop + index * (rowHeight + gap)
            val color = tierColor(reward.id)
            val label = Component.translatable("screen.mondungeons.tier.${reward.id}")
            graphics.drawString(font, label, left + 20, y + 3, color)
            val chance = percent.format(reward.probability * 100)
            graphics.drawString(font, chance, right - 20 - font.width(chance), y + 3, TEXT)
            val hint = Component.translatable("screen.mondungeons.view_all")
            graphics.drawString(font, hint, right - 82 - font.width(hint), y + 3, FAINT)
            val examples = reward.examples.joinToString(" · ")
            if (rowHeight >= 29) graphics.drawString(font, shorten(examples, panelWidth - 40), left + 20, y + 15, MUTED)
            val barX = left + 20
            val barY = y + rowHeight - 4
            val barWidth = panelWidth - 40
            graphics.fill(barX, barY, barX + barWidth, barY + 2, SLOT)
            // Literal scale: a 3% chance fills 3% of the bar. No eased/minimum-width odds.
            val fill = (barWidth * reward.probability.coerceIn(0.0, 1.0)).toInt()
            if (fill > 0) graphics.fill(barX, barY, barX + fill, barY + 2, color)
            if (mouseX in left + 12 until right - 12 && mouseY in y until y + rowHeight) {
                tooltip = Component.literal(label.string + " - " + chance + "\n" + reward.options.joinToString("\n") {
                    it.label + (if (it.count > 1) " x${it.count}" else "") + " - " +
                        Component.translatable("screen.mondungeons.reward_kind.${it.kind}").string
                })
            }
        }
        if (mouseX in left + 12 until right - 12 && mouseY in keyTop until keyTop + keyHeight) {
            tooltip = Component.literal(odds.keyName)
        }
        footerLines.forEachIndexed { index, line ->
            graphics.drawCenteredString(font, line, width / 2, footerTop + index * 10, MUTED)
        }
        tooltip?.let { graphics.renderTooltip(font, font.split(it, (panelWidth - 32).coerceAtLeast(1)), mouseX, mouseY) }
    }

    private fun shorten(text: String, available: Int): String = if (font.width(text) <= available) text
        else font.plainSubstrByWidth(text, (available - font.width("...")).coerceAtLeast(0)) + "..."

    private inner class RewardDetailsScreen(private val group: RaidOddsEntry) :
        Screen(Component.translatable("screen.mondungeons.group_rewards", Component.translatable("screen.mondungeons.tier.${group.id}"))), RaidMenuState {
        private var page = 0
        private val boxWidth get() = (width - 20).coerceAtMost(460)
        private val boxHeight get() = (height - 20).coerceAtMost(356)
        private val boxX get() = (width - boxWidth) / 2
        private val boxY get() = (height - boxHeight) / 2
        private val pageSize get() = ((boxHeight - 90) / 30).coerceAtLeast(1)
        private val pageCount get() = ((group.options.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        override fun init() {
            page = page.coerceIn(0, pageCount - 1)
            addRenderableWidget(MenuButton(boxX + boxWidth - 32, boxY + 10, 20, 18, Component.literal("X")) { onClose() })
            addRenderableWidget(MenuButton(boxX + 12, boxY + boxHeight - 30, 64, 20, Component.translatable("gui.back")) { onClose() })
            val previous = addRenderableWidget(MenuButton(boxX + boxWidth - 64, boxY + boxHeight - 30, 22, 20, Component.literal("<")) {
                page--; rebuildWidgets()
            })
            val next = addRenderableWidget(MenuButton(boxX + boxWidth - 36, boxY + boxHeight - 30, 22, 20, Component.literal(">")) {
                page++; rebuildWidgets()
            })
            previous.active = page > 0
            next.active = page + 1 < pageCount
        }
        override fun onClose() { minecraft?.setScreen(this@RaidOddsScreen) }
        override fun isPauseScreen() = false
        override fun setOccupied(value: Boolean) { this@RaidOddsScreen.setOccupied(value) }
        override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.fillGradient(0, 0, width, height, 0xA9080710.toInt(), 0xD0080710.toInt())
            rounded(graphics, boxX, boxY, boxWidth, boxHeight, 7, BORDER)
            rounded(graphics, boxX + 1, boxY + 1, boxWidth - 2, boxHeight - 2, 6, 0xFF15121E.toInt())
        }
        override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            super.render(graphics, mouseX, mouseY, partialTick)
            graphics.drawString(font, title, boxX + 14, boxY + 12, tierColor(group.id))
            graphics.drawString(font, Component.translatable("screen.mondungeons.reward_chances"), boxX + 14, boxY + 28, MUTED)
            var tooltip: Component? = null
            group.options.drop(page * pageSize).take(pageSize).forEachIndexed { index, option ->
                val y = boxY + 48 + index * 30
                rounded(graphics, boxX + 12, y, boxWidth - 24, 27, 3, INNER)
                val label = option.label + if (option.count > 1) " x${option.count}" else ""
                val available = boxWidth - 100
                val displayed = if (font.width(label) > available) font.plainSubstrByWidth(label, available - font.width("...")) + "..." else label
                graphics.drawString(font, displayed, boxX + 18, y + 4, TEXT)
                graphics.drawString(font, Component.translatable("screen.mondungeons.reward_kind.${option.kind}"), boxX + 18, y + 15, MUTED)
                val chance = percent.format(option.probability * 100)
                graphics.drawString(font, chance, boxX + boxWidth - 18 - font.width(chance), y + 4, tierColor(group.id))
                if (mouseX in boxX + 12 until boxX + boxWidth - 12 && mouseY in y until y + 27) tooltip = Component.literal(label)
            }
            graphics.drawCenteredString(font, Component.translatable("screen.mondungeons.page", page + 1, pageCount), width / 2,
                boxY + boxHeight - 24, MUTED)
            tooltip?.let { graphics.renderTooltip(font, it, mouseX, mouseY) }
        }
    }

    private class MenuButton(x: Int, y: Int, width: Int, height: Int, label: Component,
                             private val primary: Boolean = false, private val quiet: Boolean = false, action: (Button) -> Unit) :
        Button(x, y, width, height, label, OnPress { action(it) }, CreateNarration { it.get() }) {
        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val hover = active && isHoveredOrFocused
            if (quiet) {
                if (hover) rounded(graphics, x, y, width, height, 4, SLOT)
                return
            }
            val edge = if (primary && active) 0xFF2F7D3E.toInt() else if (hover) HIGHLIGHT else BORDER
            val fill = if (primary && active) { if (hover) 0xFF78DF89.toInt() else 0xFF5FCF72.toInt() }
                else if (hover) HIGHLIGHT else SLOT
            rounded(graphics, x, y, width, height, 4, edge)
            rounded(graphics, x + 1, y + 1, width - 2, height - 4, 3, fill)
            val color = if (!active) FAINT else if (primary) 0xFF0B2A14.toInt() else TEXT
            graphics.drawCenteredString(Minecraft.getInstance().font, message, x + width / 2, y + (height - 8) / 2 - 1, color)
        }
    }

    companion object {
        private val ORDER = listOf("jackpot", "rare", "uncommon", "common")
        private const val INNER = 0xFF1D1829.toInt()
        private const val SLOT = 0xFF241D33.toInt()
        private const val BORDER = 0xFF393154.toInt()
        private const val HIGHLIGHT = 0xFF4A3F6B.toInt()
        private const val TEXT = 0xFFF1ECFA.toInt()
        private const val MUTED = 0xFFA79FC0.toInt()
        private const val FAINT = 0xFF6F6788.toInt()
        private const val GOLD = 0xFFF4C25A.toInt()
        private const val RARE = 0xFFA874E8.toInt()
        private const val UNCOMMON = 0xFF4F9DE8.toInt()
        private const val COMMON = 0xFF8A93A8.toInt()
        private fun tierColor(id: String) = when (id) {
            "jackpot" -> GOLD
            "rare" -> RARE
            "uncommon" -> UNCOMMON
            else -> COMMON
        }
        private fun rounded(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, radius: Int, color: Int) {
            val r = radius.coerceAtMost(height / 2).coerceAtMost(width / 2)
            graphics.fill(x, y + r, x + width, y + height - r, color)
            for (line in 0 until r) {
                val dy = r - line - 0.5
                val inset = (r - sqrt(r * r - dy * dy)).toInt()
                graphics.fill(x + inset, y + line, x + width - inset, y + line + 1, color)
                graphics.fill(x + inset, y + height - line - 1, x + width - inset, y + height - line, color)
            }
        }
        private fun keyGlyph(graphics: GuiGraphics, x: Int, y: Int, color: Int) {
            rounded(graphics, x + 1, y + 1, 11, 11, 3, color)
            rounded(graphics, x + 4, y + 4, 5, 5, 1, SLOT)
            for (step in 0..5) graphics.fill(x + 9 + step, y + 9 + step, x + 13 + step, y + 13 + step, color)
            graphics.fill(x + 16, y + 13, x + 20, y + 17, color)
            graphics.fill(x + 13, y + 16, x + 17, y + 20, color)
        }
    }
}
