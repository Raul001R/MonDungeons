package sauceology.mondungeon.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.TitleScreen
import sauceology.mondungeon.DungeonLootData
import sauceology.mondungeon.RaidOddsEntry
import sauceology.mondungeon.RaidOddsPayload

/** Explicit development-only visual preview; never runs in an installed modpack. */
internal object RaidOddsPreview {
    fun register() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment) return
        val tierId = System.getProperty("mondungeons.raidUiPreview") ?: return
        var opened = false
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val reopen = org.lwjgl.glfw.GLFW.glfwGetKey(client.window.window, org.lwjgl.glfw.GLFW.GLFW_KEY_F8) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            if ((!opened || reopen) && client.overlay == null && client.screen is TitleScreen) {
                opened = true
                if (tierId == "spin" || tierId == "reward") {
                    client.setScreen(RaidSpinScreen(sauceology.mondungeon.RaidPayoutPayload("Shiny Garchomp", "jackpot", 100,
                        if (tierId == "spin") "spinning" else "granted", listOf("Master Ball", "4 x Diamond", "Random shiny Pokemon", "Ability Patch"))))
                    return@register
                }
                val tier = DungeonLootData.tiers[tierId] ?: DungeonLootData.tiers.values.first()
                val entries = sauceology.mondungeon.RaidRewardMenu.entries(DungeonLootData.tables.getValue(tier.lootTableId))
                val name = tier.id.replaceFirstChar { it.uppercase() } + " Dungeon Key"
                client.setScreen(RaidOddsScreen(RaidOddsPayload(tier.id, name, entries, "", false,
                    sauceology.mondungeon.DungeonRounds.load().rounds.size), preview = true))
                org.slf4j.LoggerFactory.getLogger("mondungeons/ui-preview").info("Opened {} odds preview", tier.id)
            }
        }
    }
}
