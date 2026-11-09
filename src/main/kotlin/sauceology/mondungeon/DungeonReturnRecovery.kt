package sauceology.mondungeon

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Only return obligations survive restart; an active run/lock never does. */
class DungeonReturnRecovery(server: MinecraftServer) {
    private val storage = server.overworld().dataStorage
    private val data = storage.computeIfAbsent(ReturnData.FACTORY, "mondungeons_returns")
    fun pending(): Map<UUID, DungeonPosition> = data.positions.toMap()
    fun remember(player: UUID, origin: DungeonPosition) {
        check(player !in data.positions) { "Player already has an outstanding dungeon return" }
        data.positions[player] = origin
        data.setDirty()
        storage.save() // Flush before moving the player, as in the gym reference.
    }
    fun forget(player: UUID) {
        if (data.positions.remove(player) == null) return
        data.setDirty()
        storage.save()
    }
}

private class ReturnData : SavedData() {
    val positions = linkedMapOf<UUID, DungeonPosition>()
    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag = tag.apply {
        putInt("SchemaVersion", 1)
        put("Returns", ListTag().apply {
            positions.forEach { (player, origin) -> add(CompoundTag().apply {
                putUUID("Player", player)
                putString("Dimension", origin.dimension.location().toString())
                putDouble("X", origin.position.x)
                putDouble("Y", origin.position.y)
                putDouble("Z", origin.position.z)
                putFloat("Yaw", origin.yaw)
                putFloat("Pitch", origin.pitch)
            }) }
        })
    }
    companion object {
        val FACTORY = Factory(::ReturnData, { tag, _ ->
            require(tag.getInt("SchemaVersion") == 1) { "Unsupported dungeon return schema" }
            ReturnData().apply {
                val entries = tag.getList("Returns", Tag.TAG_COMPOUND.toInt())
                repeat(entries.size) { index ->
                    val entry = entries.getCompound(index)
                    val pos = DungeonPosition(
                        ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.getString("Dimension"))),
                        Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")),
                        entry.getFloat("Yaw"), entry.getFloat("Pitch"))
                    require(pos.position.x.isFinite() && pos.position.y.isFinite() && pos.position.z.isFinite() &&
                        pos.yaw.isFinite() && pos.pitch.isFinite()) { "Invalid saved dungeon return" }
                    require(positions.put(entry.getUUID("Player"), pos) == null) { "Duplicate dungeon return" }
                }
            }
        }, DataFixTypes.SAVED_DATA_COMMAND_STORAGE)
    }
}
