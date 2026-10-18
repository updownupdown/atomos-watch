package app.buildatomos.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GamesTest {
    /** Every game runs for two minutes of random play at every level, without a crash or a bad number. */
    @Test fun everyGamePlays() {
        for (g in Games.all) for (seed in 1..3) for (dial in listOf(1, 50, 100)) play(g, seed = seed, dial = dial, goal = 5)
    }

    /** Left alone, every game is lost by itself (no game waits forever for a player who walked off),
     *  unless it waits on Next. */
    @Test fun everyGameEndsWhenIgnored() {
        for (g in Games.all) for (dial in listOf(1, 100)) {
            val s = play(g, ms = 600_000.0, idle = true, dial = dial)
            assertTrue(s.result == Result.LOST || s.waiting != null, "${g.id} at $dial never ended")
        }
    }

    /** Free play has no goal: a game left to random play never wins, it can only clear levels. */
    @Test fun levelsOnlyGoUp() {
        for (g in Games.all) {
            val s = play(g, ms = 300_000.0, seed = 7, dial = 1)
            assertTrue(s.level in 0..MAX_LEVEL, g.id)
            assertTrue(s.result != Result.WON || s.level >= MAX_LEVEL, g.id)
        }
    }

    @Test fun nuPicksAGame() { for (d in 0L..400L) assertNotNull(Games.nuPick(d)) }
}

class StageTest {
    @Test fun aRunIsWonAtItsGoal() {
        val s = Stage()
        s.goal = 3
        s.clear(); s.clear()
        assertNull(s.result)
        assertEquals(2, s.level)
        s.clear()
        assertEquals(Result.WON, s.result)
        s.lose()
        assertEquals(Result.WON, s.result)
        s.clear()
        assertEquals(3, s.level)
    }

    @Test fun freePlayStopsAtTheTop() {
        val s = Stage()
        repeat(MAX_LEVEL - 1) { s.clear() }
        assertNull(s.result)
        s.clear()
        assertEquals(Result.WON, s.result)
    }

    @Test fun heatClimbs() {
        val s = Stage()
        s.dial = 1
        assertEquals(0.0, s.heat, 1e-9)
        s.dial = 100
        assertEquals(1.0, s.heat, 1e-9)
        s.clear()
        assertTrue(s.heat > 1.0)
    }
}

class DifficultyTest {
    @Test fun levelsAndGoals() {
        assertEquals(0, Difficulty.level(1)); assertEquals(0, Difficulty.level(20))
        assertEquals(1, Difficulty.level(21)); assertEquals(4, Difficulty.level(100))
        assertEquals(3, Difficulty.goal(5)); assertEquals(7, Difficulty.goal(90))
        for (l in 0..4) assertEquals(l, Difficulty.level(Difficulty.dialOf(l)))
        assertEquals(5, Difficulty.atomDial(1)); assertEquals(75, Difficulty.atomDial(118))
        for (z in 1..ATOMS) for (sh in 0 until periodOf(z)) assertTrue(Difficulty.goal(Difficulty.buildDial(z, sh)) <= 8)
    }
}
