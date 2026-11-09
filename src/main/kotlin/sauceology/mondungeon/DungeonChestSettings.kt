package sauceology.mondungeon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

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

    fun load(tiers: Collection<DungeonKeyTier>): Float {
        val path = FabricLoader.getInstance().configDir.resolve("mondungeons/dungeon-chests.json")
        Files.createDirectories(path.parent)
        if (!Files.exists(path)) requireNotNull(javaClass.getResourceAsStream("/dungeon-chests.json")).use { Files.copy(it, path) }
        return Files.newBufferedReader(path).use { chance(JsonParser.parseReader(it).asJsonObject, tiers) }
    }
}
