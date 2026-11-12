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
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ClientInformation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.Vec3
import java.io.IOException
import java.nio.file.Files
import java.util.UUID

class DungeonFailureGameTests : FabricGameTest {
    private fun key(count: Int = 1) = ItemStack(DungeonItems.DUNGEON_KEY, count).apply {
        set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { putString("dungeon_key_tier", "rare") }))
    }

    private fun journal(helper: GameTestHelper): DungeonReturnRecovery {
        val dir = Files.createTempDirectory(helper.level.server.getWorldPath(LevelResource.ROOT), "dungeon-journal-test-")
        return DungeonReturnRecovery(dir.resolve("returns.dat"), helper.level.registryAccess())
    }

    private fun leaderboard(helper: GameTestHelper): DungeonLeaderboard = DungeonLeaderboard(
        Files.createTempDirectory(helper.level.server.getWorldPath(LevelResource.ROOT), "dungeon-leaderboard-test-").resolve("clears.dat"))

    private val gems get() = DungeonRewardTable(MonDungeons.id("test/gems"), listOf(DungeonReward(
        JsonParser.parseString("""{"type":"item","item":"minecraft:diamond","count":4,"label":"Diamonds"}""").asJsonObject,
        1.0, "rare")), emptyList())

    private val headless = object : DungeonPresentation {
        override fun available(player: ServerPlayer) = true
        override fun show(player: ServerPlayer, payload: RaidPayoutPayload) = Unit
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun journalReloadAndWriteFailurePreserveObligations(helper: GameTestHelper) {
        val dir = Files.createTempDirectory(helper.level.server.getWorldPath(LevelResource.ROOT), "dungeon-io-test-")
        val file = dir.resolve("returns.dat")
        val id = UUID.randomUUID()
        val runId = UUID.randomUUID()
        val origin = DungeonPosition(helper.level.dimension(), Vec3(12.125, 75.5, -17.875), 123.5f, -35.25f)
        var failWrites = true
        val recovery = DungeonReturnRecovery(file, helper.level.registryAccess()) { target, tag ->
            if (failWrites) throw IOException("injected disk failure")
            DungeonDurableFiles.write(target, tag)
        }
        check(runCatching { recovery.remember(id, origin, key(), runId) }.isFailure)
        check(recovery.pending().isEmpty() && !Files.exists(file))
        failWrites = false
        recovery.remember(id, origin, key(), runId)
        val legacySnapshot = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
        val reloaded = DungeonReturnRecovery(file, helper.level.registryAccess())
        check(reloaded.pending()[id] == origin)
        check(reloaded.record(id)?.runId == runId)
        check(ItemStack.isSameItemSameComponents(requireNotNull(reloaded.record(id)?.key), key()))
        failWrites = true
        check(runCatching { recovery.forget(id) }.isFailure)
        check(recovery.pending()[id] == origin)
        check(DungeonReturnRecovery(file, helper.level.registryAccess()).pending()[id] == origin)
        failWrites = false
        recovery.forget(id)
        check(DungeonReturnRecovery(file, helper.level.registryAccess()).pending().isEmpty())
        // Existing Stage4/6 SavedData files remain readable.
        val old = legacySnapshot
        val data = old.getCompound("data")
        data.putInt("SchemaVersion", 1)
        data.getList("Returns", 10).getCompound(0).apply { remove("Run"); remove("Key") }
        DungeonDurableFiles.write(file, old)
        val legacy = DungeonReturnRecovery(file, helper.level.registryAccess())
        check(legacy.pending()[id] == origin && legacy.record(id)?.key == null)
        legacy.forget(id)

        val player = helper.makeMockServerPlayerInLevel()
        player.inventory.setItem(0, key(2))
        legacy.remember(player.uuid, origin, key())
        player.setPos(-35.5, 80.0, 27.25)
        var failCredit = false
        val rankingFile = dir.resolve("leaderboard.dat")
        val rankings = DungeonLeaderboard(rankingFile) { path, tag ->
            if (failCredit) throw IOException("Injected leaderboard credit failure")
            DungeonDurableFiles.write(path, tag)
        }
        val restarted = DungeonRuns(helper.level.server, DungeonReturnRecovery(file, helper.level.registryAccess()), { gems }, headless, rankings)
        try {
            check(restarted.active == null) // A crash never restores a stale global lock.
            restarted.rejoin(player)
            check(DungeonPosition.capture(player) == origin)
            check(player.inventory.getItem(0).count == 2 && restarted.recovery.pending().isEmpty())

            val uncommitted = PokemonProperties.parse("eevee level=5").create(player)
            restarted.recovery.remember(player.uuid, origin, key())
            restarted.recovery.preparePayout(player.uuid, uncommitted.uuid)
            check(Cobblemon.storage.getParty(player).add(uncommitted))
            restarted.rejoin(player)
            check(Cobblemon.storage.getParty(player).get(uncommitted.uuid) == null)
            check(player.inventory.getItem(0).count == 2)

            val committed = PokemonProperties.parse("eevee level=5").create(player)
            val committedRun = UUID.randomUUID()
            restarted.recovery.remember(player.uuid, origin, key(), committedRun)
            restarted.recovery.preparePayout(player.uuid, committed.uuid)
            check(DungeonPayoutCommit.grant(player, key(), RolledDungeonReward("rare", "Eevee", null, committed), committedRun))
            failCredit = true
            restarted.rejoin(player)
            check(DungeonPosition.capture(player) == origin) { "Leaderboard errors must not prevent return" }
            check(player.uuid in restarted.recovery.pending() && rankings.page(0, 8).entries.isEmpty())
            failCredit = false
            restarted.rejoin(player)
            check(Cobblemon.storage.getParty(player).get(committed.uuid) != null)
            check(player.inventory.getItem(0).count == 1 && restarted.recovery.pending().isEmpty())
            check(rankings.page(0, 8).entries.single().clears == 1L)
            check(DungeonLeaderboard(rankingFile).page(0, 8).entries.single().clears == 1L)
        } finally { restarted.close(); player.discard() }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun deathDropsProtectOnlyOneKeyAndRespawnDoesNotDuplicate(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        val runs = requireNotNull(DungeonRunService.runs)
        runs.recovery.remember(player.uuid, DungeonPosition.capture(player), key())
        try {
            player.inventory.setItem(0, key(3))
            player.inventory.setItem(1, ItemStack(Items.DIAMOND, 4))
            player.inventory.dropAll() // Exercises the actual registered Inventory mixin.
            check(player.inventory.getItem(0).`is`(DungeonItems.DUNGEON_KEY) && player.inventory.getItem(0).count == 1)
            check(player.inventory.getItem(1).isEmpty)
            val respawn = ServerPlayer(player.server, helper.level, player.gameProfile, ClientInformation.createDefault())
            respawn.connection = player.connection
            respawn.restoreFrom(player, false) // Exercises the actual ServerPlayer mixin.
            check((0 until respawn.inventory.containerSize).sumOf { if (respawn.inventory.getItem(it).`is`(DungeonItems.DUNGEON_KEY)) respawn.inventory.getItem(it).count else 0 } == 1)
            check(player.inventory.getItem(0).isEmpty)

            player.inventory.setItem(0, key(3))
            val keep = ServerPlayer(player.server, helper.level, player.gameProfile, ClientInformation.createDefault())
            keep.connection = player.connection
            keep.restoreFrom(player, true)
            check(keep.inventory.getItem(0).count == 3) // Keep-everything must not add a fourth.
            helper.succeed()
        } finally { runs.recovery.forget(player.uuid); player.discard() }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun failedPayoutCheckpointRollsBackKeyItemsAndPokemon(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        try {
            player.inventory.setItem(0, key(2))
            val receipt = player as DungeonPayoutReceipt
            val oldReceipt = receipt.mondungeonsGetReceipt()
            val itemPrize = DungeonPayout.roll(gems, player)
            check(runCatching { DungeonPayoutCommit.grant(player, key(), itemPrize, UUID.randomUUID()) {
                throw IOException("injected player save failure")
            } }.isFailure)
            check(player.inventory.getItem(0).count == 2 && player.inventory.getItem(1).isEmpty)
            check(receipt.mondungeonsGetReceipt() == oldReceipt)
            val pokemon = PokemonProperties.parse("eevee shiny=true level=5").create(player)
            val monPrize = RolledDungeonReward("jackpot", "Shiny Eevee", null, pokemon)
            check(runCatching { DungeonPayoutCommit.grant(player, key(), monPrize, UUID.randomUUID()) {
                throw IOException("injected player save failure")
            } }.isFailure)
            check(Cobblemon.storage.getParty(player).get(pokemon.uuid) == null)
            check(Cobblemon.storage.getPC(player).get(pokemon.uuid) == null)
            check(player.inventory.getItem(0).count == 2)

            val runId = UUID.randomUUID()
            check(DungeonPayoutCommit.grant(player, key(), DungeonPayout.roll(gems, player), runId))
            check(player.inventory.getItem(0).count == 1)
            val saved = NbtIo.readCompressed(player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve("${player.uuid}.dat"), NbtAccounter.unlimitedHeap())
            check(saved.getString("MonDungeonsPayoutReceipt") == runId.toString())
            check(!DungeonPayoutCommit.grant(player, key(), DungeonPayout.roll(gems, player), runId))
            check(player.inventory.getItem(0).count == 1)
            helper.succeed()
        } finally { player.discard() }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1000)
    fun losingAndUnreportedBattleEndReleaseActualRunLock(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        val runs = DungeonRuns(helper.level.server, journal(helper), { gems }, headless, leaderboard(helper))
        val pos = helper.absolutePos(BlockPos(1, 2, 1))
        player.setPos(pos.x + 80.5, pos.y.toDouble(), pos.z + 80.5)
        check(runs.setArena(player))
        player.setPos(pos.x + 0.125, pos.y + 0.5, pos.z + 0.875)
        player.yRot = 75f; player.xRot = -20f
        val origin = DungeonPosition.capture(player)
        player.inventory.setItem(0, key(2))
        repeat(6) { Cobblemon.storage.getParty(player).set(it, PokemonProperties.parse("garchomp level=50").create()) }
        fun start() {
            runs.start(player, runs.offer(player, net.minecraft.world.InteractionHand.MAIN_HAND, player.mainHandItem))
            check(runs.active?.playerUuid == player.uuid)
        }
        fun returned() {
            check(runs.active == null && runs.recovery.pending().isEmpty())
            check(DungeonPosition.capture(player) == origin)
            check(player.inventory.getItem(0).count == 2)
            check(runs.leaderboard.page(0, 8).totalPlayers == 0) { "Failed runs must not count as clears" }
        }
        fun cleanup() { runs.close(); player.discard() }
        var phase = 0
        var acted = false
        try { start() } catch (error: Throwable) { cleanup(); throw error }
        fun step() {
            helper.runAfterDelay(1) {
                try {
                    runs.tick()
                    if (runs.active == null) {
                        check(acted) { "Run failed before test battle phase $phase" }
                        returned()
                        phase++
                        if (phase == 3) {
                            start()
                            runs.disconnect(player.uuid)
                            check(runs.active == null && player.uuid in runs.recovery.pending())
                            runs.rejoin(player); returned()
                            start()
                            runs.close(); returned() // Server stop during the NPC introduction.
                            runs.close() // Idempotent cleanup.
                            cleanup(); helper.succeed()
                            return@runAfterDelay
                        }
                        acted = false
                        start() // Free retry; bounded native recall cleanup is allowed before battle.
                    }
                    val battle = BattleRegistry.getBattleByParticipatingPlayer(player)
                    if (!acted && battle != null) {
                        acted = true
                        val challenger = battle.actors.single { player.uuid in it.getPlayerUUIDs() }
                        when (phase) {
                            0 -> {
                                val trainer = battle.actors.single { player.uuid !in it.getPlayerUUIDs() }
                                CobblemonEvents.BATTLE_VICTORY.post(BattleVictoryEvent(battle, listOf(trainer), listOf(challenger), false))
                            }
                            1 -> battle.stop() // Forfeit/flee fallback: no victory event at all.
                            2 -> {
                                battle.forfeitedBy = challenger // Native persistent marker survives response clearing.
                            }
                        }
                    }
                    step()
                } catch (error: Throwable) { cleanup(); throw error }
            }
        }
        step()
    }
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 4000)
    fun everyTierEndsWithBossPaysOnceAndCountsOneClear(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        val payloads = mutableListOf<RaidPayoutPayload>()
        val display = object : DungeonPresentation {
            override fun available(player: ServerPlayer) = true
            override fun show(player: ServerPlayer, payload: RaidPayoutPayload) { payloads += payload }
        }
        val rankings = leaderboard(helper)
        val runs = DungeonRuns(helper.level.server, journal(helper), { gems }, display, rankings)
        val tiers = listOf("common" to 3, "rare" to 5, "epic" to 7, "legendary" to 10)
        var tierIndex = 0
        val pos = helper.absolutePos(BlockPos(1, 2, 1))
        player.setPos(pos.x - 120.5, pos.y.toDouble(), pos.z - 120.5)
        check(runs.setArena(player))
        player.setPos(pos.x + 0.25, pos.y + 0.25, pos.z + 0.75)
        val origin = DungeonPosition.capture(player)
        repeat(6) { Cobblemon.storage.getParty(player).set(it, PokemonProperties.parse("garchomp level=50").create()) }
        val won = mutableSetOf<UUID>()
        fun cleanup() { runs.close(); player.discard() }
        fun start() {
            payloads.clear(); won.clear(); player.inventory.clearContent()
            val stack = key(2).apply {
                set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { putString("dungeon_key_tier", tiers[tierIndex].first) }))
            }
            player.inventory.setItem(0, stack)
            if (tierIndex == 0) {
                val stale = runs.offer(player, net.minecraft.world.InteractionHand.MAIN_HAND, stack)
                try {
                    DungeonSettingsConfig.store.set("runs.roundsByTier.common", "4")
                    runs.start(player, stale)
                    check(runs.active == null && runs.recovery.pending().isEmpty()) { "An outdated menu must not enter a different-length run" }
                    check(player.mainHandItem.count == 2 && DungeonPosition.capture(player) == origin)
                } finally { DungeonSettingsConfig.store.set("runs.roundsByTier.common", "3") }
            }
            runs.start(player, runs.offer(player, net.minecraft.world.InteractionHand.MAIN_HAND, player.mainHandItem))
            check(runs.active != null)
        }
        try { start() } catch (error: Throwable) { cleanup(); throw error }
        fun step() {
            helper.runAfterDelay(1) {
                try {
                    runs.tick()
                    val battle = BattleRegistry.getBattleByParticipatingPlayer(player)
                    val expectedRounds = tiers[tierIndex].second
                    if (battle != null && won.add(battle.battleId)) {
                        if (won.size == expectedRounds) check(Cobblemon.storage.getParty(player).all { it.currentHealth == it.maxHealth }) {
                            "Every tier must heal immediately before its final boss"
                        }
                        val challenger = battle.actors.single { player.uuid in it.getPlayerUUIDs() }
                        val trainer = battle.actors.single { player.uuid !in it.getPlayerUUIDs() }
                        if (won.size == expectedRounds) {
                            check(trainer.pokemonList.size == 1)
                            val boss = trainer.pokemonList.single().effectedPokemon
                            check(boss.level == 50 && boss.scaleModifier == 3f && boss.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.HP] == 31)
                        } else check(trainer.pokemonList.all {
                            requireNotNull(it.effectedPokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.HP]) <= 15
                        }) { "Regular rounds must use regular-trainer stats" }
                        val victory = BattleVictoryEvent(battle, listOf(challenger), listOf(trainer), false)
                        CobblemonEvents.BATTLE_VICTORY.post(victory)
                        CobblemonEvents.BATTLE_VICTORY.post(victory)
                        battle.stop()
                        if (won.size == expectedRounds - 1) Cobblemon.storage.getParty(player).forEach { it.currentHealth = 1 }
                    }
                    if (payloads.lastOrNull()?.state == "granted") {
                        check(won.size == expectedRounds)
                        check(payloads.map { it.state } == listOf("spinning", "granted"))
                        check(runs.active == null && runs.recovery.pending().isEmpty())
                        check(DungeonPosition.capture(player) == origin)
                        check(player.inventory.getItem(0).count == 1)
                        check((0 until 36).sumOf { if (player.inventory.getItem(it).`is`(Items.DIAMOND)) player.inventory.getItem(it).count else 0 } == 4)
                        val total = rankings.page(0, 8).entries.single().clears
                        check(total == (tierIndex + 1).toLong())
                        runs.rejoin(player)
                        check(rankings.page(0, 8).entries.single().clears == total)
                        tierIndex++
                        if (tierIndex == tiers.size) { cleanup(); helper.succeed() }
                        else { start(); step() }
                    } else {
                        check(runs.active != null)
                        check(player.inventory.getItem(0).count == 2)
                        step()
                    }
                } catch (error: Throwable) {
                    org.slf4j.LoggerFactory.getLogger("mondungeons/tier-tests").error("Tier ${tiers[tierIndex].first}, won ${won.size} battles", error)
                    cleanup(); throw error
                }
            }
        }
        step()
    }
}
