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
                         val key: ItemStack, val table: DungeonRewardTable, var currentRound: Int = 1,
                         var payout: RolledDungeonReward? = null, var spinRemaining: Int = 0)
private data class RaidOffer(val token: String, val hand: InteractionHand, val key: ItemStack)

/** One instance per server. Network callbacks and lifecycle events run on its server thread. */
class DungeonRuns(private val server: MinecraftServer) {
    private val config = DungeonArenaConfig()
    private val recovery = DungeonReturnRecovery(server)
    private val offers = mutableMapOf<UUID, RaidOffer>()
    private val logger = LoggerFactory.getLogger("mondungeons/runs")
    private var ticksUntilRetry = 0
    private var closing = false
    private var lastBroadcastOccupied = false
    private val admission = DungeonAdmission<ActiveDungeon>()
    val active: ActiveDungeon? get() = admission.active // null is the global lock's free state.
    private val gauntlet = DungeonGauntlet(server,
        onRound = { player, round -> active?.takeIf { it.playerUuid == player }?.currentRound = round },
        onComplete = { player -> finishGauntlet(player, true) },
        onFailure = { player -> finishGauntlet(player, false) })

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
        val table = DungeonLootData.tables.getValue(tier.lootTableId)
        try { DungeonPayout.validate(table) } catch (exception: Exception) {
            logger.error("Dungeon reward table {} cannot pay every advertised prize", table.id, exception)
            return tell(player, "rewards_unavailable")
        }
        if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, RaidPayoutPayload.TYPE)) {
            return tell(player, "ui_unavailable")
        }
        val arena = config.arena ?: return tell(player, "arena_unconfigured")
        val level = server.getLevel(arena.dimension) ?: return tell(player, "arena_unavailable")
        val pos = BlockPos.containing(arena.position)
        if (!level.worldBorder.isWithinBounds(pos) || pos.y < level.minBuildHeight || pos.y >= level.maxBuildHeight) {
            return tell(player, "arena_unavailable")
        }
        val prepared = try { gauntlet.prepare(player) } catch (exception: Exception) {
            logger.error("Dungeon trainer/party preflight failed", exception)
            return tell(player, "trainers_unavailable")
        }
        val origin = DungeonPosition.capture(player)
        try {
            if (!admission.enter(ActiveDungeon(player.uuid, tier.id, origin, held.copyWithCount(1), table),
                    remember = { recovery.remember(player.uuid, origin) },
                    move = { move(player, arena) }, rollback = { restore(player.uuid) })) {
                return tell(player, "occupied")
            }
            tell(player, "entered")
            gauntlet.start(player, prepared)
        } catch (exception: Exception) {
            logger.error("Could not enter dungeon for {}", player.uuid, exception)
            if (active?.playerUuid == player.uuid) {
                admission.clear()
                gauntlet.cancel()
                restore(player.uuid)
            }
            tell(player, "entry_failed")
        }
    }

    fun exit(player: ServerPlayer): Boolean {
        if (active?.playerUuid == player.uuid) {
            admission.clear()
            gauntlet.cancel()
        }
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
        if (active?.playerUuid == player) {
            admission.clear()
            gauntlet.cancel()
        }
        // The pre-teleport journal remains until a live player can be restored.
    }

    fun tick() {
        active?.let { run ->
            val player = server.playerList.getPlayer(run.playerUuid)
            if (player == null || !player.isAlive) disconnect(run.playerUuid)
        }
        active?.let { run ->
            if (run.payout != null && --run.spinRemaining <= 0) finishPayout(run)
        }
        gauntlet.tick()
        val occupied = active != null
        if (lastBroadcastOccupied != occupied) {
            lastBroadcastOccupied = occupied
            server.playerList.players.forEach { player ->
                if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, RaidAvailabilityPayload.TYPE)) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, RaidAvailabilityPayload(occupied))
                }
            }
        }
        if (ticksUntilRetry-- > 0) return
        ticksUntilRetry = config.returnRetryTicks - 1
        recovery.pending().keys.filter { it != active?.playerUuid }.forEach(::restore)
    }

    fun close() {
        closing = true
        admission.clear()
        gauntlet.close()
        offers.clear()
        recovery.pending().keys.forEach(::restore)
    }

    private fun finishGauntlet(playerId: UUID, complete: Boolean) {
        val run = active?.takeIf { it.playerUuid == playerId } ?: return
        if (run.payout != null) return // Duplicate final-victory callbacks never reroll.
        val player = server.playerList.getPlayer(playerId)
        if (complete && player != null) {
            try {
                run.payout = DungeonPayout.roll(run.table, player)
                run.spinRemaining = config.spinTicks
                val prize = requireNotNull(run.payout)
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    RaidPayoutPayload(prize.label, prize.group, config.spinTicks, "spinning",
                        run.table.rewards.map { it.definition.get("label")?.asString ?: "Reward" }))
                return // Keep the global lock until the server grants the prize.
            } catch (exception: Exception) {
                logger.error("Could not roll dungeon reward for {}", playerId, exception)
                tell(player, "payout_failed")
            }
        }
        admission.clear()
        if (!complete && player != null) tell(player, "gauntlet_failed")
        restore(playerId)
    }

    private fun finishPayout(run: ActiveDungeon) {
        // Keep the lock through grant and return. No subsequent tick can repeat this payout.
        run.spinRemaining = Int.MAX_VALUE
        val player = server.playerList.getPlayer(run.playerUuid)
        val prize = requireNotNull(run.payout)
        var result = "failed"
        try {
            if (player != null) {
                val granted = prize.grant(player, run.key)
                result = if (granted) "granted" else "failed"
                if (granted) player.sendSystemMessage(Component.translatable("message.mondungeons.reward_granted", prize.label))
                else tell(player, "payout_failed")
            }
        } catch (exception: Exception) {
            logger.error("Dungeon payout failed for {}", run.playerUuid, exception)
            result = "error"
            if (player != null) tell(player, "payout_error")
        } finally {
            try { restore(run.playerUuid) } finally { admission.clear() }
        }
        // Send after teleport so a dimension's loading screen cannot hide the final result.
        if (player != null) try {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                RaidPayoutPayload(prize.label, prize.group, 0, result, emptyList()))
        } catch (exception: Exception) { logger.warn("Could not show payout result for {}", run.playerUuid, exception) }
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
