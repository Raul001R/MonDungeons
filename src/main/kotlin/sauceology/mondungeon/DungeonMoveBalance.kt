package sauceology.mondungeon

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import kotlin.random.Random

/** Only affects freshly built dungeon opponents, before a battle is started. */
data class DungeonMoveBalance(val enabled: Boolean, val regularMaxPower: Int, val strongMovePower: Int,
    val regularStrongMovePp: Int, val bossStrongMovePp: Int, val utilities: Set<String>,
    val blocked: Set<String>, val blockedItems: Set<String>) {
    fun apply(pokemon: Pokemon, boss: Boolean, random: Random = Random.Default) {
        if (!enabled) return
        if (!boss) {
            val legal = pokemon.form.moves
            val candidates = (legal.getLevelUpMovesUpTo(pokemon.level) +
                pokemon.moveSet.getMoveTemplates().filter { it in legal.getAllLegalMoves() })
                .distinctBy { it.name }.filter { it.name !in blocked }
            val attacks = candidates.filter { it.power > 0 && it.power <= regularMaxPower }.shuffled(random)
            // At least one STAB attack; prefer the Pokemon's stronger attacking stat.
            val preferredCategory = if (pokemon.attack >= pokemon.specialAttack) "physical" else "special"
            val stab = attacks.filter { it.elementalType in pokemon.types }
            val first = stab.firstOrNull { it.damageCategory.name.equals(preferredCategory, true) }
                ?: stab.firstOrNull() ?: attacks.firstOrNull()
                ?: error("No balanced legal attack for ${pokemon.species.name} at level ${pokemon.level}")
            val selected = mutableListOf(first)
            attacks.firstOrNull { it.elementalType != first.elementalType }?.let(selected::add)
            candidates.filter { it.name in utilities && it.power == 0.0 }.randomOrNull(random)?.let(selected::add)
            for (move in attacks) if (selected.size < 4 && move !in selected) selected += move
            pokemon.moveSet.clear()
            selected.forEach { pokemon.moveSet.add(it.create()) }
        }
        // Native Showdown PP accounting limits repeated strong attacks without touching player moves
        // or intercepting turn/replacement requests. Bosses retain their configured moves and buffs.
        pokemon.moveSet.forEach { move ->
            if (move.power >= strongMovePower) move.currentPp = minOf(move.currentPp,
                if (boss) bossStrongMovePp else regularStrongMovePp)
        }
    }

    companion object {
        fun load(): DungeonMoveBalance {
            val path = FabricLoader.getInstance().configDir.resolve("mondungeons/dungeon-moves.json")
            Files.createDirectories(path.parent)
            if (!Files.exists(path)) requireNotNull(javaClass.getResourceAsStream("/dungeon-moves.json")).use { Files.copy(it, path) }
            return Files.newBufferedReader(path).use { parse(JsonParser.parseReader(it).asJsonObject) }
        }

        fun parse(root: JsonObject): DungeonMoveBalance {
            fun integer(key: String, range: IntRange) = root.get(key).asBigDecimal.intValueExact().also {
                require(it in range) { "Invalid move balance $key" }
            }
            fun strings(key: String) = root.getAsJsonArray(key).map { it.asString.also { name -> require(name.isNotBlank()) } }.toSet()
            val max = integer("regularMaxPower", 1..250)
            val strong = integer("strongMovePower", 1..250)
            require(strong <= max)
            return DungeonMoveBalance(root.get("enabled").asBoolean, max, strong,
                integer("regularStrongMovePp", 1..64), integer("bossStrongMovePp", 1..64),
                strings("regularUtilityMoves"), strings("blockedRegularMoves"), strings("blockedRegularItems"))
        }
    }
}
