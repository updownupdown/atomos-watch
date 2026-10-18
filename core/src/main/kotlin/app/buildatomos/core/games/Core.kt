package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Where Nu sits on its ring: nine o'clock, clear of the menu at the top and the counter below. */
private const val NU_A = PI

/** How long a drop from one ring to the next takes. */
private const val FALL_MS = 230.0

/** The pause between reaching the nucleus and the next set of rings. */
private const val NEXT_MS = 1000.0

/** A tap turns the ring this far; a finger held keeps turning it at `HOLD_SPEED` (rad/ms). */
private const val NUDGE = 0.4
private const val HOLD_SPEED = 0.0028

/** After a red, Nu can't be hurt again for this long. */
private const val SAFE_MS = 900.0

/**
 * Core (new for the watch). Rings round the nucleus, one inside another, each with a gap; Nu rests
 * on the outermost. Turn the ring Nu is on, and when its gap comes under Nu, Nu drops in to the
 * next ring, and on, down to the nucleus. A level is the nucleus reached; then a new set of rings
 * comes in.
 * The crown turns the ring Nu is on (a radian of crown, a radian of ring, eased). The rings
 * inside it drift round on their own, this way or that, so the drop is a matter of timing as well
 * as turning. Some rings carry red arcs: a red that passes under Nu as its ring turns, or that Nu
 * lands on, costs one of three lives (and burns away). The lives carry on from level to level, and
 * one comes back every third level. The rim counts down each set of rings; run out and the run is
 * lost, so a game left alone ends. For players who don't use the crown, a tap on the left or right
 * half of the screen nudges the ring that way (anticlockwise, clockwise), and a finger held there
 * keeps it turning.
 * Heat brings more rings (3, up to 5), narrower gaps, more and longer reds (and two on a ring),
 * faster drift, a second gap on some rings, and less time.
 */
class Core : Toy {
    private class Arc(val c: Double, val half: Double)
    private class Ring(val r: Double, val gaps: List<Arc>, val reds: MutableList<Arc>, val drift: Double) {
        var rot = 0.0
        var want = 0.0
    }
    private var rings = listOf<Ring>()
    /** The ring Nu is on (rings.size: in the nucleus). */
    private var k = 0
    /** Falling: from radius, to radius, started at. */
    private var fall: Triple<Double, Double, Double>? = null
    private var nuR = 0.0
    private var left = 3
    private var time = 1.0
    private var clock = 0.0
    private var rest = 0.0
    private var safeUntil = -1e9
    /** A finger held: which way it turns the ring (+1 clockwise), and since when. */
    private var held = 0
    private var heldAt = 0.0
    private var dealtAt = 0.0
    private var over = false

    private fun outer(s: Stage) = s.R * 0.8
    private fun inner(s: Stage) = s.R * 0.25
    private fun core(s: Stage) = 11.0

    override fun enter(s: Stage) {
        s.nu.r = 7.0
        deal(s)
        s.nu.at(polar(s, NU_A, nuR))
    }

    private fun deal(s: Stage) {
        val h = s.heat
        val n = min(5, 3 + floor(h * 1.6).toInt())
        val list = mutableListOf<Ring>()
        for (i in 0 until n) {
            val r = if (n == 1) outer(s) else outer(s) + (inner(s) - outer(s)) * i / (n - 1)
            // The gap: Nu's width and some room, less with heat; never under Nu's width plus 5.
            val len = max(2 * s.nu.r + 5, 2 * s.nu.r + 16 - 7 * h)
            val half = len / 2 / r
            val g0 = rnd() * TAU
            val gaps = mutableListOf(Arc(g0, half))
            if (i > 0 && h > 0.8 && rnd() < min(0.5, (h - 0.8) * 0.8)) gaps += Arc(g0 + PI, half)
            val reds = mutableListOf<Arc>()
            // The outermost has reds only from a little heat on, and never where Nu starts.
            val p = if (i == 0) clamp(h - 0.4, 0.0, 0.6) else min(0.85, 0.3 + 0.4 * h)
            val count = if (rnd() < p) (if (h > 1.0 && rnd() < 0.5) 2 else 1) else 0
            repeat(count) {
                // Reds keep well clear of the gaps, so a drop is never forced onto one.
                for (tries in 0 until 12) {
                    val rh = (0.22 + rnd() * 0.18 + 0.1 * min(h, 1.5)) * 110 / r * 0.6
                    val c = rnd() * TAU
                    val clear = gaps.all { abs(turn(it.c, c)) > it.half + rh + 0.25 } && reds.all { abs(turn(it.c, c)) > it.half + rh + 0.2 }
                    if (clear) { reds += Arc(c, rh); break }
                }
            }
            val drift = if (i == 0) 0.0 else (0.00025 + 0.00035 * min(h, 2.0)) * (0.7 + rnd() * 0.6) * (if (rnd() < 0.5) -1 else 1)
            list += Ring(r, gaps, reds, drift)
        }
        // Start the outermost with its gap well round from Nu, and no red under it.
        val first = list[0]
        val off = (if (rnd() < 0.5) 1 else -1) * (1.6 + rnd() * 0.9)
        first.rot = NU_A - first.gaps[0].c + off
        first.want = first.rot
        first.reds.removeAll { abs(turn(it.c + first.rot, NU_A)) < it.half + 0.5 }
        rings = list
        k = 0
        fall = null
        nuR = first.r
        time = n * max(2400.0, 5200.0 - 1800 * h)
        clock = time
        s.rim = 1.0
        s.part = 0.0
        dealtAt = s.now
    }

    /** Nu's angle on ring `g`'s own frame. */
    private fun local(g: Ring) = NU_A - g.rot

    private fun nuHalf(s: Stage, g: Ring) = s.nu.r / g.r

    private fun inGap(s: Stage, g: Ring): Boolean {
        val a = local(g)
        return g.gaps.any { abs(turn(it.c, a)) < it.half - nuHalf(s, g) * 0.55 }
    }

    private fun onRed(s: Stage, g: Ring): Arc? {
        val a = local(g)
        return g.reds.firstOrNull { abs(turn(it.c, a)) < it.half + nuHalf(s, g) * 0.7 }
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        if (rest > 0) {
            rest -= dt
            if (rest <= 0 && !over) { deal(s); fall = Triple(core(s), nuR, now) }
            return
        }
        // The rings inside drift; the one Nu is on follows the crown and the finger.
        for ((i, g) in rings.withIndex()) if (i != k) { g.rot += g.drift * dt; g.want = g.rot }
        if (!over && held != 0 && now - heldAt > 220 && fall == null && k < rings.size) rings[k].want += held * HOLD_SPEED * dt
        val f = fall
        if (f != null) {
            val t = clamp((now - f.third) / FALL_MS, 0.0, 1.0)
            nuR = f.first + (f.second - f.first) * t * t
            if (t >= 1) { fall = null; land(s, now) }
        } else if (k < rings.size && !over) {
            val g = rings[k]
            val go = (g.want - g.rot) * (1 - exp(-dt / 70))
            // In small steps, so a quick turn can't skip a gap or a red.
            val steps = max(1, ceil(abs(go) / 0.04).toInt())
            for (i in 0 until steps) {
                g.rot += go / steps
                if (inGap(s, g)) { g.want = g.rot; drop(s, now); break }
                val red = onRed(s, g)
                if (red != null && now >= safeUntil) { burn(s, g, red, now); if (over) return }
            }
        }
        nu.x = s.c.x + cos(NU_A) * nuR
        nu.y = s.c.y + sin(NU_A) * nuR
        nu.glow = if (now < safeUntil) 0.4 else 1.0
        if (over) return
        clock -= dt
        s.rim = clamp(clock / time, 0.0, 1.0)
        if (clock <= 0) {
            over = true
            s.rim = null
            s.fx.ring(nu.p, Ink.RED, nu.r, nu.r + 28, 600.0)
            s.out.miss()
            s.out.buzz(22)
            s.lose()
        }
    }

    /** Nu drops through the gap of ring `k` toward the next ring in, or the nucleus. */
    private fun drop(s: Stage, now: Double) {
        val to = if (k + 1 < rings.size) rings[k + 1].r else core(s)
        fall = Triple(nuR, to, now)
        s.out.tick(k)
        k++
        s.part = clamp(k.toDouble() / rings.size, 0.0, 1.0)
    }

    private fun land(s: Stage, now: Double) {
        if (k >= rings.size) {
            s.fx.ring(s.c, Ink.CYAN, core(s), core(s) + 30, 600.0)
            s.fx.burst(s.c, 12, Ink.ROSE, 0.1)
            s.rim = null
            s.clear()
            if (s.over) { over = true; return }
            s.part = 0.0
            if (s.level % 3 == 0 && left < 3) left++
            // Nu rests in the nucleus, then rises out to the next set's outermost ring.
            rest = NEXT_MS
            return
        }
        val g = rings[k]
        g.want = g.rot
        s.fx.ring(s.nu.p, Ink.K300, s.nu.r, s.nu.r + 10, 260.0)
        if (inGap(s, g)) { drop(s, now); return }
        val red = onRed(s, g)
        if (red != null && now >= safeUntil) burn(s, g, red, now)
    }

    /** A red under Nu: it costs a life and burns away. */
    private fun burn(s: Stage, g: Ring, red: Arc, now: Double) {
        g.reds.remove(red)
        left--
        safeUntil = now + SAFE_MS
        s.fx.burst(s.nu.p, 8, Ink.RED, 0.09)
        s.fx.ring(s.nu.p, Ink.RED, s.nu.r, s.nu.r + 24, 500.0)
        s.out.miss()
        s.out.buzz(22)
        if (left <= 0) {
            over = true
            s.rim = null
            s.lose()
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        // The nucleus, breathing.
        pen.glow(s.c.x, s.c.y, core(s), Ink.K300, 0.6)
        pen.dot(s.c, core(s) + sin(now / 300) * 1.0, Ink.K300, 0.85)
        // A new set grows in from the rim.
        val grow = clamp((now - dealtAt) / 350, 0.0, 1.0)
        val ease = 1 - (1 - grow) * (1 - grow)
        for ((i, g) in rings.withIndex()) {
            val passed = i < k
            val r = g.r + (s.R - g.r) * (1 - ease) * 0.3
            val alpha = (if (passed) 0.18 else if (i == k) 0.95 else 0.6) * ease
            val col = if (i == k) Ink.WHITE else Ink.K400
            // The ring, as the arcs between its gaps.
            val gs = g.gaps.sortedBy { ((it.c + g.rot) % TAU + TAU) % TAU }
            for ((j, a) in gs.withIndex()) {
                val b = gs[(j + 1) % gs.size]
                val from = a.c + g.rot + a.half
                var to = b.c + g.rot - b.half
                while (to <= from) to += TAU
                if (gs.size == 1) to = from + TAU - 2 * a.half
                pen.arc(s.c.x, s.c.y, r, from, to - from, col, if (i == k) 4.0 else 3.0, alpha)
            }
            if (!passed) for (red in g.reds) pen.arc(s.c.x, s.c.y, r, red.c + g.rot - red.half, 2 * red.half, Ink.RED, if (i == k) 5.0 else 4.0, ease)
        }
        lives(s, pen, left)
        val blink = now < safeUntil && ((now / 110).toInt() % 2 == 0)
        s.nu.hidden = blink && !over
        s.nu.draw(pen, now)
        s.nu.hidden = false
    }

    private fun turnBy(by: Double) {
        if (over || rest > 0 || fall != null || k >= rings.size) return
        rings[k].want += by
    }

    override fun crown(s: Stage, by: Double) = turnBy(by)

    override fun down(s: Stage, p: Pt) {
        if (over) return
        held = if (p.x < s.c.x) -1 else 1
        heldAt = s.now
        turnBy(held * NUDGE)
    }

    override fun up(s: Stage, p: Pt) { held = 0 }
}
