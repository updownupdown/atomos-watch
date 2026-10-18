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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Nu's size at the start, when the view pulls back, and after; and how long pulling back takes. */
private const val START = 7.0
private const val GROWN = 19.0
private const val BASE = 8.0
private const val ZOOM_MS = 1600.0
/** The arena: in this far from the court's edge, so play keeps off the menu and the counter. */
private const val ROOM = 30.0
/** The watch's court is smaller than the phone's arena: the motes go this share of its speeds. */
private const val SPEED = 0.65
/** How near a chaser looks for prey, and a fleeing mote for something after it. */
private const val CHASE_SEE = 110.0
private const val FLEE_SEE = 72.0
private const val FLEE_TOP = 0.13 * SPEED
/** After a bump, nothing hurts for this long (Nu blinks). */
private const val SAFE_MS = 1500.0
private const val LIVES = 3
/** How long a mote stays about, at least, before it drifts out again (and how much longer it may). */
private const val LIFE_MS = 14_000.0
/** New motes come as food while fewer than this many are about. */
private const val FOOD_MIN = 3

/**
 * Grow (the phone's src/nu/field.ts). Motes of every size move about. Drag Nu (from anywhere):
 * what's smaller than Nu (cool colours) it eats and grows; what's bigger (red) it mustn't touch.
 * Nothing appears out of nowhere: new motes drift in from beyond the arena's edge, and motes eat
 * each other too, so a small one can grow red before your eyes. Purple ones slowly chase anything
 * smaller (Nu too), teal ones wander, blue ones run from whatever chases them (Nu too), and pale
 * ones just drift. Grown big, the view slowly pulls back: Nu and everything shrink together, and
 * new motes come sized to it.
 *
 * A level is growing big enough for the view to pull back: about a dozen bites. On the phone one
 * red touch ends the run; on the watch, played in levels, a bump costs one of three lives (Nu
 * blinks a moment, safe, and the red one is shoved off), the lives carry on from level to level
 * and one comes back every third level. Heat is the phone's run going on: more of the new motes
 * big, all of them a little quicker, more chasers and a few more motes about.
 *
 * On the watch the arena is a circle a little in from the rim (a faint ring), so play keeps off
 * the menu at the top and the level counter at the bottom; new motes come in through its sides,
 * from the dark round it, never from under the menu, the counter or the lives. The court is
 * about a sixth of the phone's arena, so there are fewer motes (seven at first), a little bigger
 * for Nu's size, and slower. Nu is dragged from anywhere, so the finger needn't cover it; the
 * crown has nothing natural to steer here, so Grow is played by touch alone. Left alone, Nu sits
 * still, and sooner or later something bigger bumps into it.
 */
class Grow : Toy {
    internal enum class Way(val top: Double, val colors: IntArray) {
        CHASE(0.075 * SPEED, intArrayOf(Ink.F, 0xFFC48CF0.toInt())),
        WANDER(0.06 * SPEED, intArrayOf(0xFF3CC9B4.toInt(), Ink.P)),
        FLEE(0.05 * SPEED, intArrayOf(Ink.S, Ink.CYAN)),
        DRIFT(0.06 * SPEED, intArrayOf(Ink.K300, Ink.WHITE)),
    }

    internal class Mote(x: Double, y: Double, vx: Double, vy: Double, var r: Double, val c: Int, var inside: Boolean, val way: Way, var head: Double) : Mover(x, y, vx, vy) {
        var scared = 0.0
        /** Shoved off Nu: it coasts away, steering nowhere, for this long (ms). */
        var shoved = 0.0
        /** How long it stays about before it heads out again (ms), and whether it's on its way. */
        var life = LIFE_MS * (1 + rnd() * 0.7)
        var leaving = false
    }

    internal val motes = mutableListOf<Mote>()
    private var grab: Pt? = null
    private var want: Pt? = null
    private var left = LIVES
    private var safeUntil = 0.0
    private var over = false
    /** Pulling back: when it began, the whole shrink, and how much of it (eased, 0..1) is done. */
    private var zoomAt = 0.0
    private var zoomBy = 1.0
    private var zoomed = 0.0
    /** Nu's size when the level began. */
    private var from = START

    private fun arena(s: Stage) = s.R - ROOM

    /** The phone's count of bites for the heat: about 40 at Expert, climbing toward the next level. */
    private fun depth(s: Stage) = (s.heat + HEAT_PER_LEVEL * s.part) * 40

    /** The share of new motes that come bigger than Nu. */
    internal fun bigShare(n: Double) = min(0.6, 0.28 + n * 0.007)
    /** How many motes drift about at once. */
    internal fun count(h: Double) = 7 + min(3, floor(h * 2).toInt())
    /** The share of motes that chase. */
    internal fun chasers(h: Double) = min(0.32, 0.16 + h * 0.08)

    private fun pickWay(s: Stage): Way {
        val c = chasers(s.heat)
        val u = rnd()
        return if (u < c) Way.CHASE else if (u < c + 0.25) Way.WANDER else if (u < 0.95) Way.FLEE else Way.DRIFT
    }

    private fun mote(s: Stage, p: Pt, r: Double, a: Double, v: Double, inside: Boolean): Mote {
        val w = pickWay(s)
        val c = w.colors[floor(rnd() * w.colors.size).toInt().coerceAtMost(w.colors.size - 1)]
        return Mote(p.x, p.y, cos(a) * v, sin(a) * v, r, c, inside, w, a)
    }

    /** How far `p` is outside the arena's edge (negative inside). */
    private fun out(s: Stage, x: Double, y: Double) = dist(x, y, s.c.x, s.c.y) - arena(s)

    /** A point pulled inside the arena, `r` in from its edge. */
    private fun within(s: Stage, p: Pt, r: Double): Pt {
        val dx = p.x - s.c.x
        val dy = p.y - s.c.y
        val d = hypot(dx, dy)
        val most = arena(s) - r
        return if (d <= most || d == 0.0) p else Pt(s.c.x + dx / d * most, s.c.y + dy / d * most)
    }

    override fun enter(s: Stage) {
        s.nu.r = START
        from = START
        s.nu.at(s.c)
        // The first ones are all food, already about, and clear of Nu.
        repeat(count(s.heat)) {
            val r = s.nu.r * (0.35 + rnd() * 0.5)
            var p = spot(s, r + 4)
            var k = 0
            while (k++ < 30 && dist(p, s.nu.p) < s.nu.r + r + 45) p = spot(s, r + 4)
            motes += mote(s, p, r, rnd() * TAU, (0.02 + rnd() * 0.04) * SPEED, true)
        }
    }

    /** A random point in the arena, `r` in from its edge. */
    private fun spot(s: Stage, r: Double): Pt {
        val a = rnd() * TAU
        val d = sqrt(rnd()) * (arena(s) - r)
        return polar(s, a, d)
    }

    /** A new mote drifting in through a side of the arena (never by Nu, nor under the menu, the
     *  counter or the lives): more of them bigger as the run goes on. */
    private fun add(s: Stage) {
        val n = depth(s)
        val food = motes.count { it.r < s.nu.r * 0.95 && !it.leaving }
        val r = s.nu.r * if (food >= FOOD_MIN && rnd() < bigShare(n)) 1 + rnd() * 0.6 else 0.35 + rnd() * 0.55
        fun pick(): Pt = polar(s, (if (rnd() < 0.5) 0.0 else PI) + (rnd() - 0.5) * PI / 2, arena(s) + r + 2)
        var p = pick()
        var k = 0
        while (k++ < 20 && dist(p, s.nu.p) < 80 + r) p = pick()
        // Headed in, roughly at the middle.
        val a = atan2(s.c.y - p.y, s.c.x - p.x) + (rnd() - 0.5) * 1.2
        val v = (0.025 + rnd() * 0.035 + min(0.02, n * 0.0004)) * SPEED
        motes += mote(s, p, r, a, v, false)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        if (over) return
        val w = want
        if (w != null) {
            nu.seek(within(s, w, nu.r + 2), dt, 0.6, 0.05)
            val p = within(s, nu.p, nu.r)
            nu.x = p.x; nu.y = p.y
        } else { nu.vx = 0.0; nu.vy = 0.0 }
        for (m in motes) {
            if (m.leaving) {
                // On its way out: easing round to head straight out from the middle.
                val d = dist(m.x, m.y, s.c.x, s.c.y).takeIf { it > 0 } ?: 1.0
                val v = max(hypot(m.vx, m.vy), 0.035 * SPEED)
                val k = 1 - exp(-dt / 500)
                m.vx += ((m.x - s.c.x) / d * v - m.vx) * k
                m.vy += ((m.y - s.c.y) / d * v - m.vy) * k
            } else if (m.shoved > 0) m.shoved -= dt
            else if (m.inside) steer(s, m, dt)
            m.x += m.vx * dt
            m.y += m.vy * dt
            if (m.inside && m.shoved <= 0) {
                m.life -= dt
                if (m.life <= 0) { m.leaving = true; m.inside = false }
            }
            // Coming in from beyond the edge: free until it's all the way in.
            if (!m.inside) { if (!m.leaving && out(s, m.x, m.y) < -m.r) m.inside = true else continue }
            // Off the edge, a wanderer heads the new way.
            if (bounce(s, m)) m.head = atan2(m.vy, m.vx)
        }
        // Motes eat motes well smaller than them, and grow (a small one can turn red this way).
        var i = motes.size - 1
        while (i >= 0) {
            val a = motes[i]
            if (a.inside) {
                var j = motes.size - 1
                while (j >= 0) {
                    val b = motes[j]
                    if (j != i && b.inside && a.r >= b.r * 1.2 && dist(a.x, a.y, b.x, b.y) <= a.r) {
                        val was = a.r >= nu.r * 0.95
                        a.r = min(nu.r * 1.9, sqrt(a.r * a.r + b.r * b.r * 0.7))
                        motes.removeAt(j)
                        if (j < i) i--
                        if (!was && a.r >= nu.r * 0.95) s.fx.ring(Pt(a.x, a.y), Ink.RED, a.r, a.r + 16, 600.0)
                        else s.fx.ring(Pt(a.x, a.y), a.c, a.r, a.r + 9, 400.0)
                    }
                    j--
                }
            }
            i--
        }
        val it = motes.iterator()
        while (it.hasNext()) {
            val m = it.next()
            val d = dist(m.x, m.y, nu.x, nu.y)
            if (m.r < nu.r * 0.95 && d < nu.r) {
                it.remove()
                nu.r = sqrt(nu.r * nu.r + m.r * m.r * 0.5)
                // A bigger bite, a higher note.
                s.out.hit(clamp((m.r / nu.r - 0.3) / 0.65, 0.0, 1.0))
                s.out.capture()
                if (zoomAt == 0.0) s.part = progress(nu.r)
                continue
            }
            if (m.r >= nu.r * 0.95 && d < nu.r + m.r * 0.8 && now >= safeUntil) {
                // Shoved off, away from Nu, and it coasts on that way while Nu is safe.
                val k = if (d > 0) 1 / d else 0.0
                m.vx = (m.x - nu.x) * k * FLEE_TOP
                m.vy = (m.y - nu.y) * k * FLEE_TOP
                m.shoved = SAFE_MS
                hurt(s, now)
                if (over) return
                continue
            }
            // Gone out: wandered off the far side while still coming in, or left.
            if (!m.inside && out(s, m.x, m.y) > m.r + if (m.leaving) 4.0 else 50.0) it.remove()
        }
        // Grown big: the level is done, and the view pulls back, slowly, round Nu.
        if (zoomAt == 0.0 && nu.r > GROWN) {
            s.clear()
            if (s.over) { over = true; return }
            if (s.level % 3 == 0) left = min(LIVES, left + 1)
            zoomAt = now
            zoomBy = BASE / nu.r
            zoomed = 0.0
            from = BASE
            s.fx.ring(nu.p, Ink.CYAN, nu.r, nu.r + 40, 1200.0)
        }
        if (zoomAt > 0) {
            val t = clamp((now - zoomAt) / ZOOM_MS, 0.0, 1.0)
            val e = t * t * (3 - 2 * t)
            // This frame's share of the shrink, so what Nu eats meanwhile shrinks with it.
            val k = zoomBy.pow(e - zoomed)
            zoomed = e
            nu.r *= k
            for (m in motes) {
                m.r *= k
                m.x = nu.x + (m.x - nu.x) * k
                m.y = nu.y + (m.y - nu.y) * k
            }
            if (t >= 1) { zoomAt = 0.0; s.part = progress(nu.r) }
            return
        }
        while (motes.size < count(s.heat)) add(s)
    }

    /** How far Nu has grown through the level, by its area: 0 at its size at the start, 1 grown. */
    private fun progress(r: Double) = clamp((r * r - from * from) / (GROWN * GROWN - from * from), 0.0, 1.0)

    private fun hurt(s: Stage, now: Double) {
        left--
        safeUntil = now + SAFE_MS
        s.out.miss()
        s.out.buzz(22)
        s.fx.ring(s.nu.p, Ink.RED, s.nu.r, s.nu.r + 24, 600.0)
        if (left > 0) return
        over = true
        want = null
        grab = null
        s.lose()
    }

    /** Keeps a mote in the arena, bouncing it off the edge. True if it bounced. */
    private fun bounce(s: Stage, m: Mote): Boolean {
        val dx = m.x - s.c.x
        val dy = m.y - s.c.y
        val d = hypot(dx, dy).takeIf { it > 0 } ?: return false
        val past = d + m.r - arena(s)
        if (past <= 0) return false
        val nx = dx / d
        val ny = dy / d
        m.x -= nx * past
        m.y -= ny * past
        val vn = m.vx * nx + m.vy * ny
        if (vn > 0) { m.vx -= 2 * vn * nx; m.vy -= 2 * vn * ny }
        return true
    }

    /** What chases `m` (a bigger mote or Nu, near and coming its way), or null. */
    private fun chaser(s: Stage, m: Mote): Mover? {
        val nu = s.nu
        val nd = dist(nu.x, nu.y, m.x, m.y)
        if (nu.r > m.r * 1.05 && nd < FLEE_SEE + nu.r && nu.vx * (m.x - nu.x) + nu.vy * (m.y - nu.y) > 0.05 * SPEED * nd) return Mover(nu.x, nu.y)
        for (o in motes) {
            if (o === m || !o.inside || o.r < m.r * 1.2 || dist(o.x, o.y, m.x, m.y) > FLEE_SEE + o.r) continue
            if (o.way == Way.CHASE || o.vx * (m.x - o.x) + o.vy * (m.y - o.y) > 0) return o
        }
        return null
    }

    /** A mote's own way of moving, by easing its speed toward what it wants. */
    private fun steer(s: Stage, m: Mote, dt: Double) {
        val w = m.way
        var tx = m.vx
        var ty = m.vy
        val top = w.top
        var k = 1 - exp(-dt / 900)
        when (w) {
            Way.CHASE -> {
                // Slowly toward the nearest thing smaller than it: a mote, or Nu.
                var px = 0.0
                var py = 0.0
                var pd = CHASE_SEE
                var found = false
                for (o in motes) {
                    if (o === m || !o.inside || o.r * 1.2 > m.r) continue
                    val d = dist(o.x, o.y, m.x, m.y)
                    if (d < pd) { pd = d; px = o.x; py = o.y; found = true }
                }
                val nd = dist(s.nu.x, s.nu.y, m.x, m.y)
                if (s.nu.r * 1.05 < m.r && nd < pd) { pd = nd; px = s.nu.x; py = s.nu.y; found = true }
                if (found && pd > 0) { tx = (px - m.x) / pd * top; ty = (py - m.y) / pd * top; k = 1 - exp(-dt / 600) }
            }
            Way.WANDER -> {
                // A heading that turns this way and that.
                m.head += (rnd() - 0.5) * 0.006 * dt
                tx = cos(m.head) * top
                ty = sin(m.head) * top
            }
            Way.FLEE -> {
                val from = chaser(s, m)
                if (from != null) {
                    val d = dist(from.x, from.y, m.x, m.y).takeIf { it > 0 } ?: 1.0
                    tx = (m.x - from.x) / d * FLEE_TOP
                    ty = (m.y - from.y) / d * FLEE_TOP
                    k = 1 - exp(-dt / 250)
                    m.scared = 600.0
                } else if (m.scared > 0) { m.scared -= dt; k = 0.0 }
                else {
                    // Calm again: back to an easy drift.
                    val v = hypot(m.vx, m.vy).takeIf { it > 0 } ?: 1.0
                    tx = m.vx / v * 0.04 * SPEED
                    ty = m.vy / v * 0.04 * SPEED
                }
            }
            Way.DRIFT -> {}
        }
        m.vx += (tx - m.vx) * k
        m.vy += (ty - m.vy) * k
        val v = hypot(m.vx, m.vy)
        val cap = if (m.scared > 0) FLEE_TOP else max(top, 0.02 * SPEED)
        if (v > cap) { m.vx *= cap / v; m.vy *= cap / v }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        pen.ring(s.c, arena(s), Ink.K800, 2.0, 0.9)
        for (m in motes) {
            // Only what's on the screen.
            if (dist(m.x, m.y, s.c.x, s.c.y) - m.r > s.R) continue
            if (m.r < s.nu.r * 0.95) pen.dot(m.x, m.y, m.r, m.c, 0.85)
            else { pen.dot(m.x, m.y, m.r, Ink.RED, 0.25); pen.ring(m.x, m.y, max(1.0, m.r - 1.5), Ink.RED, 3.0, 0.85) }
        }
        lives(s, pen, left, LIVES)
        // Bumped a moment ago: Nu blinks.
        s.nu.hidden = !over && now < safeUntil && floor(now / 110).toInt() % 2 == 0
        s.nu.draw(pen, now)
        s.nu.hidden = false
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        val g = Pt(s.nu.x - p.x, s.nu.y - p.y)
        grab = g
        want = Pt(p.x + g.x, p.y + g.y)
    }

    override fun move(s: Stage, p: Pt) {
        val g = grab ?: return
        want = Pt(p.x + g.x, p.y + g.y)
    }

    override fun up(s: Stage, p: Pt) { grab = null; want = null }
}
