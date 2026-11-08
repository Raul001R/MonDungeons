package sauceology.mondungeon

/** Server-thread admission transaction: persist first, move second, release on any failure. */
internal class DungeonAdmission<T> {
    var active: T? = null
        private set

    fun enter(run: T, remember: () -> Unit, move: () -> Boolean, rollback: () -> Unit): Boolean {
        if (active != null) return false
        active = run
        try {
            remember()
            check(move()) { "Arena teleport was refused" }
        } catch (exception: Exception) {
            clear()
            try { rollback() } catch (rollbackFailure: Exception) { exception.addSuppressed(rollbackFailure) }
            throw exception
        }
        return true
    }

    fun clear() { active = null }
}
