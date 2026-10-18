package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where Nu sits on the rings: three o'clock, by the crown. */
private const val NU_A = 0.0
/** How far round a ring a particle comes from (radians before Nu), and goes on after it. */
private const val SPAN = 2.5
private const val AFTER = 1.0
/** Fading in and out (ms). */
private const val FADE = 320.0
private const val BIT_R = 5.0
/** The crown: a lane for every STEP radians turned; the first after a pause, or a turn back, at
 *  half that, so one click of a turning bezel always moves a lane. */
private const val STEP = 0.3
private const val IDLE = 250.0
/** The calm between levels: no new particles for this long (ms). */
private const val CALM_MS = 1000.0
/** After a hit, Nu can't be hit again for this long (ms). */
private const val SAFE_MS = 600.0

/**
 * Lanes, made for the watch (the phone has no crown). Three rings, four later, one inside the
 * other; Nu sits on one of them at three o'clock, by the crown, and particles sweep round the
 * rings toward it, each ring its own way and at its own pace. The crown moves Nu in and out, a
 * ring at a time with a click (clockwise outward): catch the cyan ones, dodge the red ones. A red
 * one costs one of three lives; the last one lost loses the run. A cyan one let by costs nothing.
 * A level is a set of cyan caught: eight, and one more every other level, up to fourteen. Then a
 * calm, the reds still out fade away, and the rings turn round and change pace. Lives carry on,
 * and one comes back every third level. Heat makes the rings turn faster (a lap in about 7 s at
 * the gentlest, 4 s at Expert, never under 2.4 s), sends particles more often and more of them
 * red, brings in a fourth ring from about Medium, and from Easy sends gates: a red on every ring
 * but one, all arriving together, with a cyan in the gap. However many reds are out, there is
 * always a ring free of them when they reach Nu.
 * A tap, or a finger slid in and out, moves Nu to the nearest ring too.
 */
class Lanes : Toy {
    /** Rings in play, and when their number last changed (a new one fades in). */
    private var n = 3
    private var grew = -1e9
    /** The ring Nu is on (0 the outer), and where it really is: a spring onto that ring's radius. */
    private var lane = 1
    private var r = 0.0
    private var rv = 0.0
    /** Each ring's way round (1 clockwise) and pace (rad/ms). */
    private var dir = intArrayOf(1, -1, 1, -1)
    private var w = DoubleArray(4) { 0.001 }
    /** A particle: its ring, how far round it still has to go to reach Nu (negative once past). */
    internal class Bit(val lane: Int, var d: Double, val red: Boolean, val born: Double) {
        var gone: Double? = null
    }
    private val bits = mutableListOf<Bit>()
    private var next = 900.0
    private var caught = 0
    private var left = LIVES
    private var safe = 0.0
    /** The crown's turn toward the next click, the way it last turned, and when. */
    private var wound = 0.0
    private var way = 0
    private var turnedAt = -1e9
    /** The ring last moved onto, lit for a moment. */
    private var litAt = -1e9
    private var over = false

    /** A ring's radius: the outer keeps Nu off the menu and the counter, the inner off the middle. */
    private fun rad(s: Stage, i: Int) = s.R * (0.74 - i * (if (n == 3) 0.17 else 0.15))
    private fun pos(s: Stage, b: Bit) = polar(s, NU_A - dir[b.lane] * b.d, rad(s, b.lane))
    private fun need(s: Stage) = 8 + min(12, s.level) / 2

    /** For the tests' player: the rings, Nu's ring, the paces and the particles still to come. */
    internal val rings get() = n
    internal val on get() = lane
    internal fun eta(b: Bit) = b.d / w[b.lane]
    internal val coming: List<Bit> get() = bits.filter { it.gone == null }

    override fun enter(s: Stage) {
        s.nu.r = 8.0
        n = rings(s.heat)
        shuffle(s)
        r = rad(s, lane)
        s.nu.at(polar(s, NU_A, r))
    }

    /** The rings for a level: how many, which way each turns and how fast. */
    private fun shuffle(s: Stage) {
        val h = s.heat
        val m = rings(h)
        if (m != n) { n = m; grew = s.now }
        lane = min(lane, n - 1)
        val flip = if (rnd() < 0.5) 1 else -1
        for (i in 0 until 4) {
            dir[i] = if (i % 2 == 0) flip else -flip
            w[i] = pace(h) * (0.8 + rnd() * 0.4)
        }
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        // Nu springs onto its ring: quick, with a little overshoot, like a detent.
        val goal = rad(s, lane)
        var t = dt
        while (t > 0) {
            val h = min(4.0, t)
            rv += (0.0012 * (goal - r) - 0.042 * rv) * h
            r += rv * h
            t -= h
        }
        s.nu.x = s.c.x + r
        s.nu.y = s.c.y
        if (over) return
        safe -= dt
        val h = s.heat
        // The particles come round.
        val it = bits.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.d -= w[b.lane] * dt
            val g = b.gone
            if (g != null) { if (now - g > FADE) it.remove(); continue }
            if (b.d < -AFTER) { b.gone = now; continue }
            if (now - b.born < FADE * 0.6) continue
            val p = pos(s, b)
            if (dist(p.x, p.y, s.nu.x, s.nu.y) >= s.nu.r + BIT_R - 1) continue
            if (b.red) {
                if (safe > 0) continue
                it.remove()
                safe = SAFE_MS
                left--
                s.fx.burst(p, 7, Ink.RED, 0.09)
                s.fx.ring(s.nu.p, Ink.RED, s.nu.r, s.nu.r + 24, 500.0)
                s.out.miss()
                s.out.buzz(22)
                if (left <= 0) { over = true; s.lose(); return }
            } else {
                it.remove()
                caught++
                s.fx.burst(p, 6, Ink.CYAN, 0.1)
                s.out.capture()
                if (caught >= need(s)) {
                    caught = 0
                    s.clear()
                    if (s.over) { over = true; return }
                    if (s.level % 3 == 0) left = min(LIVES, left + 1)
                    // The reds still out fade away, and the rings change after a breath.
                    for (q in bits) if (q.red && q.gone == null) q.gone = now
                    next = CALM_MS
                    shuffle(s)
                }
                s.part = clamp(caught.toDouble() / need(s), 0.0, 1.0)
            }
        }
        next -= dt
        if (next > 0) return
        next = every(h) * (0.7 + rnd() * 0.6)
        if (rnd() < gates(h)) { gate(s); return }
        val lane = (rnd() * n).toInt().coerceIn(0, n - 1)
        val red = rnd() < reds(h)
        val at = SPAN / w[lane]
        if (red) { if (fair(lane, at)) bits += Bit(lane, SPAN, true, now) }
        else if (clearOf(lane, at, 380.0)) bits += Bit(lane, SPAN, false, now)
    }

    /** True if no red reaches Nu on ring `lane` within `win` ms of `at` ms from now. */
    private fun clearOf(lane: Int, at: Double, win: Double) =
        bits.none { it.red && it.gone == null && it.lane == lane && abs(eta(it) - at) < win }

    /** A red on `lane` reaching Nu in `at` ms leaves a ring free then, and keeps off a cyan. */
    private fun fair(lane: Int, at: Double): Boolean {
        if (bits.any { !it.red && it.gone == null && it.lane == lane && abs(eta(it) - at) < 380 }) return false
        return (0 until n).count { it != lane && !clearOf(it, at, 650.0) } < n - 1
    }

    /** A red on every ring but one, all reaching Nu together, and a cyan in the gap. */
    private fun gate(s: Stage) {
        val at = (0 until n).minOf { SPAN / w[it] }
        val open = (0 until n).filter { clearOf(it, at, 700.0) }
        if (open.isEmpty()) return
        val gap = open[(rnd() * open.size).toInt().coerceIn(0, open.size - 1)]
        for (l in 0 until n) bits += Bit(l, w[l] * at, l != gap, s.now)
        next += at * 0.4
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val c = s.c
        // Nu's line across the rings, where everything comes to.
        pen.line(c.x + rad(s, n - 1) - 14, c.y, c.x + rad(s, 0) + 14, c.y, Ink.K800, 2.0, 0.9)
        for (i in 0 until n) {
            val f = if (i == n - 1) clamp((now - grew) / 600, 0.0, 1.0) else 1.0
            pen.ring(c, rad(s, i), if (i == lane) Ink.K400 else Ink.K500, 3.0, (if (i == lane) 0.75 else 0.5) * f)
        }
        val lit = clamp(1 - (now - litAt) / 300, 0.0, 1.0)
        if (lit > 0 && !over) pen.ring(c, rad(s, lane), Ink.WHITE, 3 + 2 * lit, 0.45 * lit)
        pen.dot(c, 6.0, Ink.K300, 0.5)
        for (b in bits) {
            val g = b.gone
            val a = if (g != null) clamp(1 - (now - g) / FADE, 0.0, 1.0) else clamp((now - b.born) / FADE, 0.0, 1.0)
            if (a <= 0) continue
            val p = pos(s, b)
            if (b.red) pen.dot(p, BIT_R + 0.5, Ink.RED, a)
            else { pen.glow(p.x, p.y, BIT_R, Ink.CYAN, a); pen.dot(p, BIT_R, Ink.CYAN, a) }
        }
        lives(s, pen, left, LIVES)
        // Blinks while it can't be hit again.
        if (safe <= 0 || ((safe / 100).roundToInt() % 2 == 0)) s.nu.draw(pen, now)
    }

    /** Moves Nu onto ring `to`, with a click; past the outer or inner ring, only a small bump. */
    private fun go(s: Stage, to: Int) {
        val l = to.coerceIn(0, n - 1)
        if (l == lane) {
            // Against the end: a small bump, no click.
            rv += (if (to < 0) 1 else -1) * 0.08
            return
        }
        lane = l
        litAt = s.now
        s.out.buzz(15)
    }

    override fun crown(s: Stage, by: Double) {
        if (over || by == 0.0) return
        val sign = if (by > 0) 1 else -1
        if (sign != way || s.now - turnedAt > IDLE) wound = 0.0
        way = sign
        turnedAt = s.now
        wound += by
        // The first click at half a step, then one every step.
        while (wound >= STEP / 2) { go(s, lane - 1); wound -= STEP }
        while (wound <= -STEP / 2) { go(s, lane + 1); wound += STEP }
    }

    private fun touch(s: Stage, p: Pt) {
        val d = dist(p, s.c)
        val l = (0 until n).minByOrNull { abs(rad(s, it) - d) } ?: return
        go(s, l)
    }

    override fun down(s: Stage, p: Pt) { if (!over) touch(s, p) }
    override fun move(s: Stage, p: Pt) { if (!over) touch(s, p) }

    internal companion object {
        const val LIVES = 3
        /** Rings in play: three, four from about Medium. */
        fun rings(heat: Double) = if (heat < 0.42) 3 else 4
        /** A ring's pace (rad/ms) before each ring's own share of chance. */
        fun pace(heat: Double) = min(0.0026, 0.00088 + 0.00068 * heat)
        /** The wait between particles (ms) before chance. */
        fun every(heat: Double) = max(260.0, 860 - 360 * heat)
        /** The share of particles that are red. */
        fun reds(heat: Double) = min(0.62, 0.42 + 0.14 * heat)
        /** The chance a new particle is a gate instead. */
        fun gates(heat: Double) = clamp((heat - 0.2) * 0.2, 0.0, 0.22)
    }
}
