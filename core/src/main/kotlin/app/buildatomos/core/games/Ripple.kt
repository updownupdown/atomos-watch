package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/** The phone's reach, speed and push, scaled to the watch's smaller court (138 / 190), so a
 *  ripple doesn't sweep the whole circle and small nudges are still possible. */
private const val SMALL = 138.0 / 190.0
private const val REACH = 260.0 * SMALL
private const val SPEED = 0.24 * SMALL
/** How long the clock waits for the first ripple before it starts anyway (ms). */
private const val WAIT = 5000.0
/** The calm between levels, while the rim fills again (ms). */
private const val CALM_MS = 800.0
/** The rim's soft edge: within this of the rim the water eases Nu back in (units), and how hard
 *  at the rim itself (units/ms²). */
private const val CUSHION = 16.0
private const val EASE = 0.00003

/**
 * Ripple (the phone's src/nu/swim.ts). Nu floats on still water. A tap starts a ripple, and as
 * it passes Nu it pushes Nu away from where it started, the harder the nearer. Float Nu into the
 * ring; each time, a new ring. The rim counts the level's time down; out of time, the run is lost.
 * A level is a set of rings (3, and one more every third level) against its own clock; then a
 * short calm while the rim fills again, and the next level's clock starts. Heat makes the rings
 * smaller (a radius of 32 at Very easy, about 22 at Expert, never under 16) and gives less time for
 * each (8 s at Very easy, 4.5 s at Expert, never under 3 s).
 * On the watch the water is the circle, and Nu bounces off its rim, keeping most of its speed;
 * near the rim the water also eases Nu gently back in, so it never sits stuck against the edge
 * with no room outside it to tap. A new ring is placed well
 * away from Nu as on the phone, but the phone's 140 is more than most of the circle allows, so
 * it wants three quarters of the court's radius and otherwise takes the farthest spot it tried;
 * never under the shell's menu at the top. The first level's clock starts with the first ripple,
 * or after five seconds without one, so a run nobody plays still ends.
 */
class Ripple : Toy {
    private class Wave(val x: Double, val y: Double, val t0: Double)
    private class Goal(val x: Double, val y: Double, val r: Double) { val p get() = Pt(x, y) }
    private val body = Mover(0.0, 0.0)
    private val waves = mutableListOf<Wave>()
    private var goal = Goal(0.0, 0.0, 30.0)
    /** Rings reached in this level. */
    private var caught = 0
    /** When the level's clock started, and how long it has. */
    private var start: Double? = null
    private var bank = 1.0
    private var waited = 0.0
    /** The calm between levels: the clock starts again then. */
    private var calm: Double? = null
    private var over = false

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.nu.at(s.c)
        body.x = s.nu.x; body.y = s.nu.y
        place(s)
        bank = need(s) * each(s)
        s.rim = 1.0
    }

    /** Rings to reach to clear the level. */
    private fun need(s: Stage) = 3 + s.level / 3

    /** The heat, climbing a little with each ring toward the next level's. */
    private fun h(s: Stage) = s.heat + HEAT_PER_LEVEL * s.part

    /** The time the clock gives for each ring. */
    private fun each(s: Stage) = 3000 + 5000 * exp(-1.2 * s.heat)

    private fun place(s: Stage) {
        val r = 16 + 16 * exp(-0.9 * h(s))
        val far = min(140.0, s.R * 0.75)
        val at = Pt(body.x, body.y)
        // The shell's menu, at the top: no ring under it.
        val menu = Pt(s.c.x, s.c.y - s.R + 5)
        fun ok(q: Pt) = dist(q, menu) > 28 + r
        var p = anywhere(s, r + 14)
        for (k in 0 until 40) {
            if (ok(p) && dist(p, at) >= far) break
            val q = anywhere(s, r + 14)
            if (ok(q) && (!ok(p) || dist(q, at) > dist(p, at))) p = q
        }
        goal = Goal(p.x, p.y, r)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        val b = body
        if (over) { nu.seek(goal.p, dt, 0.3, 0.01); return }
        // The first clock starts with the first ripple (or once the water has waited long
        // enough); the next ones after the calm, as the rim fills again.
        calm?.let { c ->
            s.rim = clamp(1 - (c - now) / CALM_MS, s.rim ?: 0.0, 1.0)
            if (now >= c) { calm = null; start = now }
        }
        if (start == null && calm == null) { waited += dt; if (waited >= WAIT) start = now }
        if (calm == null) {
            val left = start?.let { 1 - (now - it) / bank } ?: 1.0
            s.rim = clamp(left, 0.0, 1.0)
            if (left <= 0) {
                over = true
                s.fx.ring(nu.p, Ink.RED, nu.r, nu.r + 24, 500.0)
                s.out.miss()
                s.out.buzz(24)
                s.lose()
                return
            }
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
        // The soft edge: eased back in, the harder the nearer the rim.
        val (out, nx, ny) = edge(s, Pt(b.x, b.y))
        val gap = -out - nu.r
        if (gap < CUSHION) {
            val k = EASE * (1 - gap.coerceAtLeast(0.0) / CUSHION)
            b.vx -= nx * k * dt
            b.vy -= ny * k * dt
        }
        b.x += b.vx * dt
        b.y += b.vy * dt
        if (bounceIn(s, b, nu.r, 0.85)) s.out.tick(0)
        nu.x = b.x
        nu.y = b.y
        nu.vx = b.vx
        nu.vy = b.vy
        if (calm == null && dist(b.x, b.y, goal.x, goal.y) < goal.r - nu.r * 0.4) {
            caught++
            s.fx.ring(goal.p, Ink.GREEN, goal.r, goal.r + 20, 500.0)
            s.out.capture()
            s.part = caught.toDouble() / need(s)
            if (caught >= need(s)) {
                caught = 0
                s.clear()
                if (s.over) { over = true; return }
                bank = need(s) * each(s)
                calm = now + CALM_MS
            }
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
