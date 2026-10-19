package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** The breath between levels, while the clock fills again (ms). */
private const val CALM_MS = 1000.0
/** The dial's face: this many graduations, every fourth a little bigger. */
private const val TICKS = 48
/** The clock's ring round the pins in the middle (units). */
private const val CLOCK_R = 31.0

/**
 * Dial, made for the watch (the phone has no crown). A safe's dial: Nu is its pointer, riding the
 * ring, and the crown turns it, a turn of the crown a turn of the dial. A notch lights cyan on the
 * ring: turn Nu into it and hold it there a moment, while the notch fills green, and it locks with
 * a click; then the next lights. Overshooting is fine, and leaving the notch only lets its fill
 * drain away (twice as fast as it filled). The upcoming notch shows faintly, so the turn can be
 * planned; the combination's pins sit round the middle and turn green as they lock.
 * A level is a combination: five notches at first, one more every other level, up to eight, all
 * on one clock: a small quiet ring round the pins in the middle, draining as it goes, amber in its
 * last quarter (not the bezel, which would pull the eye off the notch). Running out of time costs
 * one of three lives, and the clock fills again with the pins kept; the last life lost loses the
 * run. Lives carry on from level to level, and one comes back every third level. Between levels
 * the clock fills again for a breath.
 * Every notch creeps round the ring, so a lock means following it. Heat narrows the notches (15.5
 * degrees at the gentlest, 8 at Expert and no narrower), holds them longer (0.65 s to 0.92 s, at
 * most 0.95 s), gives less time for each (3.2 s to 2 s, never under 1.9 s), and drifts them faster
 * (14 degrees a second at the gentlest, 40 at Expert and no faster).
 * The crown is the dial. A finger works too: Nu runs round the ring toward it.
 */
class Dial : Toy {
    /** Nu's place on the ring (not wrapped), and where the crown or the finger sends it. */
    private var a = PI
    private var want = PI
    /** A finger on the screen: Nu runs toward it no faster than it can. */
    private var finger = false
    private class Notch(var a: Double, val w: Double, val v: Double) {
        var fill = 0.0
        var lockedAt: Double? = null
    }
    private var combo = listOf<Notch>()
    /** The notch to lock now. */
    private var at = 0
    private var left = LIVES
    /** The level's clock: all it has, and what is left (ms). */
    private var bank = 1.0
    private var clock = 1.0
    /** The clock as drawn, 0..1 left, or null for none. */
    private var face: Double? = null
    /** The breath between levels, ms left, or 0. */
    private var calm = 0.0
    /** Nu is in the notch to lock, as of the last frame. */
    private var inside = false
    private var over = false

    private fun track(s: Stage) = s.R * 0.66

    /** The notch to lock now, if one is lit: for the tests' player. */
    internal val goal: Double? get() = if (calm > 0 || over || at >= combo.size) null else combo[at].a
    internal val goalDrift: Double get() = if (at < combo.size) combo[at].v else 0.0
    internal val pointer: Double get() = a
    /** The clock in the middle, 0..1 left, or null when none shows: for the tests. */
    internal val timeLeft: Double? get() = face

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.nu.at(polar(s, a, track(s)))
        deal(s)
    }

    /** A new combination for this level, and its clock. */
    private fun deal(s: Stage) {
        val h = s.heat
        val n = notches(s.level)
        var from = a
        combo = List(n) {
            val side = if (rnd() < 0.5) -1 else 1
            from += side * (1.5 + rnd() * 2.4)
            val v = drift(h) * (0.7 + rnd() * 0.3) * (if (rnd() < 0.5) -1 else 1)
            Notch(from, width(h), v)
        }
        at = 0
        bank = n * each(h)
        clock = bank
        inside = false
        s.part = 0.0
        s.rim = null
        face = 1.0
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        // Nu follows the crown at once, a touch smoothed; after a finger, no faster than it runs.
        val d = want - a
        val k = 1 - exp(-dt / 35)
        a += if (finger) clamp(d * k, -0.012 * dt, 0.012 * dt) else d * k
        s.nu.x = polar(s, a, track(s)).x
        s.nu.y = polar(s, a, track(s)).y
        if (over) return
        for (q in combo) if (q.lockedAt == null) q.a += q.v * dt
        if (calm > 0) {
            calm -= dt
            face = clamp(1 - calm / CALM_MS, 0.0, 1.0)
            if (calm <= 0) { calm = 0.0; deal(s) }
            return
        }
        val q = combo[at]
        val hold = hold(s.heat)
        // In once within the notch; out only a little past its edge, so it doesn't chatter there.
        val off = abs(turn(a, q.a))
        val inNow = off <= q.w / 2 || (inside && off <= q.w / 2 + 0.025)
        // The click of the tumbler as Nu comes into the notch.
        if (inNow && !inside) s.out.buzz(15)
        inside = inNow
        q.fill = if (inNow) q.fill + dt / hold else max(0.0, q.fill - 2 * dt / hold)
        if (q.fill >= 1) {
            q.fill = 1.0
            q.lockedAt = now
            q.a = a
            at++
            inside = false
            s.fx.burst(s.nu.p, 7, Ink.GREEN, 0.1)
            s.out.capture()
            s.part = clamp(at.toDouble() / combo.size, 0.0, 1.0)
            if (at >= combo.size) {
                s.fx.ring(s.c, Ink.GREEN, 10.0, track(s) - 12, 700.0)
                s.clear()
                if (s.over) { over = true; face = null; return }
                if (s.level % 3 == 0) left = min(LIVES, left + 1)
                calm = CALM_MS
                face = 0.0
            }
            return
        }
        clock -= dt
        face = clamp(clock / bank, 0.0, 1.0)
        if (clock <= 0) {
            left--
            s.fx.ring(polar(s, q.a, track(s)), Ink.RED, 8.0, 30.0, 600.0)
            s.out.miss()
            s.out.buzz(22)
            if (left <= 0) {
                over = true
                face = 0.0
                s.lose()
                return
            }
            // The clock fills again; the pins already locked stay.
            clock = bank
            q.fill = 0.0
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val t = track(s)
        val c = s.c
        // The face turns with the dial: small graduations inside the ring.
        for (i in 0 until TICKS) {
            val g = a + i * TAU / TICKS
            val p = polar(s, g, t - 16)
            if (i % 4 == 0) pen.dot(p, 2.2, Ink.K500, 0.9) else pen.dot(p, 1.4, Ink.K700, 0.9)
        }
        pen.ring(c, t, Ink.K500, 3.0, 0.6)
        // The pins of the combination round the middle, green as they lock.
        val n = combo.size
        for (i in 0 until n) {
            val p = polar(s, -PI / 2 + i * TAU / n, 17.0)
            val locked = combo[i].lockedAt != null
            if (locked) pen.dot(p, 4.0, Ink.GREEN, if (over && s.result == Result.LOST) 0.4 else 0.95)
            else pen.ring(p, 3.4, Ink.K500, 1.8, 0.8)
        }
        pen.dot(c, 5.0, Ink.K300, 0.6)
        // The clock round them: a faint track, and what is left of it draining from the top,
        // amber in its last quarter.
        face?.let { f ->
            pen.ring(c, CLOCK_R, Ink.K900, 3.0, 1.0)
            if (f > 0.005) pen.arc(c.x, c.y, CLOCK_R, -PI / 2, TAU * f, if (f < 0.25 && calm <= 0) Ink.AMBER else Ink.K400, 3.0, 0.9)
        }
        // The pins locked this level stay on the ring where they clicked.
        for (q in combo) if (q.lockedAt != null) pen.dot(polar(s, q.a, t), 4.0, Ink.GREEN, 0.8)
        if (!over && calm <= 0 && at < combo.size) {
            // The next notch, faintly, outside the ring.
            combo.getOrNull(at + 1)?.let { q -> pen.arc(c.x, c.y, t + 10, q.a - q.w / 2, q.w, Ink.K400, 2.5, 0.7) }
            // The notch to lock: cyan, filling green from its leading edge as Nu holds it.
            val q = combo[at]
            pen.arc(c.x, c.y, t, q.a - q.w / 2, q.w, Ink.CYAN, 13.0, 0.14)
            pen.arc(c.x, c.y, t, q.a - q.w / 2, q.w, Ink.CYAN, 7.0, 0.95)
            if (q.fill > 0.01) pen.arc(c.x, c.y, t, q.a - q.w / 2, q.w * q.fill, Ink.GREEN, 7.0, 1.0)
        }
        lives(s, pen, left, LIVES)
        s.nu.draw(pen, now)
    }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        if (finger) { want = a; finger = false }
        want += by
    }

    private fun aim(s: Stage, p: Pt) {
        if (dist(p, s.c) < 12) return
        finger = true
        want = a + turn(a, atan2(p.y - s.c.y, p.x - s.c.x))
    }

    override fun down(s: Stage, p: Pt) { if (!over) aim(s, p) }
    override fun move(s: Stage, p: Pt) { if (!over && finger) aim(s, p) }

    internal companion object {
        const val LIVES = 3
        /** Notches in a level's combination: five, one more every other level, at most eight. */
        fun notches(level: Int) = 5 + min(3, level / 2)
        /** A notch's width (radians). */
        fun width(heat: Double) = max(0.14, 0.27 - 0.15 * heat)
        /** How long Nu must hold a notch to lock it (ms). */
        fun hold(heat: Double) = min(950.0, 650 + 300 * heat)
        /** The clock's time for each notch of the combination (ms). */
        fun each(heat: Double) = max(1900.0, 3200 - 1300 * heat)
        /** How fast notches drift round the ring (rad/ms): 14 degrees a second at the gentlest, 40
         *  at Expert and no faster. */
        fun drift(heat: Double) = clamp(0.000245 + heat * 0.0005, 0.0, 0.0007)
    }
}
