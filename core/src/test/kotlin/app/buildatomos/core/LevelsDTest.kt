package app.buildatomos.core

import app.buildatomos.core.games.Shells
import app.buildatomos.core.games.Spot
import app.buildatomos.core.games.Stick
import app.buildatomos.core.games.Tune
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A pen that keeps the dots of the last frame drawn, for the players below to look at. */
private class Seen : Pen {
    class D(val x: Double, val y: Double, val r: Double, val c: Int, val a: Double)
    val dots = mutableListOf<D>()
    override fun dot(x: Double, y: Double, r: Double, color: Int, alpha: Double) { dots += D(x, y, r, color, alpha) }
    override fun ring(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double) {}
    override fun arc(x: Double, y: Double, r: Double, start: Double, sweep: Double, color: Int, width: Double, alpha: Double) {}
    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double, alpha: Double) {}
    override fun poly(xs: DoubleArray, ys: DoubleArray, n: Int, color: Int, width: Double, alpha: Double) {}
    override fun dashed(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double, n: Int) {}
    override fun rect(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, alpha: Double) {}
    override fun rectRing(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, width: Double, alpha: Double) {}
}

/**
 * Plays `t` at `dial` toward `goal`, a frame at a time, with `bot` choosing what to do from the
 * frame just drawn. Returns the stage when the run is over, or after `ms`.
 */
private fun <T : Toy> run(t: T, dial: Int, goal: Int?, ms: Double = 400_000.0, bot: (T, Stage, Seen) -> Unit): Stage {
    val s = Stage()
    s.dial = dial
    s.goal = goal
    t.enter(s)
    var now = 1000.0
    s.now = now
    var pen = Seen()
    while (now < ms && !s.over) {
        bot(t, s, pen)
        now += 16.0
        s.now = now
        t.step(s, 16.0, now)
        pen = Seen()
        t.draw(s, pen, now)
        assertTrue(s.part in 0.0..1.0, "part ${s.part}")
    }
    return s
}

class LevelsDTest {
    /** Shells, played by a player who never loses sight of Nu: taps where it is. */
    private fun shells(dial: Int, goal: Int?) = run(Shells(), dial, goal) { t, s, _ -> t.down(s, s.nu.p) }

    /** Spot, played by a player who sees the odd atom: the one dot of the field unlike the rest. */
    private fun spot(dial: Int, goal: Int?) = run(Spot(), dial, goal) { t, s, pen ->
        val field = pen.dots.filter { it.r >= 12 && it.c != Ink.ROSE }
        val odd = field.firstOrNull { d -> field.count { it.r == d.r && it.c == d.c } == 1 }
        if (odd != null && field.size > 2) t.down(s, Pt(odd.x, odd.y))
    }

    /** Stick, played by a player who taps as soon as the electron would land clear of the rest,
     *  allowing for the turn while it flies. */
    private fun stick(dial: Int, goal: Int?): Stage {
        var last = listOf<Double>()
        return run(Stick(), dial, goal) { t, s, pen ->
            val shell = s.R * 0.42
            val stuck = pen.dots.filter { it.r == 8.0 && it.a == 0.9 && it.c == Ink.CYAN }.map { atan2(it.y - s.c.y, it.x - s.c.x) }
            val ready = pen.dots.any { it.r == 8.0 && it.c == Ink.WHITE }
            if (ready && stuck.size == last.size && stuck.isNotEmpty()) {
                val w = turn(last[0], stuck[0]) / 16.0
                val room = 2 * asin(min(1.0, 8.0 / shell)) * 1.1
                val clear = stuck.all { a -> Math.abs(turn(a + w * 48, PI / 2)) > room + 0.06 && Math.abs(turn(a, PI / 2)) > room + 0.06 }
                if (clear) t.down(s, s.c)
            }
            last = stuck
        }
    }

    /** Tune, played by a player who turns the crown straight to the stretch going past. */
    private fun tune(dial: Int, goal: Int?) = run(Tune(), dial, goal) { t, s, _ ->
        val want = t.going ?: return@run
        t.crown(s, ln(want / t.tuned) / ln(8.0 / 1.2) * TAU)
    }

    /** Tune, played by a quick human: `react` ms after a new stretch comes past it starts turning
     *  the crown toward it, at most `rate` radians a frame. */
    private fun tuneHuman(dial: Int, goal: Int?, react: Double = 300.0, rate: Double = 0.12): Stage {
        var seen: Double? = null
        var since = 0.0
        return run(Tune(), dial, goal) { t, s, _ ->
            val want = t.going ?: return@run
            if (want != seen) { seen = want; since = s.now }
            if (s.now - since < react) return@run
            t.crown(s, clamp(ln(want / t.tuned) / ln(8.0 / 1.2) * TAU, -rate, rate))
        }
    }

    @Test fun shellsClearsLevels() {
        for (dial in listOf(1, 100)) assertEquals(Result.WON, shells(dial, 3).result, "shells at $dial")
    }

    @Test fun spotClearsLevels() {
        for (dial in listOf(1, 100)) assertEquals(Result.WON, spot(dial, 3).result, "spot at $dial")
    }

    @Test fun stickClearsLevels() {
        // The player is good, not perfect: of a few runs at the gentlest, most are won.
        val won = (1..5).count { stick(1, 3).result == Result.WON }
        assertTrue(won >= 4, "stick won $won of 5")
        assertTrue((1..5).any { stick(100, 3).level >= 1 }, "stick never cleared a level at Expert")
    }

    @Test fun tuneClearsLevels() {
        for (dial in listOf(50, 100)) assertEquals(Result.WON, tune(dial, 3).result, "tune at $dial")
    }

    /** A quick human who only answers a stretch once it reaches Nu wins an Easy run, still clears
     *  levels at the top of Hard, and gets plainly less far there in free play than at Easy. */
    @Test fun tuneComesHarderWithTheDial() {
        val won = (1..4).count { tuneHuman(50, 5).result == Result.WON }
        assertTrue(won >= 3, "tune at Easy won $won of 4")
        val easy = (1..4).sumOf { tuneHuman(50, null).level }
        val hard = (1..4).sumOf { tuneHuman(100, null).level }
        assertTrue(hard >= 4, "tune at the top of Hard: $hard levels in 4 runs")
        assertTrue(hard * 2 < easy, "levels in 4 runs: $easy at Easy, $hard at the top of Hard")
    }

    /** Free play goes on, each level a little harder, until the player fails or the top. */
    @Test fun freePlayGoesOn() {
        val s = shells(1, null)
        assertTrue(s.level >= 10 || s.result == Result.WON, "shells stopped at ${s.level}")
        val t = spot(50, null)
        assertTrue(t.level >= 10 || t.result == Result.WON, "spot stopped at ${t.level}")
    }

    @Test fun heatMakesThemHarder() {
        // Shells: longer and quicker shuffles.
        assertTrue(Shells.swaps(1.0) > Shells.swaps(0.0))
        assertTrue(Shells.swapMs(1.0) < Shells.swapMs(0.0))
        assertTrue(Shells.swaps(1.0) * Shells.swapMs(1.0) > Shells.swaps(0.0) * Shells.swapMs(0.0))
        assertTrue(Shells.swapMs(2.5) >= 180.0)
        // Spot: a bigger field, a subtler odd one and a quicker rim; never past what can be seen.
        assertTrue(Spot.size(1.0) > Spot.size(0.0))
        assertTrue(Spot.offBy(1.0) < Spot.offBy(0.0))
        assertTrue(Spot.rimMs(1.0) < Spot.rimMs(0.0))
        assertTrue(Spot.offBy(10.0) >= 0.08 && Spot.rimMs(10.0) >= 2500.0)
        // Stick: more on the shell, more to place and faster turning; room still left for the last.
        assertTrue(Stick.already(1.0, 0) > Stick.already(0.0, 0))
        assertTrue(Stick.toPlace(1.0, 0, 3) > Stick.toPlace(0.0, 0, 1))
        assertTrue(Stick.toPlace(0.05, 1, Stick.already(0.05, 1)) > Stick.toPlace(0.0, 0, Stick.already(0.0, 0)))
        assertTrue(Stick.speed(1.0) > Stick.speed(0.0))
        for (lv in 0..MAX_LEVEL) {
            val h = 1 + lv * HEAT_PER_LEVEL
            val n = Stick.already(h, lv)
            assertTrue(n + Stick.toPlace(h, lv, n) <= Stick.FULL)
        }
        // Tune: shorter stretches, a closer match, more of each to match.
        assertTrue(Tune.stretchMs(1.0) < Tune.stretchMs(0.0))
        assertTrue(Tune.tolF(1.0) < Tune.tolF(0.0) && Tune.tolP(1.0) < Tune.tolP(0.0))
        assertTrue(Tune.share(1.0) > Tune.share(0.0))
    }

    /** At Expert, Shells keeps a found Nu hidden longer before the pick than at the gentlest. */
    @Test fun shellsShufflesLongerWhenHot() {
        fun firstFind(dial: Int): Double {
            var at = 0.0
            run(Shells(), dial, null, ms = 60_000.0) { t, s, _ ->
                if (at == 0.0) { t.down(s, s.nu.p); if (s.part > 0) at = s.now }
            }
            return at
        }
        assertTrue(firstFind(100) > firstFind(1) + 1000)
    }
}
