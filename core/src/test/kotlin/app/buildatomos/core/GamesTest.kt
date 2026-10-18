package app.buildatomos.core

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GamesTest {
    /** Every game runs for two minutes of random play without a crash or a bad number. */
    @Test fun everyGamePlays() {
        for (g in Games.all) for (seed in 1..4) play(g, seed = seed)
    }

    /** Left alone, every game ends by itself (no game waits forever for a player who walked off),
     *  unless it waits on Next. */
    @Test fun everyGameEndsWhenIgnored() {
        for (g in Games.all) {
            val s = play(g, ms = 600_000.0, idle = true)
            assertTrue(s.ended != null || s.waiting != null, "${g.id} never ended")
        }
    }

    @Test fun pointsArePar() {
        for (g in Games.all) assertTrue(Games.points(g.id, g.par) == 10.0, g.id)
    }

    @Test fun nuPicksAGame() { for (d in 0L..400L) assertNotNull(Games.nuPick(d)) }
}
