package sauceology.mondungeon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import kotlin.random.Random

data class DungeonDialogue(val intro: List<String> = emptyList(), val defeat: List<String> = emptyList(),
                           val victory: List<String> = emptyList()) {
    companion object {
        fun parse(data: JsonObject?, fallback: DungeonDialogue = DungeonDialogue()): DungeonDialogue {
            fun lines(key: String, inherited: List<String>): List<String> = data?.getAsJsonArray(key)?.map {
                it.asString.also { text -> require(text.isNotBlank() && text.length <= 240) }
            }?.also { require(it.size <= 16) } ?: inherited
            return DungeonDialogue(lines("intro", fallback.intro), lines("defeat", fallback.defeat), lines("victory", fallback.victory))
        }
    }
}

data class DungeonTrainerOption(val name: String, val trainerId: String, val slots: List<Int>, val appearance: String)
data class DungeonTrainerTheme(val id: String, val trainers: List<DungeonTrainerOption>, val dialogue: DungeonDialogue)
data class DungeonBossOption(val trainer: DungeonTrainerOption, val moves: com.google.gson.JsonArray,
                             val evs: JsonObject, val dialogue: DungeonDialogue, val type: String)

/** Select encounters once per run, before creating any teams or acquiring the lock. */
class DungeonEncounterRoster private constructor(val enabled: Boolean, val themes: List<DungeonTrainerTheme>,
    val bosses: List<DungeonBossOption>, val dialogue: DungeonDialogue, val healBeforeBoss: Boolean) {
    fun select(plan: DungeonRoundPlan, random: Random = Random.Default): DungeonRoundPlan {
        val heals = plan.healAfterRounds + if (healBeforeBoss) plan.rounds.indices
            .filter { it > 0 && plan.rounds[it].boss != null }.toSet() else emptySet()
        if (!enabled) return plan.copy(rounds = plan.rounds.map { it.copy(dialogue = dialogue) }, healAfterRounds = heals)
        val rounds = plan.rounds.toMutableList()
        val usedThemes = mutableSetOf<String>()
        // Reserve larger teams first; early small-team rounds cannot exhaust their eligible themes.
        rounds.indices.filter { rounds[it].boss == null }.sortedByDescending { rounds[it].teamSlots.size }.forEach { index ->
            val base = rounds[index]
            val eligible = themes.filter { theme -> theme.id !in usedThemes && theme.trainers.any { it.slots.size >= base.teamSlots.size } }
            require(eligible.isNotEmpty()) { "Not enough distinct trainer themes for round ${index + 1}" }
            val theme = eligible.random(random)
            usedThemes += theme.id
            val trainer = theme.trainers.filter { it.slots.size >= base.teamSlots.size }.random(random)
            rounds[index] = base.copy(name = "${theme.id.replaceFirstChar { it.uppercase() }} - ${trainer.name}", trainerId = trainer.trainerId,
                teamSlots = trainer.slots.shuffled(random).take(base.teamSlots.size), appearance = trainer.appearance,
                theme = theme.id, dialogue = theme.dialogue)
        }
        val bossIndices = rounds.indices.filter { rounds[it].boss != null }
        require(bosses.size >= bossIndices.size) { "Not enough boss variants" }
        val choices = bosses.shuffled(random)
        bossIndices.forEachIndexed { choice, index ->
            val base = rounds[index]
            val boss = choices[choice]
            val settings = requireNotNull(base.boss).deepCopy().apply {
                add("moves", boss.moves.deepCopy())
                add("evs", boss.evs.deepCopy())
            }
            rounds[index] = base.copy(name = boss.trainer.name, trainerId = boss.trainer.trainerId,
                teamSlots = boss.trainer.slots, appearance = boss.trainer.appearance, boss = settings,
                theme = boss.type, dialogue = boss.dialogue)
        }
        return plan.copy(rounds = rounds, healAfterRounds = heals)
    }

    companion object {
        fun load(): DungeonEncounterRoster {
            val path = FabricLoader.getInstance().configDir.resolve("mondungeons/dungeon-trainers.json")
            Files.createDirectories(path.parent)
            if (!Files.exists(path)) requireNotNull(javaClass.getResourceAsStream("/dungeon-trainers.json")).use { Files.copy(it, path) }
            return Files.newBufferedReader(path).use { parse(JsonParser.parseReader(it).asJsonObject) }
        }

        fun parse(root: JsonObject): DungeonEncounterRoster {
            val dialogue = DungeonDialogue.parse(root.getAsJsonObject("dialogue"))
            fun trainer(data: JsonObject): DungeonTrainerOption {
                val name = data.get("name").asString
                val id = data.get("trainerId").asString
                val appearance = data.get("appearance").asString
                val slots = data.getAsJsonArray("slots").map { it.asBigDecimal.intValueExact() }
                require(name.isNotBlank() && name.length <= 80)
                require(id.matches(Regex("[a-z0-9_./-]+")))
                require(appearance.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")))
                require(slots.isNotEmpty() && slots.size <= 6 && slots.distinct() == slots && slots.all { it in 0..5 })
                return DungeonTrainerOption(name, id, slots, appearance)
            }
            val themes = root.getAsJsonArray("themes").map { entry ->
                val data = entry.asJsonObject
                val id = data.get("id").asString
                require(id in TYPES) { "Unknown dungeon type $id" }
                val trainers = data.getAsJsonArray("trainers").map { trainer(it.asJsonObject) }
                require(trainers.isNotEmpty())
                DungeonTrainerTheme(id, trainers, DungeonDialogue.parse(data.getAsJsonObject("dialogue"), dialogue))
            }
            require(themes.map { it.id }.distinct().size == themes.size) { "Duplicate trainer themes" }
            val bossDialogue = DungeonDialogue.parse(root.getAsJsonObject("bossDialogue"), dialogue)
            val bosses = root.getAsJsonArray("bosses").map { entry ->
                val data = entry.asJsonObject
                val trainer = trainer(data)
                require(trainer.slots.size == 1)
                val moves = data.getAsJsonArray("moves")
                require(moves.size() in 1..4 && moves.all { it.asString.isNotBlank() })
                val evs = data.getAsJsonObject("evs")
                require(evs.entrySet().all { it.key in setOf("hp", "atk", "def", "spa", "spd", "spe") && it.value.asInt in 0..252 })
                require(evs.entrySet().sumOf { it.value.asInt } <= 510)
                val type = data.get("type").asString
                require(type in TYPES)
                DungeonBossOption(trainer, moves.deepCopy(), evs.deepCopy(), DungeonDialogue.parse(data.getAsJsonObject("dialogue"), bossDialogue), type)
            }
            return DungeonEncounterRoster(root.get("enabled").asBoolean, themes, bosses, dialogue,
                root.get("healBeforeBoss")?.asBoolean ?: false)
        }

        val TYPES = setOf("normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
            "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy")
    }
}
