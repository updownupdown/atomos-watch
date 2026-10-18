package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Dive (the phone's src/nu/park.ts). Shells turn round the nucleus, each with a few gaps. Nu
 * waits at the top; tap and it dives through the next shell, if a gap is there. Miss and it
 * bounces back out a shell. Reach the nucleus for the next atom, with one more shell.
 * On the phone Dive never ends by itself (only the roulette's minute stops it), so on the watch
 * every run is that minute, counting down on the rim; the count is the atom Nu is on, as the
 * roulette counts it.
 */
class Dive : Toy {
    private var level = 1
    /** Each shell turns at its own speed with 2 to 4 gaps of different widths round it (their
     *  middles, from the shell's turn, and their widths, in radians): wait for a wide one, or
     *  risk a narrow one coming sooner. */
    private class Gap(val at: Double, var w: Double)
    private class Shell(val phase: Double, val speed: Double, val gaps: MutableList<Gap>)
    private val shells = mutableListOf<Shell>()
    /** How many shells Nu is through. */
    private var band = 0
    private var hitAt = -1e9
    private var hitShell = -1
    private var doneAt: Double? = null
    /** Time left in the run. */
    private var left = RUN
    private var over = false

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.rim = 1.0
        start(s)
    }

    private fun radius(s: Stage, k: Int): Double {
        val n = shells.size
        return s.R * (0.84 - k * 0.6 / max(1, n - 1))
    }

    /** Where Nu waits after `b` shells: between shell b-1 and shell b, at the top. */
    private fun spot(s: Stage, b: Int): Pt {
        val n = shells.size
        val r = if (b == 0) s.R * 0.95 else if (b >= n) 0.0 else (radius(s, b - 1) + radius(s, b)) / 2
        return Pt(s.c.x, s.c.y - r)
    }

    private fun start(s: Stage) {
        val r = seeded(level * 7919)
        val n = min(7, 2 + level)
        shells.clear()
        for (k in 0 until n) {
            val sp = (0.0006 + r() * 0.0006 + level * 0.00008) * (if (k % 2 == 1) -1 else 1)
            val m = 2 + floor(r() * 3).toInt()
            val wide = 0.83 * max(0.3, 0.55 - level * 0.03)
            val gaps = mutableListOf<Gap>()
            for (g in 0 until m) {
                // Spread round the shell, a little off even; one wide, the rest narrower.
                val at = g * TAU / m + (r() - 0.5) * (TAU / m) * 0.35
                gaps += Gap(at, if (g == 0) wide else wide * (0.55 + r() * 0.3))
            }
            shells += Shell(r() * TAU, sp, gaps)
        }
        // No gap too small to get through with room to spare: at least Nu's width and a
        // fingertip's timing on its shell. Small inner shells, then, keep fewer gaps.
        shells.forEachIndexed { k, sh ->
            val least = (2 * s.nu.r + 20) / radius(s, k)
            for (g in sh.gaps) g.w = max(g.w, least)
            while (sh.gaps.size > 1 && sh.gaps.sumOf { it.w } > TAU * 0.45) sh.gaps.removeAt(sh.gaps.lastIndex)
        }
        band = 0
        doneAt = null
        s.nu.at(spot(s, 0))
        s.score(level)
    }

    /** How far shell k has turned, now. */
    private fun turned(k: Int, now: Double): Double { val sh = shells[k]; return sh.phase + now * sh.speed }

    /** Nu is at the top (straight up from the nucleus): is one of shell k's gaps there? */
    private fun open(s: Stage, k: Int, now: Double) = room(s, k, now).first > 0

    /** How much room Nu would have, in units either way, diving through shell k now (0 or less:
     *  none), and the gap it would go through. */
    private fun room(s: Stage, k: Int, now: Double): Pair<Double, Gap?> {
        val top = -PI / 2
        val t = turned(k, now)
        val r = radius(s, k)
        val pad = s.nu.r / r
        var best = Double.NEGATIVE_INFINITY
        var gap: Gap? = null
        for (g in shells[k].gaps) {
            val px = (g.w / 2 - pad - abs(atan2(sin(t + g.at - top), cos(t + g.at - top)))) * r
            if (px > best) { best = px; gap = g }
        }
        return best to gap
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        s.nu.seek(spot(s, band), dt, 0.9, 0.04)
        if (over) return
        left -= dt
        s.rim = clamp(left / RUN, 0.0, 1.0)
        if (left <= 0) {
            over = true
            finish(s, "dive", level)
            return
        }
        val done = doneAt
        if (done != null && now - done > 1500) { level++; start(s) }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val flash = clamp(1 - (now - hitAt) / 450, 0.0, 1.0)
        shells.forEachIndexed { k, sh ->
            val r = radius(s, k)
            val t = turned(k, now)
            val c = if (k == hitShell && flash > 0) Ink.RED else if (k < band) Ink.CYAN else Ink.K300
            val alpha = if (k < band) 0.35 else 0.85
            // The shell between its gaps, in pieces.
            val gs = sh.gaps.sortedBy { it.at }
            gs.forEachIndexed { i, g ->
                val nx = gs[(i + 1) % gs.size]
                val end = nx.at - nx.w / 2 + (if (i == gs.size - 1) TAU else 0.0)
                val from = t + g.at + g.w / 2
                pen.arc(s.c.x, s.c.y, r, from, clockwise(t + end - from), c, 4.0, alpha)
            }
        }
        val won = band >= shells.size
        pen.dot(s.c, 8.0, if (won) Ink.GREEN else Ink.K300, if (won) 1.0 else 0.8)
        // A tap now would get through: the gap under Nu glows softly green, and so does a ring
        // round Nu, fading in as the gap comes round and out as it goes.
        if (!won && doneAt == null && !over && dist(s.nu.p, spot(s, band)) <= 6) {
            val k = band
            val (px, gap) = room(s, k, now)
            val o = clamp(px / 6, 0.0, 1.0)
            if (gap != null && o > 0) {
                val r = radius(s, k)
                val a = turned(k, now) + gap.at
                pen.arc(s.c.x, s.c.y, r, a - gap.w / 2 + 0.06, clockwise(gap.w - 0.12), Ink.GREEN, 4.0, 0.45 * o)
                pen.ring(s.nu.p, s.nu.r + 6, Ink.GREEN, 2.0, 0.5 * o)
            }
        }
        s.nu.draw(pen, now)
    }

    /** A canvas arc drawn clockwise from start to end turns this far: a whole turn at most. */
    private fun clockwise(sweep: Double) = if (sweep >= TAU) TAU else ((sweep % TAU) + TAU) % TAU

    override fun down(s: Stage, p: Pt) {
        if (doneAt != null || over) return
        val now = s.now
        // Still on its way to the last spot: wait for it.
        if (dist(s.nu.p, spot(s, band)) > 6) return
        val k = band
        if (open(s, k, now)) {
            band++
            s.out.tick(k % 5)
            if (band >= shells.size) {
                doneAt = now
                setBest(s, "dive", level.toDouble())
                s.fx.burst(s.c, 16, Ink.GREEN, 0.14)
                s.out.complete()
                s.out.buzz(14)
                s.score(level)
            }
            return
        }
        // The shell's in the way: Nu bumps it and is knocked back out one.
        hitAt = now
        hitShell = k
        s.fx.ring(Pt(s.c.x, s.c.y - radius(s, k)), Ink.RED, 4.0, 22.0, 450.0)
        s.out.miss()
        s.out.buzz(20)
        s.nu.vy = 0.5
        band = max(0, band - 1)
    }

    private companion object {
        /** A run: the phone roulette's minute a game. */
        const val RUN = 60_000.0
    }
}
