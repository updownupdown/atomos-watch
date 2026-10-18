package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/** Fast enough to hurt, coming down (units/ms). */
private const val SAFE = 0.15

/** The pull down and the thrust up while a finger is held (units/ms²): gentler than the phone's
 *  0.0003 and 0.00068, so there is time to see a landing coming and answer it. */
private const val GRAVITY = 0.00018
private const val THRUST = 0.00045
/** The thrust's lean toward the finger at its strongest (units/ms²), and how far to the side of
 *  Nu the finger gives it all (units). */
private const val LEAN = 0.00018
private const val LEAN_SPAN = 70.0
/** How long the fuel lasts, the finger held (ms). */
private const val FUEL_MS = 7500.0

/** How long Nu rests on the floor after a landing or a crash before it goes up again. */
private const val REST_MS = 700.0

/**
 * Land (the phone's src/nu/grove.ts). Nu drops in from the top and falls. Hold to fire it
 * upward; let go and it falls again. A ring round Nu shows its speed: green while it is slow
 * enough to land, red when not, with a line the way it is going. Set it down green on the pad
 * and it counts; too hard, or off the pad, costs one of three lives, and the last one loses the
 * run. The rim is the fuel for each landing: out of fuel, Nu just falls.
 * A level is a few good landings: two for the first two levels, then three. The lives carry from
 * level to level, and every third level cleared gives one back. Heat shrinks the pad (62 across
 * at the gentlest, never under 26) and brings in a wind that pushes Nu sideways (from a little
 * past Very easy, stronger toward Expert), shown by drifting motes; within a level, each good
 * landing goes a step further, as on the phone.
 * On the watch it is played by touch alone, as on the phone: a finger held anywhere fires the
 * thrust, leaning Nu toward the finger's side, the more the further aside (straight up under
 * Nu, all the way 70 units off), so a finger held beside Nu steers it without hiding it. Nu falls
 * slower than on the phone, and the view is drawn smaller (a smaller Nu, a lower floor, a higher
 * start), for more room and more time to answer. Nu bounces off the round court's rim; the floor
 * is a line across its lower part, above the lives and the level counter. Left alone, Nu falls
 * too fast and crashes, landing after landing, so the run ends.
 */
class Land : Toy {
    private enum class State { UP, FLY, REST }
    private var state = State.UP
    private var vx = 0.0
    private var vy = 0.0
    /** The finger held down, or null. */
    private var finger: Pt? = null
    private var fuel = 1.0
    private var padX = 0.0
    private var padW = 62.0
    /** The wind's push across (units/ms²), and the motes that show it. */
    private var wind = 0.0
    private val motes = mutableListOf<Pt>()
    private var restAt = 0.0
    private var left = 3
    /** Good landings in this level. */
    private var landed = 0
    private var over = false

    private fun floor(s: Stage) = s.c.y + s.R * 0.56
    /** Half the floor's width, where it meets the rim (a little in). */
    private fun half(s: Stage) = sqrt(max(0.0, (s.R - 8) * (s.R - 8) - (floor(s) - s.c.y) * (floor(s) - s.c.y)))
    private fun need(s: Stage) = if (s.level < 2) 2 else 3
    /** How far along the phone's run of landings this one is, which made it harder there. */
    private fun depth(s: Stage) = s.heat * 8 + landed

    override fun enter(s: Stage) {
        s.nu.r = 7.0
        s.nu.at(Pt(s.c.x, s.c.y - s.R * 0.5))
        next(s)
    }

    private fun next(s: Stage) {
        state = State.UP
        fuel = 1.0
        s.rim = 1.0
        val d = depth(s)
        padW = max(26.0, 62 - d * 4.5)
        padX = s.c.x + (rnd() - 0.5) * (2 * half(s) - padW - 40)
        wind = if (d >= 2) (if (rnd() < 0.5) -1 else 1) * min(0.00005, 0.000008 + d * 0.000006) else 0.0
        motes.clear()
        if (wind != 0.0) repeat(7) { motes += anywhere(s, 20.0).also { it.y = min(it.y, floor(s) - 16) } }
    }

    private fun safe() = hypot(vx, vy) < SAFE

    /** How far the thrust leans toward a finger at `f`: -1 all left .. 1 all right. */
    private fun lean(nu: Pt, f: Pt) = clamp((f.x - nu.x) / LEAN_SPAN, -1.0, 1.0)

    /** Where Nu waits before each drop: up top, away from the pad. */
    private fun top(s: Stage) = Pt(clamp(padX + (if (padX > s.c.x) -1 else 1) * s.hw * 0.6, s.c.x - s.hw + 30, s.c.x + s.hw - 30), s.c.y - s.R * 0.5)

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        for (m in motes) {
            m.x += wind * 2500 * dt
            val w = sqrt(max(0.0, (s.R - 16) * (s.R - 16) - (m.y - s.c.y) * (m.y - s.c.y)))
            if (m.x > s.c.x + w) m.x = s.c.x - w
            if (m.x < s.c.x - w) m.x = s.c.x + w
        }
        when (state) {
            State.REST -> {
                if (over || now - restAt <= REST_MS) return
                next(s)
                return
            }
            State.UP -> {
                val at = top(s)
                nu.seek(at, dt, 0.7, 0.03)
                if (dist(nu.p, at) < 3) { state = State.FLY; vx = 0.0; vy = 0.0 }
                return
            }
            State.FLY -> {}
        }
        var ax = wind
        var ay = GRAVITY
        val f = finger
        if (f != null && fuel > 0) {
            ay -= THRUST
            ax += lean(nu.p, f) * LEAN
            fuel = max(0.0, fuel - dt / FUEL_MS)
        }
        s.rim = fuel
        vx = clamp(vx + ax * dt, -0.22, 0.22)
        vy = clamp(vy + ay * dt, -0.22, 0.34)
        nu.x += vx * dt
        nu.y += vy * dt
        // Off the rim, softly.
        val (out, nx, ny) = edge(s, nu.p)
        val o = out + nu.r + 2
        if (o > 0) {
            nu.x -= nx * o
            nu.y -= ny * o
            val vn = vx * nx + vy * ny
            if (vn > 0) { vx -= 1.3 * vn * nx; vy -= 1.3 * vn * ny }
        }
        nu.vx = vx
        nu.vy = vy
        if (nu.y + nu.r < floor(s)) return
        nu.y = floor(s) - nu.r
        state = State.REST
        restAt = now
        val on = abs(nu.x - padX) < padW / 2
        if (on && safe()) {
            landed++
            s.fx.ring(nu.p, Ink.GREEN, nu.r, nu.r + 30, 600.0)
            s.out.capture()
            if (landed >= need(s)) {
                landed = 0
                s.clear()
                if (s.over) { over = true; return }
                if (s.level % 3 == 0 && left < 3) left++
            }
            s.part = landed.toDouble() / need(s)
            vx = 0.0; vy = 0.0
            return
        }
        vx = 0.0; vy = 0.0
        left--
        s.fx.ring(nu.p, Ink.RED, nu.r, nu.r + 30, 600.0)
        s.out.miss()
        s.out.buzz(24)
        if (left <= 0) { over = true; s.lose() }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val f = floor(s)
        val nu = s.nu
        val h = half(s)
        for (m in motes) pen.line(m.x, m.y, m.x - sign(wind) * 10, m.y, Ink.K500, 2.0, 0.7)
        pen.line(s.c.x - h, f + 3, s.c.x + h, f + 3, Ink.K500, 4.0, 0.9)
        pen.line(padX - padW / 2, f + 1, padX + padW / 2, f + 1, if (over) Ink.K500 else Ink.GREEN, 8.0, 0.95)
        if (state == State.FLY) {
            val fg = finger
            if (fg != null && fuel > 0) {
                // The thrust: small dots under Nu, trailing away from the way it leans.
                val l = lean(nu.p, fg)
                for (k in 1..3) pen.dot(nu.x + (rnd() - 0.5) * 4 - l * k * 3, nu.y + nu.r + k * 5, 2.6 - k * 0.5, Ink.CYAN, 0.9 - k * 0.2)
            }
            // The speed: a line from Nu the way it's going, out to the ring at the safe speed.
            val c = if (safe()) Ink.GREEN else Ink.RED
            val R = nu.r + 13
            val sp = hypot(vx, vy)
            val l = min(1.8, sp / SAFE) * R
            pen.ring(nu.x, nu.y, R, c, 3.0, 0.8)
            if (sp > 0.01) pen.line(nu.x + vx / sp * nu.r, nu.y + vy / sp * nu.r, nu.x + vx / sp * l, nu.y + vy / sp * l, c, 3.0, 0.9)
        }
        lives(s, pen, left)
        nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) { if (!over) finger = p }
    override fun move(s: Stage, p: Pt) { if (finger != null) finger = p }
    override fun up(s: Stage, p: Pt) { finger = null }

    /** For the tests' player: where the pad is and how wide, Nu's speed, and whether it flies. */
    internal val pad get() = padX to padW
    internal val speed get() = Pt(vx, vy)
    internal val flying get() = state == State.FLY
    internal val windNow get() = wind
}
