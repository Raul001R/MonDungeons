package sauceology.mondungeon

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Copied into a prepared run; replacing the live settings cannot change an active battle. */
data class DungeonSettings(val roundsByTier: Map<String, Int>, val regularIvCap: Int, val regularEvMultiplier: Double,
    val leaderboardPageSize: Int) {
    fun plan(tier: String, base: DungeonRoundPlan): DungeonRoundPlan {
        val count = requireNotNull(roundsByTier[tier]) { "Missing config option: runs.roundsByTier.$tier" }
        require(count in 1..base.rounds.size) { "runs.roundsByTier.$tier must be between 1 and ${base.rounds.size}" }
        require(base.rounds.last().boss != null && base.rounds.dropLast(1).all { it.boss == null }) {
            "dungeon-rounds.json must define regular rounds followed by one final boss"
        }
        return base.copy(rounds = base.rounds.take(count - 1) + base.rounds.last(),
            healAfterRounds = base.healAfterRounds.filter { it < count }.toSet(),
            regularIvCap = regularIvCap, regularEvMultiplier = regularEvMultiplier)
    }

    companion object {
        fun defaults(): JsonObject = requireNotNull(DungeonSettings::class.java.getResourceAsStream("/dungeon-settings.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

        fun parse(root: JsonObject): DungeonSettings {
            fun number(path: String): JsonElement = value(root, path).also {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) { "$path must be a number" }
            }
            fun integer(path: String): Int = try { number(path).asBigDecimal.intValueExact() }
                catch (exception: Exception) { throw IllegalArgumentException("$path must be a whole number", exception) }
            require(root.keySet().all { it in setOf("runs", "trainers", "leaderboard") }) { "Unknown settings section" }
            require(root.getAsJsonObject("runs").keySet() == setOf("roundsByTier")) { "Unknown option in runs" }
            require(root.getAsJsonObject("trainers").keySet() == setOf("regularIvCap", "regularEvMultiplier")) { "Unknown option in trainers" }
            require(root.getAsJsonObject("leaderboard").keySet() == setOf("pageSize")) { "Unknown option in leaderboard" }
            val counts = root.getAsJsonObject("runs").getAsJsonObject("roundsByTier").keySet().associateWith { tier ->
                require(tier.matches(Regex("[a-z0-9_]+"))) { "Invalid tier in runs.roundsByTier: $tier" }
                integer("runs.roundsByTier.$tier").also { require(it > 0) { "runs.roundsByTier.$tier must be positive" } }
            }
            require(counts.isNotEmpty()) { "runs.roundsByTier must not be empty" }
            val ivs = integer("trainers.regularIvCap")
            require(ivs in 0..31) { "trainers.regularIvCap must be between 0 and 31" }
            val evs = number("trainers.regularEvMultiplier").asDouble
            require(evs.isFinite() && evs in 0.0..1.0) { "trainers.regularEvMultiplier must be between 0 and 1" }
            val pageSize = integer("leaderboard.pageSize")
            require(pageSize in 1..10) { "leaderboard.pageSize must be between 1 and 10" }
            return DungeonSettings(counts, ivs, evs, pageSize)
        }

        fun value(root: JsonObject, path: String): JsonElement {
            var value: JsonElement = root
            path.split('.').forEach { part ->
                require(value.isJsonObject && value.asJsonObject.has(part)) { "Unknown config option: $path" }
                value = value.asJsonObject.get(part)
            }
            return value
        }
    }
}

/** Validate and persist a complete candidate before publishing it to future runs. */
class DungeonSettingsStore(private val file: Path, private val legacyRounds: Path,
    private val validate: (DungeonSettings) -> Unit = {}) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var document: JsonObject
    lateinit var current: DungeonSettings
        private set

    fun reload(): DungeonSettings = apply(read())

    fun get(path: String?): String = gson.toJson(if (path == null) document else DungeonSettings.value(document, path))

    fun paths(): List<String> = current.roundsByTier.keys.map { "runs.roundsByTier.$it" } +
        listOf("trainers.regularIvCap", "trainers.regularEvMultiplier", "leaderboard.pageSize")

    fun set(path: String, text: String): DungeonSettings {
        require(path in paths()) { "Unknown editable config option: $path" }
        val candidate = read()
        val parent = DungeonSettings.value(candidate, path.substringBeforeLast('.')).asJsonObject
        parent.add(path.substringAfterLast('.'), JsonParser.parseString(text))
        return apply(candidate)
    }

    private fun read(): JsonObject {
        val result = if (Files.exists(file)) Files.newBufferedReader(file).use { JsonParser.parseReader(it).asJsonObject }
            else JsonObject()
        val defaults = DungeonSettings.defaults()
        // Preserve customized 1.0.2 trainer stats during the first upgrade. The old file stays intact.
        if (!Files.exists(file) && Files.exists(legacyRounds)) {
            val legacy = Files.newBufferedReader(legacyRounds).use { JsonParser.parseReader(it).asJsonObject }
            listOf("regularIvCap", "regularEvMultiplier").forEach { key ->
                legacy.get(key)?.let { defaults.getAsJsonObject("trainers").add(key, it.deepCopy()) }
            }
        }
        fun fill(target: JsonObject, fallback: JsonObject) {
            fallback.entrySet().forEach { (key, value) ->
                if (!target.has(key)) target.add(key, value.deepCopy())
                else if (value.isJsonObject) {
                    require(target.get(key).isJsonObject) { "Settings section $key must be an object" }
                    fill(target.getAsJsonObject(key), value.asJsonObject)
                }
            }
        }
        fill(result, defaults)
        return result
    }

    private fun apply(candidate: JsonObject): DungeonSettings {
        val parsed = DungeonSettings.parse(candidate)
        validate(parsed)
        val content = gson.toJson(candidate) + "\n"
        Files.createDirectories(file.toAbsolutePath().parent)
        if (!Files.exists(file) || Files.readString(file) != content) {
            val temporary = Files.createTempFile(file.toAbsolutePath().parent, ".dungeon-settings-", ".tmp")
            try {
                Files.writeString(temporary, content)
                if (Files.exists(file)) {
                    val backup = Files.createTempFile(file.toAbsolutePath().parent, "dungeon-settings.backup-", ".json")
                    Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING)
                }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { runCatching { Files.deleteIfExists(temporary) } }
        }
        document = candidate.deepCopy()
        current = parsed
        return parsed
    }
}

object DungeonSettingsConfig {
    val store by lazy {
        val directory = FabricLoader.getInstance().configDir.resolve("mondungeons")
        DungeonSettingsStore(directory.resolve("dungeon-settings.json"), directory.resolve("dungeon-rounds.json")) { settings ->
            val base = DungeonRounds.load()
            settings.roundsByTier.keys.union(DungeonLootData.tiers.keys).forEach { settings.plan(it, base) }
        }
    }
    val current: DungeonSettings get() = store.current
}
