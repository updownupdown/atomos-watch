package app.buildatomos.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A pen that keeps the dots and rings of the last frame, so a bot can see what the player sees. */
private class Spy : Pen {
    class Mark(val ring: Boolean, val x: Double, val y: Double, val r: Double, val color: Int, val width: Double, val alpha: Double)
    val marks = mutableListOf<Mark>()
    fun rings(color: Int, width: Double) = marks.filter { it.ring && it.color == color && it.width == width }
    fun dots(color: Int, r: Double? = null) = marks.filter { !it.ring && it.color == color && (r == null || it.r == r) }
    override fun dot(x: Double, y: Double, r: Double, color: Int, alpha: Double) { marks += Mark(false, x, y, r, color, 0.0, alpha) }
    override fun ring(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double) { marks += Mark(true, x, y, r, color, width, alpha) }
    override fun arc(x: Double, y: Double, r: Double, start: Double, sweep: Double, color: Int, width: Double, alpha: Double) {}
    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double, alpha: Double) {}
    override fun poly(xs: DoubleArray, ys: DoubleArray, n: Int, color: Int, width: Double, alpha: Double) {}
    override fun dashed(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double, n: Int) {}
    override fun rect(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, alpha: Double) {}
    override fun rectRing(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, width: Double, alpha: Double) {}
}

/** Pulse, Rain, Ready, Recall and Ripple in levels: a bot that plays well clears them, and the dial
 *  makes them harder. */
class LevelsCTest {
    /** Plays `id` at `dial` toward `goal` for up to `ms`, the bot acting on the last frame drawn. */
    private fun run(id: String, dial: Int, goal: Int?, ms: Double, bot: (Stage, Toy, Spy) -> Unit): Stage {
        val s = Stage()
        s.dial = dial
        s.goal = goal
        val t = Games.byId(id)!!.make()
        val spy = Spy()
        val fx = CheckPen()
        t.enter(s)
        var now = 1000.0
        s.now = now
        while (now < ms && !s.over) {
            now += 16.0
            s.now = now
            bot(s, t, spy)
            s.waiting?.let { go -> s.waiting = null; go() }
            t.step(s, 16.0, now)
            spy.marks.clear()
            t.draw(s, spy, now)
            s.fx.draw(fx, now, 16.0)
        }
        return s
    }

    private fun tap(s: Stage, t: Toy, p: Pt = s.c) { t.down(s, p); t.up(s, p) }

    // ---- Pulse ----

    /** Taps as the wave reaches the middle of the band. */
    private fun pulseBot(s: Stage, t: Toy, spy: Spy) {
        val wave = spy.rings(Ink.WHITE, 3.0).firstOrNull() ?: return
        val band = spy.rings(Ink.CYAN, 2.0).firstOrNull() ?: return
        if (wave.r >= band.r - 1) tap(s, t)
    }

    @Test fun pulseClears() {
        assertEquals(Result.WON, run("pulse", 50, 5, 240_000.0, ::pulseBot).result)
        assertTrue(run("pulse", 100, null, 120_000.0, ::pulseBot).level >= 3)
    }

    /** The first wave waits a moment before it spreads. */
    @Test fun pulseWaitsAtTheStart() {
        var first = -1.0
        run("pulse", 50, null, 5000.0) { s, _, spy -> if (first < 0 && spy.rings(Ink.WHITE, 3.0).isNotEmpty()) first = s.now }
        assertTrue(first - 1000 >= 1200, "first wave at ${first - 1000} ms")
    }

    @Test fun pulseHeats() {
        fun look(dial: Int): Pair<Double, Double> {
            var r0 = -1.0; var speed = 0.0; var band = 0.0
            run("pulse", dial, null, 4000.0) { _, _, spy ->
                val w = spy.rings(Ink.WHITE, 3.0).firstOrNull()
                if (w != null) { if (r0 >= 0) speed = (w.r - r0) / 16; r0 = w.r }
                spy.marks.firstOrNull { it.ring && it.width > 4 }?.let { band = it.width }
            }
            return speed to band
        }
        val (slow, wide) = look(1)
        val (fast, narrow) = look(100)
        assertTrue(fast > slow * 1.5, "wave $slow -> $fast")
        assertTrue(narrow < wide * 0.6, "band $wide -> $narrow")
    }

    // ---- Rain ----

    /** Holds a finger down and slides Nu toward the lowest photon, by the nearest spot it can reach
     *  without a red landing on it on the way. */
    private fun rainBot(): (Stage, Toy, Spy) -> Unit {
        var last = listOf<Spy.Mark>()
        var held = false
        return { s, t, spy ->
            val nu = s.nu
            if (!held) { t.down(s, Pt(nu.x, 200.0)); held = true }
            val photon = spy.dots(Ink.CYAN, 5.0).filter { it.alpha == 1.0 && it.y < nu.y - 4 }.maxByOrNull { it.y }
            val reds = spy.dots(Ink.RED, 6.0).filter { it.y < nu.y + 20 }
            // Each red's fall, from where it was a frame ago.
            fun fall(r: Spy.Mark) = last.firstOrNull { abs(it.x - r.x) < 4 && r.y - it.y in 0.5..14.0 }?.let { (r.y - it.y) / 16 } ?: 0.3
            val want = photon?.x ?: s.c.x
            // How near the reds come to Nu on its way to `x`, while each passes Nu's row.
            fun danger(x: Double) = reds.sumOf { r ->
                val v = fall(r)
                val enter = ((nu.y - 18 - r.y) / v).coerceAtLeast(0.0)
                val exit = ((nu.y + 18 - r.y) / v).coerceAtLeast(0.0)
                fun at(ms: Double) = nu.x + nu.vx * minOf(ms, 90.0) * 0.5 + (x - nu.x).coerceIn(-0.7 * ms, 0.7 * ms)
                val a = at(enter); val b = at(exit)
                val near = if (r.x in minOf(a, b)..maxOf(a, b)) 0.0 else minOf(abs(a - r.x), abs(b - r.x))
                if (enter < 1400) (36 - near).coerceAtLeast(0.0) * (1400 - enter) else 0.0
            }
            var to = nu.x
            var best = Double.MAX_VALUE
            var x = s.c.x - 90
            while (x <= s.c.x + 90) {
                val cost = abs(x - want) + abs(x - nu.x) * 0.3 + danger(x)
                if (cost < best) { best = cost; to = x }
                x += 6
            }
            t.move(s, Pt(to, 200.0))
            val lv = spy.dots(Ink.WHITE, 4.0).size
            last = spy.dots(Ink.RED, 6.0)
        }
    }

    @Test fun rainClears() {
        val won = (1..5).count { run("rain", 1, 3, 180_000.0, rainBot()).result == Result.WON }
        assertTrue(won >= 3, "won $won of 5")
    }

    @Test fun rainHeats() {
        /** Drops falling past the middle of the court, a second, while the bot plays. */
        fun rate(dial: Int): Double {
            var frames = 0; var past = 0
            var last = listOf<Spy.Mark>()
            val bot = rainBot()
            run("rain", dial, null, 15_000.0) { s, t, spy ->
                bot(s, t, spy)
                val drops = spy.dots(Ink.CYAN, 5.0).filter { it.alpha == 1.0 } + spy.dots(Ink.RED, 6.0)
                frames++
                past += drops.count { d -> d.y >= s.c.y && last.any { abs(it.x - d.x) < 4 && it.y < s.c.y && d.y - it.y < 14 } }
                last = drops
            }
            return past * 1000.0 / 16 / frames.coerceAtLeast(1)
        }
        val few = (1..3).sumOf { rate(1) }
        val many = (1..3).sumOf { rate(100) }
        assertTrue(many > few * 1.8, "drops a second $few -> $many")
    }

    // ---- Ready ----

    /** Taps a green 150 ms after it shows, never a red. */
    private fun readyBot(): (Stage, Toy, Spy) -> Unit {
        var seen = -1.0
        return { s, t, spy ->
            val go = spy.dots(Ink.GREEN, s.nu.r + 10).isNotEmpty()
            if (!go) seen = -1.0
            else if (seen < 0) seen = s.now
            else if (s.now - seen >= 150) { tap(s, t); seen = -1.0 }
        }
    }

    @Test fun readyClears() {
        assertEquals(Result.WON, run("ready", 1, 3, 180_000.0, readyBot()).result)
        assertEquals(Result.WON, run("ready", 100, 7, 300_000.0, readyBot()).result)
    }

    @Test fun readyHeats() {
        /** How long the first green waits for a tap that never comes. */
        fun window(dial: Int): Double {
            var from = -1.0; var to = -1.0
            run("ready", dial, null, 12_000.0) { s, _, spy ->
                val go = spy.dots(Ink.GREEN, s.nu.r + 10).isNotEmpty()
                if (go && from < 0) from = s.now
                if (!go && from >= 0 && to < 0) to = s.now
            }
            return to - from
        }
        val slow = window(1)
        val quick = window(100)
        assertTrue(slow in 600.0..700.0, "Very easy $slow")
        assertTrue(quick in 250.0..360.0, "Expert $quick")
    }

    // ---- Recall ----

    /** Remembers the lit dots and taps them back once they go dark. */
    private fun recallBot(): (Stage, Toy, Spy) -> Unit {
        val lit = mutableListOf<Pt>()
        var shown = false
        var last = 0.0
        return { s, t, spy ->
            val cyan = spy.dots(Ink.CYAN).filter { it.alpha == 0.9 }
            if (cyan.isNotEmpty()) {
                if (!shown) lit.clear()
                shown = true
                for (d in cyan) if (lit.none { dist(it.x, it.y, d.x, d.y) < 1 }) lit += Pt(d.x, d.y)
            } else if (shown && lit.isNotEmpty() && s.now - last > 120) {
                tap(s, t, lit.removeAt(0))
                last = s.now
                if (lit.isEmpty()) shown = false
            }
        }
    }

    @Test fun recallClears() {
        assertEquals(Result.WON, run("recall", 1, 3, 180_000.0, recallBot()).result)
        assertEquals(Result.WON, run("recall", 100, 7, 300_000.0, recallBot()).result)
    }

    @Test fun recallHeats() {
        fun lit(dial: Int): Int {
            var most = 0
            run("recall", dial, null, 3000.0) { _, _, spy -> most = maxOf(most, spy.dots(Ink.CYAN).count { it.alpha == 0.9 }) }
            return most
        }
        assertEquals(3, lit(1))
        assertEquals(7, lit(100))
    }

    // ---- Ripple ----

    /** When the water is calm, taps behind Nu (away from the ring) as far off as sends Nu about
     *  the way to it. */
    private fun rippleBot(): (Stage, Toy, Spy) -> Unit {
        var last = 0.0
        return { s, t, spy ->
            val goal = spy.rings(Ink.WHITE, 3.0).firstOrNull()
            val nu = s.nu
            if (goal != null && s.now - last > 1000 && hypot(nu.vx, nu.vy) < 0.03) {
                val dx = goal.x - nu.x; val dy = goal.y - nu.y
                val d = hypot(dx, dy).coerceAtLeast(1.0)
                // Ripple's push: 0.42 * small * (1 - at / reach)^0.7, carried about 1100 ms by the drag.
                val small = 138.0 / 190.0
                val reach = 260.0 * small
                val v = (d * 0.8 / 1100).coerceAtMost(0.42 * small * 0.95)
                val at = reach * (1 - (v / (0.42 * small)).pow(1 / 0.7))
                tap(s, t, Pt(nu.x - dx / d * at, nu.y - dy / d * at))
                last = s.now
            }
        }
    }

    @Test fun rippleClears() {
        val won = (1..5).count { run("ripple", 1, 3, 180_000.0, rippleBot()).result == Result.WON }
        assertTrue(won >= 3, "won $won of 5")
    }

    /** Nu sent drifting slowly to the rim doesn't stay stuck there: the water eases it back in,
     *  leaving room to tap outside it. */
    @Test fun rippleLetsGoOfTheRim() {
        for (k in 0 until 8) {
            val a = k * TAU / 8
            var tapped = false
            // A ripple from the far side, so faint that it only just carries Nu to the rim.
            val s = run("ripple", 1, null, 9000.0) { s, t, _ ->
                if (!tapped && s.now > 1100) { tap(s, t, Pt(s.nu.x - cos(a) * 135, s.nu.y - sin(a) * 135)); tapped = true }
            }
            val gap = s.R - dist(s.nu.x, s.nu.y, s.c.x, s.c.y) - s.nu.r
            assertTrue(gap > 12, "pushed toward ${k * 45} degrees, Nu ended $gap from the rim")
        }
    }

    @Test fun rippleHeats() {
        fun look(dial: Int): Pair<Double, Double> {
            var r = 0.0
            val s = run("ripple", dial, null, 10_000.0) { _, _, spy -> spy.rings(Ink.WHITE, 3.0).firstOrNull()?.let { r = it.r } }
            return r to (s.rim ?: 1.0)
        }
        val (big, slow) = look(1)
        val (small, quick) = look(100)
        assertTrue(small < big * 0.8, "ring $big -> $small")
        assertTrue(quick < slow, "clock $slow -> $quick")
    }
}
