// Small drawings the app shares: the ATOMOS mark (a lithium ion), a score as a tiny atom, and the
// games' icons, all drawn with a Pen in flat circles, rings and lines like the phone's.

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

/** A score as a tiny atom (the phone's score-atom.ts): each whole point an electron, filling
 *  shells 2, 8, 8, 18..., the tenths as the next electron, faint. */
fun Pen.scoreAtom(cx: Double, cy: Double, size: Double, points: Double, color: Int = Ink.WHITE, ring: Int? = null) {
    val shells = intArrayOf(2, 8, 8, 18, 18, 32, 32)
    val whole = max(0, floor(points + 1e-6).toInt())
    val frac = max(0.0, points - whole)
    var left = whole + if (frac > 0.05) 1 else 0
    val counts = mutableListOf<Int>()
    var used = 0
    while (left > 0 && used < shells.size) { val n = min(shells[used], left); counts += n; left -= n; used++ }
    if (counts.isEmpty()) counts += 0
    val k = size / 44
    val R = 20.0
    val nuc = 3.6
    val step = (R - nuc - 2.5) / max(1, counts.size)
    dot(cx, cy, nuc * k, color)
    var e = 0
    counts.forEachIndexed { i, c ->
        val r = nuc + 2.5 + step * (i + 1) - 0.4
        ring(cx, cy, r * k, color, 1.2 * k, 0.28)
        val full = shells[i]
        val d = max(0.9, min(2.1, PI * r / full * 0.55))
        for (j in 0 until c) {
            val a = -PI / 2 + j * 2 * PI / full + i * 0.4
            val last = e == whole
            dot(cx + cos(a) * r * k, cy + sin(a) * r * k, d * k, color, if (last) max(0.2, frac) else 1.0)
            e++
        }
    }
    if (ring != null) ring(cx, cy, 21.2 * k, ring, 1.6 * k)
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
        "tune" -> { wave(2.0, 14.0, 12.0, 4.0, 1.0, false); wave(14.0, 22.0, 12.0, 2.7, 1.0, true); f(14.0, 12.0, 2.6) }
        else -> { o(12.0, 12.0, 8.0); f(12.0, 12.0, 3.0) }
    }
}
