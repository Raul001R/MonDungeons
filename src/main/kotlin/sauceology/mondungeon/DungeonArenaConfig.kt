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
import java.nio.file.StandardCopyOption

/** Loaded per server, never chooses an arena on the owner's behalf. */
class DungeonArenaConfig {
    private val file = FabricLoader.getInstance().configDir.resolve("mondungeons/dungeon-arena.json")
    private val root: JsonObject
    var arena: DungeonPosition? = null
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
        root.get("arena")?.takeUnless { it.isJsonNull }?.asJsonObject?.let { data ->
            val pos = Vec3(data.get("x").asDouble, data.get("y").asDouble, data.get("z").asDouble)
            val yaw = data.get("yaw").asFloat
            val pitch = data.get("pitch").asFloat
            require(pos.x.isFinite() && pos.y.isFinite() && pos.z.isFinite() && yaw.isFinite() && pitch.isFinite())
            arena = DungeonPosition(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(data.get("dimension").asString)), pos, yaw, pitch)
        }
    }

    fun setArena(position: DungeonPosition) {
        val data = JsonObject().apply {
            addProperty("dimension", position.dimension.location().toString())
            addProperty("x", position.position.x)
            addProperty("y", position.position.y)
            addProperty("z", position.position.z)
            addProperty("yaw", position.yaw)
            addProperty("pitch", position.pitch)
        }
        val updated = root.deepCopy().apply { add("arena", data) }
        val temp = file.resolveSibling("dungeon-arena.json.tmp")
        Files.newBufferedWriter(temp).use { GsonBuilder().setPrettyPrinting().create().toJson(updated, it) }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        root.add("arena", data)
        arena = position
    }
}
