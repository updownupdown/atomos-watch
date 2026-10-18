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

private const val ER = 4.5
/** The nucleus: an electron this near the middle is in. */
private const val ZONE = 17.0
/** Nearer than this, the nucleus draws an electron in (a faint ring shows it). */
private const val PULL = 32.0
/** Nu's top speed round its ring (rad/ms): a lap in about 0.7 s. */
private const val RUN = 0.009
/** Left this long with no touch or turn, Nu dozes off and herds nothing (ms). */
private const val DOZE_MS = 4000.0
private const val CALM_MS = 1200.0
private const val FADE_MS = 400.0

/**
 * Herd (the phone's src/nu/park.ts), turned inside out for the watch. Electrons wander in a
 * cloud round the nucleus, and they shy away from Nu. On the phone Nu ran about among them; here
 * it runs round a ring outside the cloud, by the crown (a turn of the crown, a turn round the
 * ring), and from out there every push is inward: get behind an electron and it flees into the
 * nucleus, which draws it the last of the way in, and it settles into a shell (two, then eight).
 * Coming at one from the side only sends it across. Electrons left to themselves drift slowly
 * back out to the edge of the cloud.
 * A level is an atom: every electron in, before the clock round the nucleus runs out. Out of
 * time costs one of three lives, and the clock fills again with the electrons already in kept;
 * the last life lost loses the run. Lives carry on from level to level, and one comes back every
 * third level.
 * Heat brings more electrons (seven at the gentlest, eight at Expert, up to ten), livelier and
 * quicker, shyer only nearer (Nu's reach from 79 down to 67, never under 56), and less time for
 * each (5.7 s at the gentlest, 3.3 s at Expert, never under 2.6 s).
 * A finger on the screen works too: Nu runs round toward it. Left alone, Nu dozes off after four
 * seconds (it dims and pushes nothing) until the next touch or turn, so a run nobody plays runs
 * out of time and ends.
 */
class Herd : Toy {
    /** Nu's place round its ring (not wrapped), and where it is sent. */
    private var ang = -PI / 2
    private var want = -PI / 2
    internal class E(x: Double, y: Double, val born: Double) : Mover(x, y)
    internal val free = mutableListOf<E>()
    private var caught = 0
    private var total = 0
    private var left = LIVES
    private var bank = 1.0
    private var clock = 1.0
    private var calm = 0.0
    private var idle = 0.0
    private var finger = false
    private var over = false

    internal fun track(s: Stage) = s.R * 0.72
    /** The cloud's edge: the electrons stay inside it, Nu runs outside it. */
    internal fun cloud(s: Stage) = track(s) - 17
    internal val at: Double get() = ang
    internal val sent: Double get() = want
    internal val asleep: Boolean get() = idle >= DOZE_MS

    override fun enter(s: Stage) {
        s.nu.r = 8.0
        s.nu.at(polar(s, ang, track(s)))
        s.rim = null
        deal(s)
    }

    /** A new atom: its electrons about the cloud, away from Nu, and its clock. */
    private fun deal(s: Stage) {
        val h = s.heat
        val n = count(h)
        free.clear()
        for (i in 0 until n) {
            var p = s.c
            for (k in 0 until 30) {
                val a = rnd() * TAU
                p = polar(s, a, cloud(s) * (0.6 + rnd() * 0.32))
                if (dist(p, s.nu.p) > 60 && free.all { dist(it.x, it.y, p.x, p.y) > 16 }) break
            }
            free += E(p.x, p.y, s.now)
        }
        caught = 0
        total = n
        bank = n * each(h)
        clock = bank
        s.part = 0.0
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        // Round the ring toward where it's sent, no faster than it runs.
        val d = want - ang
        ang += clamp(d * (1 - exp(-dt / 40)), -RUN * dt, RUN * dt)
        if (finger) want = ang + turn(ang, want)
        val np = polar(s, ang, track(s))
        s.nu.x = np.x
        s.nu.y = np.y
        s.nu.glow = if (asleep) 0.3 else 1.0
        if (over) return
        idle += dt
        if (calm > 0) {
            calm -= dt
            if (calm <= 0) { calm = 0.0; deal(s) }
            return
        }
        val h = s.heat
        val reach = reach(h)
        val wander = 0.00003 + 0.00005 * h
        val top = min(0.24, 0.06 + 0.1 * h)
        val damp = exp(-dt / 250)
        val pen = cloud(s)
        val it = free.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val dx = e.x - s.nu.x
            val dy = e.y - s.nu.y
            val dn = hypot(dx, dy).takeIf { it > 0 } ?: 1.0
            if (!asleep && dn < reach) {
                val f = 0.0012 * (reach - dn) / reach
                e.vx += dx / dn * f * dt
                e.vy += dy / dn * f * dt
            }
            // Restless, and drifting slowly back out from the nucleus, unless near enough for it
            // to draw them in.
            val cx = e.x - s.c.x
            val cy = e.y - s.c.y
            val dc = hypot(cx, cy).takeIf { it > 0 } ?: 1.0
            val out = if (dc < PULL) -0.00012 else 0.000016
            e.vx += (rnd() - 0.5) * wander * dt * 4 + cx / dc * out * dt
            e.vy += (rnd() - 0.5) * wander * dt * 4 + cy / dc * out * dt
            e.vx *= damp
            e.vy *= damp
            val sp = hypot(e.vx, e.vy)
            if (sp > top) { e.vx *= top / sp; e.vy *= top / sp }
            e.x += e.vx * dt
            e.y += e.vy * dt
            // Off the cloud's edge, back in.
            val ex = e.x - s.c.x
            val ey = e.y - s.c.y
            val de = hypot(ex, ey)
            if (de + ER > pen && de > 0) {
                val nx = ex / de
                val ny = ey / de
                e.x = s.c.x + nx * (pen - ER)
                e.y = s.c.y + ny * (pen - ER)
                val vn = e.vx * nx + e.vy * ny
                if (vn > 0) { e.vx -= 1.8 * vn * nx; e.vy -= 1.8 * vn * ny }
            }
            if (now - e.born > FADE_MS && de < ZONE) {
                it.remove()
                caught++
                s.fx.ring(s.c, Ink.CYAN, ZONE * 0.6, ZONE * 1.6, 500.0)
                s.out.capture()
                s.part = clamp(caught.toDouble() / total, 0.0, 1.0)
            }
        }
        if (free.isEmpty()) {
            s.fx.burst(s.c, 12, Ink.CYAN, 0.12)
            s.clear()
            if (s.over) { over = true; return }
            if (s.level % 3 == 0) left = min(LIVES, left + 1)
            calm = CALM_MS
            return
        }
        clock -= dt
        if (clock <= 0) {
            left--
            s.fx.ring(s.c, Ink.RED, ZONE, ZONE + 30, 600.0)
            s.out.miss()
            s.out.buzz(22)
            if (left <= 0) { over = true; s.lose(); return }
            clock = bank
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val c = s.c
        pen.ring(c, cloud(s) + 3, Ink.K800, 2.0, 0.9)
        pen.ring(c, track(s), Ink.K800, 3.0, 0.7)
        pen.ring(c, PULL, Ink.K800, 2.0, 0.9)
        pen.ring(c, ZONE, Ink.K500, 2.0, 0.6)
        pen.dot(c, 6.0, Ink.K300, 0.8)
        // The clock, round the nucleus.
        if (!over && calm <= 0) {
            val f = clamp(clock / bank, 0.0, 1.0)
            pen.arc(c.x, c.y, ZONE + 6, -PI / 2, TAU * f, if (f < 0.25) Ink.AMBER else Ink.K400, 3.0, 0.9)
        }
        // The electrons in, settled into shells round the nucleus: two, then up to eight.
        val shown = if (calm > 0) total else caught
        for (i in 0 until shown) {
            val outer = i >= 2
            val n = if (outer) min(8, shown - 2) else min(2, shown)
            val k = if (outer) i - 2 else i
            val r = if (outer) ZONE * 0.78 else ZONE * 0.4
            val a = now / (if (outer) 1800.0 else 1200.0) + k * TAU / n
            pen.dot(c.x + cos(a) * r, c.y + sin(a) * r, 2.4, Ink.CYAN, 0.95)
        }
        for (e in free) {
            val f = clamp((now - e.born) / FADE_MS, 0.0, 1.0)
            pen.glow(e.x, e.y, ER, Ink.CYAN, f)
            pen.dot(e.x, e.y, ER, Ink.CYAN, 0.95 * f)
        }
        lives(s, pen, left, LIVES)
        s.nu.draw(pen, now)
    }

    private fun wake() { idle = 0.0 }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        wake()
        if (finger) { want = ang; finger = false }
        want += by
    }

    private fun aim(s: Stage, p: Pt) {
        wake()
        if (dist(p, s.c) < 12) return
        finger = true
        want = ang + turn(ang, atan2(p.y - s.c.y, p.x - s.c.x))
    }

    override fun down(s: Stage, p: Pt) { if (!over) aim(s, p) }
    override fun move(s: Stage, p: Pt) { if (!over && finger) aim(s, p) }
    override fun up(s: Stage, p: Pt) { if (finger) { want = ang + turn(ang, want); finger = false } }

    internal companion object {
        const val LIVES = 3
        /** Electrons in an atom. */
        fun count(heat: Double) = min(10, 7 + floor(max(0.0, heat) * 2.2).toInt())
        /** How near Nu an electron shies away. */
        fun reach(heat: Double) = max(56.0, 80 - 14 * heat)
        /** The clock's time for each electron (ms). */
        fun each(heat: Double) = max(2600.0, 6000 - 3000 * heat)
    }
}
