package app.buildatomos.core

import app.buildatomos.core.games.More
import app.buildatomos.core.games.Rain
import app.buildatomos.core.games.Spot
import app.buildatomos.core.games.Stick
import kotlin.test.Test
import kotlin.test.assertTrue

/** Stick, More, Spot and Rain, before they join the list in Games.kt. Par from src/nu/points.ts. */
class Port3Test {
    private val games = listOf(
        Game("stick", "Stick", 8.0) { Stick() },
        Game("more", "More", 10.0) { More() },
        Game("spot", "Spot", 8.0) { Spot() },
        Game("rain", "Rain", 12.0, crown = true) { Rain() },
    )

    @Test fun theyPlay() {
        for (g in games) for (seed in 1..4) play(g, seed = seed)
    }

    @Test fun theyEndWhenIgnored() {
        for (g in games) {
            val s = play(g, ms = 600_000.0, idle = true)
            assertTrue(s.ended != null || s.waiting != null, "${g.id} never ended")
        }
    }
}
