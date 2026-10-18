package app.buildatomos.core

import app.buildatomos.core.games.Flash
import app.buildatomos.core.games.Pulse
import app.buildatomos.core.games.Ready
import app.buildatomos.core.games.Recall
import kotlin.test.Test
import kotlin.test.assertTrue

/** Flash, Pulse, Ready and Recall, before they join the registry: they play, and end when left alone. */
class Port2Test {
    private val games = listOf(
        Game("flash", "Flash", 20.0) { Flash() },
        Game("pulse", "Pulse", 8.0) { Pulse() },
        Game("ready", "Ready", 10.0) { Ready() },
        Game("recall", "Recall", 5.0) { Recall() },
    )

    @Test fun theyPlay() {
        for (g in games) for (seed in 1..4) play(g, seed = seed)
    }

    @Test fun theyEndWhenIgnored() {
        for (g in games) for (seed in 1..4) {
            val s = play(g, ms = 600_000.0, idle = true, seed = seed)
            assertTrue(s.ended != null || s.waiting != null, "${g.id} never ended")
        }
    }
}
