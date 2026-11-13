package sauceology.mondungeon

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import com.google.gson.JsonObject
import net.minecraft.world.item.Item
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import kotlin.random.Random

/** Uses the same normalized probabilities as the odds menu. Never filters invalid prizes. */
object DungeonPayout {
    fun itemChoices(data: JsonObject): List<Item> {
        if (data.has("itemTag")) {
            val tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(data.get("itemTag").asString))
            val choices = BuiltInRegistries.ITEM.getTag(tag).orElse(null)?.map { it.value() }.orEmpty().distinct()
            require(choices.isNotEmpty()) { "Empty or unknown reward item tag: ${tag.location()}" }
            require(choices.none { it == net.minecraft.world.item.Items.AIR })
            return choices
        }
        val ids = if (data.has("items")) data.getAsJsonArray("items").map { it.asString } else listOf(data.get("item").asString)
        require(ids.isNotEmpty())
        return ids.distinct().map { value ->
            require(!value.startsWith("TODO:", ignoreCase = true)) { "Unresolved TODO item: $value" }
            val id = ResourceLocation.parse(value)
            require(BuiltInRegistries.ITEM.containsKey(id) && id != ResourceLocation.withDefaultNamespace("air")) {
                "Unregistered reward item: $id"
            }
            BuiltInRegistries.ITEM.get(id)
        }
    }

    fun problems(table: DungeonRewardTable): List<String> = table.rewards.filter { it.probability > 0 }.mapNotNull { reward ->
        try { validateReward(reward); null } catch (exception: Exception) {
            "${reward.definition.get("label")?.asString ?: reward.groupId}: ${exception.message}"
        }
    }

    fun validate(table: DungeonRewardTable) {
        val problems = problems(table)
        require(problems.isEmpty()) { problems.joinToString("; ") }
    }

    private fun validateReward(reward: DungeonReward) {
        val data = reward.definition
        when (data.get("type").asString) {
            "item" -> itemChoices(data)
            "pokemon" -> {
                val properties = PokemonProperties.parse(data.get("spec").asString)
                val species = properties.species
                require(species == "random" || species != null && PokemonSpecies.getByName(species) != null) {
                    "Unknown reward Pokemon: ${data.get("spec").asString}"
                }
                require(PokemonSpecies.implemented.isNotEmpty())
                val selection = data.get("speciesSelection")?.asString ?: "uniform"
                require(selection in setOf("uniform", "spawn_rarity")) { "Invalid speciesSelection: $selection" }
                if (species == "random" && selection == "spawn_rarity") DungeonPokemonRarity.loadedPool()
            }
            else -> error("Unknown reward type")
        }
    }

    fun select(table: DungeonRewardTable, unitRoll: Double): DungeonReward {
        require(unitRoll >= 0 && unitRoll < 1)
        val eligible = table.rewards.filter { it.probability > 0 }
        require(eligible.isNotEmpty())
        var cumulative = 0.0
        eligible.forEach { reward ->
            cumulative += reward.probability
            if (unitRoll < cumulative) return reward
        }
        // Only floating point summation error may reach the final entry.
        require(kotlin.math.abs(cumulative - 1.0) < 0.000000001)
        return eligible.last()
    }

    fun roll(table: DungeonRewardTable, player: ServerPlayer): RolledDungeonReward {
        val reward = select(table, Random.nextDouble())
        val data = reward.definition
        if (data.get("type").asString == "item") {
            val item = itemChoices(data).random()
            val stack = ItemStack(item, data.get("count").asInt)
            return RolledDungeonReward(reward.groupId, "${stack.count} x ${stack.hoverName.string}", stack, null)
        }
        val properties = PokemonProperties.parse(data.get("spec").asString)
        if (properties.species == "random") {
            properties.species = if (data.get("speciesSelection")?.asString == "spawn_rarity") {
                DungeonPokemonRarity.roll()
            } else PokemonSpecies.implemented.random().name
        }
        val pokemon = properties.create(player)
        val actualLabel = (if (pokemon.shiny) "Shiny " else "") + pokemon.species.translatedName.string
        return RolledDungeonReward(reward.groupId, actualLabel, null, pokemon)
    }
}

/** A concrete, already rolled prize. The spin cannot change it or request another roll. */
class RolledDungeonReward(val group: String, val label: String, val item: ItemStack?, val pokemon: Pokemon?) {
    init { require((item == null) != (pokemon == null)) }
    private var granted = false

    fun grant(player: ServerPlayer, originalKey: ItemStack): Boolean {
        if (granted) return false
        val inventory = player.inventory
        val keySlot = (0 until inventory.containerSize).firstOrNull { slot ->
            val stack = inventory.getItem(slot)
            !stack.isEmpty && ItemStack.isSameItemSameComponents(stack, originalKey)
        } ?: return false

        // Plan the entire insertion, including the slot freed by the key, before mutating anything.
        val planned = (0 until inventory.containerSize).map { inventory.getItem(it).copy() }.toMutableList()
        planned[keySlot].shrink(1)
        if (item != null) {
            var remaining = item.count
            // Inventory slots 0..35 only: never insert rewards into armor/offhand slots.
            for (slot in 0 until 36) {
                val stack = planned[slot]
                if (!stack.isEmpty && ItemStack.isSameItemSameComponents(stack, item)) {
                    val count = minOf(remaining, (stack.maxStackSize - stack.count).coerceAtLeast(0))
                    stack.grow(count)
                    remaining -= count
                }
            }
            for (slot in 0 until 36) {
                if (remaining == 0) break
                if (planned[slot].isEmpty) {
                    val count = minOf(remaining, item.maxStackSize)
                    planned[slot] = item.copyWithCount(count)
                    remaining -= count
                }
            }
            if (remaining != 0) return false
        }
        // Native party insertion overflows into the player's PC. A full PC returns false.
        if (pokemon != null && !Cobblemon.storage.getParty(player).add(pokemon)) return false
        planned.forEachIndexed { slot, stack -> inventory.setItem(slot, stack) }
        inventory.setChanged()
        player.containerMenu.broadcastChanges()
        granted = true
        return true
    }
}
