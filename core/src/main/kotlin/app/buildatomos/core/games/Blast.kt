package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Blast (the phone's src/nu/meadow.ts). Nu in the middle of the court, red atoms drifting in
 * from the rim. Tap and Nu fires a photon toward the tap; it bursts the first red it meets. A red
 * reaching Nu costs one of three lives; the last one lost loses the run.
 * The phone already played it in the round court, so on the watch only the court is smaller:
 * the reds start nearer Nu and reach it sooner.
 *
 * A level is a wave: burst its reds (six, and one more each level) and the court falls quiet for a
 * breath, the reds still drifting burst on their own, and the next wave comes. Lives carry over
 * from wave to wave, and every third wave cleared gives one back. Heat brings the reds faster,
 * thicker and less straight at Nu. They never come from under the menu at the top or the counter
 * at the bottom.
 * On the watch the crown can aim too, so the finger needn't cover the reds: turn it and a short
 * white pointer swings round Nu, a turn of the crown for a turn of the pointer; tap Nu and the
 * photon goes along it. A tap anywhere else fires toward the tap, as before, and hides the
 * pointer until the crown turns again.
 */
class Blast : Toy {
    internal val reds = mutableListOf<Mover>()
    private val shots = mutableListOf<Mover>()
    private var nextAt = 0.0
    private var left = LIVES
    /** Reds burst in this wave, and how many it takes. */
    private var burst = 0
    private var need = 0
    /** The breath between waves: when the next one starts. */
    private var calmUntil = 0.0
    /** The crown's aim (radians, clockwise from three o'clock): it starts up, and follows the
     *  last shot fired by touch. Shown, and fired along by a tap on Nu, once the crown turns. */
    internal var aim = -PI / 2
        private set
    internal var aiming = false
        private set

    override fun enter(s: Stage) {
        s.nu.r = 11.0
        s.nu.at(s.c)
        wave(s, s.now + 900)
    }

    private fun wave(s: Stage, at: Double) {
        burst = 0
        need = min(20, 6 + s.level)
        nextAt = at
        s.part = 0.0
    }

    /** How fast a red comes in, units/ms, and how long between them, ms. */
    internal fun speed(h: Double) = min(0.11, 0.03 + 0.03 * h)
    internal fun every(h: Double) = max(380.0, 1700.0 - 900 * h)

    override fun step(s: Stage, dt: Double, now: Double) {
        s.nu.seek(s.c, dt, 0.5, 0.02)
        if (s.over) return
        val h = s.heat
        if (now >= nextAt && now >= calmUntil) {
            nextAt = now + every(h) * (0.7 + rnd() * 0.6)
            // Anywhere round the rim but the top (the menu) and the bottom (the counter).
            var a = rnd() * TAU
            if (abs(turn(a, -PI / 2)) < 0.35 || abs(turn(a, PI / 2)) < 0.3) a += PI / 2
            val v = speed(h)
            val p = polar(s, a, s.R - 6)
            // Not quite straight at Nu, more askew the hotter.
            val way = a + PI + (rnd() - 0.5) * min(0.8, 0.2 + 0.2 * h)
            reds += Mover(p.x, p.y, cos(way) * v, sin(way) * v)
        }
        for (q in shots) { q.x += q.vx * dt; q.y += q.vy * dt }
        shots.removeAll { dist(it.x, it.y, s.c.x, s.c.y) >= s.R }
        for (i in reds.indices.reversed()) {
            val r = reds[i]
            // Drawn in toward Nu, a little more as they near.
            val dx = s.c.x - r.x
            val dy = s.c.y - r.y
            val d = hypot(dx, dy).takeIf { it > 0 } ?: 1.0
            r.vx += dx / d * 0.00002 * dt
            r.vy += dy / d * 0.00002 * dt
            r.x += r.vx * dt
            r.y += r.vy * dt
            val hit = shots.indexOfFirst { dist(it.x, it.y, r.x, r.y) < 14 }
            if (hit >= 0) {
                shots.removeAt(hit)
                reds.removeAt(i)
                s.fx.burst(Pt(r.x, r.y), 8, Ink.RED, 0.1)
                // Pitched by how near it got and how fast it came: higher the nearer and faster.
                s.out.hit(clamp(0.65 * (1 - d / s.R) + 0.35 * min(1.0, hypot(r.vx, r.vy) / 0.12), 0.0, 1.0))
                if (now >= calmUntil && ++burst >= need) { cleared(s, now); return }
                s.part = clamp(burst.toDouble() / need, 0.0, 1.0)
                continue
            }
            if (d < s.nu.r + 8) {
                reds.removeAt(i)
                left--
                s.fx.ring(s.nu.p, Ink.RED, s.nu.r, s.nu.r + 30, 600.0)
                s.out.miss()
                s.out.buzz(24)
                if (left <= 0) { s.lose(); return }
            }
        }
    }

    /** The wave is burst: the rest go up with it, a breath, and the next. */
    private fun cleared(s: Stage, now: Double) {
        for (r in reds) s.fx.burst(Pt(r.x, r.y), 5, Ink.RED, 0.07)
        reds.clear()
        s.fx.ring(s.c, Ink.CYAN, s.nu.r, s.R * 0.6, 900.0)
        s.out.buzz(14)
        s.clear()
        if (s.over) return
        if (s.level % 3 == 0) left = min(LIVES, left + 1)
        calmUntil = now + CALM
        wave(s, calmUntil)
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        for (r in reds) { pen.dot(r.x, r.y, 9.0, Ink.RED, 0.25); pen.ring(r.x, r.y, 7.5, Ink.RED, 3.0, 0.9) }
        for (q in shots) pen.dot(q.x, q.y, 4.5, Ink.CYAN)
        lives(s, pen, left, LIVES)
        if (aiming && !s.over) {
            val nu = s.nu
            val a = nu.r + 6
            val b = nu.r + 24
            pen.line(nu.x + cos(aim) * a, nu.y + sin(aim) * a, nu.x + cos(aim) * b, nu.y + sin(aim) * b, Ink.WHITE, 3.0, 0.85)
        }
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (s.over) return
        val dx = p.x - s.nu.x
        val dy = p.y - s.nu.y
        val d = hypot(dx, dy)
        // Aiming by the crown: a tap on Nu fires along the pointer.
        if (aiming && d < s.nu.r + TRIGGER) { fire(s, cos(aim), sin(aim)); return }
        if (d < 4 || shots.size >= 4) return
        aim = atan2(dy, dx)
        aiming = false
        fire(s, dx / d, dy / d)
    }

    private fun fire(s: Stage, ux: Double, uy: Double) {
        if (shots.size >= 4) return
        shots += Mover(s.nu.x, s.nu.y, ux * 0.6, uy * 0.6)
        s.out.tick(2)
    }

    override fun crown(s: Stage, by: Double) {
        if (s.over) return
        aim += by
        aiming = true
    }

    private companion object {
        const val LIVES = 3
        /** The breath between waves, ms. */
        const val CALM = 800.0
        /** How far past Nu's edge a tap still fires along the crown's aim: about a fingertip. */
        const val TRIGGER = 14.0
    }
}
