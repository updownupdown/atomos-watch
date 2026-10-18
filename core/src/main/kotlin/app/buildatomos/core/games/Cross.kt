package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Rows across the court: the two banks and five lanes between. */
private const val ROWS = 7
/** A step aside (units). */
private const val SIDE = 28.0
/** After a touch, Nu can't be touched again for this long (ms). */
private const val SAFE_MS = 600.0

/** The colors of things to keep clear of: red and its warm neighbors (never Nu's rose). */
private val WARM = intArrayOf(
    Ink.RED, 0xFFF07A3A.toInt(), 0xFFC8323C.toInt(), 0xFFE09A2A.toInt(), 0xFFFF7A5C.toInt(),
    0xFFE2552A.toInt(), 0xFFF2B33D.toInt(), 0xFFB02A48.toInt(), 0xFFFF9A3C.toInt(), 0xFFD9463A.toInt(),
)

/**
 * Cross (the phone's src/nu/meadow.ts). Lanes across the court, electrons going by in each, this
 * way or that, each lane at its own speed, its electrons in their own colors and sizes. Nu waits
 * on a bank at one side: tap on the far side of it and it hops a lane on, tap beside it and it
 * steps aside, tap behind it and it hops back. Over to the green bank counts; the lanes are dealt
 * again, and Nu sets off back the other way. An electron touching Nu costs one of three lives and
 * sends it back to the bank it set off from; the last life lost loses the run.
 * A level is three crossings. Lives carry on from level to level, and one comes back every third
 * level. Up to about Medium a still lane in the middle gives a place to rest. Heat makes the
 * lanes faster (about 0.03 units a millisecond at the gentlest, 0.05 at Expert) and the gaps
 * between electrons closer, and takes the still lane away from Hard on.
 * On the watch the court is the round one, so the lanes are its chords, shortest at the banks,
 * and the electrons come and go through the rim. A tap counts by where it is from Nu, so the
 * finger never has to cover Nu or the lane it's going to. Nu doesn't wait for ever: left still a
 * few seconds (about seven at the gentlest, four at Expert), it pulses pale three times and hops on by
 * itself, so a run nobody plays ends.
 */
class Cross : Toy {
    internal class Lane(val y: Double, val v: Double, val xs: DoubleArray, val rs: DoubleArray, val cs: IntArray)
    internal var lanes = listOf<Lane?>()
        private set
    /** The row Nu is on (0 the bottom bank) and its place across. */
    internal var row = 0
        private set
    private var col = 0.0
    /** The way across: up from the bottom bank, or down from the top one. */
    internal var up = true
        private set
    private var crossed = 0
    private var best = 0
    private var left = LIVES
    private var safe = 0.0
    private val wait = Patience()
    private var over = false

    internal fun rowY(s: Stage, j: Int) = s.c.y + s.R * 0.66 - j * (s.R * 1.32 / (ROWS - 1))
    /** How far either way of the middle Nu can stand on row `j`. */
    internal fun reach(s: Stage, j: Int): Double {
        val dy = rowY(s, j) - s.c.y
        return sqrt(max(0.0, s.R * s.R - dy * dy)) - 20
    }
    /** The bank Nu is crossing to, and the one it set off from. */
    internal val goal get() = if (up) ROWS - 1 else 0
    private val start get() = if (up) 0 else ROWS - 1
    internal val x: Double get() = col
    internal val safeNow: Boolean get() = safe > 0
    /** The span the electrons wrap over. */
    private fun x0(s: Stage) = s.c.x - s.R - 24
    private fun x1(s: Stage) = s.c.x + s.R + 24

    override fun enter(s: Stage) {
        s.nu.r = 8.0
        col = s.c.x
        row = 0
        s.nu.at(Pt(col, rowY(s, 0)))
        s.rim = null
        deal(s)
        wait.reset(patience(s.heat))
    }

    /** The lanes for a crossing, at the heat now. */
    private fun deal(s: Stage) {
        val h = s.heat
        val span = x1(s) - x0(s)
        lanes = List(ROWS) { j ->
            if (j == 0 || j == ROWS - 1 || (j == ROWS / 2 && h < 0.6)) null
            else {
                val r = 4.5 + rnd() * 4
                val v = (if ((j + (if (rnd() < 0.5) 1 else 0)) % 2 == 0) 1 else -1) * pace(h) * (0.75 + rnd() * 0.5) * min(1.35, 7 / r)
                val two = intArrayOf(WARM[(rnd() * WARM.size).toInt()], WARM[(rnd() * WARM.size).toInt()])
                val xs = mutableListOf<Double>()
                val rs = mutableListOf<Double>()
                val cs = mutableListOf<Int>()
                var x = x0(s) + rnd() * 60
                while (x < x0(s) + span - 30) {
                    xs += x
                    rs += r * (0.8 + rnd() * 0.4)
                    cs += if (rnd() < 0.3) WARM[(rnd() * WARM.size).toInt()] else two[(rnd() * 2).toInt()]
                    x += gap(h) * (0.85 + rnd() * 0.5) + 2 * r
                }
                Lane(rowY(s, j), v, xs.toDoubleArray(), rs.toDoubleArray(), cs.toIntArray())
            }
        }
    }

    /** True if an electron on row `j` comes within `room` of `x` between `from` and `to` ms from
     *  now: for the tests' player. */
    internal fun danger(s: Stage, j: Int, x: Double, from: Double, to: Double, room: Double = 0.0): Boolean {
        val l = lanes.getOrNull(j) ?: return false
        val span = x1(s) - x0(s)
        var t = from
        while (t <= to) {
            for (i in l.xs.indices) {
                var p = l.xs[i] + l.v * t
                p = x0(s) + (((p - x0(s)) % span) + span) % span
                if (abs(p - x) < s.nu.r + l.rs[i] + room) return true
            }
            t += 16
        }
        return false
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        val to = Pt(col, rowY(s, row))
        nu.seek(to, dt, if (safe > 0) 0.6 else 0.9, 0.08)
        val span = x1(s) - x0(s)
        for (l in lanes) {
            if (l == null) continue
            for (i in l.xs.indices) {
                l.xs[i] += l.v * dt
                if (l.xs[i] > x1(s)) l.xs[i] -= span
                if (l.xs[i] < x0(s)) l.xs[i] += span
            }
        }
        if (over) return
        safe -= dt
        if (wait.step(dt)) hop(s, if (up) 1 else -1)
        if (safe > 0) return
        // Only the lane Nu is hopping onto, or standing in, can touch it (not one it is leaving,
        // or passing on its way back to the bank).
        lanes[row]?.let { l ->
            if (abs(l.y - nu.y) > 12) return@let
            for (i in l.xs.indices) {
                if (abs(l.xs[i] - nu.x) >= nu.r + l.rs[i] - 1) continue
                left--
                safe = SAFE_MS
                s.fx.ring(nu.p, Ink.RED, nu.r, nu.r + 30, 600.0)
                s.out.miss()
                s.out.buzz(22)
                row = start
                col = clamp(col, s.c.x - reach(s, row), s.c.x + reach(s, row))
                wait.reset(patience(s.heat))
                if (left <= 0) { over = true; wait.hold(); s.lose() }
                return
            }
        }
        val gone = if (up) row else ROWS - 1 - row
        best = max(best, gone)
        s.part = clamp((crossed + best.toDouble() / (ROWS - 1)) / NEED, 0.0, 1.0)
        if (row == goal && abs(nu.y - to.y) < 4) {
            crossed++
            best = 0
            s.fx.ring(nu.p, Ink.GREEN, nu.r, nu.r + 34, 600.0)
            if (crossed >= NEED) {
                crossed = 0
                s.clear()
                if (s.over) { over = true; wait.hold(); return }
                if (s.level % 3 == 0) left = min(LIVES, left + 1)
            } else s.out.capture()
            s.part = clamp(crossed.toDouble() / NEED, 0.0, 1.0)
            up = !up
            deal(s)
            wait.reset(patience(s.heat))
        }
    }

    /** Hops a row on (+1, up the screen) or back. */
    private fun hop(s: Stage, by: Int) {
        val to = (row + by).coerceIn(0, ROWS - 1)
        if (to == row) return
        row = to
        col = clamp(col, s.c.x - reach(s, row), s.c.x + reach(s, row))
        s.out.tick(1)
        wait.reset(patience(s.heat))
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val c = s.c
        // The bank to cross to: a band of green, brightest along its near edge.
        val gy = rowY(s, goal)
        val gw = reach(s, goal) + 14
        pen.line(c.x - gw, gy, c.x + gw, gy, Ink.GREEN, 18.0, 0.14)
        val edgeY = gy + (if (up) 9.0 else -9.0)
        pen.line(c.x - gw, edgeY, c.x + gw, edgeY, Ink.GREEN, 3.0, 0.55)
        for ((j, l) in lanes.withIndex()) {
            if (l != null || j == 0 || j == ROWS - 1) continue
            val w = reach(s, j) + 14
            pen.line(c.x - w, rowY(s, j), c.x + w, rowY(s, j), Ink.K800, 16.0, 0.8)
        }
        for (l in lanes) {
            if (l == null) continue
            for (i in l.xs.indices) {
                val f = clamp((s.R - 4 - dist(l.xs[i], l.y, c.x, c.y)) / 12, 0.0, 1.0)
                if (f <= 0) continue
                val color = if (over) Ink.K500 else l.cs[i]
                pen.dot(l.xs[i], l.y, l.rs[i] + 3.5, color, 0.22 * f)
                pen.dot(l.xs[i], l.y, l.rs[i], color, (if (over) 0.6 else 0.92) * f)
            }
        }
        lives(s, pen, left, LIVES)
        // Blinks while it can't be touched again.
        if (safe <= 0 || ((safe / 100).roundToInt() % 2 == 0)) {
            s.nu.draw(pen, now)
            pen.pale(s.nu, wait.pale)
        }
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        val dx = p.x - s.nu.x
        val dy = p.y - s.nu.y
        if (abs(dx) > abs(dy) * 1.2 && abs(dx) > 18) {
            col = clamp(col + (if (dx > 0) SIDE else -SIDE), s.c.x - reach(s, row), s.c.x + reach(s, row))
            s.out.tick(0)
            wait.reset(patience(s.heat))
        } else if (abs(dy) > 6) hop(s, if (dy < 0) 1 else -1)
        else wait.reset(patience(s.heat))
    }

    internal companion object {
        const val LIVES = 3
        /** Crossings in a level. */
        const val NEED = 3
        /** A lane's pace (units/ms) before its own share of chance and its electrons' size. */
        fun pace(heat: Double) = min(0.1, 0.028 + 0.026 * heat)
        /** The room between electrons in a lane (units) before chance. */
        fun gap(heat: Double) = max(52.0, 115 - 30 * min(1.6, heat))
        /** How long Nu waits on a player who has stopped (ms). */
        fun patience(heat: Double) = max(4000.0, 7000 - 3000 * heat)
    }
}
