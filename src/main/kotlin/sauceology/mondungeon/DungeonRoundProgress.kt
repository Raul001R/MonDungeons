package sauceology.mondungeon

import java.util.UUID

data class RoundAdvance(val finishedRound: Int, val heal: Boolean, val complete: Boolean)

/** The battle ID is removed before advancing, making unrelated and duplicate results inert. */
class DungeonRoundProgress(private val roundCount: Int, private val healingRounds: Set<Int>) {
    init { require(roundCount > 0) }
    var currentRound = 1
        private set
    var expectedBattleId: UUID? = null
        private set
    var complete = false
        private set
    fun attach(id: UUID) {
        check(!complete && expectedBattleId == null)
        expectedBattleId = id
    }
    fun victory(id: UUID): RoundAdvance? {
        if (complete || expectedBattleId != id) return null
        expectedBattleId = null
        val finished = currentRound
        complete = finished == roundCount
        if (!complete) currentRound++
        return RoundAdvance(finished, !complete && finished in healingRounds, complete)
    }
}
