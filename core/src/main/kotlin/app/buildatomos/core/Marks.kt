// Small drawings the app shares: the ATOMOS mark (a lithium ion), a run's levels as a tiny atom,
// a difficulty's pips, and the games' icons, all drawn with a Pen in flat circles, rings and lines like the phone's.

package app.buildatomos.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The mark (the phone's src/ui/mark.ts) round (cx, cy), `size` across; `nu` is the nucleon Nu
 *  rests on (0 the middle, then clockwise from the top right), or -1. */
fun Pen.mark(cx: Double, cy: Double, size: Double, color: Int = Ink.WHITE, nu: Int = -1) {
    val k = size / 100
    val core = 0.66
    val d = 21 * 1.2 * core
    val r = 9.6 * core
    for (i in 0..6) {
        val (x, y) = if (i == 0) 50.0 to 50.0 else {
            val a = ((i - 1) * 60 + 30) * PI / 180
            50 + d * cos(a) to 50 + d * sin(a)
        }
        val px = cx + (x - 50) * k
        val py = cy + (y - 50) * k
        val proton = i == 0 || (i - 1) % 2 == 0
        when {
            i == nu -> dot(px, py, r * k * 1.05, Ink.ROSE)
            proton -> dot(px, py, r * k, color)
            else -> ring(px, py, r * k * 0.83, color, r * k * 0.36)
        }
    }
    val ring = 41 * k
    val gap = 40.0
    val start = 45.0
    for (half in 0..1) {
        val a0 = (start + half * 180 + gap / 2) * PI / 180
        arc(cx, cy, ring, a0, (180 - gap) * PI / 180, color, 7.5 * k)
        val e = (start + half * 180) * PI / 180
        dot(cx + cos(e) * ring, cy + sin(e) * ring, 6 * k, color)
    }
}

/**
 * The levels of a run, as a tiny atom: a nucleus and one shell of electron slots.
 *
 * A run for an atom has a goal (at most 8): the shell has that many slots, and each level cleared
 * fills one, the next filling as the level goes (`part`, 0..1). Free play has no goal: the shell
 * has 8 slots and counts in binary, slot 1 at the top for 1, the next clockwise for 2, then 4 and
 * so on, so the count can reach 255 and no further.
 */
fun Pen.levels(cx: Double, cy: Double, size: Double, level: Int, goal: Int?, part: Double = 0.0, color: Int = Ink.CYAN) {
    val k = size / 44
    val r = 15.0 * k
    val e = 3.2 * k
    dot(cx, cy, 4.2 * k, Ink.WHITE)
    ring(cx, cy, r, Ink.K700, 1.6 * k)
    val slots = if (goal != null) goal.coerceIn(1, 8) else 8
    for (i in 0 until slots) {
        val a = -PI / 2 + i * TAU / slots
        val x = cx + cos(a) * r
        val y = cy + sin(a) * r
        val on = if (goal != null) i < level else (level.coerceIn(0, MAX_LEVEL) shr i) and 1 == 1
        when {
            on -> dot(x, y, e, color)
            goal != null && i == level && part > 0.02 -> {
                dot(x, y, e, Ink.BLACK)
                ring(x, y, e - 0.7 * k, Ink.K500, 1.4 * k)
                arc(x, y, e - 0.7 * k, -PI / 2, TAU * part.coerceIn(0.0, 1.0), color, 1.4 * k)
            }
            else -> { dot(x, y, e, Ink.BLACK); ring(x, y, e - 0.7 * k, Ink.K500, 1.4 * k) }
        }
    }
}

/** The five pips of a difficulty: as many lit as its level, all in that level's color. */
fun Pen.pips(cx: Double, cy: Double, size: Double, dial: Int) {
    val n = Difficulty.level(dial) + 1
    val color = Difficulty.color(dial)
    val r = size / 12
    for (i in 0 until Difficulty.LEVELS) {
        val x = cx + (i - 2) * size / 5
        if (i < n) dot(x, cy, r, color) else dot(x, cy, r, Ink.K700)
    }
}

/** A game's icon (the phone's 24x24 icons in src/nu/games.ts), round (cx, cy), `size` across. */
fun Pen.gameIcon(id: String, cx: Double, cy: Double, size: Double, color: Int) {
    val k = size / 24
    val ox = cx - 12 * k
    val oy = cy - 12 * k
    fun f(x: Double, y: Double, r: Double) = dot(ox + x * k, oy + y * k, r * k, color)
    fun o(x: Double, y: Double, r: Double) = ring(ox + x * k, oy + y * k, r * k, color, 2 * k)
    fun l(x1: Double, y1: Double, x2: Double, y2: Double, w: Double = 2.0) = line(ox + x1 * k, oy + y1 * k, ox + x2 * k, oy + y2 * k, color, w * k)
    fun a(x: Double, y: Double, r: Double, from: Double, sweep: Double) = arc(ox + x * k, oy + y * k, r * k, from, sweep, color, 2 * k)
    fun dash(x: Double, y: Double, r: Double, n: Int) = dashed(ox + x * k, oy + y * k, r * k, color, 2 * k, 1.0, n)
    /** A wave from x0 to x1 about y, `amp` high, `cycles` across, solid or in dashes. */
    fun wave(x0: Double, x1: Double, y: Double, amp: Double, cycles: Double, dashes: Boolean) {
        val n = 24
        for (i in 0 until n) {
            if (dashes && i % 2 == 1) continue
            val t0 = i.toDouble() / n; val t1 = (i + 1.0) / n
            l(x0 + (x1 - x0) * t0, y - sin(t0 * cycles * 2 * PI) * amp, x0 + (x1 - x0) * t1, y - sin(t1 * cycles * 2 * PI) * amp)
        }
    }
    when (id) {
        "halo" -> { o(12.0, 12.0, 8.5); f(12.0, 12.0, 2.2); f(18.0, 6.0, 2.4); l(14.0, 14.5, 16.0, 16.5) }
        "orbit" -> { o(12.0, 12.0, 8.0); f(12.0, 4.0, 2.6) }
        "pulse" -> { f(12.0, 12.0, 2.4); o(12.0, 12.0, 6.0); dash(12.0, 12.0, 9.6, 10) }
        "dive" -> { a(12.0, 12.0, 8.5, -PI / 2 + 0.45, TAU - 0.9); f(12.0, 13.0, 3.0); f(12.0, 2.8, 2.0) }
        "flash" -> {
            f(12.0, 12.0, 3.0); o(12.0, 4.5, 2.0); o(18.5, 8.2, 2.0); f(18.5, 15.8, 2.0)
            o(12.0, 19.5, 2.0); o(5.5, 15.8, 2.0); o(5.5, 8.2, 2.0)
        }
        "gate" -> { f(12.0, 5.0, 2.8); f(5.0, 13.0, 1.8); f(13.0, 13.0, 1.8); o(11.0, 20.5, 1.8); o(19.0, 20.5, 1.8) }
        "grow" -> { f(10.0, 13.0, 6.0); f(19.0, 6.0, 1.8); o(19.5, 17.5, 3.0) }
        "echo" -> { f(12.0, 4.5, 2.3); f(19.5, 10.0, 2.3); f(16.7, 19.0, 2.3); f(7.3, 19.0, 2.3); f(4.5, 10.0, 2.3) }
        "stick" -> { f(12.0, 10.0, 3.5); dash(12.0, 10.0, 7.0, 9); f(5.5, 7.5, 1.9); f(17.5, 5.0, 1.9); o(12.0, 21.0, 1.9) }
        "ready" -> { f(12.0, 12.0, 3.5); o(12.0, 12.0, 8.5); l(12.0, 1.5, 12.0, 3.5); l(12.0, 20.5, 12.0, 22.5) }
        "shells" -> { o(5.5, 14.0, 3.5); f(12.0, 14.0, 3.5); o(18.5, 14.0, 3.5) }
        "recall" -> {
            for (r in 0..2) for (c in 0..2) {
                val x = 5.0 + c * 7; val y = 5.0 + r * 7
                if (r == c) f(x, y, 2.2) else o(x, y, 2.2)
            }
        }
        "spot" -> {
            for (r in 0..2) for (c in 0..2) if (!(r == 1 && c == 1)) f(6.0 + c * 6, 6.0 + r * 6.5, 2.4)
            o(12.5, 12.5, 3.4)
        }
        "juggle" -> { f(12.0, 7.5, 4.0); l(7.0, 15.5, 9.0, 17.5); l(17.0, 15.5, 15.0, 17.5); l(4.0, 21.0, 20.0, 21.0) }
        "ripple" -> { f(6.0, 18.0, 2.2); a(6.0, 18.0, 6.5, -PI / 2, PI / 2); a(6.0, 18.0, 12.5, -PI / 2, PI / 2); o(18.0, 6.0, 2.6) }
        "more" -> {
            for (i in 0..4) l(12.0, 3.0 + i * 4.0, 12.0, 4.6 + i * 4.0)
            f(4.5, 7.0, 1.9); f(8.0, 12.0, 1.9); f(4.5, 17.0, 1.9); f(18.0, 12.0, 1.9)
        }
        "blast" -> { f(12.0, 12.0, 3.2); o(4.5, 5.0, 2.0); o(19.5, 17.0, 2.0); f(17.5, 6.5, 1.5); l(14.5, 9.5, 16.0, 8.0) }
        "rain" -> { f(7.0, 4.5, 1.9); f(16.0, 7.0, 1.9); o(11.0, 11.0, 1.9); f(12.0, 19.0, 3.2) }
        "pop" -> {
            f(5.0, 4.5, 2.2); o(10.0, 4.5, 2.2); f(15.0, 4.5, 2.2); f(7.5, 9.0, 2.2); o(12.5, 9.0, 2.2)
            f(12.0, 19.5, 2.4); l(12.0, 16.0, 12.8, 13.0)
        }
        "fuse" -> { f(9.0, 3.8, 2.3); l(3.0, 8.5, 21.0, 8.5); f(7.0, 17.5, 3.5); o(15.5, 16.0, 5.0) }
        "dial" -> {
            // The ring, its hub, the lit notch on it and Nu, the pointer, coming round to it.
            o(12.0, 12.0, 8.0); f(12.0, 12.0, 2.0)
            arc(ox + 12 * k, oy + 12 * k, 8.0 * k, -PI / 2 - 0.4, 0.8, color, 4.6 * k)
            f(12.0 + 8 * cos(0.9), 12.0 + 8 * sin(0.9), 2.6)
        }
        "lanes" -> {
            // Three whole rings, thin, and Nu on the middle one at three o'clock.
            for (r in listOf(3.4, 6.6, 9.8)) ring(ox + 12 * k, oy + 12 * k, r * k, color, 1.5 * k)
            f(18.6, 12.0, 2.5)
        }
        "hop" -> { f(15.0, 5.0, 2.8); l(13.5, 12.5, 19.5, 12.5, 3.0); l(4.5, 19.0, 10.5, 19.0, 3.0) }
        "shade" -> { f(4.0, 4.0, 2.2); l(5.5, 5.5, 21.0, 12.0); l(5.5, 5.5, 12.0, 21.0); o(18.0, 19.0, 3.5); f(18.0, 19.0, 1.6) }
        "tune" -> { wave(2.0, 14.0, 12.0, 4.0, 1.0, false); wave(14.0, 22.0, 12.0, 2.7, 1.0, true); f(14.0, 12.0, 2.6) }
        "hue" -> {
            // A ring in four quarters, two of them dashed on the phone: here a small gap between each.
            for (q in 0..3) a(12.0, 9.5, 6.0, -PI / 2 + q * PI / 2 + 0.22, PI / 2 - 0.44)
            f(12.0, 20.5, 2.3)
        }
        "land" -> { f(9.0, 6.0, 3.0); f(9.0, 11.5, 1.3); f(9.0, 14.5, 1.0); l(3.0, 20.5, 21.0, 20.5); l(13.0, 20.5, 19.0, 20.5, 3.6) }
        "order" -> { f(4.5, 15.0, 1.6); f(9.5, 14.0, 2.5); o(17.0, 12.5, 4.2) }
        "pairs" -> { f(7.0, 7.0, 4.0); o(17.0, 7.0, 4.0); o(7.0, 17.0, 4.0); f(17.0, 17.0, 4.0) }
        "dodge" -> { f(12.0, 12.0, 3.0); o(4.0, 6.0, 1.7); o(20.0, 8.0, 1.7); o(6.0, 19.5, 1.7); o(18.5, 19.0, 1.7) }
        "core" -> {
            // Two rings, each open at a gap, round the nucleus; Nu on the outer one at nine o'clock.
            a(12.0, 12.0, 9.0, -PI / 4 + 0.5, TAU - 1.0)
            a(12.0, 12.0, 5.2, PI * 0.75 + 0.6, TAU - 1.2)
            f(12.0, 12.0, 1.8); f(3.0, 12.0, 2.6)
        }
        "bank" -> {
            // The shot's way as dots, off a bank on the left, to the ring at the top right; Nu below.
            for ((x, y) in listOf(9.5 to 16.2, 6.5 to 13.6, 3.8 to 11.2, 7.3 to 9.4, 11.0 to 7.4, 14.6 to 5.6)) f(x, y, 1.1)
            o(18.5, 4.5, 2.6); f(12.0, 20.0, 2.6)
        }
        "cross" -> { f(12.0, 20.0, 2.4); f(5.0, 14.0, 1.8); f(17.0, 14.0, 1.8); f(9.0, 8.5, 1.8); f(20.0, 8.5, 1.8); l(4.0, 3.5, 20.0, 3.5) }
        "fill" -> { f(8.5, 9.5, 6.0); o(17.0, 16.5, 4.5); f(17.0, 16.5, 1.7); f(19.5, 5.0, 1.7) }
        "fit" -> {
            // A ring with two gaps closing on the shell, its two electrons lined up with them.
            a(12.0, 12.0, 9.5, -PI / 2 + 0.5, PI - 1.0); a(12.0, 12.0, 9.5, PI / 2 + 0.5, PI - 1.0)
            f(12.0, 12.0, 2.4); f(12.0, 6.5, 1.7); f(12.0, 17.5, 1.7)
        }
        "herd" -> {
            // Nu on its ring outside, two electrons, and the nucleus they're headed for.
            ring(ox + 12 * k, oy + 12 * k, 9.5 * k, color, 1.5 * k)
            f(12.0 + 9.5 * cos(-0.8), 12.0 + 9.5 * sin(-0.8), 2.5)
            f(12.0, 12.0, 2.2); f(14.6, 9.4, 1.4); f(7.8, 14.2, 1.4)
        }
        "poise" -> { l(3.0, 15.5, 21.0, 12.5); f(12.0, 17.0, 2.0); l(12.0, 19.0, 12.0, 22.0); f(6.0, 11.5, 3.0); o(18.0, 8.5, 2.2) }
        else -> { o(12.0, 12.0, 8.0); f(12.0, 12.0, 3.0) }
    }
}

/** The app's own icons, drawn on a 24 grid like the phone's: flat strokes and fills, round caps. */
enum class Icon { PLAY, RESTART, EXIT, NEXT, CHECK, GEAR, LOCK, CHEVRON, BACK, GAMES, TABLE, WHEEL, ATLAS }

/** Icon `icon` round (cx, cy), `size` across. */
fun Pen.icon(icon: Icon, cx: Double, cy: Double, size: Double, color: Int) {
    val k = size / 24
    val ox = cx - 12 * k
    val oy = cy - 12 * k
    fun X(x: Double) = ox + x * k
    fun Y(y: Double) = oy + y * k
    fun l(x1: Double, y1: Double, x2: Double, y2: Double, w: Double = 2.6) = line(X(x1), Y(y1), X(x2), Y(y2), color, w * k)
    /** A filled shape with its corners softened by a thin stroke round it. */
    fun shape(vararg p: Double) {
        val n = p.size / 2
        val xs = DoubleArray(n + 1) { X(p[(it % n) * 2]) }
        val ys = DoubleArray(n + 1) { Y(p[(it % n) * 2 + 1]) }
        fill(xs, ys, n, color)
        poly(xs, ys, n + 1, color, 1.6 * k)
    }
    when (icon) {
        Icon.PLAY -> shape(8.5, 5.5, 19.0, 12.0, 8.5, 18.5)
        Icon.NEXT -> { shape(5.5, 6.0, 14.5, 12.0, 5.5, 18.0); l(18.0, 6.0, 18.0, 18.0) }
        Icon.EXIT -> { l(6.5, 6.5, 17.5, 17.5); l(17.5, 6.5, 6.5, 17.5) }
        Icon.CHECK -> poly(doubleArrayOf(X(5.5), X(10.0), X(18.5)), doubleArrayOf(Y(12.5), Y(17.0), Y(7.5)), 3, color, 2.8 * k)
        Icon.CHEVRON -> poly(doubleArrayOf(X(9.5), X(15.5), X(9.5)), doubleArrayOf(Y(6.0), Y(12.0), Y(18.0)), 3, color, 2.6 * k)
        Icon.BACK -> poly(doubleArrayOf(X(14.5), X(8.5), X(14.5)), doubleArrayOf(Y(6.0), Y(12.0), Y(18.0)), 3, color, 2.6 * k)
        Icon.RESTART -> {
            // A ring turning clockwise, open at the top right, the arrowhead at its end.
            val r = 7.5
            val start = -PI * 0.22
            val sweep = PI * 1.55
            arc(X(12.0), Y(12.0), r * k, start, sweep, color, 2.6 * k)
            val e = start + sweep
            val px = 12 + cos(e) * r
            val py = 12 + sin(e) * r
            val tx = -sin(e); val ty = cos(e)
            val nx = cos(e); val ny = sin(e)
            shape(
                px + tx * 4.4, py + ty * 4.4,
                px + nx * 3.6 - tx * 0.4, py + ny * 3.6 - ty * 0.4,
                px - nx * 3.6 - tx * 0.4, py - ny * 3.6 - ty * 0.4,
            )
        }
        Icon.GEAR -> {
            for (i in 0 until 8) {
                val a = i * PI / 4 + PI / 8
                l(12 + cos(a) * 6.8, 12 + sin(a) * 6.8, 12 + cos(a) * 9.0, 12 + sin(a) * 9.0, 3.6)
            }
            ring(X(12.0), Y(12.0), 5.4 * k, color, 3.4 * k)
        }
        Icon.LOCK -> {
            arc(X(12.0), Y(10.0), 4.0 * k, PI, PI, color, 2.4 * k)
            l(8.0, 10.0, 8.0, 11.5, 2.4); l(16.0, 10.0, 16.0, 11.5, 2.4)
            rect(X(5.5), Y(11.0), 13.0 * k, 9.5 * k, 2.2 * k, color)
        }
        Icon.GAMES -> for (r in 0..1) for (c in 0..1) rect(X(4.5 + c * 8.5), Y(4.5 + r * 8.5), 6.5 * k, 6.5 * k, 2.0 * k, color)
        Icon.TABLE -> for (i in 0..5) rect(X(4.0 + (i % 3) * 6), Y(7.0 + (i / 3) * 6), 4.4 * k, 4.4 * k, 1.0 * k, color)
        Icon.WHEEL -> { ring(X(12.0), Y(12.0), 7.5 * k, color, 2.4 * k); dot(X(12.0), Y(4.5), 2.6 * k, color) }
        Icon.ATLAS -> {
            // The phone's Atlas: an open book, lines of text on the left page, a little table on the
            // right (one cell left open), drawn a touch bolder for the watch.
            val w = 1.9
            val xs = doubleArrayOf(12.0, 9.6, 6.3, 2.75, 2.75, 6.3, 9.6, 12.0, 14.4, 17.7, 21.25, 21.25, 17.7, 14.4, 12.0)
            val ys = doubleArrayOf(6.4, 4.7, 4.3, 4.9, 19.1, 18.5, 18.9, 20.6, 18.9, 18.5, 19.1, 4.9, 4.3, 4.7, 6.4)
            poly(DoubleArray(xs.size) { X(xs[it]) }, DoubleArray(ys.size) { Y(ys[it]) }, xs.size, color, w * k)
            l(12.0, 6.4, 12.0, 20.6, w)
            l(5.0, 9.0, 9.4, 9.0, w); l(5.0, 12.0, 9.4, 12.0, w); l(5.0, 15.0, 8.0, 15.0, w)
            for ((cx, cy) in listOf(14.4 to 8.2, 17.6 to 8.2, 14.4 to 11.4, 14.4 to 14.6)) rect(X(cx), Y(cy), 2.4 * k, 2.4 * k, 0.6 * k, color)
        }
    }
}
