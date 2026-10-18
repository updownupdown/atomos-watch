package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Blast (the phone's src/nu/meadow.ts). Nu in the middle of the court, red atoms drifting in
 * from the rim. Tap and Nu fires a photon toward the tap; it bursts the first red it meets. A red
 * reaching Nu costs one of three lives. They come faster and thicker as you go.
 * The phone already played it in the round court, so on the watch only the court is smaller:
 * the reds start nearer Nu and reach it sooner.
 */
class Blast : Toy {
    private val reds = mutableListOf<Mover>()
    private val shots = mutableListOf<Mover>()
    private var nextAt = 0.0
    private var left = 3
    private var count = 0
    private var over = false

    override fun enter(s: Stage) {
        s.nu.r = 11.0
        s.nu.at(s.c)
        nextAt = s.now + 900
        s.score(0)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        s.nu.seek(s.c, dt, 0.5, 0.02)
        if (over) return
        if (now >= nextAt) {
            nextAt = now + max(420.0, 1500.0 - count * 30) * (0.7 + rnd() * 0.6)
            val a = rnd() * TAU
            val v = 0.035 + min(0.05, count * 0.0012)
            val p = polar(s, a, s.R - 6)
            // Not quite straight at Nu.
            val aim = a + Math.PI + (rnd() - 0.5) * 0.25
            reds += Mover(p.x, p.y, cos(aim) * v, sin(aim) * v)
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
                count++
                s.fx.burst(Pt(r.x, r.y), 8, Ink.RED, 0.1)
                // Pitched by how near it got and how fast it came: higher the nearer and faster.
                s.out.hit(clamp(0.65 * (1 - d / s.R) + 0.35 * min(1.0, hypot(r.vx, r.vy) / 0.12), 0.0, 1.0))
                s.score(count)
                continue
            }
            if (d < s.nu.r + 8) {
                reds.removeAt(i)
                left--
                s.fx.ring(s.nu.p, Ink.RED, s.nu.r, s.nu.r + 30, 600.0)
                s.out.miss()
                s.out.buzz(24)
                if (left <= 0) {
                    over = true
                    finish(s, "blast", count)
                    s.score(count)
                    return
                }
            }
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        for (r in reds) { pen.dot(r.x, r.y, 9.0, Ink.RED, 0.25); pen.ring(r.x, r.y, 7.5, Ink.RED, 3.0, 0.9) }
        for (q in shots) pen.dot(q.x, q.y, 4.5, Ink.CYAN)
        lives(s, pen, left)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        val dx = p.x - s.nu.x
        val dy = p.y - s.nu.y
        val d = hypot(dx, dy)
        if (d < 4 || shots.size >= 4) return
        shots += Mover(s.nu.x, s.nu.y, dx / d * 0.6, dy / d * 0.6)
        s.out.tick(2)
    }
}
