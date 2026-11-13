package sauceology.mondungeon

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

data class DungeonClearRecord(val player: UUID, val name: String, val clears: Long, val lastRun: UUID)

/** One durable total per player. A pending return retries credit after a crash without counting twice. */
class DungeonLeaderboard internal constructor(private val file: Path,
    private val write: (Path, CompoundTag) -> Unit = DungeonDurableFiles::write) {
    constructor(server: MinecraftServer) : this(server.getWorldPath(LevelResource.ROOT).resolve("data/mondungeons_leaderboard.dat"))

    private var records: Map<UUID, DungeonClearRecord> = load()
    private var ranked = rank(records)

    fun record(player: UUID, name: String, run: UUID, points: Int = 1) {
        val previous = records[player]
        if (previous?.lastRun == run) return
        require(name.isNotBlank() && name.length <= 16) { "Invalid leaderboard player name" }
        require(points > 0) { "Leaderboard points must be positive" }
        val next = records + (player to DungeonClearRecord(player, name, Math.addExact(previous?.clears ?: 0, points.toLong()), run))
        val root = CompoundTag().apply {
            putInt("SchemaVersion", 1)
            put("Players", ListTag().apply {
                next.values.forEach { entry -> add(CompoundTag().apply {
                    putUUID("Player", entry.player)
                    putString("Name", entry.name)
                    putLong("Clears", entry.clears) // Retain the field name so historical totals remain readable.
                    putUUID("LastRun", entry.lastRun)
                }) }
            })
        }
        write(file, root)
        records = next
        ranked = rank(next)
    }

    fun page(requested: Int, size: Int): RaidLeaderboardPayload {
        require(size in 1..10)
        val page = requested.coerceIn(0, ((ranked.size - 1).coerceAtLeast(0)) / size)
        return RaidLeaderboardPayload(page, size, ranked.size, ranked.drop(page * size).take(size).map {
            RaidLeaderboardEntry(it.name, it.clears)
        })
    }

    private fun rank(entries: Map<UUID, DungeonClearRecord>) = entries.values.sortedWith(
        compareByDescending<DungeonClearRecord> { it.clears }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.player })

    private fun load(): Map<UUID, DungeonClearRecord> {
        if (!Files.exists(file)) return emptyMap()
        val root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
        require(root.getInt("SchemaVersion") == 1) { "Unsupported dungeon leaderboard schema" }
        val players = root.getList("Players", Tag.TAG_COMPOUND.toInt())
        return buildMap {
            repeat(players.size) { index ->
                val data = players.getCompound(index)
                val entry = DungeonClearRecord(data.getUUID("Player"), data.getString("Name"), data.getLong("Clears"), data.getUUID("LastRun"))
                require(entry.name.isNotBlank() && entry.name.length <= 16 && entry.clears > 0) { "Invalid dungeon leaderboard entry" }
                require(put(entry.player, entry) == null) { "Duplicate dungeon leaderboard player" }
            }
        }
    }
}
