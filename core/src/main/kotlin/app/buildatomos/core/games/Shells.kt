package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Shells (the phone's src/nu/park.ts). Three shells. Nu hides under one and the shells shuffle;
 * tap the one it is under. A wrong shell loses the run.
 * Levels: a level is a few finds in a row (three, and one more every few levels, up to five).
 * Heat makes the shuffle longer (six swaps at the gentlest, about eighteen at Expert, more after)
 * and quicker (each swap from half a second down to about a quarter at Expert, a fifth past it),
 * and each find in a level adds a swap. Each level plays as the next one up did at first. The next round follows a find by itself, after Nu has been shown.
 * On the watch the run is also lost if no shell is picked for 20 seconds (the phone waits for
 * ever): the shell Nu was under opens.
 */
class Shells : Toy {
    private enum class St { HIDE, SHUFFLE, PICK, SHOW }
    private var spots = listOf<Pt>()
    /** Which spot each shell is at. */
    private val at = intArrayOf(0, 1, 2)
    private var under = 0
    private var state = St.HIDE
    private class Swap(val a: Int, val b: Int, val t0: Double, val ms: Double)
    private var swap: Swap? = null
    private var left = 0
    /** Finds so far in this level. */
    private var found = 0
    private var timer = 0.0
    private val open = mutableSetOf<Int>()
    private val pos = Array(3) { Pt(0.0, 0.0) }
    /** When the shells stopped and the pick began. */
    private var since = 0.0

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.nu.at(s.c)
        val r = s.R * 0.42
        spots = listOf(-90.0, 30.0, 150.0).map { d -> polar(s, d * PI / 180, r) }
        for (i in 0 until 3) pos[i] = spots[at[i]].copy()
        found = 0
        round()
    }

    private fun round() {
        under = floor(rnd() * 3).toInt()
        state = St.HIDE
        open.clear()
        open += listOf(0, 1, 2)
        timer = 1100.0
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        s.part = clamp(found.toDouble() / need(s.level), 0.0, 1.0)
        // Positions: each shell at its spot, or on its arc while two swap.
        for (i in 0 until 3) pos[i] = spots[at[i]].copy()
        swap?.let { w ->
            val t = clamp((now - w.t0) / w.ms, 0.0, 1.0)
            val e = t * t * (3 - 2 * t)
            val A = spots[at[w.a]]
            val B = spots[at[w.b]]
            val m = Pt((A.x + B.x) / 2, (A.y + B.y) / 2)
            val half = dist(A, B) / 2
            val a0 = atan2(A.y - m.y, A.x - m.x)
            pos[w.a] = Pt(m.x + cos(a0 + PI * e) * half, m.y + sin(a0 + PI * e) * half)
            pos[w.b] = Pt(m.x - cos(a0 + PI * e) * half, m.y - sin(a0 + PI * e) * half)
            if (t >= 1) {
                val k = at[w.a]; at[w.a] = at[w.b]; at[w.b] = k
                swap = null
                for (i in 0 until 3) pos[i] = spots[at[i]].copy()
                if (--left <= 0) { state = St.PICK; since = now }
            }
        }
        if (state == St.HIDE) {
            nu.seek(pos[under], dt, 0.55, 0.015)
            timer -= dt
            if (timer < 300) open.clear()
            if (timer <= 0) { state = St.SHUFFLE; left = swaps(s.heat) + found }
        }
        if (state == St.SHUFFLE && swap == null) {
            val a = floor(rnd() * 3).toInt()
            val b = (a + 1 + floor(rnd() * 2).toInt()) % 3
            swap = Swap(a, b, now, swapMs(s.heat))
        }
        if (state != St.HIDE) { nu.x = pos[under].x; nu.y = pos[under].y }
        if (state == St.PICK && now - since > QUIET) {
            // Walked off: Nu's shell opens and the run is lost.
            open.clear()
            open += under
            state = St.SHOW
            s.out.miss()
            s.lose()
        }
        if (state == St.SHOW && !s.over) {
            timer -= dt
            if (timer <= 0) round()
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val hiddenNu = state == St.SHUFFLE || state == St.PICK || (state == St.HIDE && under !in open)
        if (!hiddenNu) s.nu.draw(pen, now)
        for (i in 0 until 3) {
            val p = pos[i]
            val r = 30.0
            if (i in open) pen.ring(p, r, Ink.K300, 3.0, 0.7)
            else {
                pen.dot(p, r, Ink.K800)
                pen.ring(p, r, Ink.K300, 3.0, if (state == St.PICK) 0.75 + 0.2 * sin(now / 300 + i) else 0.6)
            }
        }
        if (hiddenNu && state == St.HIDE) pen.dot(pos[under], 3.0, Ink.ROSE, 0.6)
    }

    override fun down(s: Stage, p: Pt) {
        if (state != St.PICK || s.over) return
        val i = (0 until 3).firstOrNull { dist(p, pos[it]) < 36 } ?: return
        open.clear()
        open += i
        open += under
        state = St.SHOW
        timer = 1300.0
        if (i == under) {
            s.fx.burst(pos[i], 10, Ink.ROSE, 0.12)
            s.out.capture()
            if (++found >= need(s.level)) { found = 0; s.clear() }
        } else {
            s.out.miss()
            s.lose()
        }
        s.part = clamp(found.toDouble() / need(s.level), 0.0, 1.0)
    }

    internal companion object {
        /** How long the watch waits for a pick before the run is lost. */
        const val QUIET = 20_000.0
        /** Finds in a row to clear a level: three, one more every three levels, at most five. */
        fun need(level: Int) = 3 + min(2, level / 3)
        /** Swaps in a shuffle at this heat: 6 at the gentlest, 18 at Expert, on up. */
        fun swaps(heat: Double) = 6 + (heat * 13).roundToInt()
        /** How long one swap takes (ms): half a second at the gentlest, about a quarter at Expert,
         *  never under 180. */
        fun swapMs(heat: Double) = max(180.0, 500.0 - heat * 270)
    }
}
