package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** How far in from the court's edge the grid's corner dots stay. */
private const val ROOM = 10.0

/** No tap for this long while asked: the player has walked off, and it counts as a wrong one. */
private const val IDLE_MS = 20_000.0

/** Rounds in a level. */
private const val ROUNDS = 3

/**
 * Recall (the phone's src/nu/grove.ts). A grid of dots. Some light up for a moment, then go
 * dark: tap the same ones back. A wrong one shows where they were and costs one of three lives;
 * the last one loses the run.
 * A level is three rounds all found; a wrong one deals the round again. The lives carry on from
 * level to level, and one comes back every third level. Heat lights more dots (3 at Very easy, 7
 * at Expert, 11 by heat 2.5, and a bigger grid as they grow), up to one more as a level goes on,
 * and shows them for less time per dot.
 * The phone lays the grid out in its rectangular arena, with Nu above it. On the watch the grid
 * sits in the middle of the round court, its spacing (at most the phone's 74) as big as lets the
 * corner dots stay inside the rim; Nu waits at the lower right of it, clear of the level counter
 * under it. The grid stops at 5 by 5 (the phone goes to 6 by 6), where the dots are about 41
 * apart, so a tap goes to the nearest dot in reach rather than the last one. And since the
 * watch's run must end when nobody plays, 20 s without a tap while asked counts as a wrong one.
 */
class Recall : Toy {
    private var n = 3
    private var size = 3
    private var lit = setOf<Int>()
    private val found = mutableSetOf<Int>()
    private enum class State { SHOW, ASK, AFTER }
    private var state = State.SHOW
    private var at = 0.0
    private var wrong = -1
    private var left = 3
    /** Rounds found in this level. */
    private var round = 0
    private var over = false
    private var begun = false
    /** When the player last did something while asked (the ask's start, or a tap). */
    private var since = 0.0

    /** The grid's spacing: the corner dots (0.3 of it across) stay `ROOM` inside the rim. */
    private fun gap(s: Stage) = min(74.0, (s.R - ROOM) / ((size - 1) / sqrt(2.0) + 0.3))

    private fun cell(s: Stage, k: Int): Pt {
        val g = gap(s)
        val i = k % size
        val j = k / size
        return Pt(s.c.x + (i - (size - 1) / 2.0) * g, s.c.y + (j - (size - 1) / 2.0) * g)
    }

    private fun dotR(s: Stage) = gap(s) * 0.3

    override fun enter(s: Stage) {
        s.nu.r = 8.0
        deal(s, s.now)
    }

    /** How long the dots stay lit. */
    private fun show(s: Stage) = (900 + n * 120) * max(0.55, 1 - 0.18 * s.heat)

    /** Where Nu waits: low on the right, off the grid and clear of the level counter. */
    private fun rest(s: Stage) = polar(s, PI * 65 / 180, s.R - 24)

    private fun deal(s: Stage, now: Double) {
        // As many as the heat says, and half a dot more for each round found in the level.
        n = 3 + floor(4 * s.heat.pow(0.8) + round * 0.5).toInt()
        // The phone goes up to 6 by 6; on the watch 5 by 5 is as fine as a fingertip allows.
        size = if (n <= 4) 3 else if (n <= 7) 4 else 5
        lit = (0 until size * size).shuffled().take(min(n, size * size - 4)).toSet()
        found.clear()
        s.part = round.toDouble() / ROUNDS
        wrong = -1
        state = State.SHOW
        at = now + 400
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        // The clock isn't known until the first frame: the first show starts there.
        if (!begun) { begun = true; at = now + 400 }
        s.nu.seek(rest(s), dt, 0.6, 0.03)
        if (over) return
        if (state == State.SHOW && now > at + show(s)) { state = State.ASK; since = now }
        if (state == State.ASK && now - since > IDLE_MS) lose(s, now, -1)
        if (state == State.AFTER && now > at) deal(s, now)
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val r = dotR(s)
        val showing = state == State.SHOW && now > at
        for (k in 0 until size * size) {
            val p = cell(s, k)
            val on = (showing && k in lit) || k in found || ((state == State.AFTER || over) && k in lit)
            pen.dot(p, r, Ink.K800, 0.9)
            if (on) pen.dot(p, r, if (k in found) Ink.GREEN else Ink.CYAN, 0.9)
            if (k == wrong) pen.ring(p, r, Ink.RED, 3.0, 0.95)
        }
        lives(s, pen, left)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (over || state != State.ASK) return
        val now = s.now
        since = now
        // The nearest dot in reach.
        val reach = dotR(s) + 8
        var k = -1
        var near = reach
        for (q in 0 until size * size) {
            val d = dist(cell(s, q), p)
            if (d < near) { near = d; k = q }
        }
        if (k < 0 || k in found) return
        if (k in lit) {
            found += k
            s.out.capture()
            s.part = (round + found.size.toDouble() / lit.size) / ROUNDS
            if (found.size == lit.size) {
                state = State.AFTER
                at = now + 600
                if (++round == ROUNDS) {
                    round = 0
                    s.clear()
                    if (s.over) { over = true; return }
                    if (s.level % 3 == 0 && left < 3) left++
                    at = now + 900
                }
            }
            return
        }
        lose(s, now, k)
    }

    /** A wrong one (`k`, or -1 for none tapped): shows where they were, costs a life. */
    private fun lose(s: Stage, now: Double, k: Int) {
        wrong = k
        left--
        s.out.miss()
        s.out.buzz(22)
        state = State.AFTER
        at = now + 1300
        if (left <= 0) {
            over = true
            s.lose()
        }
    }
}
