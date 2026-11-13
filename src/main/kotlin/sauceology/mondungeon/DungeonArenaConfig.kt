package sauceology.mondungeon

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Loaded per server, never chooses an arena on the owner's behalf. */
class DungeonArenaConfig(private val file: Path = FabricLoader.getInstance().configDir.resolve("mondungeons/dungeon-arena.json")) {
    private val root: JsonObject
    var arenas: Map<String, DungeonPosition> = emptyMap()
        private set
    val returnRetryTicks: Int
    val spinTicks: Int

    init {
        Files.createDirectories(file.parent)
        if (!Files.exists(file)) {
            requireNotNull(javaClass.getResourceAsStream("/dungeon-arena.json")).use { Files.copy(it, file) }
        }
        root = Files.newBufferedReader(file).use { JsonParser.parseReader(it).asJsonObject }
        val defaults = requireNotNull(javaClass.getResourceAsStream("/dungeon-arena.json")).bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        spinTicks = (root.get("spinTicks") ?: defaults.get("spinTicks")).asBigDecimal.intValueExact()
        require(spinTicks > 0) { "spinTicks must be positive" }
        returnRetryTicks = root.get("returnRetryTicks").asBigDecimal.intValueExact()
        require(returnRetryTicks > 0) { "returnRetryTicks must be positive" }
        val definitions = root.getAsJsonObject("arenas") ?: JsonObject().apply {
            root.get("arena")?.takeUnless { it.isJsonNull }?.let { add("default", it) }
        }
        arenas = definitions.entrySet().associate { (id, element) ->
            require(id.matches(Regex("[a-z0-9_-]+"))) { "Invalid arena id: $id" }
            val data = element.asJsonObject
            val pos = Vec3(data.get("x").asDouble, data.get("y").asDouble, data.get("z").asDouble)
            val yaw = data.get("yaw").asFloat
            val pitch = data.get("pitch").asFloat
            require(pos.x.isFinite() && pos.y.isFinite() && pos.z.isFinite() && yaw.isFinite() && pitch.isFinite())
            id to DungeonPosition(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(data.get("dimension").asString)), pos, yaw, pitch)
        }
        require(arenas.values.map { it.dimension to it.position }.distinct().size == arenas.size) { "Dungeon arenas must have different positions" }
    }

    fun setArena(id: String, position: DungeonPosition) {
        require(id.matches(Regex("[a-z0-9_-]+"))) { "Use lowercase letters, numbers, underscores or hyphens for the arena id" }
        require(arenas.none { (other, pos) -> other != id && pos.dimension == position.dimension && pos.position == position.position }) {
            "Another dungeon arena already uses this position"
        }
        save(arenas + (id to position))
    }

    fun removeArena(id: String): Boolean {
        if (id !in arenas) return false
        save(arenas - id)
        return true
    }

    private fun save(candidate: Map<String, DungeonPosition>) {
        val definitions = JsonObject().apply {
            candidate.forEach { (id, position) ->
                add(id, JsonObject().apply {
                    addProperty("dimension", position.dimension.location().toString())
                    addProperty("x", position.position.x)
                    addProperty("y", position.position.y)
                    addProperty("z", position.position.z)
                    addProperty("yaw", position.yaw)
                    addProperty("pitch", position.pitch)
                })
            }
        }
        val updated = root.deepCopy().apply { remove("arena"); add("arenas", definitions) }
        val temp = file.resolveSibling("dungeon-arena.json.tmp")
        Files.newBufferedWriter(temp).use { GsonBuilder().setPrettyPrinting().create().toJson(updated, it) }
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        root.remove("arena")
        root.add("arenas", definitions)
        arenas = candidate.toMap()
    }
}
