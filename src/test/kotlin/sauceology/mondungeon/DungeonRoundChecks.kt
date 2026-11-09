package sauceology.mondungeon

import com.google.gson.JsonParser
import java.util.UUID

fun main() {
    val root = requireNotNull(DungeonRoundPlan::class.java.getResourceAsStream("/dungeon-rounds.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    val plan = DungeonRoundPlan.parse(root)
    check(plan.rounds.size == 10)
    check(plan.healAfterRounds == setOf(2, 4, 6, 8, 9))
    check(plan.rounds.any { it.format == "doubles" } && plan.rounds.any { it.format == "singles" })
    check(plan.rounds.last().level == 50 && plan.rounds.last().boss!!.get("scale").asFloat > 1)
    val progress = DungeonRoundProgress(plan.rounds.size, plan.healAfterRounds)
    val heals = mutableListOf<Int>()
    var previousId = UUID.randomUUID()
    for (round in 1..10) {
        check(progress.currentRound == round)
        val id = UUID.randomUUID()
        progress.attach(id)
        check(progress.victory(previousId) == null) { "Old/unrelated battle must not advance" }
        check(progress.expectedBattleId == id)
        val result = requireNotNull(progress.victory(id))
        check(progress.victory(id) == null) { "Duplicate victory must be ignored" }
        if (result.heal) heals += result.finishedRound
        check(result.complete == (round == 10))
        previousId = id
    }
    check(heals == listOf(2, 4, 6, 8, 9))
    check(progress.complete)
    check(runCatching { progress.attach(UUID.randomUUID()) }.isFailure)
    val retry = DungeonRoundProgress(10, plan.healAfterRounds)
    check(retry.currentRound == 1 && retry.victory(previousId) == null)
    val badOdds = root.deepCopy().apply { add("healAfterRounds", JsonParser.parseString("[10]")) }
    check(runCatching { DungeonRoundPlan.parse(badOdds) }.isFailure)
    val badFormat = root.deepCopy().apply { getAsJsonArray("rounds")[0].asJsonObject.addProperty("format", "invented") }
    check(runCatching { DungeonRoundPlan.parse(badFormat) }.isFailure)
    val badBoss = root.deepCopy().apply { getAsJsonArray("rounds")[9].asJsonObject.getAsJsonObject("boss").addProperty("scale", 1) }
    check(runCatching { DungeonRoundPlan.parse(badBoss) }.isFailure)
    val rosterJson = requireNotNull(DungeonEncounterRoster::class.java.getResourceAsStream("/dungeon-trainers.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    val roster = DungeonEncounterRoster.parse(rosterJson)
    val selections = (0..99).map { roster.select(plan, kotlin.random.Random(it)) }
    selections.forEach { selected ->
        check(selected.rounds.size == plan.rounds.size && selected.healAfterRounds == plan.healAfterRounds + 9)
        check(selected.rounds.filter { it.boss == null }.map { it.theme }.distinct().size == 9)
        selected.rounds.zip(plan.rounds).forEach { (round, original) ->
            check(round.level == original.level && round.format == original.format && round.skill == original.skill)
            check(round.teamSlots.size == original.teamSlots.size && round.teamSlots.distinct() == round.teamSlots)
            check(round.theme in DungeonEncounterRoster.TYPES && round.dialogue.intro.isNotEmpty())
            if (round.boss == null) check(round.name.startsWith(round.theme!!.replaceFirstChar { it.uppercase() }))
            else check(round.boss.get("scale") == original.boss!!.get("scale"))
        }
    }
    check(selections.map { it.rounds.map { round -> Triple(round.theme, round.trainerId, round.teamSlots) } }.distinct().size > 90)
    check(selections.map { it.rounds.last().name }.distinct().size == roster.bosses.size)
    check(roster.select(plan, kotlin.random.Random(42)) == roster.select(plan, kotlin.random.Random(42)))
    val oldHealPlan = plan.copy(healAfterRounds = setOf(2, 4, 6, 8))
    check(9 in roster.select(oldHealPlan, kotlin.random.Random(1)).healAfterRounds)
    val disabled = DungeonEncounterRoster.parse(rosterJson.deepCopy().apply { addProperty("enabled", false) }).select(plan)
    check(disabled.rounds.map { it.trainerId to it.teamSlots } == plan.rounds.map { it.trainerId to it.teamSlots })
    val invalidTheme = rosterJson.deepCopy().apply { getAsJsonArray("themes")[0].asJsonObject.addProperty("id", "madeup") }
    check(runCatching { DungeonEncounterRoster.parse(invalidTheme) }.isFailure)
    val moveJson = requireNotNull(DungeonMoveBalance::class.java.getResourceAsStream("/dungeon-moves.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    val balance = DungeonMoveBalance.parse(moveJson)
    check(balance.enabled && balance.regularStrongMovePp < balance.bossStrongMovePp)
    check(runCatching { DungeonMoveBalance.parse(moveJson.deepCopy().apply { addProperty("regularStrongMovePp", 0) }) }.isFailure)
    println("Dungeon round checks passed: ten rounds, ID filtering, duplicates, healing after 2/4/6/8/9, boss and config validation")
    println("Roster checks passed: 100 seeded runs, unique typed themes, varied teams/bosses, preserved difficulty and config validation")
}
