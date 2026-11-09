package sauceology.mondungeon

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer

/** Reuse installed RCT trainer data, not its card/progression/payout lifecycle. No RCT classes are linked. */
object RctDungeonTeams {
    private val stats = mapOf("hp" to Stats.HP, "atk" to Stats.ATTACK, "def" to Stats.DEFENCE,
        "spa" to Stats.SPECIAL_ATTACK, "spd" to Stats.SPECIAL_DEFENCE, "spe" to Stats.SPEED)

    fun prepare(server: MinecraftServer, plan: DungeonRoundPlan): List<List<Pokemon>> {
        check(FabricLoader.getInstance().isModLoaded("rctmod")) { "Radical Cobblemon Trainers (rctmod) is not installed" }
        return plan.rounds.map { round ->
            val resource = ResourceLocation.fromNamespaceAndPath("rctmod", "trainers/${round.trainerId}.json")
            val file = server.resourceManager.getResource(resource).orElseThrow { IllegalStateException("Missing RCT team $resource") }
            val definition = file.openAsReader().use { JsonParser.parseReader(it).asJsonObject }
            val team = definition.getAsJsonArray("team")
            round.teamSlots.map { slot ->
                check(slot < team.size()) { "RCT team ${round.trainerId} has no slot $slot" }
                create(team[slot].asJsonObject, round)
            }
        }
    }

    private fun create(data: JsonObject, round: DungeonRound): Pokemon {
        val species = data.get("species").asString
        val aspects = data.getAsJsonArray("aspects")?.joinToString(" ") { it.asString } ?: ""
        val properties = PokemonProperties.parse("$species $aspects").apply {
            level = round.level
            data.get("nature")?.let { nature = it.asString }
            data.get("ability")?.let { ability = it.asString }
            data.get("gender")?.let { gender = Gender.valueOf(it.asString.uppercase()) }
            shiny = data.get("shiny")?.asBoolean ?: false
            moves = data.getAsJsonArray("moveset").map { it.asString }
        }
        val boss = round.boss
        if (boss != null) {
            properties.moves = boss.getAsJsonArray("moves").map { it.asString }
            properties.scaleModifier = boss.get("scale").asFloat
        }
        val items = boss?.get("heldItem")?.asString?.let { listOf(it) }
            ?: data.getAsJsonArray("heldItem")?.map { it.asString } ?: emptyList()
        if (items.isNotEmpty()) {
            // RCT supplies ordered alternatives for optional addon items. Preserve those fallbacks.
            val id = items.map { item ->
                if (':' in item) ResourceLocation.parse(item) else ResourceLocation.fromNamespaceAndPath("cobblemon", item)
            }.firstOrNull { BuiltInRegistries.ITEM.containsKey(it) }
                ?: error("No installed held item from RCT choices $items")
            properties.heldItem = id.toString()
        }
        return properties.create().apply {
            check(this.species.name.equals(species, ignoreCase = true)) { "Unknown RCT species $species" }
            stats.forEach { (key, stat) ->
                val iv = if (boss?.get("perfectIVs")?.asBoolean == true) 31 else data.getAsJsonObject("ivs")?.get(key)?.asInt ?: 0
                setIV(stat, iv)
                setEV(stat, 0)
            }
            val evs = boss?.getAsJsonObject("evs") ?: data.getAsJsonObject("evs")
            evs?.entrySet()?.forEach { (key, value) -> setEV(stats.getValue(key), value.asInt) }
            heal()
        }
    }
}
