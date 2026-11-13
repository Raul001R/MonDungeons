package sauceology.mondungeon

/** Dependency-free regression checks executed by verifyDungeonAdmission during check/build. */
fun main() {
    val lock = DungeonAdmission<String>()
    val events = mutableListOf<String>()
    check(lock.enter("player-one", { events += "persist" }, { events += "move"; true }, { error("Unexpected rollback") }))
    check(events == listOf("persist", "move")) { "Return record must be saved before teleport" }
    check(lock.active == "player-one")
    check(!lock.enter("player-two", { error("Occupied run must not persist") }, { error("Occupied run must not move") }, {}))
    check(lock.active == "player-one") { "Second entry must not replace the global owner" }
    lock.clear()
    check(lock.active == null)

    val diskFailure = IllegalStateException("disk unavailable")
    events.clear()
    val failedSave = runCatching {
        lock.enter("player-two", { events += "persist"; throw diskFailure },
            { error("Must not teleport after failed persistence") }, {
                check(lock.active == null) { "Release must precede rollback" }
                events += "rollback"
            })
    }.exceptionOrNull()
    check(failedSave === diskFailure)
    check(events == listOf("persist", "rollback"))
    check(lock.active == null)

    events.clear()
    check(runCatching {
        lock.enter("player-three", { events += "persist" }, { events += "move"; false }, { events += "rollback" })
    }.isFailure)
    check(events == listOf("persist", "move", "rollback"))
    check(lock.active == null) { "Refused teleport must release the global lock" }

    val teleportFailure = IllegalStateException("teleport failed")
    val restoreFailure = IllegalStateException("return unavailable")
    val failedMove = runCatching {
        lock.enter("player-four", {}, { throw teleportFailure }, { throw restoreFailure })
    }.exceptionOrNull()
    check(failedMove === teleportFailure)
    check(failedMove.suppressed.single() === restoreFailure)
    check(lock.active == null) { "Even failed rollback must release the lock" }
    check(lock.enter("player-five", {}, { true }, {})) { "Next player must be able to retry after failures" }
    lock.clear()
    lock.clear() // Cleanup remains safe when repeated by lifecycle callbacks.
    check(lock.active == null)
    checkDungeonQueue()
    println("Dungeon admission checks passed: ordering, exclusivity, persistence/teleport/rollback failures, retry and repeated cleanup")
}

private fun checkDungeonQueue() {
    val queue = DungeonQueue<String>()
    val alice = java.util.UUID.randomUUID()
    val bob = java.util.UUID.randomUUID()
    val chris = java.util.UUID.randomUUID()
    queue.join(alice, "alice-key"); queue.join(bob, "bob-key"); queue.join(alice, "replacement")
    check(queue.position(alice) == 1 && queue.position(bob) == 2)
    val first = requireNotNull(queue.reserve("north", 10, 600))
    check(first.player == alice && first.offer == "alice-key" && first.expires == 610L)
    check(queue.position(alice) == 0 && queue.reserved("north"))
    check(runCatching { queue.reserve("north", 10, 600) }.isFailure)
    queue.join(chris, "chris-key")
    check(queue.answer(bob, first.token) == null && queue.answer(alice, "forged") == null)
    val second = requireNotNull(queue.reserve("south", 10, 600))
    check(second.player == bob && second.token != first.token)
    check(queue.answer(alice, first.token) == first && queue.answer(alice, first.token) == null)
    check(!queue.reserved("north") && queue.reserved("south"))
    check(queue.reserve("north", 20, 600)?.player == chris)
    check(queue.remove(bob) == second && !queue.contains(bob))
    queue.clear(); queue.clear()
    check(queue.invitations.isEmpty() && !queue.hasWaiting && queue.position(chris) == -1)
    println("Dungeon queue checks passed: FIFO, two-slot reservations, duplicate joins, forged/replayed replies and cleanup")
}
