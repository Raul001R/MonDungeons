package sauceology.mondungeon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Configure the absolute common-key chance while retaining tier weight ratios. */
object DungeonChestSettings {
    fun chance(root: JsonObject, tiers: Collection<DungeonKeyTier>): Float {
        val common = requireNotNull(tiers.singleOrNull { it.id == "common" }) { "Chest rolls require a common key tier" }
        val total = tiers.sumOf { it.chestWeight.toLong() }
        val target = root.get("commonKeyChance").asDouble
        require(target.isFinite() && target in 0.0..1.0) { "commonKeyChance must be between 0 and 1" }
        val chance = target * total / common.chestWeight
        require(chance <= 1.0) { "commonKeyChance is too high for the configured tier weights" }
        return chance.toFloat()
    }

    fun load(tiers: Collection<DungeonKeyTier>, path: Path = FabricLoader.getInstance().configDir.resolve("mondungeons/dungeon-chests.json")): Float {
        Files.createDirectories(path.parent)
        if (!Files.exists(path)) requireNotNull(javaClass.getResourceAsStream("/dungeon-chests.json")).use { Files.copy(it, path) }
        val root = Files.newBufferedReader(path).use { JsonParser.parseReader(it).asJsonObject }
        val version = root.get("schemaVersion")?.asBigDecimal?.intValueExact() ?: 1
        require(version in 1..2) { "Unsupported dungeon-chests.json schemaVersion: $version" }
        if (version == 2) return chance(root, tiers)
        val updated = root.deepCopy().apply {
            // Only the old shipped default migrates. Owner-customized rates remain deliberate overrides.
            if (get("commonKeyChance").asDouble == 0.10) {
                val defaults = requireNotNull(DungeonChestSettings::class.java.getResourceAsStream("/dungeon-chests.json")).bufferedReader().use {
                    JsonParser.parseReader(it).asJsonObject
                }
                add("commonKeyChance", defaults.get("commonKeyChance").deepCopy())
            }
            addProperty("schemaVersion", 2)
        }
        val result = chance(updated, tiers) // Validate before changing an existing file.
        val backup = Files.createTempFile(path.parent, "dungeon-chests.backup-", ".json")
        Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING)
        val temporary = Files.createTempFile(path.parent, "dungeon-chests-", ".tmp")
        try {
            Files.newBufferedWriter(temporary).use { GsonBuilder().setPrettyPrinting().create().toJson(updated, it) }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { runCatching { Files.deleteIfExists(temporary) } }
        return result
    }
}
