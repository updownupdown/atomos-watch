package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The waves' frequencies, in whole waves across the court: Nu's range and the targets'. */
private const val LO = 1.2
private const val HI = 8.0
private const val T_LO = 1.4
private const val T_HI = 7.0
/** How long a stretch lasts at the start and at its shortest (ms). */
private const val LONG = 5400.0
private const val SHORT = 3000.0
/** From this stretch on, a new stretch's wave also jumps along (its phase), more as it goes. */
private const val JUMP_AT = 2
/** The share of a stretch's passing that must match to score. */
private const val GOAL = 0.25
/** How close Nu's wave is, by its colour: matched, near, nearer than far, and far (red). How
 *  near is how many times the match's tolerance it's off by. */
private const val BLUE = 0xFF5F8AE0.toInt()
private const val AMBER = 0xFFD98A2B.toInt()
private const val NEAR = 2.2
private const val MID = 4.5
/** How quickly Nu's wave slides along onto the stretch going past (ms): the watch's phase. */
private const val LOCK = 250.0
/** Stretches gone by before the game starts counting on its own, if the crown hasn't turned. */
private const val GRACE = 2
/** The gauge by the crown: where it runs along the rim, lower right (radians, clockwise). */
private const val GAUGE_A0 = 0.42
private const val GAUGE_A1 = 1.12

/** An angle brought into -π..π. */
private fun wrap(x: Double) = ((((x + PI) % TAU) + TAU) % TAU) - PI

/**
 * Tune (the phone's src/nu/brook.ts), a simpler watch version. Resonance: a solid grey wave rolls
 * in from the right, a stretch at a time, each with its own frequency, and later each a little
 * along from where the last left off. Nu rides its own wave, drawn thin over the incoming one
 * ahead of it and left behind as a trace. Nu's wave shows how close it is: red far off, amber,
 * blue near, and green when it matches the stretch going past, which turns green too. Match a
 * stretch for a quarter of its passing and it scores; less and one of three lives empties. The
 * stretches come quicker and the match must be closer as it goes.
 * On the watch there is one control instead of the phone's two sliders: the crown tunes the
 * frequency (clockwise quicker, one whole turn across the range; a drag across the screen does
 * the same), and the phase matches itself, Nu's wave sliding along onto the stretch going past
 * within about a quarter of a second. The waves run across the circle's middle, and a short
 * gauge on the rim by the crown shows the frequency. The phone counts nothing until the first
 * touch; the watch also starts counting on its own once two stretches have gone by, so a run
 * nobody plays still ends.
 */
class Tune : Toy {
    /** A stretch of the target wave: where it starts (world units), how long it is, its frequency,
     *  its phase at the start, and how long it matched as it passed Nu. */
    private class Seg(val u0: Double, val len: Double, val c: Double, val ph0: Double) {
        var on = 0.0
        var all = 0.0
    }
    private class Mark(val u: Double, val y: Double, val c: Int)
    private val segs = ArrayDeque<Seg>()
    private val trail = ArrayDeque<Mark>()
    private var scroll = 0.0
    private var c = 3.0
    /** Nu's wave's phase where Nu is, as it runs, and the shift that slides it onto the target. */
    private var ph = 0.0
    private var off = 0.0
    /** A drag across: where it started and the frequency then. */
    private var grabX: Double? = null
    private var grabC = 3.0
    private var started = false
    private var gone = 0
    private var matched = false
    private var near = Double.POSITIVE_INFINITY
    private var left = 3
    private var count = 0
    private var over = false

    private fun W(s: Stage) = 2 * s.R
    private fun X0(s: Stage) = s.c.x - s.R * 0.3
    private fun cy(s: Stage) = s.c.y
    /** The waves' height. */
    private fun amp(s: Stage) = s.R * 0.34
    /** The scroll speed (units/ms). */
    private fun v(s: Stage) = W(s) / 5200
    /** How close a match must be: in frequency (as a log ratio) and in phase (radians). */
    private fun tolF() = max(0.08, 0.16 - count * 0.004)
    private fun tolP() = max(0.4, 0.75 - count * 0.015)

    override fun enter(s: Stage) {
        s.rim = null
        s.nu.r = 9.0
        // The first stretch matches Nu as it is, to show what green means.
        val u0 = -(s.c.x + s.R - X0(s))
        segs += Seg(u0, W(s) * 0.8, c, TAU * (c / W(s)) * u0)
        grow(s)
        place(s)
        s.nu.at(s.nu.p)
        s.score(0)
    }

    /** More target ahead, as far as the court shows. */
    private fun grow(s: Stage) {
        val ahead = scroll + (s.c.x + s.R - X0(s)) + 40
        var last = segs.last()
        while (last.u0 + last.len < ahead) {
            val n = count + segs.count { it.u0 > scroll }
            var nc = last.c
            for (i in 0 until 20) {
                if (abs(ln(nc / last.c)) >= 0.3) break
                nc = exp(ln(T_LO) + rnd() * ln(T_HI / T_LO))
            }
            val jump = if (n >= JUMP_AT) (rnd() - 0.5) * TAU * min(1.0, 0.35 + (n - JUMP_AT) * 0.08) else 0.0
            val ms = max(SHORT, LONG - n * 100)
            val end = last.u0 + last.len
            val seg = Seg(end, v(s) * ms, nc, phase(s, last, end) + jump)
            segs += seg
            last = seg
        }
    }

    /** A stretch's phase at world place `u`. */
    private fun phase(s: Stage, g: Seg, u: Double) = g.ph0 + TAU * (g.c / W(s)) * (u - g.u0)

    /** The stretch at world place `u`, or null. */
    private fun at(u: Double): Seg? = segs.firstOrNull { u >= it.u0 && u < it.u0 + it.len }

    /** The target's height at world place `u` (up is +), easing from one stretch into the next. */
    private fun target(s: Stage, u: Double): Double {
        val i = segs.indexOfFirst { u >= it.u0 && u < it.u0 + it.len }
        val g = segs[if (i < 0) (if (u < segs.first().u0) 0 else segs.size - 1) else i]
        val y = amp(s) * sin(phase(s, g, u))
        if (i <= 0 || u - g.u0 >= 30) return y
        val e = (u - g.u0) / 30
        val k = e * e * (3 - 2 * e)
        return amp(s) * sin(phase(s, segs[i - 1], u)) * (1 - k) + y * k
    }

    /** Nu's own wave's height at world place `u`, as it is set now. */
    private fun mine(s: Stage, u: Double) = amp(s) * sin(ph + off + TAU * (c / W(s)) * (u - scroll))

    private fun place(s: Stage) {
        s.nu.x = X0(s)
        s.nu.y = cy(s) - mine(s, scroll)
    }

    /** Nu's wave's colour: white before it counts, then by how close it is. */
    private fun hue(): Int {
        if (!started || over) return Ink.WHITE
        return if (matched) Ink.GREEN else if (near < NEAR) BLUE else if (near < MID) AMBER else Ink.RED
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        if (over) return
        val v = v(s)
        scroll += v * dt
        ph = (ph + TAU * (c / W(s)) * v * dt) % TAU
        val g = at(scroll)
        // The phase matches itself: Nu's wave slides along onto the stretch going past.
        if (g != null) off = wrap(off + wrap(phase(s, g, scroll) - ph - off) * (1 - exp(-dt / LOCK)))
        place(s)
        // How far off Nu's wave is (in tolerances, frequency and phase together), and whether it
        // matches.
        val offF = if (g != null) abs(ln(c / g.c)) / tolF() else Double.POSITIVE_INFINITY
        val offP = if (g != null) abs(wrap(ph + off - phase(s, g, scroll))) / tolP() else Double.POSITIVE_INFINITY
        near = hypot(offF, offP)
        val ok = near < 1
        if (ok && !matched && started) s.out.tick(2)
        matched = ok
        if (g != null && started) { g.all += dt; if (ok) g.on += dt }
        trail += Mark(scroll, s.nu.y, hue())
        val keep = scroll - (X0(s) - (s.c.x - s.R)) - 10
        while (trail.size > 2 && trail.first().u < keep) trail.removeFirst()
        // Stretches that have gone by: scored, or a life.
        while (segs.size > 1 && segs.first().u0 + segs.first().len <= scroll) {
            val d = segs.removeFirst()
            if (!started) { if (++gone >= GRACE) started = true; continue }
            if (d.all < 300) continue
            if (d.on / d.all >= GOAL) {
                count++
                s.out.capture()
                s.fx.ring(s.nu.p, Ink.GREEN, s.nu.r, s.nu.r + 30, 500.0)
                s.score(count)
            } else {
                left--
                s.out.miss()
                s.out.buzz(20)
                s.fx.ring(s.nu.p, Ink.RED, s.nu.r, s.nu.r + 30, 600.0)
                if (left <= 0) {
                    over = true
                    grabX = null
                    finish(s, "tune", count)
                    s.score(count)
                    return
                }
            }
        }
        grow(s)
    }

    private val xs = DoubleArray(400)
    private val ys = DoubleArray(400)

    /** Draws a wave from `x0` to `x1` as one line per stretch that lies inside the court. */
    private inline fun wave(s: Stage, pen: Pen, x0: Double, x1: Double, color: Int, width: Double, alpha: Double, y: (Double) -> Double) {
        if (x1 <= x0) return
        val lim = s.R - width
        var n = 0
        var x = x0
        while (true) {
            val yy = y(x)
            if (dist(x, yy, s.c.x, s.c.y) < lim && n < xs.size) { xs[n] = x; ys[n] = yy; n++ }
            else { if (n > 1) pen.poly(xs, ys, n, color, width, alpha); n = 0 }
            if (x >= x1) break
            x = min(x + 3, x1)
        }
        if (n > 1) pen.poly(xs, ys, n, color, width, alpha)
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val x0 = X0(s)
        val x1 = s.c.x + s.R + 4
        val cy = cy(s)
        val amp = amp(s)
        val live = matched && started && !over
        // Where Nu is: a faint upright line through the waves.
        pen.line(x0, cy - amp - 22, x0, cy + amp + 22, Ink.K800, 2.0, 0.9)
        // The target, thick and translucent: grey, and green along the stretch going past while
        // it matches.
        val g = at(scroll)
        val split = if (g != null && live) min(x1, x0 + (g.u0 + g.len - scroll)) else x0
        wave(s, pen, x0, split, Ink.GREEN, 5.0, 0.55) { x -> cy - target(s, scroll + (x - x0)) }
        wave(s, pen, split, x1, Ink.K300, 5.0, 0.55) { x -> cy - target(s, scroll + (x - x0)) }
        // Where one stretch gives way to the next: a small mark on the line.
        for (q in segs) {
            val x = x0 + (q.u0 - scroll)
            val y = cy - target(s, q.u0)
            if (x > x0 + 4 && x < x1 && dist(x, y, s.c.x, s.c.y) < s.R - 4) pen.dot(x, y, 3.5, Ink.K300, 0.9)
        }
        // Nu's wave, thin and solid: its trace to the left in the colour of how close it was at
        // each point, and ahead of it as it is set now, over the incoming wave.
        val hue = hue()
        for (i in 1 until trail.size) {
            val a = trail[i - 1]
            val b = trail[i]
            val ax = x0 - (scroll - a.u)
            val bx = x0 - (scroll - b.u)
            if (dist(ax, a.y, s.c.x, s.c.y) < s.R - 2 && dist(bx, b.y, s.c.x, s.c.y) < s.R - 2) pen.line(ax, a.y, bx, b.y, b.c, 2.0)
        }
        wave(s, pen, x0, x1, hue, 2.0, 1.0) { x -> cy - mine(s, scroll + (x - x0)) }
        gauge(s, pen, hue)
        s.nu.draw(pen, now)
        lives(s, pen, left)
    }

    /** The frequency, as a short arc on the rim by the crown: its track, the part up to the knob in
     *  Nu's colour, and the knob. Clockwise is quicker, as the crown turns. */
    private fun gauge(s: Stage, pen: Pen, color: Int) {
        val r = s.R - 12
        val f = clamp(ln(c / LO) / ln(HI / LO), 0.0, 1.0)
        val a = GAUGE_A0 + (GAUGE_A1 - GAUGE_A0) * f
        pen.arc(s.c.x, s.c.y, r, GAUGE_A0, GAUGE_A1 - GAUGE_A0, Ink.K500, 4.0, 0.7)
        if (a > GAUGE_A0) pen.arc(s.c.x, s.c.y, r, GAUGE_A0, a - GAUGE_A0, color, 4.0, 0.8)
        val p = polar(s, a, r)
        pen.dot(p, 6.0, color, 0.95)
        pen.ring(p, 10.0, color, 2.0, 0.4)
    }

    private fun tuneTo(nc: Double) { c = clamp(nc, LO, HI) }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        started = true
        // One whole turn of the crown runs the whole range.
        tuneTo(c * exp(by / TAU * ln(HI / LO)))
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        started = true
        grabX = p.x
        grabC = c
    }

    override fun move(s: Stage, p: Pt) {
        val gx = grabX ?: return
        // Across the whole court runs the whole range.
        tuneTo(grabC * exp((p.x - gx) / W(s) * ln(HI / LO)))
    }

    override fun up(s: Stage, p: Pt) { grabX = null }
}
