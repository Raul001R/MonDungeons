package sauceology.mondungeon

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

data class ProtectedDungeonKey(val slot: Int, val stack: ItemStack)

/** Protect one run key only. Every other inventory item follows normal death rules. */
object DungeonDeathKeys {
    @JvmStatic fun beforeDrop(inventory: Inventory): ProtectedDungeonKey? {
        val player = inventory.player as? ServerPlayer ?: return null
        val key = DungeonRunService.runs?.keyToRetain(player) ?: return null
        val slot = (0 until inventory.containerSize).firstOrNull {
            !inventory.getItem(it).isEmpty && ItemStack.isSameItemSameComponents(inventory.getItem(it), key)
        } ?: return null
        val protected = ProtectedDungeonKey(slot, inventory.getItem(slot).copyWithCount(1))
        inventory.getItem(slot).shrink(1)
        return protected
    }

    @JvmStatic fun afterDrop(inventory: Inventory, key: ProtectedDungeonKey?) {
        if (key == null) return
        val current = inventory.getItem(key.slot)
        if (current.isEmpty) inventory.setItem(key.slot, key.stack)
        else if (ItemStack.isSameItemSameComponents(current, key.stack)) current.grow(1)
        else check(inventory.add(key.stack)) { "Cannot restore protected dungeon key" }
        inventory.setChanged()
    }

    @JvmStatic fun transfer(oldPlayer: ServerPlayer, newPlayer: ServerPlayer) {
        val key = DungeonRunService.runs?.keyToRetain(oldPlayer) ?: return
        val source = (0 until oldPlayer.inventory.containerSize).firstOrNull {
            !oldPlayer.inventory.getItem(it).isEmpty && ItemStack.isSameItemSameComponents(oldPlayer.inventory.getItem(it), key)
        } ?: return
        // keepInventory/keepEverything already copied the protected key.
        if ((0 until newPlayer.inventory.containerSize).any {
            !newPlayer.inventory.getItem(it).isEmpty && ItemStack.isSameItemSameComponents(newPlayer.inventory.getItem(it), key)
        }) return
        check(newPlayer.inventory.add(key.copyWithCount(1))) { "Cannot transfer protected dungeon key" }
        oldPlayer.inventory.getItem(source).shrink(1)
    }
}
