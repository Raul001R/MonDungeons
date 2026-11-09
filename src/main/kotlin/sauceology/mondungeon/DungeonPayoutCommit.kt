package sauceology.mondungeon

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import java.util.UUID

/** A player-file checkpoint makes the receipt and key/item changes one atomic write. */
internal object DungeonPayoutCommit {
    fun grant(player: ServerPlayer, key: ItemStack, prize: RolledDungeonReward, runId: UUID,
              save: (ServerPlayer) -> Unit = DungeonDurableFiles::savePlayer): Boolean {
        val receipt = player as DungeonPayoutReceipt
        if (receipt.mondungeonsGetReceipt() == runId.toString()) return false
        val before = (0 until player.inventory.containerSize).map { player.inventory.getItem(it).copy() }
        val oldReceipt = receipt.mondungeonsGetReceipt()
        try {
            if (!prize.grant(player, key)) return false
            receipt.mondungeonsSetReceipt(runId.toString())
            save(player)
            return true
        } catch (exception: Exception) {
            receipt.mondungeonsSetReceipt(oldReceipt)
            before.forEachIndexed { slot, stack -> player.inventory.setItem(slot, stack) }
            player.inventory.setChanged()
            try { prize.pokemon?.let { removePokemon(player, it.uuid) }; save(player) }
            catch (rollback: Exception) { exception.addSuppressed(rollback) }
            throw exception
        }
    }

    fun removePokemon(player: ServerPlayer, id: UUID) {
        val party = Cobblemon.storage.getParty(player)
        party.get(id)?.let { check(party.remove(it)) { "Cannot roll back dungeon Pokemon from party" } }
        val pc = Cobblemon.storage.getPC(player)
        pc.get(id)?.let { check(pc.remove(it)) { "Cannot roll back dungeon Pokemon from PC" } }
    }
}
