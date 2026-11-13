package sauceology.mondungeon

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

data class DungeonReturnRecord(val origin: DungeonPosition, val key: ItemStack?, val runId: UUID,
                               val payoutPokemon: UUID? = null, val leaderboardPoints: Int = 1)

/** Durable obligations only. The global run lock is deliberately never restored. */
class DungeonReturnRecovery internal constructor(private val file: Path, private val registries: HolderLookup.Provider,
    private val write: (Path, CompoundTag) -> Unit = DungeonDurableFiles::write) {
    constructor(server: MinecraftServer) : this(server.getWorldPath(LevelResource.ROOT)
        .resolve("data/mondungeons_returns.dat"), server.registryAccess())

    private var records: Map<UUID, DungeonReturnRecord> = load()
    fun pending(): Map<UUID, DungeonPosition> = records.mapValues { it.value.origin }
    fun record(player: UUID): DungeonReturnRecord? = records[player]?.let { it.copy(key = it.key?.copy()) }

    fun remember(player: UUID, origin: DungeonPosition, key: ItemStack? = null, runId: UUID = UUID.randomUUID(), leaderboardPoints: Int = 1) {
        check(player !in records) { "Player already has an outstanding dungeon return" }
        require(leaderboardPoints > 0) { "Leaderboard points must be positive" }
        commit(records + (player to DungeonReturnRecord(origin, key?.copyWithCount(1), runId, leaderboardPoints = leaderboardPoints)))
    }

    fun preparePayout(player: UUID, pokemon: UUID?) {
        val entry = records.getValue(player)
        commit(records + (player to entry.copy(payoutPokemon = pokemon)))
    }

    fun forget(player: UUID) {
        if (player in records) commit(records - player)
    }

    private fun commit(next: Map<UUID, DungeonReturnRecord>) {
        val data = CompoundTag().apply {
            putInt("SchemaVersion", 3)
            put("Returns", ListTag().apply {
                next.forEach { (player, entry) -> add(CompoundTag().apply {
                    putUUID("Player", player)
                    putUUID("Run", entry.runId)
                    putInt("LeaderboardPoints", entry.leaderboardPoints)
                    putString("Dimension", entry.origin.dimension.location().toString())
                    putDouble("X", entry.origin.position.x); putDouble("Y", entry.origin.position.y); putDouble("Z", entry.origin.position.z)
                    putFloat("Yaw", entry.origin.yaw); putFloat("Pitch", entry.origin.pitch)
                    entry.key?.let { put("Key", it.save(registries)) }
                    entry.payoutPokemon?.let { putUUID("PayoutPokemon", it) }
                }) }
            })
        }
        // Copy-on-write: a failed disk write never changes the in-memory obligation.
        write(file, CompoundTag().apply { put("data", data) })
        records = next
    }

    private fun load(): Map<UUID, DungeonReturnRecord> {
        if (!Files.exists(file)) return emptyMap()
        val root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data")
        val version = root.getInt("SchemaVersion")
        require(version in 1..3) { "Unsupported dungeon return schema: $version" }
        val entries = root.getList("Returns", Tag.TAG_COMPOUND.toInt())
        return buildMap {
            repeat(entries.size) { index ->
                val entry = entries.getCompound(index)
                val origin = DungeonPosition(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(entry.getString("Dimension"))),
                    Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")), entry.getFloat("Yaw"), entry.getFloat("Pitch"))
                require(origin.position.x.isFinite() && origin.position.y.isFinite() && origin.position.z.isFinite() &&
                    origin.yaw.isFinite() && origin.pitch.isFinite()) { "Invalid saved dungeon return" }
                val key = if (entry.contains("Key")) ItemStack.parse(registries, requireNotNull(entry.get("Key"))).orElseThrow() else null
                require(key == null || key.`is`(DungeonItems.DUNGEON_KEY) && key.count == 1) { "Invalid saved dungeon key" }
                val points = if (version >= 3) entry.getInt("LeaderboardPoints") else 1
                require(points > 0) { "Invalid saved leaderboard points" }
                val record = DungeonReturnRecord(origin, key,
                    if (entry.hasUUID("Run")) entry.getUUID("Run") else UUID.randomUUID(),
                    if (entry.hasUUID("PayoutPokemon")) entry.getUUID("PayoutPokemon") else null, points)
                require(put(entry.getUUID("Player"), record) == null) { "Duplicate dungeon return" }
            }
        }
    }
}
