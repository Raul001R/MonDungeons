package sauceology.mondungeon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

data class DungeonKeyTier(val id: String, val chestWeight: Int, val lootTableId: ResourceLocation)

/** Custom reward tables support both items and Pokemon; these are not vanilla item-only loot tables. */
data class DungeonReward(val definition: JsonObject, val probability: Double, val groupId: String)
data class DungeonRewardGroup(val id: String, val probability: Double, val examples: List<String>)
data class DungeonRewardTable(val id: ResourceLocation, val rewards: List<DungeonReward>, val groups: List<DungeonRewardGroup>)

object DungeonLootData {
    val tiers: Map<String, DungeonKeyTier>
    val tables: Map<ResourceLocation, DungeonRewardTable>

    init {
        val config = FabricLoader.getInstance().configDir.resolve("mondungeons/dungeon-loot.json")
        if (!Files.exists(config)) {
            Files.createDirectories(config.parent)
            requireNotNull(javaClass.getResourceAsStream("/dungeon-loot.json")).use {
                Files.copy(it, config)
            }
        }
        val root = Files.newBufferedReader(config).use { JsonParser.parseReader(it).asJsonObject }
        // Older server configs inherit the new selection policy without rewriting owner loot tables.
        val bundled = requireNotNull(javaClass.getResourceAsStream("/dungeon-loot.json")).bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val selectionByGroup = root.getAsJsonObject("randomPokemonSelectionByGroup")
            ?: bundled.getAsJsonObject("randomPokemonSelectionByGroup")
        require(selectionByGroup.entrySet().all { it.value.asString in setOf("uniform", "spawn_rarity") }) {
            "randomPokemonSelectionByGroup values must be uniform or spawn_rarity"
        }
        val groups = root.getAsJsonObject("rewardTiers")
        val loadedTables = linkedMapOf<ResourceLocation, DungeonRewardTable>()
        tiers = root.getAsJsonObject("keyTiers").entrySet().associate { (id, value) ->
            require(id.matches(Regex("[a-z0-9_]+"))) { "Invalid dungeon key tier: $id" }
            val definition = value.asJsonObject
            val weight = definition.get("chestWeight").asBigDecimal.intValueExact()
            require(weight > 0) { "$id chestWeight must be positive" }
            val tableId = definition.get("lootTableId")?.let { ResourceLocation.parse(it.asString) }
                ?: MonDungeons.id("rewards/$id") // Accept the original supplied draft config.
            require(tableId !in loadedTables) { "Duplicate dungeon reward table $tableId" }
            val odds = definition.getAsJsonObject("tierOdds").entrySet()
            require(kotlin.math.abs(odds.sumOf { it.value.asDouble } - 100.0) < 0.000001) {
                "$id tierOdds must sum to 100"
            }
            val rewards = odds.flatMap { (group, chanceValue) ->
                val chance = chanceValue.asDouble
                require(chance.isFinite() && chance >= 0) { "$id/$group has invalid odds" }
                val entries = requireNotNull(groups.getAsJsonArray(group)) { "Unknown reward tier $group" }
                    .map { it.asJsonObject }
                require(entries.isNotEmpty()) { "Empty reward tier $group" }
                val weights = entries.map { it.get("weight").asDouble }
                require(weights.all { it.isFinite() && it > 0 }) { "Invalid reward weights in $group" }
                val total = weights.sum()
                require(total.isFinite())
                entries.mapIndexed { index, reward ->
                    val type = reward.get("type").asString
                    require(type in setOf("item", "pokemon"))
                    if (type == "pokemon") require(reward.get("spec").asString.isNotBlank())
                    if (type == "item") {
                        require(listOf("item", "items", "itemTag").count { reward.has(it) } == 1) {
                            "Item prize must specify exactly one of item, items or itemTag"
                        }
                        if (reward.has("items")) require(reward.getAsJsonArray("items").size() > 0)
                        require(reward.get("count").asBigDecimal.intValueExact() > 0)
                    }
                    val resolved = reward.deepCopy()
                    if (type == "pokemon") {
                        val selection = reward.get("speciesSelection")?.asString
                            ?: selectionByGroup.get(group)?.asString ?: "uniform"
                        require(selection in setOf("uniform", "spawn_rarity")) { "Invalid speciesSelection: $selection" }
                        resolved.addProperty("speciesSelection", selection)
                    }
                    DungeonReward(resolved, chance / 100.0 * weights[index] / total, group)
                }
            }
            val summaries = odds.map { (group, chance) ->
                DungeonRewardGroup(group, chance.asDouble / 100.0, groups.getAsJsonArray(group).map {
                    val reward = it.asJsonObject
                    reward.get("label")?.asString ?: reward.get("spec")?.asString ?: reward.toString()
                })
            }
            loadedTables[tableId] = DungeonRewardTable(tableId, rewards, summaries)
            id to DungeonKeyTier(id, weight, tableId)
        }
        require(tiers.isNotEmpty()) { "At least one dungeon key tier is required" }
        tables = loadedTables
    }
}
