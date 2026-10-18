package app.buildatomos.core

import app.buildatomos.core.games.Hop
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Hop in levels: a small bot playing well clears it, a level takes a while, and heat makes it
 *  harder. */
class LevelsFTest {
    /** Plays `toy` frame by frame for up to `ms`, letting `bot` act; returns the stage. */
    private fun <T : Toy> run(toy: T, dial: Int, goal: Int?, ms: Double = 300_000.0, bot: (T, Stage) -> Unit = { _, _ -> }): Stage {
        val s = Stage()
        s.dial = dial
        s.goal = goal
        val pen = CheckPen()
        var now = 1000.0
        s.now = now
        toy.enter(s)
        while (now < ms && !s.over) {
            now += 16.0
            s.now = now
            bot(toy, s)
            toy.step(s, 16.0, now)
            toy.draw(s, pen, now)
            s.fx.draw(pen, now, 16.0)
            require(s.part in 0.0..1.0) { "part ${s.part}" }
        }
        return s
    }

    /** Steers by the crown, as a player watching the pads would: going up off a plain pad, under
     *  the next pad up; coming down, over the first pad below. */
    private val hopper: (Hop, Stage) -> Unit = { t, s ->
        val on = t.on
        val next = if (t.vy > 0) t.pads.filter { it.y - it.r * 0.6 - s.nu.r >= t.y }.minByOrNull { it.y }
        else if (on != null && !on.spring) t.pads.filter { it.y < on.y && on.y - it.y < 105 }.maxByOrNull { it.y }
        else null
        if (next != null) {
            val d = next.x - t.aim
            if (abs(d) > 0.5) t.crown(s, d / (t.run(s) * 2 / 3))
        }
    }

    @Test fun aGoodPlayerWinsAnEasyRun() {
        val hop = (1..5).count { run(Hop(), 1, 3, bot = hopper).result == Result.WON }
        assertTrue(hop >= 4, "hop won $hop of 5")
    }

    @Test fun aGoodPlayerClearsLevelsAtExpert() {
        assertTrue(run(Hop(), 100, 7, bot = hopper).level >= 2, "hop")
    }

    /** A level takes a while at the easiest: not over in a few seconds, not a slog. */
    @Test fun aLevelTakesAWhileAtTheEasiest() {
        val h = run(Hop(), 1, 1, bot = hopper)
        assertEquals(Result.WON, h.result, "hop")
        assertTrue(h.now in 5_000.0..45_000.0, "hop: first level took ${h.now} ms")
    }

    @Test fun heatMakesThemHarder() {
        // Hop's pads: smaller, and some drifting, at Expert; never too far apart to reach.
        fun hopAt(dial: Int, level: Int = 0): Triple<Double, Double, Int> {
            val s = Stage()
            s.dial = dial
            repeat(level) { s.clear() }
            val t = Hop()
            t.enter(s)
            val p = t.pads.drop(1)
            val gaps = t.pads.zipWithNext { a, b -> a.y - b.y }
            return Triple(p.map { it.r }.average(), gaps.maxOrNull() ?: 0.0, p.count { it.vx != 0.0 })
        }
        val (r0, g0, m0) = hopAt(1)
        val (r1, _, _) = hopAt(100)
        assertTrue(r1 < r0 && m0 == 0 && g0 < 70, "hop: pads $r0 vs $r1, $m0 drifting, gap $g0")
        val (r2, g2, _) = hopAt(100, 200)
        assertTrue(r2 >= 6.0 && g2 < 105, "hop deep in free play: pads $r2, gap $g2")
    }

    /** Left alone, it is lost, and well within a few minutes. */
    @Test fun ignoredTheyEnd() {
        for (dial in listOf(1, 100)) {
            val s = run(Hop(), dial, null, 600_000.0)
            assertEquals(Result.LOST, s.result, "hop at $dial")
            assertTrue(s.now < 120_000, "hop at $dial took ${s.now}")
        }
    }
}
