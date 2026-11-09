package sauceology.mondungeon

import com.google.gson.JsonParser
import java.util.UUID

fun main() {
    val root = requireNotNull(DungeonRoundPlan::class.java.getResourceAsStream("/dungeon-rounds.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    val plan = DungeonRoundPlan.parse(root)
    check(plan.rounds.size == 10)
    check(plan.healAfterRounds == setOf(2, 4, 6, 8))
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
    check(heals == listOf(2, 4, 6, 8))
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
    println("Dungeon round checks passed: ten rounds, ID filtering, duplicates, healing after 2/4/6/8, boss and config validation")
}
