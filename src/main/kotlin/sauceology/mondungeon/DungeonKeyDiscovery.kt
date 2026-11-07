package sauceology.mondungeon

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.component.CustomData

object DungeonKeyDiscovery {
    /** Opening a chest has already generated its loot; inspect only its own slots. */
    @JvmStatic
    fun onChestOpened(player: ServerPlayer) {
        val chest = (player.containerMenu as? ChestMenu)?.container ?: return
        var foundNewKey = false
        for (slot in 0 until chest.containerSize) {
            val stack = chest.getItem(slot)
            if (!stack.`is`(DungeonItems.DUNGEON_KEY)) continue
            val data = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
            if (data.getBoolean("dungeon_key_discovered")) continue
            // Persist on the item so moving it to another chest, another player,
            // splitting its stack or restarting the server cannot announce it again.
            data.putBoolean("dungeon_key_discovered", true)
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data))
            foundNewKey = true
        }
        if (foundNewKey) {
            chest.setChanged()
            player.sendSystemMessage(Component.translatable("message.mondungeons.dungeon_key_found"))
        }
    }
}
