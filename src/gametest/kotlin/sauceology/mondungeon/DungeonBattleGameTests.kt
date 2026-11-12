package sauceology.mondungeon

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.battles.BattleRegistry
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper

class DungeonBattleGameTests : FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun payoutDeliversExactlyOnceAndKeepsKeysOnFailure(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        fun key(count: Int = 1) = net.minecraft.world.item.ItemStack(DungeonItems.DUNGEON_KEY, count).apply {
            set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(net.minecraft.nbt.CompoundTag().apply {
                    putString("dungeon_key_tier", "rare")
                }))
        }
        fun table(json: String) = DungeonRewardTable(MonDungeons.id("test/payout"), listOf(
            DungeonReward(com.google.gson.JsonParser.parseString(json).asJsonObject, 1.0, "jackpot")), emptyList())
        try {
            val originalKey = key()
            player.inventory.setItem(0, key(2))
            val items = table("""{"type":"item","items":["minecraft:diamond","minecraft:emerald"],"count":4,"label":"Gems"}""")
            DungeonPayout.validate(items)
            val prize = DungeonPayout.roll(items, player)
            check(player.inventory.getItem(0).count == 2) // Rolling/spinning never consumes.
            check(prize.grant(player, originalKey))
            check(player.inventory.getItem(0).count == 1)
            check((0 until 36).sumOf { if (player.inventory.getItem(it).`is`(prize.item!!.item)) player.inventory.getItem(it).count else 0 } == 4)
            check(!prize.grant(player, originalKey))
            check(player.inventory.getItem(0).count == 1)

            // A full inventory with a stacked key cannot receive a different stack.
            repeat(36) { player.inventory.setItem(it, net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64)) }
            player.inventory.setItem(0, key(2))
            val blocked = DungeonPayout.roll(items, player)
            check(!blocked.grant(player, originalKey))
            check(player.inventory.getItem(0).count == 2)
            // A single key frees its own slot, so delivery remains possible when otherwise full.
            player.inventory.setItem(0, key())
            check(blocked.grant(player, originalKey))
            check(player.inventory.getItem(0).count == 4)

            player.inventory.clearContent()
            player.inventory.setItem(40, key()) // Moved offhand keys still work.
            val shiny = table("""{"type":"pokemon","spec":"random shiny=true","label":"Shiny"}""")
            DungeonPayout.validate(shiny)
            val pokemonPrize = DungeonPayout.roll(shiny, player)
            check(requireNotNull(pokemonPrize.pokemon).shiny)
            check(pokemonPrize.grant(player, originalKey))
            check(Cobblemon.storage.getParty(player).any { it.uuid == pokemonPrize.pokemon.uuid })
            check(player.inventory.getItem(40).isEmpty)

            val party = Cobblemon.storage.getParty(player)
            repeat(6) { party.set(it, PokemonProperties.parse("eevee level=5").create()) }
            player.inventory.setItem(0, key())
            val overflow = DungeonPayout.roll(shiny, player)
            check(overflow.grant(player, originalKey))
            check(Cobblemon.storage.getPC(player).any { it.uuid == overflow.pokemon!!.uuid })
            check(party.count() == 6 && player.inventory.getItem(0).isEmpty)

            val missing = DungeonPayout.roll(items, player)
            check(!missing.grant(player, originalKey)) // Never gives a free prize without a key.
            val tag = table("""{"type":"item","itemTag":"minecraft:planks","count":2}""")
            DungeonPayout.validate(tag)
            check(DungeonPayout.itemChoices(tag.rewards.single().definition).size > 1)
            val bad = table("""{"type":"item","items":["minecraft:diamond","TODO:missing"],"count":1}""")
            check(runCatching { DungeonPayout.validate(bad) }.isFailure)
            val invalidTag = table("""{"type":"item","itemTag":"mondungeons:missing","count":1}""")
            check(runCatching { DungeonPayout.validate(invalidTag) }.isFailure)

            DungeonLootData.tables.values.forEach { source ->
                var lower = 0.0
                source.rewards.filter { it.probability > 0 }.forEach { entry ->
                    check(DungeonPayout.select(source, lower + entry.probability / 2) === entry)
                    lower += entry.probability
                }
            }
            val payload = RaidPayoutPayload("Shiny Garchomp", "jackpot", 100, "spinning", listOf("Diamond", "Shiny"))
            val buffer = net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.level.registryAccess())
            try { RaidPayoutPayload.CODEC.encode(buffer, payload); check(RaidPayoutPayload.CODEC.decode(buffer) == payload) }
            finally { buffer.release() }
            val matches = net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().filter {
                it.namespace in setOf("mega_showdown", "zamega", "cobblemon", "cobblefurnies") &&
                    Regex("wishing|crystal|stone|shard|scrap|snack|fossil|bottle|ability|candy").containsMatchIn(it.path)
            }
            org.slf4j.LoggerFactory.getLogger("mondungeons/reward-registry").info("Installed reward candidates: {}", matches)
            org.slf4j.LoggerFactory.getLogger("mondungeons/reward-registry").info("Unresolved default prizes: {}", DungeonPayout.problems(DungeonLootData.tables.values.first()))
            helper.succeed()
        } finally { player.discard() }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun commandTreeUsesMonDungeons(helper: GameTestHelper) {
        val commands = helper.level.server.commands.dispatcher.root
        val root = requireNotNull(commands.getChild("monDungeons"))
        check(root.getChild("exit") != null && root.getChild("setarena") != null)
        check(commands.getChild("mondungeons")?.redirect === root)
        check(commands.getChild("raiddungeon") == null)
        check(commands.getChild("setdungeon") != null)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun tierOddsAndMenuPayloadUseTheConfiguredSource(helper: GameTestHelper) {
        val configPath = net.fabricmc.loader.api.FabricLoader.getInstance().configDir.resolve("mondungeons/dungeon-loot.json")
        // Initialize the same data used by the server's odds screen and eventual payout.
        val tiers = DungeonLootData.tiers
        val source = java.nio.file.Files.newBufferedReader(configPath).use {
            com.google.gson.JsonParser.parseReader(it).asJsonObject
        }
        tiers.values.forEach { tier ->
            val table = DungeonLootData.tables.getValue(tier.lootTableId)
            val configured = source.getAsJsonObject("keyTiers").getAsJsonObject(tier.id).getAsJsonObject("tierOdds")
            check(table.groups.map { it.id }.toSet() == setOf("jackpot", "rare", "uncommon", "common"))
            table.groups.forEach { group ->
                check(kotlin.math.abs(group.probability - configured.get(group.id).asDouble / 100) < 0.000000001)
                val expectedLabels = source.getAsJsonObject("rewardTiers").getAsJsonArray(group.id).map { it.asJsonObject.get("label").asString }
                check(group.examples == expectedLabels)
            }
            check(kotlin.math.abs(table.rewards.sumOf { it.probability } - 1) < 0.000000001)
            for (occupied in listOf(false, true)) {
                val entries = RaidRewardMenu.entries(table)
                check(entries.sumOf { it.options.size } == table.rewards.size)
                check(entries.single { it.id == "jackpot" }.options.any { it.kind == "random_shiny" })
                entries.forEach { group -> check(group.examples.size == group.options.size) }
                val payload = RaidOddsPayload(tier.id, "Custom ${tier.id} key", entries,
                    "bound-start-token", occupied, DungeonRounds.load().rounds.size)
                val buffer = net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.level.registryAccess())
                try {
                    RaidOddsPayload.CODEC.encode(buffer, payload)
                    check(RaidOddsPayload.CODEC.decode(buffer) == payload)
                    val availability = RaidAvailabilityPayload(occupied)
                    RaidAvailabilityPayload.CODEC.encode(buffer, availability)
                    check(RaidAvailabilityPayload.CODEC.decode(buffer) == availability)
                } finally { buffer.release() }
            }
        }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun installedRctTeamsAndOversizedBoss(helper: GameTestHelper) {
        val plan = DungeonRounds.load()
        val teams = RctDungeonTeams.prepare(helper.level.server, plan)
        check(teams.size == 10)
        check(plan.rounds.map { it.appearance }.distinct().size == 10) { "Default rounds must use a varied trainer cast" }
        plan.rounds.forEach { round ->
            val appearance = net.minecraft.resources.ResourceLocation.parse(round.appearance)
            val variation = requireNotNull(javaClass.getResourceAsStream("/assets/${appearance.namespace}/bedrock/npcs/variations/${appearance.path}.json"))
                .bufferedReader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
            check(variation.get("name").asString == round.appearance)
            val texture = net.minecraft.resources.ResourceLocation.parse(variation.getAsJsonArray("variations")[0].asJsonObject.get("texture").asString)
            check(net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(texture.namespace).orElseThrow()
                .findPath("assets/${texture.namespace}/${texture.path}").isPresent) { "Trainer skin missing: $texture" }
        }
        teams.forEachIndexed { index, team ->
            check(team.size == plan.rounds[index].teamSlots.size)
            check(team.all { it.level == plan.rounds[index].level })
        }
        val boss = teams.last().single()
        check(boss.species.name.equals("garchomp", true))
        check(boss.scaleModifier == 3f && boss.level == 50)
        check(boss.ivs[Stats.HP] == 31 && boss.ivs[Stats.ATTACK] == 31)
        check(boss.evs[Stats.HP] == 252 && boss.evs[Stats.ATTACK] == 252)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 600)
    fun tenNativeRoundsPreserveAttritionAndHealEveryOtherRound(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        val party = Cobblemon.storage.getParty(player)
        repeat(6) { party.set(it, PokemonProperties.parse("garchomp level=50").create()) }
        val pos = helper.absolutePos(BlockPos(1, 2, 1))
        player.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        val roundsStarted = mutableListOf<Int>()
        var complete = false
        var failed = false
        val flow = DungeonGauntlet(helper.level.server, { _, round -> roundsStarted += round },
            { complete = true }, { failed = true }, { if (it == player.uuid) player else null })
        val plan = DungeonRounds.load().copy(interRoundDelayTicks = 1, challengeDelayTicks = 2)
        val prepared = PreparedDungeon(plan, RctDungeonTeams.prepare(helper.level.server, plan))
        fun cleanup() { flow.close(); player.discard() }
        try {
            // No automatic heal on entry: the player's actual party begins and stays injured.
            party.forEach { it.currentHealth = 1 }
            flow.start(player, prepared)
            check(party.all { it.currentHealth == 1 })
            check(BattleRegistry.getBattleByParticipatingPlayer(player) == null) { "Trainer must spawn before issuing the battle" }
            val spawned = helper.level.getEntitiesOfClass(com.cobblemon.mod.common.entity.npc.NPCEntity::class.java,
                net.minecraft.world.phys.AABB(pos).inflate(12.0)).single { DungeonTrainer.TEMPORARY_TAG in it.tags }
            check(spawned.npc.id == MonDungeons.id("dungeon_trainer")) { "Client must receive a registered NPC class" }
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(spawned, helper.level)
            check(!spawned.isRemoved) { "Delayed entity tracking must not delete the current challenger" }
            check(spawned.forcedResourceIdentifier.toString() == plan.rounds.first().appearance)
            check(com.cobblemon.mod.common.api.npc.NPCClasses.getByIdentifier(spawned.npc.id) != null)
        } catch (exception: Throwable) { cleanup(); throw exception }
        var round = 1
        fun next() {
            helper.runAfterDelay(4) {
                try {
                    check(!failed)
                    check(roundsStarted.last() == round)
                    flow.tick() // The visible introduction has elapsed; the NPC now initiates battle.
                    val battle = requireNotNull(BattleRegistry.getBattleByParticipatingPlayer(player))
                    val challenger = battle.actors.single { player.uuid in it.getPlayerUUIDs() }
                    val trainer = battle.actors.single { player.uuid !in it.getPlayerUUIDs() }
                    check(trainer.pokemonList.all { it.effectedPokemon.level == plan.rounds[round - 1].level })
                    val movePolicy = DungeonMoveBalance.load()
                    trainer.pokemonList.forEach { opponent ->
                        opponent.effectedPokemon.moveSet.filter { it.power >= movePolicy.strongMovePower }.forEach {
                            check(it.currentPp <= if (round == 10) movePolicy.bossStrongMovePp else movePolicy.regularStrongMovePp) {
                                "Native battle start reset the dungeon PP limit"
                            }
                        }
                    }
                    check(battle.format.battleType == (if (plan.rounds[round - 1].format == "doubles")
                        com.cobblemon.mod.common.battles.BattleFormat.GEN_9_DOUBLES.battleType else
                        com.cobblemon.mod.common.battles.BattleFormat.GEN_9_SINGLES.battleType))
                    val win = BattleVictoryEvent(battle, listOf(challenger), listOf(trainer), false)
                    CobblemonEvents.BATTLE_VICTORY.post(win)
                    CobblemonEvents.BATTLE_VICTORY.post(win) // Duplicate must not skip a round or heal twice.
                    battle.stop()
                    party.forEach { it.currentHealth = 1 }
                    helper.runAfterDelay(4) {
                        try {
                            flow.tick()
                            check(!failed)
                            if (round in plan.healAfterRounds) check(party.all { it.currentHealth == it.maxHealth })
                            else check(party.all { it.currentHealth == 1 })
                            if (round == 10) {
                                check(complete && roundsStarted == (1..10).toList())
                                cleanup()
                                helper.succeed()
                            } else { round++; next() }
                        } catch (exception: Throwable) { cleanup(); throw exception }
                    }
                } catch (exception: Throwable) { cleanup(); throw exception }
            }
        }
        next()
    }
}
