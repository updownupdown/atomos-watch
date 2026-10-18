package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val RED_R = 4.5
/** A bubble starts this big. */
private const val START_R = 6.0
/** The biggest a bubble grows, of the arena's radius: about an eighth of the arena. */
private const val BIGGEST = 0.36
/** After a level, the bubbles fade away for this long (ms). */
private const val CLEAR_MS = 800.0
/** After a pop, Nu blinks for this long (ms). */
private const val BLINK_MS = 600.0

/**
 * Fill, made for the watch. Red electrons bounce about a round arena. Tap a spot and Nu blows a
 * bubble there; it grows until a second tap sets it, or until it meets the arena's edge or
 * another bubble, or grows as big as a bubble gets (about an eighth of the arena), and sets by
 * itself. Set, it is solid, and the reds bounce off it; but a red that touches a bubble still
 * growing pops it, and that costs one of three lives. The last life lost
 * loses the run. Fill enough of the arena and the level is done.
 * A level is the arena filled to its mark: 57% of it at the gentlest, 62% at Expert, at most
 * 64%. Then the bubbles fade away and the next level starts in an empty arena. Lives carry
 * on from level to level, and one comes back every third level. Heat brings more reds (two at
 * the gentlest, four at Expert, up to seven) and quicker ones (from about 0.05 units a
 * millisecond to 0.08 at Expert), and asks for more of the arena.
 * The arena is a circle a little in from the rim (a faint ring), off the menu at the top and the
 * level counter at the bottom. Both taps can be anywhere, so the finger is off the bubble while
 * it grows. No crown: a bubble is a place and a moment. Left alone a few seconds (seven at the
 * gentlest, four at Expert), Nu pulses pale three times and blows a bubble by itself, right in a
 * red's way, so a run nobody plays ends.
 */
class Fill : Toy {
    internal class Bubble(val x: Double, val y: Double, var r: Double)
    internal class Red(x: Double, y: Double, vx: Double, vy: Double) : Mover(x, y, vx, vy)
    internal val set = mutableListOf<Bubble>()
    internal val reds = mutableListOf<Red>()
    /** The bubble growing, if one is. */
    internal var grow: Bubble? = null
        private set
    private var left = LIVES
    private var clearing = 0.0
    private var blink = 0.0
    private var rest = Pt(0.0, 0.0)
    private val wait = Patience()
    private var over = false

    internal fun arena(s: Stage) = s.R - 30
    /** How much of the arena the bubbles fill, 0..1. */
    internal fun filled(s: Stage) = set.sumOf { it.r * it.r } / (arena(s) * arena(s))

    override fun enter(s: Stage) {
        s.nu.r = 7.0
        rest = s.c.copy()
        s.nu.at(rest)
        s.rim = null
        deal(s)
    }

    /** A fresh arena: its reds, away from Nu, each its own way. */
    private fun deal(s: Stage) {
        val h = s.heat
        set.clear()
        reds.clear()
        val a = arena(s)
        repeat(count(h)) {
            var p = s.c
            for (k in 0 until 40) {
                val q = Pt(s.c.x + (rnd() * 2 - 1) * a, s.c.y + (rnd() * 2 - 1) * a)
                if (dist(q, s.c) < a - RED_R - 4 && dist(q, s.nu.p) > 45) { p = q; break }
            }
            val d = rnd() * TAU
            val v = speed(h) * (0.85 + rnd() * 0.3)
            reds += Red(p.x, p.y, cos(d) * v, sin(d) * v)
        }
        s.part = 0.0
        wait.reset(patience(h))
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        val g = grow
        nu.seek(if (g != null) Pt(g.x, g.y) else rest, dt, 1.0, 0.04)
        blink -= dt
        val a = arena(s)
        // The reds, in small steps, off the arena's edge and the bubbles that are set.
        for (r in reds) {
            val sp = hypot(r.vx, r.vy)
            val n = max(1, (sp * dt / 3).toInt() + 1)
            for (k in 0 until n) {
                r.x += r.vx * dt / n
                r.y += r.vy * dt / n
                bounce(r, s.c.x, s.c.y, a, inside = true)
                for (b in set) bounce(r, b.x, b.y, b.r, inside = false)
            }
        }
        if (over) return
        if (clearing > 0) {
            clearing -= dt
            if (clearing <= 0) { clearing = 0.0; deal(s) }
            return
        }
        if (g == null && wait.step(dt)) puff(s, careless(s))
        val b = grow ?: return
        b.r += grows(s.heat) * dt
        if (reds.any { dist(it.x, it.y, b.x, b.y) < b.r + RED_R }) { popped(s, b); return }
        // Out to the arena's edge, against a bubble, or as big as a bubble gets: set where it is.
        val room = minOf(a - dist(b.x, b.y, s.c.x, s.c.y), set.minOfOrNull { dist(b.x, b.y, it.x, it.y) - it.r } ?: Double.MAX_VALUE, a * BIGGEST)
        if (b.r >= room) { b.r = max(START_R, room); setIt(s, b) }
    }

    /** Keeps `m` inside (or outside) the circle at (cx, cy) of radius `R`, mirroring it off. */
    private fun bounce(m: Mover, cx: Double, cy: Double, R: Double, inside: Boolean) {
        val dx = m.x - cx
        val dy = m.y - cy
        val d = hypot(dx, dy).takeIf { it > 0 } ?: return
        val nx = dx / d
        val ny = dy / d
        val out = if (inside) d + RED_R - R else R + RED_R - d
        if (out <= 0) return
        val sx = if (inside) -1.0 else 1.0
        m.x += nx * out * sx
        m.y += ny * out * sx
        val vn = (m.vx * nx + m.vy * ny) * sx
        if (vn < 0) { m.vx -= 2 * vn * nx * sx; m.vy -= 2 * vn * ny * sx }
    }

    /** Where Nu blows a bubble by itself: just ahead of the nearest red, in its way. */
    private fun careless(s: Stage): Pt {
        val a = arena(s)
        for (r in reds.sortedBy { dist(it.x, it.y, s.nu.x, s.nu.y) }) {
            val sp = hypot(r.vx, r.vy).takeIf { it > 0 } ?: continue
            val p = Pt(r.x + r.vx / sp * 18, r.y + r.vy / sp * 18)
            if (free(s, p) && dist(p, s.c) < a - START_R - 2) return p
        }
        return s.nu.p
    }

    /** True if a bubble could start at `p`: in the arena and not in a bubble already set. */
    private fun free(s: Stage, p: Pt) =
        dist(p, s.c) < arena(s) - START_R && set.none { dist(p.x, p.y, it.x, it.y) < it.r + START_R }

    private fun puff(s: Stage, at: Pt) {
        val p = inside(s, at, 30 + START_R + 1)
        if (!free(s, p)) { wait.reset(patience(s.heat)); return }
        grow = Bubble(p.x, p.y, START_R)
        wait.hold()
        s.out.tick(1)
    }

    private fun setIt(s: Stage, b: Bubble) {
        grow = null
        set += b
        rest = Pt(b.x, b.y)
        s.out.capture()
        s.fx.ring(Pt(b.x, b.y), Ink.S, b.r, b.r + 10, 400.0)
        s.part = clamp(filled(s) / goal(s.heat), 0.0, 1.0)
        wait.reset(patience(s.heat))
        if (filled(s) >= goal(s.heat)) {
            s.part = 1.0
            s.clear()
            for (q in set) s.fx.ring(Pt(q.x, q.y), Ink.GREEN, q.r, q.r + 12, 600.0)
            if (s.over) { over = true; wait.hold(); return }
            if (s.level % 3 == 0) left = min(LIVES, left + 1)
            s.part = 0.0
            clearing = CLEAR_MS
            wait.hold()
        }
    }

    private fun popped(s: Stage, b: Bubble) {
        grow = null
        left--
        blink = BLINK_MS
        s.fx.burst(Pt(b.x, b.y), 8, Ink.WHITE, 0.1)
        s.fx.ring(Pt(b.x, b.y), Ink.RED, b.r, b.r + 20, 500.0)
        s.out.miss()
        s.out.buzz(22)
        if (left <= 0) { over = true; wait.hold(); s.lose(); return }
        wait.reset(patience(s.heat))
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val c = s.c
        pen.ring(c, arena(s) + 2, Ink.K800, 2.0, 0.9)
        val fade = if (clearing > 0) clamp(clearing / CLEAR_MS, 0.0, 1.0) else 1.0
        val color = if (clearing > 0) Ink.GREEN else Ink.S
        for (b in set) {
            pen.dot(b.x, b.y, b.r, color, 0.28 * fade)
            pen.ring(b.x, b.y, b.r - 1.5, color, 3.0, 0.9 * fade)
        }
        grow?.let { b ->
            pen.dot(b.x, b.y, b.r, Ink.WHITE, 0.08)
            pen.ring(b.x, b.y, b.r - 1.5, Ink.WHITE, 3.0, 0.9)
        }
        for (r in reds) {
            pen.dot(r.x, r.y, RED_R + 3.5, Ink.RED, if (over) 0.1 else 0.22)
            pen.dot(r.x, r.y, RED_R, if (over) Ink.K500 else Ink.RED, 0.95)
        }
        lives(s, pen, left, LIVES)
        if (blink <= 0 || ((blink / 100).roundToInt() % 2 == 0)) {
            s.nu.draw(pen, now)
            pen.pale(s.nu, wait.pale)
        }
    }

    override fun down(s: Stage, p: Pt) {
        if (over || clearing > 0) return
        val b = grow
        if (b != null) { setIt(s, b); return }
        puff(s, p)
    }

    internal companion object {
        const val LIVES = 3
        fun count(heat: Double) = min(7, 2 + floor(max(0.0, heat) * 2.5).toInt())
        /** A red's speed (units/ms) before its own share of chance. */
        fun speed(heat: Double) = min(0.13, 0.045 + 0.04 * heat)
        /** How fast a bubble grows (units/ms). */
        fun grows(heat: Double) = min(0.07, 0.032 + 0.03 * heat)
        /** The share of the arena to fill. */
        fun goal(heat: Double) = min(0.64, 0.57 + 0.05 * heat)
        /** How long Nu waits on a player who has stopped (ms). */
        fun patience(heat: Double) = max(4000.0, 7000 - 3500 * heat)
    }
}
