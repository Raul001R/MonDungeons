package sauceology.mondungeon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.util.UUID
import kotlin.random.Random

fun checkDungeonSettings(base: DungeonRoundPlan, roster: DungeonEncounterRoster) {
    val defaults = DungeonSettings.parse(DungeonSettings.defaults())
    check(defaults.roundsByTier == mapOf("common" to 3, "rare" to 5, "epic" to 7, "legendary" to 10))
    defaults.roundsByTier.forEach { (tier, count) ->
        val plan = defaults.plan(tier, base)
        check(plan.rounds.size == count && plan.rounds.last() == base.rounds.last())
        check(plan.rounds.dropLast(1) == base.rounds.take(count - 1))
        (0..99).forEach { seed ->
            val selected = roster.select(plan, Random(seed))
            check(selected.rounds.last().boss != null && selected.rounds.dropLast(1).all { it.boss == null })
            check(count - 1 in selected.healAfterRounds)
            check(selected.healAfterRounds.all { it in 1 until count })
            val progress = DungeonRoundProgress(count, selected.healAfterRounds)
            repeat(count) { index ->
                val battle = UUID.randomUUID()
                progress.attach(battle)
                check(progress.victory(UUID.randomUUID()) == null)
                val result = requireNotNull(progress.victory(battle))
                check(result.complete == (index == count - 1))
                check(progress.victory(battle) == null)
            }
            check(progress.complete)
        }
    }
    val directory = Files.createTempDirectory("mondungeons-settings-checks-")
    val file = directory.resolve("dungeon-settings.json")
    val legacy = directory.resolve("dungeon-rounds.json")
    Files.writeString(legacy, """{"regularIvCap":12,"regularEvMultiplier":0.25}""")
    val store = DungeonSettingsStore(file, legacy) { candidate ->
        defaults.roundsByTier.keys.forEach { candidate.plan(it, base) }
    }
    store.reload()
    check(store.current.regularIvCap == 12 && store.current.regularEvMultiplier == 0.25)
    check(Files.readString(legacy) == """{"regularIvCap":12,"regularEvMultiplier":0.25}""")
    val frozen = store.current.plan("common", base)
    store.set("runs.roundsByTier.common", "4")
    store.set("trainers.regularIvCap", "10")
    check(store.current.plan("common", base).rounds.size == 4 && frozen.rounds.size == 3 && frozen.regularIvCap == 12)
    check(DungeonSettingsStore(file, legacy).reload() == store.current)
    val goodFile = Files.readString(file)
    val goodSettings = store.current
    listOf("runs.roundsByTier.common" to "0", "runs.roundsByTier.common" to "11",
        "runs.roundsByTier.common" to "3.5", "runs.roundsByTier.common" to "\"3\"",
        "trainers.regularIvCap" to "32", "trainers.regularEvMultiplier" to "-0.1",
        "trainers.regularEvMultiplier" to "NaN", "leaderboard.pageSize" to "11",
        "runs.typo.common" to "3").forEach { (path, value) ->
        check(runCatching { store.set(path, value) }.isFailure) { "$path=$value should fail" }
        check(Files.readString(file) == goodFile && store.current == goodSettings)
    }
    Files.writeString(file, """{"runs":{"roundsByTier":{"common":0}}}""")
    val invalid = Files.readString(file)
    check(runCatching { store.reload() }.isFailure)
    check(store.current == goodSettings && Files.readString(file) == invalid)
    Files.writeString(file, """{"trainers":{"regularIvCap":8}}""")
    store.reload()
    check(store.current.regularIvCap == 8 && store.current.roundsByTier == defaults.roundsByTier)
    val partial = JsonParser.parseString(Files.readString(file)).asJsonObject
    check(partial.has("leaderboard") && partial.getAsJsonObject("trainers").has("regularEvMultiplier"))
    partial.getAsJsonObject("trainers").addProperty("typo", 1)
    Files.writeString(file, partial.toString())
    check(runCatching { store.reload() }.isFailure)
    check(store.current.regularIvCap == 8 && Files.readString(file) == partial.toString())
    println("Tier/settings checks passed: 3/5/7/10 rounds, final bosses/heals, 400 seeded runs, upgrade, persistence, frozen plans and invalid edits")
}
