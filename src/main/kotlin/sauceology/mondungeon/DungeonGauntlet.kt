package sauceology.mondungeon

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.battles.ForfeitActionResponse
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID

data class PreparedDungeon(val plan: DungeonRoundPlan, val teams: List<List<Pokemon>>)
private data class DungeonBattle(val battle: PokemonBattle, val npc: NPCEntity)
private class GauntletSession(val player: UUID, val prepared: PreparedDungeon, val anchor: DungeonPosition) {
    val progress = DungeonRoundProgress(prepared.plan.rounds.size, prepared.plan.healAfterRounds)
    var round: DungeonBattle? = null
    var challenger: NPCEntity? = null
    var challengeAt = 0L
    var pending: RoundAdvance? = null
    var transitionSince = 0L
    var endedSince: Long? = null
    var lost = false
}

class DungeonGauntlet(private val server: MinecraftServer, private val onRound: (UUID, Int) -> Unit,
                     private val onComplete: (UUID) -> Unit, private val onFailure: (UUID) -> Unit,
                     private val findPlayer: (UUID) -> ServerPlayer? = { server.playerList.getPlayer(it) }) {
    private var session: GauntletSession? = null
    private val logger = LoggerFactory.getLogger("mondungeons/gauntlet")
    private val victories = CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
        server.execute { victory(event) }
    }

    fun prepare(player: ServerPlayer): PreparedDungeon {
        val plan = DungeonRounds.load()
        val required = if (plan.rounds.any { it.format == "doubles" }) 2 else 1
        check(Cobblemon.storage.getParty(player).count { it.currentHealth > 0 } >= required) {
            "Bring at least $required conscious Pokemon"
        }
        // Validate every installed trainer/resource before acquiring the lock or teleporting.
        return PreparedDungeon(plan, RctDungeonTeams.prepare(server, plan))
    }

    fun start(player: ServerPlayer, prepared: PreparedDungeon) {
        check(session == null)
        val next = GauntletSession(player.uuid, prepared, DungeonPosition.capture(player))
        session = next
        try { launch(next, player) } catch (exception: Exception) { cancel(); throw exception }
    }

    private fun launch(current: GauntletSession, player: ServerPlayer) {
        val number = current.progress.currentRound
        val definition = current.prepared.plan.rounds[number - 1]
        val required = if (definition.format == "doubles") 2 else 1
        check(Cobblemon.storage.getParty(player).count { it.currentHealth > 0 } >= required) {
            "Not enough conscious Pokemon for ${definition.format}"
        }
        val npc = DungeonTrainer.spawn(player, definition, current.prepared.teams[number - 1], current.prepared.plan, current.anchor)
        current.challenger = npc
        current.challengeAt = server.tickCount.toLong() + current.prepared.plan.challengeDelayTicks
        onRound(current.player, number)
        player.sendSystemMessage(Component.translatable("message.mondungeons.round_challenge", definition.name))
    }

    private fun challenge(current: GauntletSession, player: ServerPlayer) {
        val number = current.progress.currentRound
        val definition = current.prepared.plan.rounds[number - 1]
        val npc = requireNotNull(current.challenger)
        check(!npc.isRemoved) { "Dungeon challenger disappeared before battle" }
        check(BattleRegistry.getBattleByParticipatingPlayer(player) == null) { "Player started another battle during the introduction" }
        var startedBattle: PokemonBattle? = null
        try {
            val format = (if (definition.format == "doubles") BattleFormat.GEN_9_DOUBLES else BattleFormat.GEN_9_SINGLES)
                .copy(adjustLevel = 0)
            // Explicitly disable virtual parties and pre-battle healing: odd-round attrition must persist.
            val started = BattleBuilder.pvn(player, npc, battleFormat = format, cloneParties = false, healFirst = false)
            check(started is SuccessfulBattleStart) { "Cobblemon refused dungeon round $number: $started" }
            startedBattle = started.battle
            current.round = DungeonBattle(started.battle, npc)
            current.challenger = null
            current.progress.attach(started.battle.battleId)
            player.sendSystemMessage(Component.translatable("message.mondungeons.round_started", number,
                current.prepared.plan.rounds.size, definition.name))
        } catch (exception: Exception) {
            startedBattle?.let { if (!it.ended) it.stop() }
            npc.discard()
            throw exception
        }
    }

    private fun victory(event: BattleVictoryEvent) {
        val current = session ?: return
        if (current.progress.expectedBattleId != event.battle.battleId) return
        if (event.winners.none { current.player in it.getPlayerUUIDs() } ||
            event.battle.actors.any { current.player in it.getPlayerUUIDs() && it.responses.any { response -> response is ForfeitActionResponse } }) {
            current.lost = true
            return
        }
        current.pending = current.progress.victory(event.battle.battleId) ?: return
        current.transitionSince = server.tickCount.toLong()
    }

    fun tick() {
        val current = session ?: return
        try {
            if (current.lost) return fail(current)
            val player = findPlayer(current.player)
            if (player == null || !player.isAlive) return fail(current)
            val now = server.tickCount.toLong()
            current.challenger?.let {
                if (it.isRemoved) return fail(current)
                if (now >= current.challengeAt) challenge(current, player)
                return
            }
            val tracked = current.round ?: return fail(current)
            if (tracked.npc.isRemoved && current.pending == null) return fail(current)
            val pending = current.pending
            if (pending != null) {
                val elapsed = now - current.transitionSince
                if (elapsed > current.prepared.plan.transitionTimeoutTicks) return fail(current)
                if (!tracked.battle.ended || BattleRegistry.getBattleByParticipatingPlayer(player) != null ||
                    elapsed < current.prepared.plan.interRoundDelayTicks) return
                tracked.npc.discard()
                current.round = null
                current.pending = null
                if (pending.heal) {
                    Cobblemon.storage.getParty(player).forEach { it.heal() }
                    player.sendSystemMessage(Component.translatable("message.mondungeons.round_healed", pending.finishedRound))
                }
                if (pending.complete) {
                    session = null
                    onComplete(current.player)
                } else launch(current, player)
                return
            }
            // Forfeits/flees may never emit BATTLE_VICTORY. Do not leave the run locked forever.
            if (tracked.battle.ended || BattleRegistry.getBattle(tracked.battle.battleId) == null) {
                val since = current.endedSince ?: now.also { current.endedSince = it }
                if (now - since >= current.prepared.plan.battleEndGraceTicks) fail(current)
            } else current.endedSince = null
        } catch (exception: Exception) {
            logger.error("Dungeon round failed for {}", current.player, exception)
            fail(current)
        }
    }

    private fun fail(current: GauntletSession) {
        cancel()
        onFailure(current.player)
    }

    fun cancel() {
        val current = session ?: return
        session = null // Detach before stopping the battle; resulting events cannot advance another run.
        current.challenger?.discard()
        current.round?.let { tracked ->
            try { if (!tracked.battle.ended) tracked.battle.stop() }
            catch (exception: Exception) { logger.error("Could not stop dungeon battle", exception) }
            finally { tracked.npc.discard() }
        }
    }

    fun close() {
        victories.unsubscribe()
        cancel()
    }
}
