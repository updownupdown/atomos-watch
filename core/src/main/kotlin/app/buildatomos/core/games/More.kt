package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * More (the phone's src/nu/lawn.ts). Two fields of atoms, one each side: tap the side with more
 * before the rim runs out. A wrong side, or the rim running out, costs one of three lives.
 * A level is a set of right answers: six at first and one more every other level, up to eleven,
 * flowing straight on. The lives carry on from level to level, and one lost comes back every
 * third level. Heat shortens the rim's time, puts more atoms in the fields and brings the two
 * counts closer, and mixes the atoms' sizes, to fool the eye, more and more often.
 * On the watch each field is half the round court (off the line down the middle, in from the
 * edge, under the menu and over the level counter), not the phone's two rectangles; Nu waits at
 * the top, just under the menu. Every atom a side should have is always placed: if the field is
 * crowded the atoms may sit a little closer. The crown answers too, so no finger hides the
 * fields: a little turn anticlockwise picks the left side, clockwise the right.
 */
class More : Toy {
    private class Atom(val x: Double, val y: Double, val r: Double)
    private var sides: List<List<Atom>> = listOf(emptyList(), emptyList())
    /** The side with more: 0 left, 1 right. */
    private var more = 0
    /** When this ask began (set on the first frame after it). */
    private var askAt: Double? = null
    private var shownAt = 0.0
    private var answered: Int? = null
    private var left = 3
    /** Right answers this level. */
    private var count = 0
    private var over = false
    /** How far the crown has turned one way during this ask. */
    private var wound = 0.0
    /** The rim's time for one ask: 4.5 s at the easiest, 2.5 s at Expert, never under 1.1 s. */
    private fun ms(s: Stage) = max(1100.0, 4500.0 / (1 + s.heat * 0.8))
    private fun need(s: Stage) = 6 + min(s.level, 10) / 2

    private fun home(s: Stage) = Pt(s.c.x, s.c.y - s.R + 44)
    private fun spot(s: Stage, side: Int) = Pt(s.c.x + (if (side == 1) 1 else -1) * s.hw * 0.5, s.c.y)

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.nu.at(home(s))
        ask(s)
    }

    private fun ask(s: Stage) {
        val h = s.heat
        // From 3-5 a side at the easiest to 3-12 at Expert; the gap from 60% of the smaller down
        // to an eighth of it (one atom, mostly).
        val lo = 3 + floor(rnd() * min(10.0, 3 + h * 7)).toInt()
        val diff = max(1, (lo * max(0.12, 0.6 - h * 0.35)).roundToInt())
        more = if (rnd() < 0.5) 0 else 1
        val mixed = rnd() < clamp((h - 0.3) * 0.8, 0.0, 0.9)
        sides = (0..1).map { side ->
            val n = if (side == more) lo + diff else lo
            val out = mutableListOf<Atom>()
            val sx = if (side == 1) 1.0 else -1.0
            // Room between atoms: a little less if the field is too full to fit them all.
            var gap = 8.0
            var tries = 0
            while (out.size < n && gap > -1) {
                val r = if (mixed) 4 + rnd() * (if (side == more) 4 else 7) else 6.0
                // Somewhere in this half: off the middle line, in from the rim, under the menu and
                // over the level counter.
                val off = 18 + r + rnd() * (s.R - 30 - 2 * r)
                val p = Atom(s.c.x + sx * off, s.c.y + (rnd() * 2 - 1) * (s.R - 12 - r), r)
                val fits = dist(p.x, p.y, s.c.x, s.c.y) <= s.R - 12 - r && p.y - r > s.c.y - s.R + 30 &&
                    p.y + r < s.c.y + s.R - 32
                if (fits && out.all { q -> dist(p.x, p.y, q.x, q.y) > p.r + q.r + gap }) out += p
                if (++tries % 2000 == 0) gap -= 3
            }
            out
        }
        askAt = null
        answered = null
        wound = 0.0
        s.rim = 1.0
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        val start = askAt ?: now.also { askAt = it }
        val side = answered
        if (side != null && !over) {
            nu.seek(spot(s, side), dt, 0.8, 0.02)
            if (now - shownAt > 700) ask(s)
            return
        }
        nu.seek(home(s), dt, 0.6, 0.03)
        if (over) { s.rim = 0.0; return }
        val left = 1 - (now - start) / ms(s)
        s.rim = clamp(left, 0.0, 1.0)
        if (left <= 0) answer(s, now, 1 - more, true)
    }

    private fun answer(s: Stage, now: Double, side: Int, late: Boolean = false) {
        answered = side
        shownAt = now
        val at = spot(s, side)
        if (side == more && !late) {
            count++
            s.fx.ring(at, Ink.GREEN, 16.0, 70.0, 600.0)
            s.out.capture()
            if (count >= need(s)) {
                count = 0
                s.clear()
                if (s.over) { over = true; return }
                if (s.level % 3 == 0) left = min(3, left + 1)
            }
            s.part = count.toDouble() / need(s)
        } else {
            left--
            s.fx.ring(at, Ink.RED, 16.0, 70.0, 600.0)
            s.out.miss()
            s.out.buzz(22)
        }
        if (left <= 0) { over = true; s.lose() }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        // The line down the middle, in dashes, stopping short of the level counter.
        val y0 = s.c.y - s.R + 58
        val y1 = s.c.y + s.R - 38
        var y = y0
        while (y < y1) {
            pen.line(s.c.x, y, s.c.x, min(y + 8, y1), Ink.K500, 3.0, 0.8)
            y += 18
        }
        sides.forEachIndexed { i, side ->
            val lit = answered != null && i == more
            for (a in side) pen.dot(a.x, a.y, a.r, if (lit) Ink.GREEN else Ink.CYAN, if (over) 0.4 else 0.9)
        }
        lives(s, pen, left)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (over || answered != null) return
        answer(s, s.now, if (p.x < s.c.x) 0 else 1)
    }

    /** The crown: a little turn one way picks a side, anticlockwise the left, clockwise the right. */
    override fun crown(s: Stage, by: Double) {
        if (over || answered != null || by == 0.0) return
        if (by * wound < 0) wound = 0.0
        wound += by
        if (abs(wound) >= 0.15) answer(s, s.now, if (wound > 0) 1 else 0)
    }
}
