package sauceology.mondungeon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

data class DungeonRound(val name: String, val trainerId: String, val format: String, val level: Int,
                        val teamSlots: List<Int>, val skill: Int, val boss: JsonObject?, val appearance: String,
                        val theme: String? = null, val dialogue: DungeonDialogue = DungeonDialogue())

data class DungeonRoundPlan(val rounds: List<DungeonRound>, val healAfterRounds: Set<Int>,
                            val interRoundDelayTicks: Int, val battleEndGraceTicks: Int,
                            val transitionTimeoutTicks: Int, val offsetX: Double, val offsetY: Double, val offsetZ: Double,
                            val challengeDelayTicks: Int, val regularIvCap: Int, val regularEvMultiplier: Double) {
    companion object {
        fun parse(root: JsonObject): DungeonRoundPlan {
            val cap = root.get("maxTrainerLevel").asInt
            require(cap in 1..100)
            val regularIvCap = (root.get("regularIvCap") ?: bundledDefaults.get("regularIvCap")).asBigDecimal.intValueExact()
            require(regularIvCap in 0..31) { "regularIvCap must be between 0 and 31" }
            val regularEvMultiplier = (root.get("regularEvMultiplier") ?: bundledDefaults.get("regularEvMultiplier")).asDouble
            require(regularEvMultiplier.isFinite() && regularEvMultiplier in 0.0..1.0) {
                "regularEvMultiplier must be between 0 and 1"
            }
            val appearances = root.getAsJsonObject("trainerAppearances") ?: bundledDefaults.getAsJsonObject("trainerAppearances")
            val fallbackAppearance = root.get("defaultAppearance")?.asString ?: bundledDefaults.get("defaultAppearance").asString
            val bossAppearance = root.get("bossAppearance")?.asString ?: bundledDefaults.get("bossAppearance").asString
            val rounds = root.getAsJsonArray("rounds").map { entry ->
                val data = entry.asJsonObject
                val slots = data.getAsJsonArray("teamSlots").map { it.asInt }
                val format = data.get("format").asString
                require(format in setOf("singles", "doubles"))
                require(slots.isNotEmpty() && slots.size <= 6 && slots.distinct() == slots && slots.all { it in 0..5 })
                require(format != "doubles" || slots.size >= 2)
                val level = data.get("level").asInt
                require(level in 1..cap)
                val skill = data.get("skill").asInt
                require(skill in 0..5)
                val trainerId = data.get("trainerId").asString
                require(trainerId.matches(Regex("[a-z0-9_./-]+")))
                val boss = data.getAsJsonObject("boss")
                boss?.let {
                    require(format == "singles" && slots.size == 1) { "Boss rounds use one oversized Pokemon" }
                    val scale = it.get("scale").asFloat
                    require(scale.isFinite() && scale > 1) { "Boss scale must exceed normal size" }
                    val evs = it.getAsJsonObject("evs").entrySet()
                    require(evs.all { ev -> ev.key in setOf("hp", "atk", "def", "spa", "spd", "spe") && ev.value.asInt in 0..252 })
                    require(evs.sumOf { ev -> ev.value.asInt } <= 510)
                    require(it.getAsJsonArray("moves").size() in 1..4)
                }
                val appearance = data.get("appearance")?.asString ?: if (boss != null) bossAppearance
                    else appearances.get(trainerId)?.asString ?: fallbackAppearance
                require(appearance.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))) { "Invalid trainer appearance $appearance" }
                val theme = data.get("type")?.asString
                require(theme == null || theme in DungeonEncounterRoster.TYPES) { "Unknown round type $theme" }
                DungeonRound(data.get("name").asString, trainerId, format, level, slots, skill, boss?.deepCopy(), appearance,
                    theme, DungeonDialogue.parse(data.getAsJsonObject("dialogue")))
            }
            require(rounds.isNotEmpty())
            require(rounds.zipWithNext().all { (a, b) -> a.level <= b.level }) { "Rounds must progress in level" }
            val heals = root.getAsJsonArray("healAfterRounds").map { it.asInt }.toSet()
            require(heals.all { it in 1 until rounds.size }) { "Healing applies between rounds only" }
            val delay = root.get("interRoundDelayTicks").asInt
            val challengeDelay = root.get("challengeDelayTicks")?.asInt ?: bundledChallengeDelay()
            val grace = root.get("battleEndGraceTicks").asInt
            val timeout = root.get("transitionTimeoutTicks").asInt
            require(delay >= 0 && challengeDelay >= 0 && grace > 0 && timeout > grace && timeout > delay)
            val offset = root.getAsJsonObject("trainerOffset")
            val x = offset.get("x").asDouble; val y = offset.get("y").asDouble; val z = offset.get("z").asDouble
            require(x.isFinite() && y.isFinite() && z.isFinite())
            return DungeonRoundPlan(rounds, heals, delay, grace, timeout, x, y, z, challengeDelay, regularIvCap, regularEvMultiplier)
        }

        private val bundledDefaults by lazy {
            requireNotNull(DungeonRoundPlan::class.java.getResourceAsStream("/dungeon-rounds.json"))
                .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        }
        private fun bundledChallengeDelay(): Int = bundledDefaults.get("challengeDelayTicks").asInt
    }
}

object DungeonRounds {
    fun load(): DungeonRoundPlan {
        val path = FabricLoader.getInstance().configDir.resolve("mondungeons/dungeon-rounds.json")
        Files.createDirectories(path.parent)
        if (!Files.exists(path)) requireNotNull(javaClass.getResourceAsStream("/dungeon-rounds.json")).use { Files.copy(it, path) }
        return Files.newBufferedReader(path).use { DungeonRoundPlan.parse(JsonParser.parseReader(it).asJsonObject) }
    }
}
