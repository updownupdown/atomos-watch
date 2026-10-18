package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val RUN_MS = 40_000.0
/** The phone's reach, speed and push, scaled to the watch's smaller court (138 / 190), so a
 *  ripple doesn't sweep the whole circle and small nudges are still possible. */
private const val SMALL = 138.0 / 190.0
private const val REACH = 260.0 * SMALL
private const val SPEED = 0.24 * SMALL
/** How long the clock waits for the first ripple before it starts anyway (ms). */
private const val WAIT = 5000.0

/**
 * Ripple (the phone's src/nu/swim.ts). Nu floats on still water. A tap starts a ripple, and as
 * it passes Nu it pushes Nu away from where it started, the harder the nearer. Float Nu into the
 * ring; each time, a new ring, smaller as you go. The rim counts the time down.
 * On the watch the water is the circle, and Nu bounces off its rim. A new ring is placed well
 * away from Nu as on the phone, but the phone's 140 is more than most of the circle allows, so
 * it wants three quarters of the court's radius and otherwise takes the farthest spot it tried.
 * The clock still starts with the first ripple, or after five seconds without one, so a run
 * nobody plays still ends.
 */
class Ripple : Toy {
    private class Wave(val x: Double, val y: Double, val t0: Double)
    private class Goal(val x: Double, val y: Double, val r: Double) { val p get() = Pt(x, y) }
    private val body = Mover(0.0, 0.0)
    private val waves = mutableListOf<Wave>()
    private var goal = Goal(0.0, 0.0, 30.0)
    private var count = 0
    private var start: Double? = null
    private var waited = 0.0
    private var over = false

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.nu.at(s.c)
        body.x = s.nu.x; body.y = s.nu.y
        place(s)
        s.rim = 1.0
        s.score(0)
    }

    private fun place(s: Stage) {
        val r = max(18.0, 32 - count * 1.2)
        val far = min(140.0, s.R * 0.75)
        val at = Pt(body.x, body.y)
        var p = anywhere(s, r + 14)
        for (k in 0 until 30) {
            if (dist(p, at) >= far) break
            val q = anywhere(s, r + 14)
            if (dist(q, at) > dist(p, at)) p = q
        }
        goal = Goal(p.x, p.y, r)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        val b = body
        if (over) { nu.seek(goal.p, dt, 0.3, 0.01); return }
        // The clock starts with the first ripple (or once the water has waited long enough).
        if (start == null) { waited += dt; if (waited >= WAIT) start = now }
        val left = start?.let { 1 - (now - it) / RUN_MS } ?: 1.0
        s.rim = clamp(left, 0.0, 1.0)
        if (left <= 0) {
            over = true
            s.out.complete()
            finish(s, "ripple", count)
            s.score(count)
            return
        }
        val it = waves.iterator()
        while (it.hasNext()) {
            val w = it.next()
            val was = (now - dt - w.t0) * SPEED
            val r = (now - w.t0) * SPEED
            val d = dist(w.x, w.y, b.x, b.y)
            if (was < d && r >= d && d < REACH) {
                val k = 0.42 * SMALL * (1 - d / REACH).pow(0.7)
                val n = if (d > 0) d else 1.0
                b.vx += (b.x - w.x) / n * k
                b.vy += (b.y - w.y) / n * k
            }
            if (r >= REACH) it.remove()
        }
        val drag = exp(-dt / 1100)
        b.vx *= drag
        b.vy *= drag
        b.x += b.vx * dt
        b.y += b.vy * dt
        if (bounceIn(s, b, nu.r, 0.6)) s.out.tick(0)
        nu.x = b.x
        nu.y = b.y
        nu.vx = b.vx
        nu.vy = b.vy
        if (dist(b.x, b.y, goal.x, goal.y) < goal.r - nu.r * 0.4) {
            count++
            s.fx.ring(goal.p, Ink.GREEN, goal.r, goal.r + 20, 500.0)
            s.out.capture()
            s.score(count)
            place(s)
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        // Each ripple a bright front ring with a fainter one following, and a mark where it
        // started while it's young.
        for (w in waves) {
            val r = (now - w.t0) * SPEED
            val f = 1 - r / REACH
            if (f <= 0) continue
            pen.ring(w.x, w.y, r, Ink.CYAN, 4.0, 0.95 * f)
            if (r > 16) pen.ring(w.x, w.y, r - 14, Ink.CYAN, 3.0, 0.45 * f)
            if (r < 60) pen.dot(w.x, w.y, 5.0, Ink.CYAN, 0.8 * (1 - r / 60))
        }
        if (!over) {
            pen.ring(goal.x, goal.y, goal.r, Ink.WHITE, 3.0, 0.85)
            pen.dot(goal.x, goal.y, goal.r - 7, Ink.K500, 0.35)
        }
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        val now = s.now
        if (start == null) start = now
        // A few at a time at most: the water needs to settle.
        if (waves.size >= 4) return
        waves += Wave(p.x, p.y, now)
        s.out.tick(2)
    }
}
