package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val BR = 9.0
private const val POST = 12.0
/** What a bounce keeps of Nu's speed. */
private const val KEEP = 0.85
/** A shot by the crown and a tap: this fast (units/ms), slowing as it goes. */
private const val SHOT = 0.5
private const val SLOW_MS = 1700.0
private const val CALM_MS = 900.0

/**
 * Bank (the phone's src/nu/glade.ts). Bank shots: green rings, grey posts and, from Medium, red
 * blocks stand about the court; Nu waits low in it. Aim and shoot: Nu flies off, bounces off the
 * rim and the posts, a little slower each time, and catches every ring it passes through. Dots
 * show the way it will go, round the first bounce at the gentlest, less of it as heat rises. Red
 * stops the shot. Each layout gives so many shots: the rings, and a few spare at the gentlest.
 * A level is a layout's rings all caught. Out of shots with rings left costs one of three lives,
 * and a fresh layout is dealt; the last life lost loses the run. Lives carry on from level to
 * level, and one comes back every third level. Heat brings more rings (four at the gentlest, five
 * at Expert, six past it) and smaller ones, more posts, red blocks from about Medium, fewer spare
 * shots (three at the gentlest, none at Expert), a shorter guide, and from Medium some rings that
 * drift to and fro. Every ring can be shot straight at from where Nu waits, wherever it drifts.
 * On the watch the crown aims (a turn of the crown, a turn of the aim, so it can be set finely
 * without a finger over the court) and a tap anywhere shoots at full strength. A finger can also
 * pull back from anywhere and let go, as on the phone, harder the further the pull. The court is
 * the round one, so every shot banks off the circle. Shots left show as small cyan dots low on
 * the right, mirroring the lives. Left alone, Nu pulses pale three times and shoots where it aims, a
 * shot that never catches every ring, so a run nobody plays ends.
 */
class Bank : Toy {
    /** A ring: at (bx, by), or drifting to and fro either side of it, `ax`, `ay` at the furthest,
     *  `w` radians a millisecond. */
    internal class Ring(val bx: Double, val by: Double, val ax: Double = 0.0, val ay: Double = 0.0, val w: Double = 0.0, val ph: Double = 0.0) {
        var got = false
        var x = bx
        var y = by
        fun at(t: Double): Pt = Pt(bx + ax * sin(w * t + ph), by + ay * sin(w * t + ph))
        val drifts get() = w != 0.0
    }
    internal class Block(val x: Double, val y: Double, val w: Double, val h: Double)
    internal val rings = mutableListOf<Ring>()
    internal val posts = mutableListOf<Pt>()
    internal val blocks = mutableListOf<Block>()
    private val b = Mover(0.0, 0.0)
    private enum class St { BACK, READY, FLY, CLEAR }
    private var st = St.BACK
    /** Where it aims: an angle, up to start. */
    internal var aim = -PI / 2
        private set
    private var from: Pt? = null
    private var pull: Pt? = null
    internal var shots = 0
        private set
    private var flyMs = 0.0
    private var calm = 0.0
    private var left = LIVES
    private var ringR = 12.0
    private val wait = Patience()
    private var over = false

    internal fun home(s: Stage) = Pt(s.c.x, s.c.y + s.R * 0.58)
    internal val ready: Boolean get() = st == St.READY && !over
    internal val catchR: Double get() = ringR + BR * 0.6

    override fun enter(s: Stage) {
        s.nu.r = BR
        s.nu.at(home(s))
        s.rim = null
        layout(s)
    }

    /** A new layout: rings, posts and blocks where they don't crowd each other or Nu, every ring
     *  in plain sight of Nu, and the shot straight up never catching them all. */
    private fun layout(s: Stage) {
        val h = s.heat
        ringR = max(9.0, 13 - 3.5 * h)
        val home = home(s)
        for (attempt in 0 until 60) {
            rings.clear(); posts.clear(); blocks.clear()
            val placed = mutableListOf<Triple<Double, Double, Double>>()
            fun spot(r: Double): Pt {
                var p = s.c
                for (k in 0 until 200) {
                    p = Pt(s.c.x + (rnd() * 2 - 1) * (s.R - 20), s.c.y - s.R + 34 + rnd() * (s.R * 1.32))
                    // In from the rim, above Nu and clear of it, off the menu at the top.
                    if (edge(s, p).first < -r - 12 && p.y < home.y - 30 && dist(p, home) > 70 &&
                        dist(p, Pt(s.c.x, s.c.y - s.R + 5)) > 34 + r &&
                        placed.all { (x, y, q) -> dist(x, y, p.x, p.y) > q + r + 14 }) break
                }
                placed += Triple(p.x, p.y, r)
                return p
            }
            repeat(ringsOf(h)) {
                if (rnd() < drifters(h)) {
                    val amp = 14 + rnd() * 10
                    val p = spot(ringR + amp)
                    val d = rnd() * TAU
                    rings += Ring(p.x, p.y, cos(d) * amp, sin(d) * amp, TAU / (2600 + rnd() * 1200), rnd() * TAU)
                } else {
                    val p = spot(ringR)
                    rings += Ring(p.x, p.y)
                }
            }
            repeat(postsOf(h)) { posts += spot(POST) }
            repeat(blocksOf(h)) {
                val p = spot(20.0)
                val across = rnd() < 0.6
                blocks += Block(p.x, p.y, if (across) 18.0 else 6.0, if (across) 6.0 else 18.0)
            }
            // Every ring in plain sight, all along its drift; and the shot straight up never
            // passing a ring, so Nu left alone never clears a layout.
            val clear = rings.all { r -> listOf(-1.0, 0.0, 1.0).all { f -> sight(home, Pt(r.bx + r.ax * f, r.by + r.ay * f)) } }
            if (clear && !passes(s, -PI / 2)) break
        }
        for (r in rings) { val p = r.at(s.now); r.x = p.x; r.y = p.y }
        shots = rings.size + spare(h)
        st = St.BACK
        from = null
        pull = null
        s.part = 0.0
    }

    /** Nothing in the way of a shot straight from `a` to `p`. */
    private fun sight(a: Pt, p: Pt): Boolean {
        val n = 40
        for (i in 1..n) {
            val q = Pt(a.x + (p.x - a.x) * i / n, a.y + (p.y - a.y) * i / n)
            if (posts.any { dist(it, q) < POST + BR + 2 } || hitsBlock(q, BR + 2)) return false
        }
        return true
    }

    private fun hitsBlock(p: Pt, r: Double) = blocks.any { k ->
        hypot(p.x - clamp(p.x, k.x - k.w, k.x + k.w), p.y - clamp(p.y, k.y - k.h, k.y + k.h)) < r
    }

    /** Moves `m` by (dx, dy), off the rim and the posts; true if it bounced. */
    private fun move(s: Stage, m: Mover, dx: Double, dy: Double): Boolean {
        m.x += dx
        m.y += dy
        var hit = bounceIn(s, m, BR, KEEP)
        if (hit) { m.vx *= 0.96; m.vy *= 0.96 }
        for (p in posts) {
            val d = dist(m.x, m.y, p.x, p.y)
            val rr = POST + BR
            if (d >= rr || d == 0.0) continue
            val nx = (m.x - p.x) / d
            val ny = (m.y - p.y) / d
            m.x = p.x + nx * rr
            m.y = p.y + ny * rr
            val vn = m.vx * nx + m.vy * ny
            if (vn < 0) { m.vx -= (1 + KEEP) * vn * nx; m.vy -= (1 + KEEP) * vn * ny; hit = true }
        }
        return hit
    }

    /** True if a full shot from home at `a` would pass within reach of anywhere a ring goes. */
    private fun passes(s: Stage, a: Double): Boolean {
        val m = Mover(home(s).x, home(s).y, cos(a) * SHOT, sin(a) * SHOT)
        var t = 0.0
        while (t < 6000) {
            val n = max(1, (hypot(m.vx, m.vy) * 16 / 3).toInt() + 1)
            for (k in 0 until n) {
                move(s, m, m.vx * 16 / n, m.vy * 16 / n)
                if (rings.any { r -> sweep(r, m.x, m.y) <= catchR + 4 }) return true
                if (hitsBlock(Pt(m.x, m.y), BR)) return false
            }
            val f = exp(-16 / SLOW_MS)
            m.vx *= f; m.vy *= f
            if (hypot(m.vx, m.vy) < 0.06) break
            t += 16
        }
        return false
    }

    /** How near (x, y) comes to anywhere ring `r` drifts. */
    private fun sweep(r: Ring, x: Double, y: Double): Double {
        val l2 = 4 * (r.ax * r.ax + r.ay * r.ay)
        if (l2 == 0.0) return dist(r.bx, r.by, x, y)
        val t = clamp(((x - r.bx + r.ax) * 2 * r.ax + (y - r.by + r.ay) * 2 * r.ay) / l2, 0.0, 1.0)
        return dist(r.bx - r.ax + 2 * r.ax * t, r.by - r.ay + 2 * r.ay * t, x, y)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        val home = home(s)
        for (r in rings) if (r.drifts) { val p = r.at(now); r.x = p.x; r.y = p.y }
        if (st != St.FLY) {
            nu.seek(home, dt, 0.7, 0.03)
            b.x = nu.x; b.y = nu.y; b.vx = 0.0; b.vy = 0.0
            if (over) return
            if (st == St.CLEAR) {
                calm -= dt
                if (calm <= 0) layout(s)
                return
            }
            if (st == St.BACK && dist(nu.p, home) < 3) { st = St.READY; wait.reset(patience(s.heat)) }
            if (st == St.READY && wait.step(dt)) fire(s, aim, SHOT)
            return
        }
        flyMs += dt
        val n = max(1, (hypot(b.vx, b.vy) * dt / 3).toInt() + 1)
        for (k in 0 until n) {
            if (move(s, b, b.vx * dt / n, b.vy * dt / n)) s.out.tick(0)
            for (r in rings) {
                if (r.got || dist(r.x, r.y, b.x, b.y) > catchR) continue
                r.got = true
                s.fx.ring(Pt(r.x, r.y), Ink.GREEN, ringR, ringR + 22, 500.0)
                s.out.capture()
                s.part = clamp(rings.count { it.got }.toDouble() / rings.size, 0.0, 1.0)
            }
            if (hitsBlock(Pt(b.x, b.y), BR)) {
                s.fx.ring(Pt(b.x, b.y), Ink.RED, BR, 34.0, 500.0)
                s.out.miss()
                s.out.buzz(20)
                end(s)
                break
            }
        }
        val f = exp(-dt / SLOW_MS)
        b.vx *= f
        b.vy *= f
        nu.x = b.x
        nu.y = b.y
        if (st == St.FLY && (hypot(b.vx, b.vy) < 0.06 || flyMs > 5500)) end(s)
    }

    /** The shot is done: the layout cleared, the next shot, or a life. */
    private fun end(s: Stage) {
        b.vx = 0.0; b.vy = 0.0
        s.nu.vx = 0.0; s.nu.vy = 0.0
        if (rings.all { it.got }) {
            s.clear()
            if (s.over) { over = true; return }
            if (s.level % 3 == 0) left = min(LIVES, left + 1)
            st = St.CLEAR
            calm = CALM_MS
            return
        }
        st = St.BACK
        if (shots > 0) return
        left--
        s.out.miss()
        s.out.buzz(22)
        for (r in rings) if (!r.got) s.fx.ring(Pt(r.x, r.y), Ink.RED, ringR, ringR + 18, 500.0)
        if (left <= 0) { over = true; s.lose(); return }
        layout(s)
    }

    private fun fire(s: Stage, a: Double, v: Double) {
        if (st != St.READY || over || shots <= 0) return
        b.vx = cos(a) * v
        b.vy = sin(a) * v
        st = St.FLY
        flyMs = 0.0
        shots--
        wait.hold()
        s.out.tick(2)
    }

    /** The way a shot at `a` would go, `len` units of it, as points every 13 units; the last is
     *  red if it stops on a block. */
    private fun guide(s: Stage, a: Double, len: Double): Pair<List<Pt>, Boolean> {
        val m = Mover(b.x, b.y, cos(a), sin(a))
        val pts = mutableListOf<Pt>()
        var run = 0.0
        while (run < len) {
            move(s, m, m.vx * 3, m.vy * 3)
            val l = hypot(m.vx, m.vy).takeIf { it > 0 } ?: 1.0
            m.vx /= l; m.vy /= l
            run += 3
            if (hitsBlock(Pt(m.x, m.y), BR)) return pts + Pt(m.x, m.y) to true
            if (run % 13 < 3 && run > 12) pts += Pt(m.x, m.y)
        }
        return pts to false
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val dim = if (over) 0.5 else 1.0
        for (p in posts) { pen.dot(p, POST, Ink.K500, 0.9 * dim); pen.ring(p, POST - 1.5, Ink.K300, 3.0, 0.9 * dim) }
        for (k in blocks) pen.rect(k.x - k.w, k.y - k.h, 2 * k.w, 2 * k.h, min(k.w, k.h), Ink.RED, 0.9 * dim)
        for (r in rings) if (!r.got) pen.ring(r.x, r.y, ringR, Ink.GREEN, 3.0, 0.95 * dim)
        if (st == St.READY && !over) {
            val (a, len) = pulled()?.let { (pa, pv) -> pa to guideLen(s.heat) * clamp(pv / SHOT, 0.3, 1.0) } ?: (aim to guideLen(s.heat))
            val (pts, red) = guide(s, a, len)
            pts.forEachIndexed { i, p ->
                if (red && i == pts.size - 1) pen.dot(p, 3.5, Ink.RED, 0.85)
                else pen.dot(p, 2.4, Ink.WHITE, 0.8 - 0.5 * i / max(1, pts.size))
            }
        }
        // Shots left, low on the right.
        for (i in 0 until shots) {
            val a = PI / 2 - (20 + i * 9) * PI / 180
            pen.dot(s.c.x + cos(a) * (s.R - 6), s.c.y + sin(a) * (s.R - 6), 3.2, Ink.CYAN, 0.85)
        }
        lives(s, pen, left, LIVES)
        s.nu.draw(pen, now)
        pen.pale(s.nu, wait.pale)
    }

    /** A pull from the finger: its aim and its strength, once it has gone far enough. */
    private fun pulled(): Pair<Double, Double>? {
        val f = from ?: return null
        val p = pull ?: return null
        val dx = f.x - p.x
        val dy = f.y - p.y
        val d = hypot(dx, dy)
        if (d < 14) return null
        return atan2(dy, dx) to min(SHOT, d * 0.006)
    }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        aim += by
        if (st == St.READY) wait.reset(patience(s.heat))
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        from = p
        pull = p
        if (st == St.READY) wait.reset(patience(s.heat))
    }

    override fun move(s: Stage, p: Pt) {
        if (from == null) return
        pull = p
        pulled()?.let { (a, _) -> aim = a }
        if (st == St.READY) wait.reset(patience(s.heat))
    }

    override fun up(s: Stage, p: Pt) {
        if (from == null) return
        val shot = pulled()
        from = null
        pull = null
        if (st != St.READY) return
        if (shot != null) { aim = shot.first; fire(s, shot.first, max(0.2, shot.second)) } else fire(s, aim, SHOT)
    }

    internal companion object {
        const val LIVES = 3
        fun ringsOf(heat: Double) = min(6, 4 + floor(max(0.0, heat) * 1.5).toInt())
        fun postsOf(heat: Double) = min(6, 1 + floor(max(0.0, heat) * 3.5).toInt())
        fun blocksOf(heat: Double) = min(4, floor(max(0.0, heat - 0.1) * 3).toInt())
        fun spare(heat: Double) = clamp(floor(3.5 - 3 * heat), 0.0, 3.0).toInt()
        /** The share of rings that drift to and fro: from Medium, about half at Expert. */
        fun drifters(heat: Double) = clamp((heat - 0.4) * 0.9, 0.0, 0.6)
        /** How much of the way the guide shows (units). */
        fun guideLen(heat: Double) = max(90.0, 300 - 220 * heat)
        /** How long Nu waits on a player who has stopped (ms). */
        fun patience(heat: Double) = max(4000.0, 7500 - 3500 * heat)
    }
}
