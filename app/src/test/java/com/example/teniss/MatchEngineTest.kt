package com.example.teniss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchEngineTest {

    private fun MatchEngine.win(p: Int, times: Int) = repeat(times) { pointWon(p) }

    private fun MatchEngine.winGame(p: Int) = win(p, 4)

    private fun MatchEngine.winSetLove(p: Int) = repeat(6) { winGame(p) }

    @Test
    fun pointLabels_progress_0_15_30_40() {
        val e = MatchEngine().apply { reset(0) }
        assertEquals("0", e.pointLabel(0))
        e.pointWon(0); assertEquals("15", e.pointLabel(0))
        e.pointWon(0); assertEquals("30", e.pointLabel(0))
        e.pointWon(0); assertEquals("40", e.pointLabel(0))
        assertEquals("0", e.pointLabel(1))
    }

    @Test
    fun game_won_at_four_points_with_two_lead() {
        val e = MatchEngine().apply { reset(0) }
        e.win(0, 3)
        assertEquals(MatchEngine.Result.GAME, e.pointWon(0))
        assertEquals(1, e.games[0])
        assertEquals("0", e.pointLabel(0))
    }

    @Test
    fun deuce_and_advantage() {
        val e = MatchEngine().apply { reset(0) }
        e.win(0, 3); e.win(1, 3)
        assertTrue(e.isDeuce)
        assertEquals("40", e.pointLabel(0)); assertEquals("40", e.pointLabel(1))
        e.pointWon(0)
        assertEquals("AV", e.pointLabel(0)); assertEquals("40", e.pointLabel(1))
        e.pointWon(1)
        assertEquals("40", e.pointLabel(0)); assertEquals("40", e.pointLabel(1))
        e.pointWon(1)
        assertEquals(MatchEngine.Result.GAME, e.pointWon(1))
        assertEquals(1, e.games[1])
    }

    @Test
    fun server_alternates_each_game() {
        val e = MatchEngine().apply { reset(0) }
        assertEquals(0, e.server)
        e.winGame(0); assertEquals(1, e.server)
        e.winGame(1); assertEquals(0, e.server)
    }

    @Test
    fun set_won_at_six_games() {
        val e = MatchEngine().apply { reset(0) }
        repeat(5) { e.winGame(0) }
        e.win(0, 3)
        assertEquals(MatchEngine.Result.SET, e.pointWon(0))
        assertEquals(1, e.sets[0])
        assertEquals(0, e.games[0])
        assertEquals(listOf("6-0"), e.setHistory)
    }

    @Test
    fun tie_break_at_six_all_and_set_ends_seven_six() {
        val e = MatchEngine().apply { reset(0) }
        repeat(5) { e.winGame(0); e.winGame(1) }
        e.winGame(0); e.winGame(1) // 6-6
        assertTrue(e.isTieBreak)
        e.win(0, 6)
        assertFalse(e.sets[0] == 1)
        assertEquals(MatchEngine.Result.SET, e.pointWon(0))
        assertEquals(listOf("7-6"), e.setHistory)
        assertFalse(e.isTieBreak)
    }

    @Test
    fun tie_break_needs_two_point_lead() {
        val e = MatchEngine().apply { reset(0) }
        repeat(5) { e.winGame(0); e.winGame(1) }
        e.winGame(0); e.winGame(1)
        repeat(6) { e.pointWon(0); e.pointWon(1) } // 6-6 in tie-break
        e.pointWon(0)
        assertEquals(0, e.sets[0]) // 7-6 not enough
        assertEquals(MatchEngine.Result.SET, run { e.pointWon(0) }) // 8-6
    }

    @Test
    fun seven_five_wins_a_set_without_tie_break() {
        val e = MatchEngine().apply { reset(0) }
        repeat(5) { e.winGame(0); e.winGame(1) } // 5-5
        e.winGame(0) // 6-5
        assertEquals(0, e.sets[0])
        e.win(0, 3)
        assertEquals(MatchEngine.Result.SET, e.pointWon(0)) // 7-5
        assertEquals(listOf("7-5"), e.setHistory)
    }

    @Test
    fun match_won_after_two_sets() {
        val e = MatchEngine().apply { reset(1) }
        e.winSetLove(1)
        repeat(5) { e.winGame(1) }
        e.win(1, 3)
        assertEquals(MatchEngine.Result.MATCH, e.pointWon(1))
        assertEquals(1, e.winner)
        assertEquals(2, e.sets[1])
    }

    @Test
    fun match_goes_to_third_set() {
        val e = MatchEngine().apply { reset(0) }
        e.winSetLove(0)
        e.winSetLove(1)
        assertEquals(-1, e.winner)
        assertEquals(1, e.sets[0]); assertEquals(1, e.sets[1])
    }
}
