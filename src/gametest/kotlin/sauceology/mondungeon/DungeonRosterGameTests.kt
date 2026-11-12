package sauceology.mondungeon

import com.google.gson.JsonParser
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper

class DungeonRosterGameTests : FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun leaderboardPersistsRanksPagesAndNeverDuplicatesCredit(helper: GameTestHelper) {
        val directory = java.nio.file.Files.createTempDirectory(helper.level.server.getWorldPath(
            net.minecraft.world.level.storage.LevelResource.ROOT), "leaderboard-checks-")
        val file = directory.resolve("clears.dat")
        var failWrites = false
        val leaderboard = DungeonLeaderboard(file) { path, tag ->
            if (failWrites) throw java.io.IOException("Injected leaderboard write failure")
            DungeonDurableFiles.write(path, tag)
        }
        check(leaderboard.page(0, 8).entries.isEmpty())
        val alice = java.util.UUID.randomUUID()
        val bob = java.util.UUID.randomUUID()
        val firstRun = java.util.UUID.randomUUID()
        leaderboard.record(alice, "Alice", firstRun)
        leaderboard.record(alice, "Alice", firstRun)
        check(leaderboard.page(0, 8).entries.single().clears == 1L)
        leaderboard.record(bob, "Bob", java.util.UUID.randomUUID())
        check(leaderboard.page(0, 8).entries.map { it.name } == listOf("Alice", "Bob"))
        leaderboard.record(bob, "NewBob", java.util.UUID.randomUUID())
        check(leaderboard.page(0, 8).entries.first() == RaidLeaderboardEntry("NewBob", 2))
        val reloaded = DungeonLeaderboard(file)
        check(reloaded.page(0, 8) == leaderboard.page(0, 8))
        reloaded.record(alice, "Alice", firstRun)
        check(reloaded.page(0, 8) == leaderboard.page(0, 8))
        failWrites = true
        check(runCatching { leaderboard.record(alice, "Alice", java.util.UUID.randomUUID()) }.isFailure)
        check(DungeonLeaderboard(file).page(0, 8) == leaderboard.page(0, 8))
        failWrites = false
        repeat(11) { leaderboard.record(java.util.UUID.randomUUID(), "Player$it", java.util.UUID.randomUUID()) }
        val first = leaderboard.page(-20, 8)
        val last = leaderboard.page(Int.MAX_VALUE, 8)
        check(first.page == 0 && first.totalPlayers == 13 && first.entries.size == 8)
        check(last.page == 1 && last.entries.size == 5)
        check(first.entries.first().name == "NewBob")
        check((first.entries + last.entries).map { it.name }.distinct().size == 13)
        val buffer = net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.level.registryAccess())
        try {
            RaidLeaderboardPayload.CODEC.encode(buffer, last)
            check(RaidLeaderboardPayload.CODEC.decode(buffer) == last)
            val request = RaidLeaderboardRequest(Int.MAX_VALUE)
            RaidLeaderboardRequest.CODEC.encode(buffer, request)
            check(RaidLeaderboardRequest.CODEC.decode(buffer) == request)
        } finally { buffer.release() }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun consoleSettingsPersistValidateAndRequireOperator(helper: GameTestHelper) {
        val file = net.fabricmc.loader.api.FabricLoader.getInstance().configDir.resolve("mondungeons/dungeon-settings.json")
        val original = java.nio.file.Files.readString(file)
        val store = DungeonSettingsConfig.store
        val dispatcher = helper.level.server.commands.dispatcher
        val source = helper.level.server.createCommandSourceStack().withSuppressedOutput()
        try {
            val frozen = store.current.plan("common", DungeonRounds.load())
            check(dispatcher.execute("mondungeons config get runs.roundsByTier.common", source) == 1)
            check(dispatcher.execute("mondungeons config set runs.roundsByTier.common 4", source) == 1)
            check(store.current.plan("common", DungeonRounds.load()).rounds.size == 4 && frozen.rounds.size == 3)
            val persisted = java.nio.file.Files.readString(file)
            check(JsonParser.parseString(persisted).asJsonObject.getAsJsonObject("runs").getAsJsonObject("roundsByTier").get("common").asInt == 4)
            check(dispatcher.execute("monDungeons config set runs.roundsByTier.common 11", source) == 0)
            check(java.nio.file.Files.readString(file) == persisted && store.current.roundsByTier.getValue("common") == 4)
            check(dispatcher.execute("mondungeons config set trainers.regularEvMultiplier 0.25", source) == 1)
            check(store.current.regularEvMultiplier == 0.25)
            check(runCatching { dispatcher.execute("mondungeons config set trainers.regularIvCap 1", source.withPermission(0)) }.isFailure)
            java.nio.file.Files.writeString(file, original)
            check(dispatcher.execute("mondungeons config reload", source) == 1)
            check(store.current.roundsByTier.getValue("common") == 3)
        } finally {
            java.nio.file.Files.writeString(file, original)
            store.reload()
        }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    fun increasedChestOddsAndExistingChestInsertion(helper: GameTestHelper) {
        val params = net.minecraft.world.level.storage.loot.LootParams.Builder(helper.level)
            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(net.minecraft.core.BlockPos(1, 2, 1))))
            .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST)
        val key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
            net.minecraft.resources.ResourceLocation.parse("minecraft:chests/simple_dungeon"))
        val table = helper.level.server.reloadableRegistries().getLootTable(key)
        var common = 0
        repeat(5000) { seed ->
            val keys = table.getRandomItems(params, seed.toLong() + 1).filter { it.`is`(DungeonItems.DUNGEON_KEY) }
            check(keys.size <= 1)
            if (keys.singleOrNull()?.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)?.copyTag()
                    ?.getString("dungeon_key_tier") == "common") common++
        }
        check(common in 400..600) { "Expected about10% common keys, got $common/5000" }
        val relative = net.minecraft.core.BlockPos(1, 2, 1)
        helper.setBlock(relative, net.minecraft.world.level.block.Blocks.CHEST)
        val pos = helper.absolutePos(relative)
        val chest = helper.level.getBlockEntity(pos) as net.minecraft.world.level.block.entity.ChestBlockEntity
        chest.setItem(0, net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7))
        helper.level.server.commands.dispatcher.execute(
            "loot insert ${pos.x} ${pos.y} ${pos.z} loot mondungeons:inject/dungeon_key",
            helper.level.server.createCommandSourceStack().withSuppressedOutput())
        check(chest.getItem(0).`is`(net.minecraft.world.item.Items.DIAMOND) && chest.getItem(0).count == 7)
        check((0 until chest.containerSize).sumOf { if (chest.getItem(it).`is`(DungeonItems.DUNGEON_KEY)) chest.getItem(it).count else 0 } == 1)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    fun everyTrainerAndBossMatchesItsTypeAndMovePolicy(helper: GameTestHelper) {
        fun json(name: String) = requireNotNull(javaClass.getResourceAsStream("/$name"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val roster = DungeonEncounterRoster.parse(json("dungeon-trainers.json"))
        val base = DungeonRoundPlan.parse(json("dungeon-rounds.json"))
        val policy = DungeonMoveBalance.load()
        val normal = roster.themes.flatMap { theme -> theme.trainers.map { trainer ->
            base.rounds.first().copy(name = trainer.name, trainerId = trainer.trainerId, teamSlots = trainer.slots,
                appearance = trainer.appearance, theme = theme.id)
        } }
        val bosses = roster.bosses.map { option -> base.rounds.last().copy(name = option.trainer.name,
            trainerId = option.trainer.trainerId, teamSlots = option.trainer.slots, theme = option.type,
            appearance = option.trainer.appearance,
            boss = base.rounds.last().boss!!.deepCopy().apply { add("moves", option.moves); add("evs", option.evs) }) }
        val rounds = normal + bosses
        val fallbackData = json("assets/mondungeons/npc_skin_fallbacks.json").getAsJsonObject("appearances")
        rounds.map { it.appearance }.distinct().forEach { appearance ->
            val path = appearance.replace(':', '/')
            val variant = json("assets/${path.substringBefore('/')}/bedrock/npcs/variations/${path.substringAfter('/')}.json")
                .getAsJsonArray("variations")[0].asJsonObject
            check(variant.get("model").asString == "cobblemon:steve.geo")
            check(fallbackData.has(appearance))
            val texture = net.minecraft.resources.ResourceLocation.parse(variant.get("texture").asString)
            val texturePath = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(texture.namespace).orElseThrow()
                .findPath("assets/${texture.namespace}/${texture.path}").orElseThrow()
            val png = java.nio.ByteBuffer.wrap(java.nio.file.Files.readAllBytes(texturePath))
            check(png.getInt(16) == 64 && png.getInt(20) == 64) { "Player model requires a 64x64 skin: $texture" }
        }
        val errors = mutableListOf<String>()
        val teams = rounds.map { round ->
            runCatching { RctDungeonTeams.prepare(helper.level.server, base.copy(rounds = listOf(round))).single() }
                .getOrElse { errors += "${round.trainerId}/${round.theme}: ${it.message}"; emptyList() }
        }
        check(errors.isEmpty()) { errors.joinToString("; ") }
        teams.zip(rounds).forEach { (team, round) ->
            check(team.size == round.teamSlots.size)
            val sourceId = net.minecraft.resources.ResourceLocation.parse("rctmod:trainers/${round.trainerId}.json")
            val source = helper.level.server.resourceManager.getResource(sourceId).orElseThrow().openAsReader()
                .use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("team") }
            val stats = mapOf("hp" to com.cobblemon.mod.common.api.pokemon.stats.Stats.HP,
                "atk" to com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK,
                "def" to com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE,
                "spa" to com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK,
                "spd" to com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE,
                "spe" to com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED)
            team.forEachIndexed { index, pokemon ->
                val original = source[round.teamSlots[index]].asJsonObject
                stats.forEach { (key, stat) ->
                    if (round.boss == null) {
                        val sourceIv = original.getAsJsonObject("ivs")?.get(key)?.asInt ?: 0
                        val sourceEv = original.getAsJsonObject("evs")?.get(key)?.asInt ?: 0
                        check(pokemon.ivs[stat] == minOf(sourceIv, base.regularIvCap)) { "${round.name}: wrong $key IV" }
                        check(pokemon.evs[stat] == (sourceEv * base.regularEvMultiplier).toInt()) { "${round.name}: wrong $key EV" }
                    } else {
                        check(pokemon.ivs[stat] == 31) { "${round.name}: boss must keep perfect IVs" }
                        check(pokemon.evs[stat] == (round.boss.getAsJsonObject("evs").get(key)?.asInt ?: 0)) {
                            "${round.name}: boss EV spread changed"
                        }
                    }
                }
                check(pokemon.currentHealth == pokemon.maxHealth) { "Stat changes must leave NPCs at full health" }
                check(pokemon.types.any { it.name.equals(round.theme, true) }) { "${round.name}: wrong type ${pokemon.species.name}" }
                val moves = pokemon.moveSet.getMoveTemplates()
                check(moves.isNotEmpty() && moves.map { it.name }.distinct().size == moves.size)
                if (round.boss == null && policy.enabled) {
                    check(moves.all { it.name !in policy.blocked })
                    check(moves.all { it.power <= policy.regularMaxPower })
                    check(moves.count { it.power == 0.0 } <= 1)
                    check(moves.all { it in pokemon.form.moves.getAllLegalMoves() })
                    pokemon.moveSet.filter { it.power >= policy.strongMovePower }.forEach {
                        check(it.currentPp <= policy.regularStrongMovePp)
                    }
                } else if (round.boss != null) {
                    check(moves.map { it.name }.toSet() == round.boss.getAsJsonArray("moves").map { it.asString }.toSet()) {
                        "${round.name}: expected ${round.boss.getAsJsonArray("moves")}, got ${moves.map { it.name }}"
                    }
                    check(moves.all { it in pokemon.form.moves.getAllLegalMoves() }) { "Illegal boss move on ${pokemon.species.name}" }
                    pokemon.moveSet.filter { it.power >= policy.strongMovePower }.forEach {
                        check(it.currentPp <= policy.bossStrongMovePp)
                    }
                }
            }
        }
        // The original reported mismatch must be refused before admission/teleport, not quietly fielded.
        val invalid = base.rounds.first().copy(name = "Dragon", trainerId = "elite_four_lance_0057",
            teamSlots = listOf(2), theme = "dragon") // Melmetal is pure Steel.
        check(runCatching { RctDungeonTeams.prepare(helper.level.server, base.copy(rounds = listOf(invalid))) }.isFailure)
        // Dragon/Steel remains eligible.
        val dual = invalid.copy(teamSlots = listOf(4)) // Dialga.
        check(RctDungeonTeams.prepare(helper.level.server, base.copy(rounds = listOf(dual))).single().single().types.any { it.name.equals("dragon", true) })
        helper.succeed()
    }
}
