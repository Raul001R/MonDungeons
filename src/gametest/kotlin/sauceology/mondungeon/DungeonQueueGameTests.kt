package sauceology.mondungeon

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.battles.BattleRegistry
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.util.UUID

class DungeonQueueGameTests : FabricGameTest {
    private fun key() = ItemStack(DungeonItems.DUNGEON_KEY, 2).apply {
        set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { putString("dungeon_key_tier", "common") }))
    }
    private fun directory(helper: GameTestHelper) = Files.createTempDirectory(helper.level.server.getWorldPath(LevelResource.ROOT), "queue-tests-")
    private class Display : DungeonPresentation {
        val prompts = mutableListOf<Pair<UUID, RaidQueuePromptPayload>>()
        val payouts = mutableListOf<Pair<UUID, RaidPayoutPayload>>()
        override fun available(player: ServerPlayer) = true
        override fun queueAvailable(player: ServerPlayer) = true
        override fun show(player: ServerPlayer, payload: RaidPayoutPayload) { payouts += player.uuid to payload }
        override fun prompt(player: ServerPlayer, payload: RaidQueuePromptPayload) { prompts += player.uuid to payload }
        fun latest(player: ServerPlayer) = prompts.last { it.first == player.uuid && it.second.seconds > 0 }.second
    }
    private fun createRuns(helper: GameTestHelper, display: Display): DungeonRuns {
        val dir = directory(helper)
        val config = DungeonArenaConfig(dir.resolve("arena.json"))
        val pos = helper.absolutePos(BlockPos(0, 2, 0))
        config.setArena("north", DungeonPosition(helper.level.dimension(), Vec3(pos.x + 220.5, pos.y.toDouble(), pos.z + 220.5), 0f, 0f))
        config.setArena("south", DungeonPosition(helper.level.dimension(), Vec3(pos.x + 320.5, pos.y.toDouble(), pos.z + 220.5), 90f, 0f))
        val gems = DungeonRewardTable(MonDungeons.id("test/queue_gems"), listOf(DungeonReward(JsonParser.parseString(
            """{"type":"item","item":"minecraft:diamond","count":4,"label":"Diamonds"}""").asJsonObject, 1.0, "rare")), emptyList())
        return DungeonRuns(helper.level.server, DungeonReturnRecovery(dir.resolve("returns.dat"), helper.level.registryAccess()),
            { gems }, display, DungeonLeaderboard(dir.resolve("clears.dat")), config)
    }
    private fun player(helper: GameTestHelper): ServerPlayer = helper.makeMockServerPlayerInLevel().also {
        it.inventory.setItem(0, key())
        repeat(6) { slot -> Cobblemon.storage.getParty(it).set(slot, PokemonProperties.parse("garchomp level=50").create()) }
    }
    private fun start(runs: DungeonRuns, player: ServerPlayer) = runs.start(player,
        runs.offer(player, InteractionHand.MAIN_HAND, player.mainHandItem))

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun arenaUpgradeAndQueuePackets(helper: GameTestHelper) {
        val file = directory(helper).resolve("arena.json")
        val dimension = helper.level.dimension().location()
        val legacy = """{"arena":{"dimension":"$dimension","x":1.125,"y":64.5,"z":-3.75,"yaw":123.5,"pitch":-12.5},"spinTicks":100,"returnRetryTicks":20}"""
        Files.writeString(file, legacy)
        val config = DungeonArenaConfig(file)
        val original = config.arenas.getValue("default")
        check(Files.readString(file) == legacy) // Loading preserves the legacy file.
        config.setArena("second", original.copy(position = Vec3(100.5, 70.5, 100.5)))
        val stored = Files.readString(file)
        check(!JsonParser.parseString(stored).asJsonObject.has("arena"))
        check(DungeonArenaConfig(file).arenas == config.arenas)
        check(runCatching { config.setArena("duplicate", original) }.isFailure)
        check(runCatching { config.setArena("BAD ID", original) }.isFailure)
        check(Files.readString(file) == stored)
        check(config.removeArena("second") && !config.removeArena("missing"))
        check(DungeonArenaConfig(file).arenas == mapOf("default" to original))
        val commands = helper.level.server.commands.dispatcher.root
        check(commands.getChild("setdungeon").getChild("id") != null)
        check(commands.getChild("monDungeons").getChild("arenas").getChild("remove") != null)
        check(commands.getChild("monDungeons").getChild("queue").getChild("leave") != null)
        val buffer = net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.level.registryAccess())
        try {
            for (seconds in listOf(0, 30)) {
                val prompt = RaidQueuePromptPayload("bound-token", seconds, "Common Dungeon Key", 3)
                RaidQueuePromptPayload.CODEC.encode(buffer, prompt)
                check(RaidQueuePromptPayload.CODEC.decode(buffer) == prompt)
            }
            for (accept in listOf(false, true)) {
                val reply = RaidQueueReplyPayload("bound-token", accept)
                RaidQueueReplyPayload.CODEC.encode(buffer, reply)
                check(RaidQueueReplyPayload.CODEC.decode(buffer) == reply)
            }
        } finally { buffer.release() }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 2000)
    fun concurrentRoundsQueueReservationsAndShutdown(helper: GameTestHelper) {
        val display = Display()
        val runs = createRuns(helper, display)
        val players = List(6) { player(helper) }
        val (a, b, c, d, e) = players
        val f = players[5]
        val origins = players.associate { it.uuid to DungeonPosition.capture(it) }
        val confirmation = DungeonSettingsConfig.current.queueConfirmationSeconds
        fun cleanup() { runs.close(); DungeonSettingsConfig.store.set("queue.confirmationSeconds", confirmation.toString()); players.forEach { it.discard() } }
        var phase = 0
        var firstBattle: UUID? = null
        try {
            DungeonSettingsConfig.store.set("queue.confirmationSeconds", "1")
            players.take(5).forEach { start(runs, it) }
            check(runs.activeRuns.keys == setOf(a.uuid, b.uuid))
            check(runs.activeRuns.values.map { it.arenaId }.toSet() == setOf("north", "south"))
            check(runs.queuePosition(c.uuid) == 1 && runs.queuePosition(d.uuid) == 2 && runs.queuePosition(e.uuid) == 3)
            check(display.prompts.isEmpty() && runs.occupied)
            check(!runs.setArena(c, "north") && !runs.removeArena("north"))
            start(runs, c)
            check(runs.queuePosition(c.uuid) == 1) // Repeated Start never duplicates/reorders a waiter.
        } catch (error: Throwable) { cleanup(); throw error }
        fun step() {
            helper.runAfterDelay(1) {
                try {
                    runs.tick()
                    val aBattle = BattleRegistry.getBattleByParticipatingPlayer(a)
                    val bBattle = BattleRegistry.getBattleByParticipatingPlayer(b)
                    when (phase) {
                        0 -> if (aBattle != null && bBattle != null) {
                            check(aBattle.battleId != bBattle.battleId)
                            firstBattle = aBattle.battleId
                            val challenger = aBattle.actors.single { a.uuid in it.getPlayerUUIDs() }
                            val trainer = aBattle.actors.single { a.uuid !in it.getPlayerUUIDs() }
                            CobblemonEvents.BATTLE_VICTORY.post(BattleVictoryEvent(aBattle, listOf(challenger), listOf(trainer), false))
                            aBattle.stop(); phase = 1
                        }
                        1 -> if (aBattle != null && aBattle.battleId != firstBattle) {
                            check(runs.activeRuns.getValue(a.uuid).currentRound == 2)
                            check(runs.activeRuns.getValue(b.uuid).currentRound == 1)
                            requireNotNull(bBattle).stop() // Missing-victory/flee path frees only south.
                            phase = 2
                        }
                        2 -> if (runs.queuePosition(c.uuid) == 0) {
                            check(runs.activeRuns.keys == setOf(a.uuid))
                            check(DungeonPosition.capture(b) == origins.getValue(b.uuid))
                            val cPrompt = display.latest(c)
                            check(!runs.setArena(c, "south") && !runs.removeArena("south"))
                            start(runs, f)
                            check(runs.queuePosition(f.uuid) == 3 && f.uuid !in runs.activeRuns)
                            runs.answerQueue(f, cPrompt.token, true) // Cannot answer another player's invitation.
                            check(runs.queuePosition(c.uuid) == 0)
                            runs.answerQueue(c, cPrompt.token, false)
                            check(runs.queuePosition(d.uuid) == 0 && runs.queuePosition(c.uuid) == -1)
                            val dPrompt = display.latest(d)
                            repeat(20) { runs.tick() }
                            check(runs.queuePosition(d.uuid) == -1 && runs.queuePosition(e.uuid) == 0)
                            runs.answerQueue(d, dPrompt.token, true) // Expired reply cannot enter.
                            check(d.uuid !in runs.activeRuns)
                            e.inventory.setItem(5, e.inventory.removeItemNoUpdate(0))
                            e.setPos(e.x + 9.125, e.y + 1.25, e.z - 2.875)
                            val eOrigin = DungeonPosition.capture(e)
                            val ePrompt = display.latest(e)
                            runs.answerQueue(e, ePrompt.token, true)
                            check(runs.activeRuns.keys == setOf(a.uuid, e.uuid))
                            check(runs.activeRuns.getValue(e.uuid).returnPos == eOrigin)
                            check(e.inventory.getItem(5).count == 2) // Queue/accept never consumes the key.
                            runs.answerQueue(e, ePrompt.token, true)
                            start(runs, c)
                            check(runs.queuePosition(c.uuid) == 2)
                            check(runs.leaveQueue(f) && runs.queuePosition(c.uuid) == 1)
                            runs.disconnect(e.uuid); runs.rejoin(e); runs.tick()
                            check(DungeonPosition.capture(e) == eOrigin)
                            check(runs.queuePosition(c.uuid) == 0)
                            start(runs, d)
                            runs.disconnect(c.uuid); runs.tick() // Invited disconnect passes the slot on.
                            check(runs.queuePosition(d.uuid) == 0)
                            val changed = display.latest(d)
                            d.inventory.clearContent()
                            runs.answerQueue(d, changed.token, true)
                            check(d.uuid !in runs.activeRuns && runs.queuePosition(d.uuid) == -1)
                            d.inventory.setItem(0, key())
                            start(runs, d)
                            check(runs.activeRuns.keys == setOf(a.uuid, d.uuid))
                            start(runs, c)
                            runs.exit(d); runs.tick()
                            check(runs.queuePosition(c.uuid) == 0)
                            start(runs, b)
                            runs.close(); runs.close()
                            check(runs.activeRuns.isEmpty() && runs.recovery.pending().isEmpty())
                            check(runs.queuePosition(c.uuid) == -1 && runs.queuePosition(b.uuid) == -1)
                            check(display.prompts.any { it.first == c.uuid && it.second.seconds == 0 })
                            check(DungeonPosition.capture(a) == origins.getValue(a.uuid))
                            players.forEach { p -> check((0 until p.inventory.containerSize).sumOf {
                                if (p.inventory.getItem(it).`is`(DungeonItems.DUNGEON_KEY)) p.inventory.getItem(it).count else 0
                            } == 2) }
                            cleanup(); helper.succeed(); return@runAfterDelay
                        }
                    }
                    step()
                } catch (error: Throwable) { cleanup(); throw error }
            }
        }
        step()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 2500)
    fun simultaneousClearsPayAndReleaseBothArenas(helper: GameTestHelper) {
        val display = Display()
        val runs = createRuns(helper, display)
        val players = List(2) { player(helper) }
        val origins = players.associate { it.uuid to DungeonPosition.capture(it) }
        val won = players.associate { it.uuid to mutableSetOf<UUID>() }
        fun cleanup() { runs.close(); players.forEach { it.discard() } }
        try { players.forEach { start(runs, it) }; check(runs.activeRuns.size == 2) }
        catch (error: Throwable) { cleanup(); throw error }
        fun step() {
            helper.runAfterDelay(1) {
                try {
                    runs.tick()
                    players.forEach { player ->
                        val battle = BattleRegistry.getBattleByParticipatingPlayer(player)
                        if (battle != null && won.getValue(player.uuid).add(battle.battleId)) {
                            val challenger = battle.actors.single { player.uuid in it.getPlayerUUIDs() }
                            val trainer = battle.actors.single { player.uuid !in it.getPlayerUUIDs() }
                            val event = BattleVictoryEvent(battle, listOf(challenger), listOf(trainer), false)
                            CobblemonEvents.BATTLE_VICTORY.post(event); CobblemonEvents.BATTLE_VICTORY.post(event)
                            battle.stop()
                        }
                    }
                    if (display.payouts.count { it.second.state == "granted" } == 2) {
                        check(runs.activeRuns.isEmpty() && runs.recovery.pending().isEmpty() && !runs.occupied)
                        players.forEach { player ->
                            check(won.getValue(player.uuid).size == 3)
                            check(player.mainHandItem.count == 1)
                            check((0 until player.inventory.containerSize).sumOf {
                                if (player.inventory.getItem(it).`is`(Items.DIAMOND)) player.inventory.getItem(it).count else 0
                            } == 4)
                            check(DungeonPosition.capture(player) == origins.getValue(player.uuid))
                            check(display.payouts.filter { it.first == player.uuid }.map { it.second.state } == listOf("spinning", "granted"))
                        }
                        check(runs.leaderboard.page(0, 8).entries.sumOf { it.clears } == 2L)
                        cleanup(); helper.succeed()
                    } else step()
                } catch (error: Throwable) { cleanup(); throw error }
            }
        }
        step()
    }
}
