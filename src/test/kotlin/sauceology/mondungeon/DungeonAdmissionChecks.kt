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
    println("Dungeon admission checks passed: ordering, exclusivity, persistence/teleport/rollback failures, retry and repeated cleanup")
}
