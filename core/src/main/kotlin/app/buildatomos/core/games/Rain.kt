package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The calm between levels: no drops start for this long. */
private const val CALM_MS = 900.0

/** How long Nu shakes after a red (ms). */
private const val SHAKE_MS = 320.0

/**
 * Rain (the phone's src/nu/field.ts). Nu runs along the bottom; photons rain down: catch them.
 * Red ones rain too: each caught costs one of three lives, and the last one loses the run. Some
 * drops fall faster than the rest. A life lost shows plainly: red sparks and a wide red ring
 * from Nu, Nu shaken for a moment, the lives swelling and the lost one flashing red, and a tap
 * on the wrist.
 * A level is a set of photons caught (12, and one more every other level); then a short calm
 * with no new drops, and the rain comes back harder. The lives carry on from level to level, and
 * one comes back every third level. Heat makes the rain faster, thicker and redder, and brings in
 * what the phone brought with the catches: from a little past Very easy some drops drift
 * sideways and reds come in pairs, and toward Expert in threes and in rows with one gap to slip
 * through. Past Expert it keeps thickening, up to a drop every ~120 ms and two reds in three.
 * On the watch Nu runs along a level line across the lower part of the round court, as far as
 * keeps it whole on the screen, and the crown moves it: a radian of crown is a third of the run.
 * A finger slid anywhere still moves it too, as on the phone. Each drop starts just above the rim
 * where it falls, so it comes in through the edge of the circle; the drifting ones bounce off two
 * unseen walls set in from the sides, so they stay inside it.
 */
class Rain : Toy {
    private class Drop(var x: Double, var y: Double, val v: Double, var vx: Double, val red: Boolean)
    private val drops = mutableListOf<Drop>()
    private var x = 0.0
    private var grab: Double? = null
    private var want: Double? = null
    /** Moving by the crown: `want` is where it sent Nu, dropped once Nu gets there. */
    private var turning = false
    /** Photons caught in this level. */
    private var caught = 0
    private var left = 3
    /** When the last life was lost. */
    private var hitAt = -1e9
    private var next = 600.0
    /** Drops fall about a third as far here as on the phone, so they fall slower, to leave the
     *  phone's time to get under them. */
    private val FALL = 0.55
    private var over = false
    /** Half the room across, from the middle to the walls (the phone's s.hw). */
    private var wall = 0.0

    /** Nu's line, under the middle. */
    private fun row(s: Stage) = s.c.y + s.R * 0.58
    /** How far Nu runs either way from the middle. */
    private fun run(s: Stage) = wall - s.nu.r - 6
    /** Just above the rim, at `x`. */
    private fun top(s: Stage, x: Double) = s.c.y - sqrt(max(0.0, s.R * s.R - (x - s.c.x) * (x - s.c.x))) - 10

    override fun enter(s: Stage) {
        s.nu.r = 11.0
        // The walls are where Nu, at the end of its run, just keeps clear of the rim.
        val dy = row(s) - s.c.y
        wall = sqrt((s.R - s.nu.r - 4) * (s.R - s.nu.r - 4) - dy * dy) + s.nu.r + 6
        x = s.c.x
        s.nu.at(Pt(x, row(s)))
    }

    /** Photons to catch to clear the level. */
    private fun need(s: Stage) = 12 + s.level / 2

    /** How far into the phone's run the rain is: the phone's count of catches, which made it
     *  harder there, about 30 at Expert, and climbing with each catch toward the next level. */
    private fun depth(s: Stage) = (s.heat + HEAT_PER_LEVEL * s.part) * 30

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        want?.let { w ->
            x += clamp(w - x, -0.8 * dt, 0.8 * dt)
            if (turning && abs(w - x) < 1e-3) { turning = false; want = null }
        }
        x = clamp(x, s.c.x - run(s), s.c.x + run(s))
        nu.seek(Pt(x, row(s)), dt, 1.2, 0.08)
        if (over) return
        next -= dt
        if (next <= 0) {
            val n = depth(s)
            // Drops come quicker with the heat.
            next = max(120.0, 580.0 - n * 13) * (0.6 + rnd() * 0.8) / 1.2
            val lo = s.c.x - wall + 24
            val hi = s.c.x + wall - 24
            // Red from the first drop: from one in three to two in three.
            val red = rnd() < min(0.66, 0.32 + n * 0.01)
            // Nearly half fall faster, up to 60% faster.
            val fast = if (rnd() < 0.45) 1 + rnd() * 0.6 else 1.0
            // Soon some drift sideways (bouncing off the walls).
            val vx = if (n > 4 && rnd() < min(0.6, (n - 4) * 0.04)) (rnd() - 0.5) * 0.16 * FALL else 0.0
            val at = lo + rnd() * (hi - lo)
            val v = (0.17 + min(0.4, n * 0.008) + rnd() * 0.06) * fast * FALL
            // Later, now and then a row of reds across, with one gap wide enough for Nu: a
            // moment's calm after it, so the row can be read. A row comes in level, from above
            // the top of the circle.
            if (n > 14 && rnd() < min(0.28, (n - 14) * 0.02)) {
                val gap = max(76.0, nu.r * 2 + 50)
                val g0 = lo + rnd() * (hi - lo - gap)
                val sv = (0.17 + min(0.3, n * 0.006)) * FALL
                val y = s.c.y - s.R - 10
                var gx = lo
                while (gx <= hi) {
                    if (gx < g0 - 8 || gx > g0 + gap + 8) drops += Drop(gx, y, sv, 0.0, true)
                    gx += 30
                }
                next += 500
            } else {
                drops += Drop(at, top(s, at), v, vx, red)
                // Soon reds can come in pairs or threes, spread a Nu's width or two apart.
                if (red && n > 5 && rnd() < min(0.55, (n - 5) * 0.04)) {
                    val more = if (n > 12 && rnd() < 0.35) 2 else 1
                    val side = if (rnd() < 0.5) -1 else 1
                    for (j in 1..more) {
                        val x2 = clamp(at + side * j * (34 + rnd() * 30), lo, hi)
                        drops += Drop(x2, top(s, x2) - rnd() * 40, v, vx, true)
                    }
                }
            }
        }
        val it = drops.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.y += d.v * dt
            d.x += d.vx * dt
            if (abs(d.x - s.c.x) > wall - 8) { d.vx = -d.vx; d.x = clamp(d.x, s.c.x - wall + 8, s.c.x + wall - 8) }
            if (dist(d.x, d.y, nu.x, nu.y) < nu.r + 6) {
                it.remove()
                if (d.red) {
                    left--
                    hitAt = now
                    s.fx.ring(nu.p, Ink.RED, nu.r, nu.r + 44, 700.0)
                    s.fx.burst(nu.p, 10, Ink.RED, 0.14)
                    s.out.miss()
                    s.out.buzz(30)
                    if (left <= 0) {
                        over = true
                        drops.clear()
                        s.lose()
                        return
                    }
                } else {
                    caught++
                    s.fx.burst(Pt(d.x, d.y), 6, Ink.CYAN, 0.1)
                    s.out.capture()
                    s.part = caught.toDouble() / need(s)
                    if (caught >= need(s)) {
                        caught = 0
                        s.clear()
                        if (s.over) { over = true; drops.clear(); return }
                        if (s.level % 3 == 0 && left < 3) left++
                        next = max(next, CALM_MS)
                    }
                }
                continue
            }
            // Gone out through the bottom of the circle.
            if (d.y > s.c.y && dist(d.x, d.y, s.c.x, s.c.y) > s.R + 10) it.remove()
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        for (d in drops) {
            // Only once it's in through the rim.
            if (dist(d.x, d.y, s.c.x, s.c.y) > s.R + 6) continue
            if (d.red) pen.dot(d.x, d.y, 6.0, Ink.RED)
            else { pen.glow(d.x, d.y, 5.0, Ink.CYAN); pen.dot(d.x, d.y, 5.0, Ink.CYAN) }
        }
        lives(s, pen, left, lost = now - hitAt)
        // Shaken for a moment by a red: drawn aside, not moved.
        val shake = clamp(1 - (now - hitAt) / SHAKE_MS, 0.0, 1.0)
        val dx = sin((now - hitAt) / 18) * 4 * shake
        s.nu.x += dx
        s.nu.draw(pen, now)
        s.nu.x -= dx
    }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        grab = null
        val from = if (turning) want ?: x else x
        want = clamp(from + by * run(s) * 2 / 3, s.c.x - run(s), s.c.x + run(s))
        turning = true
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        turning = false
        grab = x - p.x
        want = x
    }

    override fun move(s: Stage, p: Pt) {
        grab?.let { g -> turning = false; want = p.x + g }
    }

    override fun up(s: Stage, p: Pt) {
        grab = null
        if (!turning) want = null
    }
}
