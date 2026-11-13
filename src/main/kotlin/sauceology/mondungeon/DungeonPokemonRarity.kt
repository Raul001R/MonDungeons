package sauceology.mondungeon

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.BestSpawner
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import kotlin.random.Random

/** Bucket odds come from Cobblemon; each species occurs once, irrespective of biome/form entries. */
object DungeonPokemonRarity {
    data class Bucket(val id: String, val weight: Double, val species: List<String>)

    fun loadedPool(): List<Bucket> = buildPool(
        BestSpawner.config.worldBuckets.mapValues { it.value.toDouble() },
        PokemonSpecies.implemented.map { it.name }.toSet(),
        CobblemonSpawnPools.WORLD_SPAWN_POOL.details.filterIsInstance<PokemonSpawnDetail>()
            .filter { it.weight > 0 || it.percentage > 0 }
            .mapNotNull { detail ->
                detail.pokemon.species?.let(PokemonSpecies::getByName)?.let { it.name to detail.bucket }
            }
    )

    internal fun buildPool(weights: Map<String, Double>, implemented: Set<String>, spawns: List<Pair<String, String>>): List<Bucket> {
        require(weights.values.all { it.isFinite() && it >= 0 }) { "Invalid Cobblemon world bucket weights" }
        val enabled = weights.filterValues { it > 0 }
        require(enabled.isNotEmpty()) { "No enabled Cobblemon world spawn buckets" }
        require(implemented.isNotEmpty()) { "No implemented reward Pokemon" }
        // Preserve eligibility for species with no world spawn (e.g. addon legends), at the rarest weight.
        val fallback = enabled.minBy { it.value }.key
        val assignments = spawns.filter { it.first in implemented && it.second in enabled }
            .groupBy({ it.first }, { it.second })
        val members = implemented.sorted().groupBy { species ->
            assignments[species]?.maxByOrNull { enabled.getValue(it) } ?: fallback
        }
        return enabled.mapNotNull { (id, weight) ->
            members[id]?.let { Bucket(id, weight, it) }
        }.also { require(it.sumOf { bucket -> bucket.weight }.isFinite()) }
    }

    internal fun selectBucket(pool: List<Bucket>, unitRoll: Double): Bucket {
        require(unitRoll >= 0 && unitRoll < 1 && pool.isNotEmpty())
        val threshold = unitRoll * pool.sumOf { it.weight }
        var cumulative = 0.0
        return pool.firstOrNull { cumulative += it.weight; threshold < cumulative } ?: pool.last()
    }

    fun roll(): String = selectBucket(loadedPool(), Random.nextDouble()).species.random()
}
