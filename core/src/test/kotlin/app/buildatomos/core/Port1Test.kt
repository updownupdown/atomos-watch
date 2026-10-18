package app.buildatomos.core

import app.buildatomos.core.games.*
import kotlin.test.Test
import kotlin.test.assertTrue

/** Orbit, Dive, Echo and Shells, before they join Games.all. */
class Port1Test {
    private val games = listOf(
        Game("orbit", "Orbit", 12.0, crown = true) { Orbit() },
        Game("dive", "Dive", 5.0, crown = false) { Dive() },
        Game("echo", "Echo", 6.0, crown = false) { Echo() },
        Game("shells", "Shells", 4.0, crown = false) { Shells() },
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

    /** Played at random, a run that ends does so with a sane count. */
    @Test fun countsAreSane() {
        for (g in games) for (seed in 1..4) {
            val s = play(g, ms = 600_000.0, seed = seed)
            val n = s.ended ?: continue
            assertTrue(n >= 0 && n.isFinite(), "${g.id}: $n")
        }
    }
}
