package sauceology.mondungeon

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.ChestMenu

object DungeonKeyDiscovery {
    /** Opening a chest has already generated its loot; inspect only its own slots. */
    @JvmStatic
    fun onChestOpened(player: ServerPlayer) {
        val chest = (player.containerMenu as? ChestMenu)?.container ?: return
        if ((0 until chest.containerSize).any { chest.getItem(it).`is`(DungeonItems.DUNGEON_KEY) }) {
            player.sendSystemMessage(Component.translatable("message.mondungeons.dungeon_key_found"))
        }
    }
}
