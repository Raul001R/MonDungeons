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
                         var payout: RolledDungeonReward? = null, var spinRemaining: Int = 0,
                         val runId: UUID = UUID.randomUUID(), val arenaId: String = "default")
private data class RaidOffer(val token: String, val hand: InteractionHand, val key: ItemStack, val roundCount: Int)

/** One instance per server. Network callbacks and lifecycle events run on its server thread. */
class DungeonRuns(private val server: MinecraftServer,
    internal val recovery: DungeonReturnRecovery = DungeonReturnRecovery(server),
    private val rewardTable: (DungeonKeyTier) -> DungeonRewardTable = { DungeonLootData.tables.getValue(it.lootTableId) },
    private val presentation: DungeonPresentation = NetworkDungeonPresentation,
    val leaderboard: DungeonLeaderboard = DungeonLeaderboard(server),
    private val config: DungeonArenaConfig = DungeonArenaConfig()) {
    private val offers = mutableMapOf<UUID, RaidOffer>()
    private val logger = LoggerFactory.getLogger("mondungeons/runs")
    private var ticksUntilRetry = 0
    private var closing = false
    private var lastBroadcastOccupied = false
    private inner class Slot {
        val admission = DungeonAdmission<ActiveDungeon>()
        val gauntlet = DungeonGauntlet(server,
            onRound = { player, round -> admission.active?.takeIf { it.playerUuid == player }?.currentRound = round },
            onComplete = { player -> finishGauntlet(player, true) },
            onFailure = { player -> finishGauntlet(player, false) })
    }
    private val slots = linkedMapOf<String, Slot>()
    private val queue = DungeonQueue<RaidOffer>()
    private var queueClock = 0L
    val activeRuns: Map<UUID, ActiveDungeon> get() = slots.values.mapNotNull { it.admission.active }.associateBy { it.playerUuid }
    // Compatibility view for callers inspecting a single-arena run; capacity uses activeRuns/freeArena.
    val active: ActiveDungeon? get() = activeRuns.values.firstOrNull()
    val occupied: Boolean get() = queue.hasWaiting || (config.arenas.isNotEmpty() && freeArena() == null)
    private fun slot(id: String) = slots.getOrPut(id) { Slot() }
    private fun playerSlot(player: UUID) = slots.values.firstOrNull { it.admission.active?.playerUuid == player }
    private fun usable(position: DungeonPosition): Boolean {
        val level = server.getLevel(position.dimension) ?: return false
        val pos = BlockPos.containing(position.position)
        return level.worldBorder.isWithinBounds(pos) && pos.y >= level.minBuildHeight && pos.y < level.maxBuildHeight
    }
    private fun freeArena(): String? = config.arenas.entries.firstOrNull { (id, position) ->
        slots[id]?.admission?.active == null && !queue.reserved(id) && usable(position)
    }?.key

    fun offer(player: ServerPlayer, hand: InteractionHand, stack: ItemStack): String {
        val token = UUID.randomUUID().toString()
        val tier = requireNotNull(DungeonItems.tier(stack))
        val count = DungeonSettingsConfig.current.plan(tier.id, DungeonRounds.load()).rounds.size
        offers[player.uuid] = RaidOffer(token, hand, stack.copy(), count)
        return token
    }

    fun start(player: ServerPlayer, token: String) {
        val offer = offers[player.uuid] ?: return
        if (offer.token != token) return
        offers.remove(player.uuid) // A request is single-use, including rejected requests.
        if (queue.contains(player.uuid)) return queueStatus(player)
        startOffer(player, offer)
    }

    private fun startOffer(player: ServerPlayer, offer: RaidOffer, reservedArena: String? = null) {
        if (closing) return
        if (playerSlot(player.uuid) != null) return tell(player, "already_running")
        if (player.uuid in recovery.pending()) return tell(player, "return_pending")
        if (!player.isAlive || player.isSpectator || player.isSleeping || player.isPassenger ||
            BattleRegistry.getBattleByParticipatingPlayer(player) != null) return tell(player, "cannot_enter")
        // Queued players can move the original key to another inventory slot while waiting.
        val held = if (reservedArena == null) player.getItemInHand(offer.hand) else
            (0 until player.inventory.containerSize).map { player.inventory.getItem(it) }
                .firstOrNull { !it.isEmpty && ItemStack.isSameItemSameComponents(it, offer.key) } ?: ItemStack.EMPTY
        if (held.isEmpty || !ItemStack.isSameItemSameComponents(held, offer.key)) return tell(player, "key_changed")
        val tier = DungeonItems.tier(held) ?: return tell(player, "key_changed")
        val table = rewardTable(tier)
        try { DungeonPayout.validate(table) } catch (exception: Exception) {
            logger.error("Dungeon reward table {} cannot pay every advertised prize", table.id, exception)
            return tell(player, "rewards_unavailable")
        }
        if (!presentation.available(player)) {
            return tell(player, "ui_unavailable")
        }
        if (config.arenas.isEmpty()) return tell(player, "arena_unconfigured")
        if (config.arenas.values.none(::usable)) return tell(player, "arena_unavailable")
        val prepared = try { slot(config.arenas.keys.first()).gauntlet.prepare(player, tier.id) } catch (exception: Exception) {
            logger.error("Dungeon trainer/party preflight failed", exception)
            return tell(player, "trainers_unavailable")
        }
        if (prepared.plan.rounds.size != offer.roundCount) return tell(player, "settings_changed")
        if (reservedArena == null) serviceQueue() // Older waiters always get the first free slots.
        val arenaId = reservedArena ?: freeArena()
        if (arenaId == null) {
            if (!presentation.queueAvailable(player)) return tell(player, "ui_unavailable")
            queue.join(player.uuid, offer)
            queueStatus(player)
            return
        }
        val arena = config.arenas[arenaId]?.takeIf(::usable) ?: return tell(player, "arena_unavailable")
        val currentSlot = slot(arenaId)
        if (currentSlot.admission.active != null) return tell(player, "occupied")
        val origin = DungeonPosition.capture(player)
        val run = ActiveDungeon(player.uuid, tier.id, origin, held.copyWithCount(1), table, arenaId = arenaId)
        try {
            if (!currentSlot.admission.enter(run,
                    remember = {
                        recovery.remember(player.uuid, origin, run.key, run.runId)
                        DungeonDurableFiles.savePlayer(player)
                    },
                    move = { move(player, arena) }, rollback = { restore(player.uuid) })) {
                return tell(player, "occupied")
            }
            tell(player, "entered")
            currentSlot.gauntlet.start(player, prepared)
        } catch (exception: Exception) {
            logger.error("Could not enter dungeon for {}", player.uuid, exception)
            if (currentSlot.admission.active?.playerUuid == player.uuid) {
                currentSlot.admission.clear()
                currentSlot.gauntlet.cancel()
                restore(player.uuid)
            }
            tell(player, "entry_failed")
        }
    }

    fun exit(player: ServerPlayer): Boolean {
        val queued = leaveQueue(player)
        playerSlot(player.uuid)?.let { it.admission.clear(); it.gauntlet.cancel() }
        if (player.uuid !in recovery.pending()) {
            if (queued) return true
            tell(player, "no_run")
            return false
        }
        if (!restore(player.uuid)) tell(player, "return_pending")
        return true
    }

    fun setArena(player: ServerPlayer, id: String = "default"): Boolean {
        if (slots[id]?.admission?.active != null || queue.reserved(id)) { tell(player, "arena_busy"); return false }
        return try {
            config.setArena(id, DungeonPosition.capture(player))
            player.sendSystemMessage(Component.translatable("message.mondungeons.arena_set", id))
            true
        } catch (exception: Exception) {
            logger.error("Could not save dungeon arena", exception)
            tell(player, "arena_save_failed")
            false
        }
    }

    fun arenaDescriptions(): List<String> = config.arenas.map { (id, position) ->
        val state = when {
            slots[id]?.admission?.active != null -> "running"
            queue.reserved(id) -> "reserved"
            !usable(position) -> "unavailable"
            else -> "free"
        }
        "$id: ${position.dimension.location()} ${position.position.x}, ${position.position.y}, ${position.position.z} ($state)"
    }

    fun removeArena(id: String): Boolean {
        if (slots[id]?.admission?.active != null || queue.reserved(id) || (config.arenas.size == 1 && queue.hasWaiting)) return false
        return try {
            if (!config.removeArena(id)) return false
            slots.remove(id)?.gauntlet?.close()
            true
        } catch (exception: Exception) { logger.error("Could not remove dungeon arena {}", id, exception); false }
    }

    fun queuePosition(player: UUID): Int = queue.position(player)

    fun queueStatus(player: ServerPlayer) {
        val position = queue.position(player.uuid)
        if (position > 0) player.sendSystemMessage(Component.translatable("message.mondungeons.queue_position", position))
        else tell(player, if (position == 0) "queue_invited" else "queue_none")
    }

    fun leaveQueue(player: ServerPlayer): Boolean {
        if (!queue.contains(player.uuid)) return false
        queue.remove(player.uuid)?.let(::dismiss)
        tell(player, "queue_left")
        return true
    }

    fun answerQueue(player: ServerPlayer, token: String, accept: Boolean) {
        val invitation = queue.answer(player.uuid, token) ?: return // Wrong-player, stale and duplicate replies do nothing.
        dismiss(invitation)
        try {
            when {
                closing -> Unit
                queueClock >= invitation.expires -> tell(player, "queue_expired")
                !accept -> tell(player, "queue_left")
                else -> startOffer(player, invitation.offer, invitation.arena)
            }
        } finally { serviceQueue() }
    }

    private fun dismiss(invitation: DungeonQueueInvite<RaidOffer>) {
        val player = server.playerList.getPlayer(invitation.player) ?: return
        try { presentation.prompt(player, RaidQueuePromptPayload(invitation.token, 0, "", 0)) }
        catch (exception: Exception) { logger.warn("Could not dismiss dungeon queue prompt for {}", player.uuid, exception) }
    }

    private fun serviceQueue() {
        if (closing) return
        queue.invitations.forEach { invitation ->
            val player = server.playerList.getPlayer(invitation.player)
            if (queueClock >= invitation.expires || player == null || !player.isAlive ||
                config.arenas[invitation.arena]?.let(::usable) != true) {
                queue.remove(invitation.player)
                dismiss(invitation)
                if (player != null) tell(player, "queue_expired")
            }
        }
        while (queue.hasWaiting) {
            val arena = freeArena() ?: break
            val seconds = DungeonSettingsConfig.current.queueConfirmationSeconds
            val invitation = queue.reserve(arena, queueClock, seconds.toLong() * 20) ?: break
            val player = server.playerList.getPlayer(invitation.player)
            if (player == null) { queue.remove(invitation.player); continue }
            val invalid = when {
                !player.isAlive || player.isSpectator || player.isSleeping || player.isPassenger ||
                    BattleRegistry.getBattleByParticipatingPlayer(player) != null || playerSlot(player.uuid) != null -> "cannot_enter"
                player.uuid in recovery.pending() -> "return_pending"
                (0 until player.inventory.containerSize).none {
                    val stack = player.inventory.getItem(it)
                    !stack.isEmpty && ItemStack.isSameItemSameComponents(stack, invitation.offer.key)
                } -> "key_changed"
                !presentation.queueAvailable(player) -> "ui_unavailable"
                else -> null
            }
            if (invalid != null) {
                queue.remove(player.uuid)
                tell(player, invalid)
                continue
            }
            try {
                presentation.prompt(player, RaidQueuePromptPayload(invitation.token, seconds,
                    invitation.offer.key.hoverName.string, invitation.offer.roundCount))
                tell(player, "queue_invited")
            } catch (exception: Exception) {
                queue.remove(player.uuid)
                logger.warn("Could not invite dungeon queue player {}", player.uuid, exception)
                tell(player, "ui_unavailable")
            }
        }
    }

    fun disconnect(player: UUID) {
        offers.remove(player)
        queue.remove(player)
        playerSlot(player)?.let { it.admission.clear(); it.gauntlet.cancel() }
        // The pre-teleport journal remains until a live player can be restored.
    }

    fun keyToRetain(player: ServerPlayer): ItemStack? {
        val entry = recovery.record(player.uuid) ?: return null
        if ((player as DungeonPayoutReceipt).mondungeonsGetReceipt() == entry.runId.toString()) return null
        return entry.key
    }

    fun rejoin(player: ServerPlayer) {
        if (playerSlot(player.uuid) == null) restore(player.uuid)
    }

    fun tick() {
        queueClock++
        activeRuns.values.toList().forEach { run ->
            val player = server.playerList.getPlayer(run.playerUuid)
            if (player == null || !player.isAlive) disconnect(run.playerUuid)
        }
        activeRuns.values.toList().forEach { run ->
            if (run.payout != null && --run.spinRemaining <= 0) finishPayout(run)
        }
        slots.values.toList().forEach { it.gauntlet.tick() }
        serviceQueue()
        val occupied = occupied
        if (lastBroadcastOccupied != occupied) {
            lastBroadcastOccupied = occupied
            server.playerList.players.forEach { player ->
                if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, RaidAvailabilityPayload.TYPE)) {
                    try { net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, RaidAvailabilityPayload(occupied)) }
                    catch (exception: Exception) { logger.warn("Could not update dungeon availability for {}", player.uuid, exception) }
                }
            }
        }
        if (ticksUntilRetry-- > 0) return
        ticksUntilRetry = config.returnRetryTicks - 1
        recovery.pending().keys.filter { it !in activeRuns }.forEach(::restore)
    }

    fun close() {
        closing = true
        queue.invitations.forEach(::dismiss)
        queue.clear()
        slots.values.forEach { it.admission.clear() }
        slots.values.forEach {
            try { it.gauntlet.close() } catch (exception: Exception) { logger.error("Could not close dungeon gauntlet", exception) }
        }
        slots.clear()
        offers.clear()
        recovery.pending().keys.forEach(::restore)
    }

    private fun finishGauntlet(playerId: UUID, complete: Boolean) {
        val currentSlot = playerSlot(playerId) ?: return
        val run = currentSlot.admission.active ?: return
        if (run.payout != null) return // Duplicate final-victory callbacks never reroll.
        val player = server.playerList.getPlayer(playerId)
        if (complete && player != null) {
            try {
                run.payout = DungeonPayout.roll(run.table, player)
                recovery.preparePayout(playerId, run.payout?.pokemon?.uuid)
                run.spinRemaining = config.spinTicks
                val prize = requireNotNull(run.payout)
                presentation.show(player,
                    RaidPayoutPayload(prize.label, prize.group, config.spinTicks, "spinning",
                        run.table.rewards.map { it.definition.get("label")?.asString ?: "Reward" }))
                return // Keep this arena occupied until the server grants the prize.
            } catch (exception: Exception) {
                logger.error("Could not roll dungeon reward for {}", playerId, exception)
                tell(player, "payout_failed")
            }
        }
        currentSlot.admission.clear()
        if (!complete && player != null) tell(player, "gauntlet_failed")
        restore(playerId)
    }

    private fun finishPayout(run: ActiveDungeon) {
        // Keep this arena occupied through grant and return. Other arenas keep running.
        run.spinRemaining = Int.MAX_VALUE
        val player = server.playerList.getPlayer(run.playerUuid)
        val prize = requireNotNull(run.payout)
        var result = "failed"
        try {
            if (player != null) {
                val granted = DungeonPayoutCommit.grant(player, run.key, prize, run.runId)
                result = if (granted) "granted" else "failed"
                if (granted) player.sendSystemMessage(Component.translatable("message.mondungeons.reward_granted", prize.label))
                else tell(player, "payout_failed")
            }
        } catch (exception: Exception) {
            logger.error("Dungeon payout failed for {}", run.playerUuid, exception)
            result = "error"
            if (player != null) tell(player, "payout_error")
        } finally {
            try { restore(run.playerUuid) } finally { slots[run.arenaId]?.admission?.clear() }
        }
        // Send after teleport so a dimension's loading screen cannot hide the final result.
        if (player != null) try {
            presentation.show(player,
                RaidPayoutPayload(prize.label, prize.group, 0, result, emptyList()))
        } catch (exception: Exception) { logger.warn("Could not show payout result for {}", run.playerUuid, exception) }
    }

    private fun restore(playerId: UUID): Boolean {
        val entry = recovery.record(playerId) ?: return true
        val player = server.playerList.getPlayer(playerId) ?: return false
        if (!player.isAlive) return false
        return try {
            if (!move(player, entry.origin)) return false
            if ((player as DungeonPayoutReceipt).mondungeonsGetReceipt() != entry.runId.toString()) {
                entry.payoutPokemon?.let { DungeonPayoutCommit.removePokemon(player, it) }
            } else {
                // The paid receipt is durable before credit. Keep the return obligation until credit
                // is also durable so a crash or write failure can retry without a second payout/clear.
                leaderboard.record(player.uuid, player.gameProfile.name, entry.runId)
            }
            // Save the restored player before clearing their durable return obligation.
            DungeonDurableFiles.savePlayer(player)
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
        try { player.sendSystemMessage(Component.translatable("message.mondungeons.$key")) }
        catch (exception: Exception) { logger.warn("Could not show dungeon message to {}", player.uuid, exception) }
    }
}
