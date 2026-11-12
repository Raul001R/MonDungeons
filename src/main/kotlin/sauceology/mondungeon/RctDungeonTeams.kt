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
        val balance = DungeonMoveBalance.load()
        return plan.rounds.map { round ->
            val resource = ResourceLocation.fromNamespaceAndPath("rctmod", "trainers/${round.trainerId}.json")
            val file = server.resourceManager.getResource(resource).orElseThrow { IllegalStateException("Missing RCT team $resource") }
            val definition = file.openAsReader().use { JsonParser.parseReader(it).asJsonObject }
            val team = definition.getAsJsonArray("team")
            round.teamSlots.map { slot ->
                check(slot < team.size()) { "RCT team ${round.trainerId} has no slot $slot" }
                create(team[slot].asJsonObject, round, balance, plan).also { pokemon ->
                    check(round.theme == null || pokemon.types.any { it.name.equals(round.theme, ignoreCase = true) }) {
                        "${round.name}: ${pokemon.species.name} (${pokemon.types.joinToString { it.name }}) does not match ${round.theme}"
                    }
                }
            }
        }
    }

    private fun create(data: JsonObject, round: DungeonRound, balance: DungeonMoveBalance, plan: DungeonRoundPlan): Pokemon {
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
        val sourceItems = boss?.get("heldItem")?.asString?.let { listOf(it) }
            ?: data.getAsJsonArray("heldItem")?.map { it.asString } ?: emptyList()
        if (sourceItems.isNotEmpty()) {
            // RCT supplies ordered alternatives for optional addon items. Preserve those fallbacks.
            val installed = sourceItems.map { item ->
                if (':' in item) ResourceLocation.parse(item) else ResourceLocation.fromNamespaceAndPath("cobblemon", item)
            }.filter { BuiltInRegistries.ITEM.containsKey(it) }
            check(installed.isNotEmpty()) { "No installed held item from RCT choices $sourceItems" }
            // An excluded competitive item is intentionally unequipped, including when it was
            // the only installed fallback for an optional Mega Stone. Missing mods are still validated.
            installed.firstOrNull { boss != null || !balance.enabled || it.toString() !in balance.blockedItems }
                ?.let { properties.heldItem = it.toString() }
        }
        return properties.create().apply {
            check(this.species.showdownId().equals(species, ignoreCase = true)) { "Unknown RCT species $species" }
            stats.forEach { (key, stat) ->
                val iv = if (boss?.get("perfectIVs")?.asBoolean == true) 31 else data.getAsJsonObject("ivs")?.get(key)?.asInt ?: 0
                setIV(stat, if (boss == null) minOf(iv, plan.regularIvCap) else iv)
                setEV(stat, 0)
            }
            val evs = boss?.getAsJsonObject("evs") ?: data.getAsJsonObject("evs")
            evs?.entrySet()?.forEach { (key, value) ->
                val ev = if (boss == null) (value.asInt * plan.regularEvMultiplier).toInt() else value.asInt
                setEV(stats.getValue(key), ev)
            }
            if (boss != null) {
                // Populate the live set explicitly: property application/initialization can reorder
                // or replace the moves declared during Pokemon construction.
                moveSet.clear()
                boss.getAsJsonArray("moves").forEach { entry ->
                    val move = requireNotNull(com.cobblemon.mod.common.api.moves.Moves.getByName(entry.asString)) {
                        "Unknown dungeon boss move ${entry.asString}"
                    }
                    check(move in form.moves.getAllLegalMoves()) { "Illegal boss move ${entry.asString} for $species" }
                    moveSet.add(move.create())
                }
            }
            heal()
            balance.apply(this, boss != null)
        }
    }
}
