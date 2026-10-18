package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/** The rings' radius and the room between one ring and the next. */
private const val RING_R = 42.0
private const val GAP = 96.0

/** Rings passed to clear a level: one set of four. */
private const val PER_LEVEL = 4

/** Nu sits on its perch this long at most before it drops on its own. */
private const val PERCH_MS = 2200.0

/**
 * Hue (the phone's src/nu/meadow.ts). Rings stacked up the court, turning, each in two to five
 * colours. Nu wears one colour (the ring round it); a tap hops it up and it falls between taps.
 * It may pass through a ring only where the ring is in its colour; a little spinning dot between
 * rings changes Nu's colour to one of the next ring's. Touch another colour, or fall off the
 * bottom, and the run is lost.
 * A level is a set of four rings passed: two colours, then three, four, five, and the next set
 * starts again at two. Heat turns the rings faster and adds colours to the first rings of a set
 * (from two up to five at Expert).
 * On the watch Nu climbs straight up the middle of the round court, and the view climbs with it.
 * The crown turns the ring Nu is about to pass (drawn a little bolder): spin it forward or back
 * to bring Nu's colour round, while the rings still turn by themselves. A tap still hops. Nu
 * waits on its perch a moment at the start, then drops on its own, so a run left alone is lost.
 */
class Hue : Toy {
    private class Ring(val y: Double, var a: Double, val v: Double, val n: Int, val i: Int) {
        var passed = false
        var swap = true
    }
    private val rings = mutableListOf<Ring>()
    /** Nu's height in the world (down is +), its speed, and how far the view has climbed (−). */
    private var y = 0.0
    private var vy = 0.0
    private var cam = 0.0
    private var hue = 0
    private var made = 0
    private var started = false
    private var since = 0.0
    private var passed = 0
    private var over = false

    private fun base(s: Stage) = s.c.y + s.R * 0.5

    override fun enter(s: Stage) {
        s.nu.r = 7.0
        y = base(s)
        s.nu.at(Pt(s.c.x, y))
        repeat(4) { add(s) }
        hue = floor(rnd() * rings[0].n).toInt()
    }

    private fun add(s: Stage) {
        val last = rings.lastOrNull()
        val y = last?.let { it.y - (2 * RING_R + GAP) } ?: (base(s) - 70 - RING_R)
        val i = made++
        val n = min(5, 2 + i % PER_LEVEL + (s.heat * 2).toInt())
        val v = (if (rnd() < 0.5) -1 else 1) * min(0.0021, 0.0007 + s.heat * 0.0006) * (0.7 + n * 0.06)
        rings += Ring(y, rnd() * TAU, v, n, i)
    }

    /** The colour of a ring at its bottom (dir 1) or top (−1). */
    private fun colorAt(q: Ring, dir: Int): Int {
        val at = if (dir > 0) PI / 2 else -PI / 2
        val k = (((at - q.a) % TAU) + TAU) % TAU / (TAU / q.n)
        return floor(k).toInt() % q.n
    }

    /** The ring Nu will pass next: the lowest not yet passed. */
    private fun target(): Ring? = rings.firstOrNull { !it.passed }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        for (q in rings) q.a += q.v * dt
        if (over) { nu.seek(Pt(s.c.x, nu.y), dt, 0.3, 0.02); return }
        if (!started) {
            since += dt
            if (since < PERCH_MS) { nu.x = s.c.x; nu.y = y - cam; return }
            started = true
            vy = 0.0
        }
        vy = min(0.5, vy + 0.0011 * dt)
        y += vy * dt
        // The view climbs with Nu, never back down.
        val high = s.c.y - 10
        if (y - cam < high) cam = y - high
        nu.x = s.c.x
        nu.y = y - cam
        nu.vx = 0.0
        nu.vy = vy
        if (nu.y > s.c.y + s.R * 0.85) { lose(s); return }
        for (q in rings) {
            val d = y - q.y
            // Passing one of its two crossings, top or bottom.
            if (abs(abs(d) - RING_R) < 4 + nu.r * 0.55 && colorAt(q, if (d >= 0) 1 else -1) != hue) { lose(s); return }
            if (q.swap && y < q.y - RING_R - GAP / 2) {
                q.swap = false
                // A colour of the next ring, not the one Nu has.
                val n = rings.firstOrNull { it.i == q.i + 1 }?.n ?: 5
                hue = if (hue < n) (hue + 1 + floor(rnd() * (n - 1)).toInt()) % n else floor(rnd() * n).toInt()
                s.out.tick(3)
            }
            if (!q.passed && y < q.y - RING_R - 10) {
                q.passed = true
                s.out.capture()
                if (++passed >= PER_LEVEL) {
                    passed = 0
                    s.clear()
                    if (s.over) { over = true; s.part = 0.0; return }
                }
                s.part = passed.toDouble() / PER_LEVEL
            }
        }
        if (rings.count { !it.passed } < 3) add(s)
        rings.removeAll { it.y - RING_R - cam > s.c.y + s.R + 40 }
    }

    private fun lose(s: Stage) {
        over = true
        s.fx.ring(s.nu.p, Ink.RED, s.nu.r, s.nu.r + 30, 600.0)
        s.out.miss()
        s.out.buzz(24)
        s.lose()
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val aim = target()
        for (q in rings) {
            val cy = q.y - cam
            if (cy - RING_R > s.c.y + s.R + 10 || cy + RING_R < s.c.y - s.R - 10) continue
            // Fainter toward the top and bottom of the court, so the menu and counter stay clear.
            val fade = clamp((s.R + 20 - abs(cy - s.c.y)) / 70, 0.25, 1.0)
            val bold = q === aim && !over
            val w = if (bold) 9.0 else 7.0
            val alpha = (if (over) 0.4 else if (bold) 0.95 else 0.75) * fade
            for (k in 0 until q.n) {
                pen.arc(s.c.x, cy, RING_R, q.a + k * TAU / q.n + 0.05, TAU / q.n - 0.1, Ink.NOTES[k], w, alpha)
            }
            if (q.swap) {
                // The colour-changer, above the ring, in the next ring's colours.
                val py = q.y - RING_R - GAP / 2 - cam
                val n = rings.firstOrNull { it.i == q.i + 1 }?.n ?: 5
                for (k in 0 until n) {
                    val a = now / 400 + k * TAU / n
                    pen.dot(s.c.x + cos(a) * 5, py + sin(a) * 5, 3.5, Ink.NOTES[k], fade)
                }
            }
        }
        if (!started) {
            val by = base(s) - cam + s.nu.r + 6
            pen.line(s.c.x - 20, by, s.c.x + 20, by, Ink.K500, 4.0, 0.9 * clamp(1 - since / PERCH_MS + 0.3, 0.0, 1.0))
        }
        pen.ring(s.nu.x, s.nu.y, s.nu.r + 5, Ink.NOTES[hue], 3.0, 0.95)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        started = true
        vy = -0.38
        s.out.tick(1)
    }

    /** The crown turns the ring Nu is about to pass, as far as the crown turns. */
    override fun crown(s: Stage, by: Double) {
        if (over) return
        target()?.let { it.a += by }
    }
}
