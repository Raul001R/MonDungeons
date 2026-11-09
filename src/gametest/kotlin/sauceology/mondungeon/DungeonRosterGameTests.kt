package sauceology.mondungeon

import com.google.gson.JsonParser
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper

class DungeonRosterGameTests : FabricGameTest {
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
            team.forEach { pokemon ->
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
