package app.buildatomos.core

import app.buildatomos.core.games.*
import kotlin.test.Test
import kotlin.test.assertTrue

/** Juggle, Ripple, Blast and Tune, before they are in the registry: each plays, and each ends
 *  when nobody plays it. */
class Port4Test {
    private val games = listOf(
        Game("juggle", "Juggle", 15.0) { Juggle() },
        Game("ripple", "Ripple", 6.0) { Ripple() },
        Game("blast", "Blast", 15.0) { Blast() },
        Game("tune", "Tune", 15.0, crown = true) { Tune() },
    )

    @Test fun eachPlays() {
        for (g in games) for (seed in 1..4) play(g, seed = seed)
    }

    @Test fun eachEndsWhenIgnored() {
        for (g in games) {
            val s = play(g, ms = 600_000.0, idle = true)
            assertTrue(s.ended != null || s.waiting != null, "${g.id} never ended")
        }
    }

    /** Random play reaches the end of a run (or keeps going) without a bad count. */
    @Test fun countsAreWhole() {
        for (g in games) for (seed in 1..4) {
            val s = play(g, ms = 600_000.0, seed = seed)
            val n = s.ended ?: s.shown
            assertTrue(n >= 0 && n == Math.floor(n), "${g.id} counted $n")
        }
    }
}
