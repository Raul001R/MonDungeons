package sauceology.mondungeon

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtUtils
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Unlike vanilla SavedData/PlayerDataStorage save, errors propagate to admission/recovery. */
internal object DungeonDurableFiles {
    fun write(file: Path, tag: CompoundTag) {
        val bytes = ByteArrayOutputStream().also { NbtIo.writeCompressed(tag, it) }.toByteArray()
        Files.createDirectories(file.parent)
        val temp = Files.createTempFile(file.parent, ".mondungeons-", ".tmp")
        try {
            FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            // Cleanup must not turn an already committed rename into a reported failure.
            runCatching { Files.deleteIfExists(temp) }
        }
    }

    fun savePlayer(player: ServerPlayer) {
        val data = NbtUtils.addCurrentDataVersion(player.saveWithoutId(CompoundTag()))
        write(player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve("${player.uuid}.dat"), data)
    }
}
