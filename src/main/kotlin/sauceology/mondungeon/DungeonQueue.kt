package sauceology.mondungeon

import java.util.UUID

internal data class DungeonQueueInvite<T>(val player: UUID, val arena: String, val token: String,
    val expires: Long, val offer: T)

/** Server-thread FIFO. Reservations count as occupied slots until answered, cancelled or expired. */
internal class DungeonQueue<T> {
    private val waiting = linkedMapOf<UUID, T>()
    private val invited = linkedMapOf<UUID, DungeonQueueInvite<T>>()
    val invitations: List<DungeonQueueInvite<T>> get() = invited.values.toList()
    val hasWaiting: Boolean get() = waiting.isNotEmpty()
    fun contains(player: UUID) = player in waiting || player in invited
    fun position(player: UUID): Int = if (player in invited) 0 else waiting.keys.indexOf(player).let { if (it < 0) -1 else it + 1 }
    fun reserved(arena: String) = invited.values.any { it.arena == arena }
    fun join(player: UUID, offer: T) { if (!contains(player)) waiting[player] = offer }
    fun reserve(arena: String, now: Long, duration: Long): DungeonQueueInvite<T>? {
        check(!reserved(arena))
        val next = waiting.entries.firstOrNull() ?: return null
        val invite = DungeonQueueInvite(next.key, arena, UUID.randomUUID().toString(), now + duration, next.value)
        waiting.remove(next.key)
        invited[next.key] = invite
        return invite
    }
    fun answer(player: UUID, token: String): DungeonQueueInvite<T>? {
        val invite = invited[player]?.takeIf { it.token == token } ?: return null
        invited.remove(player)
        return invite
    }
    fun remove(player: UUID): DungeonQueueInvite<T>? { waiting.remove(player); return invited.remove(player) }
    fun clear() { waiting.clear(); invited.clear() }
}
