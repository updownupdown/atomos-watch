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

    /** Nu picks every game in turn, one a day, whatever the number of games. */
    @Test fun nuPicksEveryGame() {
        assertEquals(Games.all.size, (0L until Games.all.size).map { Games.nuPick(it).id }.toSet().size)
    }
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
        assertEquals(3, Difficulty.LEVELS)
        assertEquals(listOf("Easy", "Medium", "Hard"), Difficulty.WORDS)
        assertEquals(0, Difficulty.level(1)); assertEquals(0, Difficulty.level(41)); assertEquals(0, Difficulty.level(60))
        assertEquals(1, Difficulty.level(61)); assertEquals(1, Difficulty.level(80))
        assertEquals(2, Difficulty.level(81)); assertEquals(2, Difficulty.level(100))
        assertEquals(listOf(50, 70, 90), (0..2).map { Difficulty.dialOf(it) })
        for (l in 0..2) assertEquals(l, Difficulty.level(Difficulty.dialOf(l)))
        assertEquals(3, Difficulty.goal(41)); assertEquals(4, Difficulty.goal(70)); assertEquals(5, Difficulty.goal(100))
    }

    /** Every shell of every atom is built inside the three levels: hydrogen at Easy's foot,
     *  oganesson's outer shell at the top of Hard, harder atom by atom and shell by shell. */
    @Test fun buildRunsPlayInsideTheLevels() {
        assertEquals(41, Difficulty.atomDial(1)); assertEquals(54, Difficulty.atomDial(26))
        assertEquals(65, Difficulty.atomDial(54)); assertEquals(77, Difficulty.atomDial(86))
        assertEquals(88, Difficulty.atomDial(118))
        assertEquals(100, Difficulty.buildDial(118, periodOf(118) - 1))
        var last = 0
        for (z in 1..ATOMS) {
            assertTrue(Difficulty.atomDial(z) >= last, "atom $z")
            last = Difficulty.atomDial(z)
            for (sh in 0 until periodOf(z)) {
                val d = Difficulty.buildDial(z, sh)
                assertTrue(d in 41..100, "atom $z shell $sh at $d")
                assertTrue(Difficulty.goal(d) in 3..5)
            }
        }
        // Each level builds a good stretch of the table.
        val levels = (1..ATOMS).map { Difficulty.level(Difficulty.atomDial(it)) }
        for (l in 0..2) assertTrue(levels.count { it == l } >= 15, "level $l: ${levels.count { it == l }} atoms")
    }

    /** A save from the five-level watch: its Medium, Hard and Expert become Easy, Medium and Hard;
     *  bests at its Very easy and Easy go, and a pick of either becomes Easy. */
    @Test fun fiveLevelSavesMove() {
        assertEquals(listOf(null, null, 0, 1, 2), (0..4).map { Difficulty.fromFiveLevels(it) })
        val bests = Difficulty.bestsFromFiveLevels(
            mapOf("orbit.0" to 30, "orbit.1" to 20, "orbit.2" to 12, "orbit.3" to 7, "orbit.4" to 3, "pop.1" to 9, "bad" to 4, "tune.x" to 2),
        )
        assertEquals(mapOf("orbit.0" to 12, "orbit.1" to 7, "orbit.2" to 3), bests)
        assertEquals(
            mapOf("orbit" to 0, "pop" to 0, "tune" to 0, "hop" to 1, "fuse" to 2),
            Difficulty.pickedFromFiveLevels(mapOf("orbit" to 0, "pop" to 1, "tune" to 2, "hop" to 3, "fuse" to 4)),
        )
    }
}
