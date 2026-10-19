package app.buildatomos.core

import app.buildatomos.core.games.Hue
import app.buildatomos.core.games.Land
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Hue and Land in levels: a small bot playing well clears them, and they leave a player time:
 *  Hue's hops are small steps, and Land falls slowly enough to answer, by touch alone. */
class LevelsHTest {
    /** Plays `toy` frame by frame for up to `ms`, letting `bot` act before each step; returns the stage. */
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

    private fun tap(t: Toy, s: Stage) { t.down(s, s.c); t.up(s, s.c) }

    // ---- Hue ----

    /** Turns the ring ahead with the crown (a quick hand, not an instant one) to bring Nu's colour
     *  to the crossing it will pass next, and hops: on up while the colour is there, else holding
     *  below the crossing until it is. */
    private val huer: (Hue, Stage) -> Unit = { t, s ->
        val q = t.target()
        if (q != null) {
            val below = t.y > q.y + Hue.R - 10
            val at = if (below) PI / 2 else -PI / 2
            val err = turn(q.a, at - (t.hue + 0.5) * TAU / q.n)
            t.crown(s, clamp(err, -0.08, 0.08))
            val cross = q.y + if (below) Hue.R else -Hue.R
            if (abs(err) < 0.25) { if (t.vy > 0.04) tap(t, s) }
            else if (t.y > cross + 60 && t.vy > 0) tap(t, s)
        }
    }

    @Test fun hueClearsLevels() {
        val won = (1..5).count { run(Hue(), 50, 5, bot = huer).result == Result.WON }
        assertTrue(won >= 4, "hue at Easy won $won of 5")
        val expert = (1..3).maxOf { run(Hue(), 100, 7, bot = huer).level }
        assertTrue(expert >= 3, "hue at the top of Hard reached level $expert")
    }

    /** A hop is a small step: it rises well under half a ring's span plus its gap, and tops out
     *  after about a third of a second. */
    @Test fun hueHopsGently() {
        var from = 0.0
        var top = Double.MAX_VALUE
        var topAt = 0.0
        var hopped = -1.0
        run(Hue(), 50, null, 2600.0) { t, s ->
            if (hopped < 0 && s.now >= 1200) { from = t.y; tap(t, s); hopped = s.now }
            if (hopped >= 0 && t.y < top) { top = t.y; topAt = s.now }
        }
        val rise = from - top
        assertTrue(rise in 32.0..42.0, "a hop rose $rise")
        assertTrue(topAt - hopped in 280.0..430.0, "a hop topped out after ${topAt - hopped} ms")
    }

    @Test fun hueHeatAddsColoursAndSpin() {
        fun first(dial: Int): Pair<Int, Double> {
            val s = Stage()
            s.dial = dial
            val t = Hue()
            t.enter(s)
            return t.rings[0].n to t.rings.maxOf { abs(it.v) / (0.7 + it.n * 0.06) }
        }
        val (n0, v0) = first(50)
        val (n1, v1) = first(100)
        assertEquals(2, n0, "Easy starts a set at two colours")
        assertEquals(3, first(70).first, "Medium starts a set at three")
        assertTrue(n1 == 3 && v1 > v0, "colours $n0 -> $n1, spin $v0 -> $v1")
    }

    /** A run keeps getting harder as it climbs, at every dial: six levels into Hard the rings
     *  turn plainly faster, come closer and have more colours than at its start. */
    @Test fun hueClimbsWithLevels() {
        val t = Hue()
        for (dial in listOf(50, 90)) {
            val s = Stage()
            s.dial = dial
            val h0 = t.heat(s)
            repeat(6) { s.clear() }
            val h6 = t.heat(s)
            assertTrue(t.spin(h6, 3) >= t.spin(h0, 3) * 1.3, "spin at dial $dial: ${t.spin(h0, 3)} -> ${t.spin(h6, 3)}")
            assertTrue(t.extra(h6) >= t.extra(h0) + 2, "colours at dial $dial: +${t.extra(h0)} -> +${t.extra(h6)}")
            assertTrue(t.gap(h6) < t.gap(h0) || t.gap(h6) == 72.0, "gap at dial $dial: ${t.gap(h0)} -> ${t.gap(h6)}")
        }
    }

    // ---- Land ----

    /** Plays by touch alone: holds a finger beside Nu to lean it toward the pad, and holds on
     *  whenever it falls faster than it should for its height. */
    private fun lander(): (Land, Stage) -> Unit {
        var held = false
        return { t, s ->
            val nu = s.nu
            if (!t.flying) {
                if (held) { t.up(s, nu.p); held = false }
            } else {
                val (px, pw) = t.pad
                val v = t.speed
                val lim = t.safeSpeed
                val h = s.c.y + s.R * 0.56 - nu.r - nu.y
                val dx = px - nu.x
                val far = abs(dx) > pw / 2 - 6
                val wantVx = clamp(dx / 400, -0.08, 0.08) * if (h < 40) 0.4 else 1.0
                // Slow down near the floor, well under the limit, and hang back while still off to
                // the side.
                val wantVy = if (far) clamp((h - 45) * 0.002, -0.02, 0.1) else clamp(0.02 + h * 0.0008, 0.02, 0.1).coerceAtMost(lim * 0.55)
                // Land's lean: 0.00018 at its strongest, 70 units aside, while held (about 40% of
                // the time, hovering).
                val lean = clamp(((wantVx - v.x) / 250 - t.windNow) / 0.00018 / 0.4, -1.0, 1.0)
                val hold = v.y > wantVy || (far && abs(lean) > 0.3 && v.y > wantVy - 0.05)
                if (hold) {
                    val f = Pt(nu.x + lean * 70, nu.y + 40)
                    if (held) t.move(s, f) else { t.down(s, f); held = true }
                } else if (held) { t.up(s, nu.p); held = false }
            }
        }
    }

    @Test fun landClearsLevels() {
        val won = (1..5).count { run(Land(), 50, 5, bot = lander()).result == Result.WON }
        assertTrue(won >= 4, "land at Easy won $won of 5")
        val expert = (1..3).maxOf { run(Land(), 100, 7, bot = lander()).level }
        assertTrue(expert >= 2, "land at the top of Hard reached level $expert")
    }

    /** Falling from the top untouched at Easy, Nu stays under the limit for well over half a
     *  second: time to see it and answer. Left alone it still crashes. */
    @Test fun landGivesTimeToAnswer() {
        var from = -1.0
        var fast = -1.0
        val s = run(Land(), 50, null, 60_000.0) { t, st ->
            if (t.flying && from < 0) from = st.now
            if (from >= 0 && fast < 0 && hypot(t.speed.x, t.speed.y) >= t.safeSpeed) fast = st.now
        }
        assertTrue(fast - from >= 600, "unsafe after ${fast - from} ms")
        assertEquals(Result.LOST, s.result)
    }

    /** The speed limit is plainly stricter at each difficulty (at least an eighth less each step)
     *  and with each level cleared, and Land leaves the rim alone: its fuel shows over Nu. */
    @Test fun landLimitStepsWithDifficulty() {
        fun limit(dial: Int, levels: Int = 0): Double {
            val s = Stage()
            s.dial = dial
            repeat(levels) { s.clear() }
            val t = Land()
            t.enter(s)
            assertEquals(null, s.rim, "Land set the rim")
            return t.safeSpeed
        }
        val easy = limit(50)
        val medium = limit(70)
        val hard = limit(90)
        assertTrue(medium <= easy * 0.88 && hard <= medium * 0.88, "limits $easy, $medium, $hard")
        assertTrue(limit(90, 3) < hard, "the limit tightens with levels: ${limit(90, 3)} vs $hard")
        var rim = false
        run(Land(), 50, null, 20_000.0) { t, s ->
            if (t.flying) t.down(s, s.nu.p)
            if (s.rim != null) rim = true
        }
        assertFalse(rim, "Land set the rim while flying")
    }

    /** Land is steered by touch alone: the crown does nothing. */
    @Test fun landIgnoresTheCrown() {
        assertFalse(Games.byId("land")!!.crown)
        var before: Pt? = null
        var after: Pt? = null
        run(Land(), 50, null, 8000.0) { t, s ->
            if (t.flying && before == null) {
                before = t.speed.copy()
                t.crown(s, 1.0)
                after = t.speed.copy()
            }
        }
        assertTrue(before != null && before!!.x == after!!.x && before!!.y == after!!.y, "crown moved Nu: $before -> $after")
    }
}
