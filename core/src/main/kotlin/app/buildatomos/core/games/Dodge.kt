package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** How long a level lasts: survive this. */
private const val LEVEL_MS = 15_000.0

/** The calm at a level's start: no new reds for this long. */
private const val CALM_MS = 900.0

/** After a hit, Nu can't be hit again for this long (it blinks). */
private const val SAFE_MS = 1300.0

/**
 * Dodge (the phone's src/nu/meadow.ts). Red things fly straight across the court from every side,
 * aimed at Nu or near it; keep Nu clear of them. Bigger reds are slower, smaller ones quicker.
 * A level is 15 seconds survived. On the phone one hit ends it; on the watch Nu has three lives
 * (a hit makes it blink, safe, for a moment), and one comes back every third level, for runs of a
 * minute or two. Heat sends them more often and faster; within a level they come a little quicker
 * toward its end.
 * On the watch a finger would hide Nu, so a drag moves Nu by how far the finger moves, wherever it
 * is on the screen: Nu doesn't jump under it. The crown sends Nu round the middle in a circle, at
 * its distance from the middle (eased out to at least 40% of the court, so a turn always moves it a
 * useful way), a full turn of the crown a full circle; a faint ring shows that circle while it
 * turns. Nobody playing: the reds find Nu, and three hits end it.
 */
class Dodge : Toy {
    private class Red(x: Double, y: Double, vx: Double, vy: Double, val r: Double) : Mover(x, y, vx, vy)
    private val reds = mutableListOf<Red>()
    /** Dragging: the offset from the finger to Nu's target, kept as it moves. */
    private var grab: Pt? = null
    private var want: Pt? = null
    /** Turning by the crown: Nu's angle round the middle now and where the crown sent it, unwrapped, and its distance. */
    private var turning = false
    private var ang = 0.0
    private var wantA = 0.0
    private var rad = 0.0
    private var lastTurn = -1e9
    private var t = 0.0
    private var next = CALM_MS
    private var left = 3
    private var safeUntil = -1e9
    private var over = false

    private fun minRad(s: Stage) = s.R * 0.4
    private fun maxRad(s: Stage) = s.R - s.nu.r - 4

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.nu.at(polar(s, PI, s.R * 0.4))
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        if (turning) {
            ang += clamp(wantA - ang, -0.012 * dt, 0.012 * dt)
            rad += (clamp(rad, minRad(s), maxRad(s)) - rad) * (1 - exp(-dt / 120))
            val p = polar(s, ang, rad)
            nu.vx = (p.x - nu.x) / dt
            nu.vy = (p.y - nu.y) / dt
            nu.x = p.x
            nu.y = p.y
            if (abs(wantA - ang) < 1e-4 && rad >= minRad(s) - 0.5) turning = false
        } else want?.let { w ->
            nu.seek(inside(s, w, nu.r + 2), dt, 0.9, 0.05)
        } ?: nu.seek(nu.p, dt)
        val kept = inside(s, nu.p, nu.r + 2)
        nu.x = kept.x; nu.y = kept.y
        val safe = now < safeUntil
        nu.glow = if (safe) 0.4 else 1.0
        if (over) return

        t += dt
        s.part = clamp(t / LEVEL_MS, 0.0, 1.0)
        next -= dt
        if (next <= 0) {
            val h = s.heat
            // One every 0.85 s at the easiest, 0.5 s at Expert, never quicker than 0.2 s; a
            // little quicker as the level goes on.
            next = max(200.0, (850.0 - 350 * h) * (1 - 0.25 * s.part)) * (0.75 + rnd() * 0.5)
            val a = rnd() * TAU
            val from = polar(s, a, s.R + 14)
            val to = Pt(nu.x + (rnd() - 0.5) * 100, nu.y + (rnd() - 0.5) * 100)
            val r = 4 + rnd() * 5
            val d = dist(from, to).takeIf { it > 0 } ?: 1.0
            val v = (min(0.2, 0.075 + 0.05 * h) + rnd() * 0.03) * (1.3 - (r - 4) * 0.08)
            reds += Red(from.x, from.y, (to.x - from.x) / d * v, (to.y - from.y) / d * v, r)
        }
        for (q in reds) { q.x += q.vx * dt; q.y += q.vy * dt }
        reds.removeAll { hypot(it.x - s.c.x, it.y - s.c.y) > s.R + 40 }
        if (!safe) {
            val hit = reds.firstOrNull { dist(it.x, it.y, nu.x, nu.y) < nu.r + it.r - 2 }
            if (hit != null) {
                reds.remove(hit)
                left--
                safeUntil = now + SAFE_MS
                s.fx.ring(nu.p, Ink.RED, nu.r, nu.r + 30, 600.0)
                s.fx.burst(Pt(hit.x, hit.y), 6, Ink.RED, 0.08)
                s.out.miss()
                s.out.buzz(24)
                if (left <= 0) { over = true; want = null; grab = null; turning = false; s.lose(); return }
            }
        }
        if (t >= LEVEL_MS) {
            t = 0.0
            s.fx.ring(nu.p, Ink.CYAN, nu.r, nu.r + 22, 500.0)
            s.clear()
            if (s.over) { over = true; return }
            s.part = 0.0
            if (s.level % 3 == 0 && left < 3) left++
            next = max(next, CALM_MS)
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        // The crown's circle, a moment after each turn.
        val f = clamp(1 - (now - lastTurn - 400) / 800, 0.0, 1.0)
        if (f > 0) pen.ring(s.c, hypot(s.nu.x - s.c.x, s.nu.y - s.c.y), Ink.K700, 1.5, 0.7 * f)
        for (q in reds) {
            val a = if (over) 0.45 else 1.0
            pen.dot(q.x, q.y, q.r * 1.6, Ink.RED, 0.16 * a)
            pen.dot(q.x, q.y, q.r, Ink.RED, 0.95 * a)
        }
        lives(s, pen, left)
        val blink = now < safeUntil && ((now / 110).toInt() % 2 == 0)
        s.nu.hidden = blink && !over
        s.nu.draw(pen, now)
        s.nu.hidden = false
    }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        if (!turning) {
            ang = atan2(s.nu.y - s.c.y, s.nu.x - s.c.x)
            wantA = ang
            rad = hypot(s.nu.x - s.c.x, s.nu.y - s.c.y)
            turning = true
        }
        wantA += by
        grab = null
        want = null
        lastTurn = s.now
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        turning = false
        grab = Pt(s.nu.x - p.x, s.nu.y - p.y)
        want = s.nu.p
    }

    override fun move(s: Stage, p: Pt) {
        if (over || turning) return
        val g = grab ?: return
        want = Pt(p.x + g.x, p.y + g.y)
        // Keep the offset honest when the target is held at the rim, so a drag back moves Nu at once.
        val kept = inside(s, want!!, s.nu.r + 2)
        g.x = kept.x - p.x; g.y = kept.y - p.y
        want = kept
    }

    override fun up(s: Stage, p: Pt) { grab = null; want = null }
}
