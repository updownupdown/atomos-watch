package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** How far the beam may tilt (radians). */
private const val LIMIT = 0.3
/** Falling (units/ms²) and rolling. */
private const val G = 0.0012
private const val ROLL = 0.0006
/** After the beam tips, this long before it is level again and Nu brings the next atom (ms). */
private const val TIP_MS = 1100.0
/** After a level, the atoms on the beam fade away for this long (ms). */
private const val CLEAR_MS = 700.0
/** An atom wedged between two others this long settles where it is (ms). */
private const val WEDGED_MS = 1500.0

/**
 * Poise (the phone's src/nu/marsh.ts). A seesaw across the lower court on a pivot in its middle.
 * Nu carries the next atom along the top; tap where it should go and Nu takes it over that spot
 * and drops it. Atoms land on the beam and stay; the bigger, the heavier. The beam tilts with
 * their weight, and tilted past a little, the atoms on it roll downhill. Tipped past the faint red
 * marks under its ends, or an atom rolled or dropped off, costs one of three lives: everything
 * slides off, the level's count starts again, and the beam comes level. The last life lost loses
 * the run.
 * A level is a number of atoms placed: seven, and one more every other level, up to nine. Then the
 * atoms on the beam fade away, and the next level starts on a bare beam. Lives carry on from level
 * to level, and one comes back every third level.
 * Heat brings what the phone's count brought: bigger atoms (up to 17 across at the gentlest, 22
 * at Expert), a looser beam that leans further for the same weight, and atoms that start to roll
 * sooner; within a level, each atom placed loosens it a little more.
 * On the watch the atoms lie in one row along the beam: one dropped onto another rolls off its
 * side onto the beam (the phone let them pile up, and a pile over the pivot would weigh nothing).
 * The tap only says where across: Nu, at the top, goes over that spot and lets go once the finger
 * is up, so the finger never hides the beam. A finger slid along the top leads Nu about before
 * it lets go. No crown: the drop is a place, not a turn. Left alone a few seconds (six at the
 * gentlest, under four at Expert), Nu pulses pale three times, sliding to the low end of the
 * beam as it does, and drops the atom there, so a run nobody plays tips and ends.
 */
class Poise : Toy {
    /** An atom: `u` along the beam from the pivot once on it, `x`, `y` while it falls. */
    internal class W(val r: Double, val c: Int) {
        val m = (r / 8) * (r / 8)
        var u = 0.0
        var vu = 0.0
        var x = 0.0
        var y = 0.0
        var vx = 0.0
        var vy = 0.0
        var fell = 0.0
    }
    internal val on = mutableListOf<W>()
    private val loose = mutableListOf<W>()
    internal var hand: W? = null
        private set
    private var falling: W? = null
    private var tx = 0.0
    private var armed = false
    private var held = false
    /** The beam's tilt (clockwise positive: its right end down) and how fast it turns. */
    internal var th = 0.0
        private set
    private var w = 0.0
    private var next = 0.0
    private var placed = 0
    private var left = LIVES
    /** A tip being played out (ms left), and which way it went. */
    private var tip = 0.0
    private var tipWay = 1.0
    private var clearing = 0.0
    private val wait = Patience()
    private var over = false

    internal fun pivot(s: Stage) = Pt(s.c.x, s.c.y + s.R * 0.3)
    internal fun len(s: Stage) = s.R * 0.68
    private fun top(s: Stage) = s.c.y - s.R * 0.42

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        tx = s.c.x
        s.nu.at(Pt(s.c.x, top(s)))
        s.rim = null
        hand = make(s)
        wait.reset(patience(s.heat))
    }

    private fun make(s: Stage): W {
        val r = 6 + rnd() * (biggest(s.heat) - 6)
        return W(r, Ink.NOTES[(rnd() * Ink.NOTES.size).toInt().coerceIn(0, Ink.NOTES.size - 1)])
    }

    private fun need(s: Stage) = 7 + min(2, s.level / 2)

    // The beam's frame: along it (e) and up from it (n).
    private val ex get() = cos(th)
    private val ey get() = sin(th)
    private val nx get() = sin(th)
    private val ny get() = -cos(th)

    private fun world(s: Stage, u: Double, v: Double): Pt {
        val p = pivot(s)
        return Pt(p.x + ex * u + nx * v, p.y + ey * u + ny * v)
    }

    /** Where an atom on the beam is now. */
    internal fun world(s: Stage, a: W): Pt = world(s, a.u, a.r)

    private fun toLocal(s: Stage, p: Pt): Pair<Double, Double> {
        val c = pivot(s)
        val dx = p.x - c.x
        val dy = p.y - c.y
        return (dx * ex + dy * ey) to (dx * nx + dy * ny)
    }

    /** Where Nu can drop an atom of radius `r`: over the beam, in from its ends. */
    internal fun span(s: Stage, r: Double): Pair<Double, Double> {
        val l = (len(s) - r - 4) * cos(th)
        return (pivot(s).x - l) to (pivot(s).x + l)
    }

    /** The weight on the beam, turning it: positive to the right. */
    internal fun torque(s: Stage): Double {
        val p = pivot(s)
        return on.sumOf { it.m * (world(s, it).x - p.x) / len(s) }
    }

    /** How stiff the beam is now: its tilt is the torque over this. */
    internal fun stiff(s: Stage) = max(6.5, 12 - 4 * s.heat - placed * 0.12)

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        val L = len(s)
        // The beam leans toward its torque over its stiffness, on a soft spring.
        val goal = if (tip > TIP_MS - 500) tipWay * LIMIT * 1.25 else if (tip > 0) 0.0 else torque(s) / stiff(s)
        val k = TAU / 1000
        w += ((goal - th) * k * k - 2 * 0.65 * k * w) * dt
        th += w * dt
        for (a in loose) { a.vy += G * dt; a.x += a.vx * dt; a.y += a.vy * dt }
        loose.removeAll { it.y > s.c.y + s.R + 30 }
        if (over) { nu.seek(Pt(nu.x, top(s)), dt); return }
        if (clearing > 0) {
            clearing -= dt
            if (clearing <= 0) { on.clear(); clearing = 0.0 }
        }
        if (tip > 0) {
            tip -= dt
            nu.seek(Pt(nu.x, top(s)), dt)
            if (tip <= 0) { tip = 0.0; hand = make(s); wait.reset(patience(s.heat)) }
            return
        }
        if (clearing <= 0) {
            if (abs(th) > LIMIT) { tipped(s); return }
            roll(s, dt)
            if (on.any { abs(it.u) > L }) { tipped(s); return }
        }
        // Left alone: three pale pulses, sliding to the low end, then it lets go.
        hand?.let { a ->
            if (wait.pale <= 0 || held) return@let
            val (x0, x1) = span(s, a.r)
            tx = if (th < -0.01) x0 else x1
            armed = false
        }
        if (hand != null && wait.step(dt)) { tx = nu.x; armed = true; held = false }
        // Nu along the top, the atom under it; it lets go once over the spot, the finger up.
        hand?.let { a ->
            val (x0, x1) = span(s, a.r)
            val to = Pt(clamp(tx, x0, x1), top(s))
            nu.seek(to, dt, 0.7, 0.03)
            if (armed && !held && abs(nu.x - to.x) < 3) {
                a.x = nu.x
                a.y = nu.y + nu.r + a.r + 2
                a.vx = 0.0; a.vy = 0.0
                falling = a
                hand = null
                armed = false
                wait.hold()
                s.out.tick(2)
            }
        } ?: nu.seek(Pt(nu.x, top(s)), dt)
        if (falling != null) fall(s, dt)
        else if (hand == null && clearing <= 0) {
            next -= dt
            if (next <= 0) { hand = make(s); wait.reset(patience(s.heat)) }
        }
    }

    /** Atoms on the beam roll downhill once it tilts past a little. */
    private fun roll(s: Stage, dt: Double) {
        val fric = max(0.06, 0.13 - 0.04 * s.heat - placed * 0.002)
        val row = on.sortedBy { it.u }
        for (a in row) {
            if (abs(th) > fric) a.vu += ROLL * sin(th) * dt else a.vu *= exp(-dt / 70)
            a.vu *= exp(-dt / 900)
            a.u += a.vu * dt
        }
        // Side by side on the beam they can't pass: they push, and share their speed.
        for (i in 1 until row.size) {
            val p = row[i - 1]
            val q = row[i]
            val gap = 2 * sqrt(p.r * q.r) - (q.u - p.u)
            if (gap <= 0) continue
            val f = q.m / (p.m + q.m)
            p.u -= gap * f
            q.u += gap * (1 - f)
            val v = (p.vu * p.m + q.vu * q.m) / (p.m + q.m)
            p.vu = v
            q.vu = v
        }
    }

    /** The dropped atom falls, in small steps, until it lands on the beam; one it meets on the
     *  way down it rolls off, to the side it hit. */
    private fun fall(s: Stage, dt: Double) {
        val a = falling ?: return
        val L = len(s)
        a.fell += dt
        val n = max(1, (hypot(a.vx, a.vy + G * dt) * dt / 3).toInt() + 1)
        for (i in 0 until n) {
            val h = dt / n
            a.vy += G * h
            a.x += a.vx * h
            a.y += a.vy * h
            for (b in on) {
                val bp = world(s, b)
                val d = dist(a.x, a.y, bp.x, bp.y)
                val touch = a.r + b.r
                if (d >= touch) continue
                var ux = (a.x - bp.x) / (if (d > 0) d else 1.0)
                var uy = (a.y - bp.y) / (if (d > 0) d else 1.0)
                // Square on top, it tips one way or the other.
                if (abs(ux) < 0.12) { ux = if (rnd() < 0.5) -0.12 else 0.12; uy = -sqrt(1 - ux * ux) }
                a.x = bp.x + ux * touch
                a.y = bp.y + uy * touch
                a.vx = (if (ux > 0) 1 else -1) * 0.12
                a.vy *= 0.5
            }
            val (lu, lv) = toLocal(s, Pt(a.x, a.y))
            if (abs(lu) <= L && ((lv <= a.r && lv > -a.r - 8) || a.fell > WEDGED_MS)) {
                land(s, a, lu)
                return
            }
        }
        // Missed the beam altogether: off it goes.
        if (a.y > s.c.y + s.R * 0.7) { loose += a; falling = null; tipped(s) }
    }

    private fun land(s: Stage, a: W, u: Double) {
        a.u = u
        a.vu = 0.0
        on += a
        falling = null
        next = 250.0
        placed++
        s.out.capture()
        s.fx.ring(world(s, a), a.c, a.r, a.r + 16, 400.0)
        s.part = clamp(placed.toDouble() / need(s), 0.0, 1.0)
        if (placed >= need(s)) {
            placed = 0
            s.clear()
            if (s.over) { over = true; wait.hold(); return }
            if (s.level % 3 == 0) left = min(LIVES, left + 1)
            for (q in on) s.fx.ring(world(s, q), Ink.GREEN, q.r, q.r + 14, 600.0)
            clearing = CLEAR_MS
        }
    }

    /** Tipped over, or an atom off the beam: a life, and everything slides off. */
    private fun tipped(s: Stage) {
        left--
        placed = 0
        s.part = 0.0
        tipWay = if (th < 0) -1.0 else 1.0
        for (a in on) {
            val p = world(s, a)
            loose += a.also { it.x = p.x; it.y = p.y; it.vx = ex * tipWay * 0.12; it.vy = 0.0 }
        }
        falling?.let { loose += it }
        on.clear()
        falling = null
        hand = null
        armed = false
        held = false
        wait.hold()
        s.out.miss()
        s.out.buzz(24)
        if (left <= 0) { over = true; s.lose(); return }
        tip = TIP_MS
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val p = pivot(s)
        val L = len(s)
        val nu = s.nu
        // The tilt limit: a faint red mark under each end, where it would be at the limit.
        for (sx in listOf(-1.0, 1.0)) {
            val x = p.x + sx * L * cos(LIMIT)
            val y = p.y + L * sin(LIMIT) + 7
            pen.line(x - 8, y, x + 8, y, Ink.RED, 3.0, 0.45)
        }
        pen.line(p.x, p.y, p.x, s.c.y + s.R * 0.62, Ink.K800, 6.0)
        val tilt = abs(th) / LIMIT
        pen.line(p.x - ex * L, p.y - ey * L, p.x + ex * L, p.y + ey * L, if (tilt > 0.75 && !over) Ink.WHITE else Ink.K300, 5.0, 0.95)
        pen.dot(p, 6.0, Ink.K500)
        pen.ring(p, 6.0, Ink.K300, 2.0, 0.8)
        val fade = if (clearing > 0) clamp(clearing / CLEAR_MS, 0.0, 1.0) else 1.0
        for (a in on) atom(pen, world(s, a), a, 1.0, fade)
        for (a in loose) atom(pen, Pt(a.x, a.y), a)
        falling?.let { atom(pen, Pt(it.x, it.y), it) }
        hand?.let { a ->
            if (over) return@let
            val at = Pt(nu.x, nu.y + nu.r + a.r + 2)
            // Where it will land: a small mark on the beam under it.
            val (lu, _) = toLocal(s, at)
            if (abs(lu) <= L) { val q = world(s, lu, 0.0); pen.dot(q.x, q.y - 5, 2.6, Ink.K400, 0.8) }
            atom(pen, at, a, min(1.0, (250 - max(0.0, next)) / 250))
        }
        lives(s, pen, left, LIVES)
        nu.draw(pen, now)
        pen.pale(nu, wait.pale)
    }

    private fun atom(pen: Pen, p: Pt, a: W, grow: Double = 1.0, alpha: Double = 1.0) {
        pen.dot(p, a.r * grow, a.c, 0.9 * alpha)
        pen.ring(p, a.r * 0.45 * grow, Ink.K975, 2.0, 0.5 * alpha)
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        tx = p.x
        armed = true
        held = true
        if (hand != null) wait.reset(patience(s.heat))
    }

    override fun move(s: Stage, p: Pt) {
        if (!held) return
        tx = p.x
        if (hand != null) wait.reset(patience(s.heat))
    }

    override fun up(s: Stage, p: Pt) { held = false }

    internal companion object {
        const val LIVES = 3
        /** How long Nu waits on a player who has stopped (ms). */
        fun patience(heat: Double) = max(3500.0, 6500 - 3000 * heat)
        /** The biggest atom Nu brings (its radius): about 9 at the gentlest, 11 at Expert, at most 12. */
        fun biggest(heat: Double) = 6 + min(6.0, 2.5 + 3 * max(0.0, heat))
    }
}
