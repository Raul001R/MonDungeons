package sauceology.mondungeon

import com.cobblemon.mod.common.battles.BattleRegistry
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.UUID

data class ActiveDungeon(val playerUuid: UUID, val keyTierId: String, val returnPos: DungeonPosition,
                         val currentRound: Int = 1)
private data class RaidOffer(val token: String, val hand: InteractionHand, val key: ItemStack)

/** One instance per server. Network callbacks and lifecycle events run on its server thread. */
class DungeonRuns(private val server: MinecraftServer) {
    private val config = DungeonArenaConfig()
    private val recovery = DungeonReturnRecovery(server)
    private val offers = mutableMapOf<UUID, RaidOffer>()
    private val logger = LoggerFactory.getLogger("mondungeons/runs")
    private var ticksUntilRetry = 0
    private var closing = false
    private val admission = DungeonAdmission<ActiveDungeon>()
    val active: ActiveDungeon? get() = admission.active // null is the global lock's free state.

    fun offer(player: ServerPlayer, hand: InteractionHand, stack: ItemStack): String {
        val token = UUID.randomUUID().toString()
        offers[player.uuid] = RaidOffer(token, hand, stack.copy())
        return token
    }

    fun start(player: ServerPlayer, token: String) {
        val offer = offers[player.uuid] ?: return
        if (offer.token != token) return
        offers.remove(player.uuid) // A request is single-use, including rejected requests.
        if (closing) return
        if (active != null) return tell(player, "occupied")
        if (player.uuid in recovery.pending()) return tell(player, "return_pending")
        if (!player.isAlive || player.isSpectator || player.isSleeping || player.isPassenger ||
            BattleRegistry.getBattleByParticipatingPlayer(player) != null) return tell(player, "cannot_enter")
        val held = player.getItemInHand(offer.hand)
        if (held.isEmpty || !ItemStack.isSameItemSameComponents(held, offer.key)) return tell(player, "key_changed")
        val tier = DungeonItems.tier(held) ?: return tell(player, "key_changed")
        val arena = config.arena ?: return tell(player, "arena_unconfigured")
        val level = server.getLevel(arena.dimension) ?: return tell(player, "arena_unavailable")
        val pos = BlockPos.containing(arena.position)
        if (!level.worldBorder.isWithinBounds(pos) || pos.y < level.minBuildHeight || pos.y >= level.maxBuildHeight) {
            return tell(player, "arena_unavailable")
        }
        val origin = DungeonPosition.capture(player)
        try {
            if (!admission.enter(ActiveDungeon(player.uuid, tier.id, origin),
                    remember = { recovery.remember(player.uuid, origin) },
                    move = { move(player, arena) }, rollback = { restore(player.uuid) })) {
                return tell(player, "occupied")
            }
            tell(player, "entered")
        } catch (exception: Exception) {
            logger.error("Could not enter dungeon for {}", player.uuid, exception)
            tell(player, "entry_failed")
        }
    }

    fun exit(player: ServerPlayer): Boolean {
        if (active?.playerUuid == player.uuid) admission.clear()
        if (player.uuid !in recovery.pending()) {
            tell(player, "no_run")
            return false
        }
        if (!restore(player.uuid)) tell(player, "return_pending")
        return true
    }

    fun setArena(player: ServerPlayer): Boolean {
        if (active != null) { tell(player, "occupied"); return false }
        return try {
            config.setArena(DungeonPosition.capture(player))
            tell(player, "arena_set")
            true
        } catch (exception: Exception) {
            logger.error("Could not save dungeon arena", exception)
            tell(player, "arena_save_failed")
            false
        }
    }

    fun disconnect(player: UUID) {
        offers.remove(player)
        if (active?.playerUuid == player) admission.clear()
        // The pre-teleport journal remains until a live player can be restored.
    }

    fun tick() {
        active?.let { run ->
            val player = server.playerList.getPlayer(run.playerUuid)
            if (player == null || !player.isAlive) disconnect(run.playerUuid)
        }
        if (ticksUntilRetry-- > 0) return
        ticksUntilRetry = config.returnRetryTicks - 1
        recovery.pending().keys.filter { it != active?.playerUuid }.forEach(::restore)
    }

    fun close() {
        closing = true
        admission.clear()
        offers.clear()
        recovery.pending().keys.forEach(::restore)
    }

    private fun restore(playerId: UUID): Boolean {
        val origin = recovery.pending()[playerId] ?: return true
        val player = server.playerList.getPlayer(playerId) ?: return false
        if (!player.isAlive) return false
        return try {
            if (!move(player, origin)) return false
            // Save the restored player before clearing their durable return obligation.
            server.playerList.saveAll()
            recovery.forget(playerId)
            tell(player, "returned")
            true
        } catch (exception: Exception) {
            logger.error("Could not return dungeon player {}", playerId, exception)
            false
        }
    }

    private fun move(player: ServerPlayer, destination: DungeonPosition): Boolean {
        val level = server.getLevel(destination.dimension) ?: return false
        player.stopRiding()
        val moved = player.teleportTo(level, destination.position.x, destination.position.y, destination.position.z,
            emptySet(), destination.yaw, destination.pitch)
        if (moved) {
            player.deltaMovement = Vec3.ZERO
            player.resetFallDistance()
        }
        return moved
    }

    private fun tell(player: ServerPlayer, key: String) {
        player.sendSystemMessage(Component.translatable("message.mondungeons.$key"))
    }
}
