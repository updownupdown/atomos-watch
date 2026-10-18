package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Halo (the phone's src/nu/court.ts). The nucleus throws photons outward; Nu runs round the ring
 * to catch them. A red one caught, or a photon that gets past, costs one of three lives.
 * On the watch the crown is the wheel: turn it and Nu goes round the ring just as far. A finger
 * on the screen still works too: Nu runs round toward it.
 */
class Halo : Toy {
    private var a = -Math.PI / 2
    private var want: Double? = null
    /** Turning by the crown: `want` is then not wrapped, so a quick turn still goes the right way. */
    private var turning = false
    private class Shot(val a: Double, var r: Double, val v: Double, val red: Boolean)
    private val shots = mutableListOf<Shot>()
    private var caught = 0
    private var left = 3
    private var next = 600.0
    private var over = false

    private fun track(s: Stage) = s.R * 0.78
    private fun pos(s: Stage, a: Double, r: Double = track(s)) = polar(s, a, r)
    private fun at(s: Stage) = atan2(s.nu.y - s.c.y, s.nu.x - s.c.x)
    private fun reach(s: Stage) = 2 * s.nu.r + 4.5

    override fun enter(s: Stage) {
        s.nu.r = 10.0
        s.nu.at(pos(s, a))
        s.score(0)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        // Nu runs round after the crown or the finger, no faster than it can.
        want?.let { w ->
            val d = if (turning) w - a else turn(a, w)
            val top = if (turning) 0.012 else 0.0075
            a += clamp(d, -top * dt, top * dt)
            if (turning && abs(w - a) < 1e-4) { turning = false; want = null }
        }
        s.nu.seek(pos(s, a), dt, 2.0, 0.2)
        if (over) return
        next -= dt
        if (next <= 0) {
            next = max(420.0, 1300.0 - caught * 18) * (0.7 + rnd() * 0.6)
            shots += Shot(rnd() * TAU, s.R * 0.1, 0.06 + min(0.08, caught * 0.002), caught > 3 && rnd() < 0.25)
        }
        val t = track(s)
        val at = at(s)
        val reach = reach(s)
        val it = shots.iterator()
        while (it.hasNext()) {
            val q = it.next()
            val was = q.r
            q.r += q.v * dt
            // Crossing the ring: is Nu there?
            if (was < t && q.r >= t) {
                val near = abs(turn(at, q.a)) * t < reach
                if (near && !q.red) {
                    caught++
                    it.remove()
                    s.fx.ring(s.nu.p, Ink.CYAN, s.nu.r, s.nu.r + 18, 400.0)
                    s.out.capture()
                    s.score(caught)
                    continue
                }
                if (near && q.red) { it.remove(); lose(s, s.nu.p); if (over) return; continue }
            }
            if (q.r > s.R + 6) {
                it.remove()
                if (!q.red) { lose(s, pos(s, q.a, s.R - 4)); if (over) return }
            }
        }
    }

    private fun lose(s: Stage, at: Pt) {
        left--
        s.fx.ring(at, Ink.RED, 6.0, 26.0, 500.0)
        s.out.miss()
        if (left <= 0) {
            over = true
            shots.clear()
            finish(s, "halo", caught)
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        pen.ring(s.c, track(s), Ink.K500, 3.0, 0.7)
        pen.dot(s.c, 9 + sin(now / 300) * 1.2, Ink.K300, 0.85)
        for (q in shots) {
            val p = pos(s, q.a, q.r)
            if (q.red) pen.ring(p, 4.5, Ink.RED, 3.0)
            else { pen.glow(p.x, p.y, 4.5, Ink.CYAN); pen.dot(p, 4.5, Ink.CYAN) }
        }
        lives(s, pen, left)
        // The paddle: a white arc on the ring under Nu, as wide as it catches.
        val t = track(s)
        val half = (2 * s.nu.r - 2.5) / t
        pen.arc(s.c.x, s.c.y, t, at(s) - half, 2 * half, Ink.WHITE, 5.0, if (over) 0.4 else 0.9)
        s.nu.draw(pen, now)
    }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        want = (if (turning) want ?: a else a) + by
        turning = true
    }

    private fun aim(s: Stage, p: Pt) {
        turning = false
        want = atan2(p.y - s.c.y, p.x - s.c.x)
    }

    override fun down(s: Stage, p: Pt) { if (!over) aim(s, p) }
    override fun move(s: Stage, p: Pt) { if (!over && !turning) aim(s, p) }
    override fun up(s: Stage, p: Pt) { if (!turning) want = null }
}
