package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.exp
import kotlin.math.min

/**
 * Ready (the phone's src/nu/grove.ts). Nu waits, dim, in the middle. Then it glows green: tap,
 * quick, before the ring closes on it. Too soon, too slow, or a tap when it glows red instead,
 * costs one of three lives; the last one loses the run. A tap shows at once where it landed, and
 * the closing ring stops where it was then.
 * A level is a set of greens caught (5, and one more every third level), and the game flows
 * straight on into the next: the wait before each green is the calm. The lives carry on from
 * level to level, and one comes back every third level. Heat closes the ring faster, from 650 ms
 * at Very easy to about 320 ms at Expert, never under 220 ms however far free play goes, and
 * brings the reds more often, from one in seven to nearly one in two.
 * It was already a round-court game on the phone; on the watch the lives are the kit's (a little
 * higher and smaller), and the first wait starts on the first frame, when the clock is known.
 */
class Ready : Toy {
    private enum class State { WAIT, GO, RED, AFTER }
    private var state = State.WAIT
    private var at = 0.0
    private var limit = 650.0
    private var left = 3
    /** Greens caught in this level, and in the run. */
    private var caught = 0
    private var count = 0
    private var over = false
    private var begun = false
    /** The last press: where, when, how it went, and the closing ring's size just then. */
    private class Mark(val p: Pt, val t: Double, val ok: Boolean, val r: Double)
    private var mark: Mark? = null

    private fun ringR(s: Stage, now: Double): Double {
        val k = clamp(1 - (now - at) / limit, 0.0, 1.0)
        val nu = s.nu
        return nu.r + 12 + k * (s.R * 0.6 - nu.r - 12)
    }

    override fun enter(s: Stage) {
        s.nu.r = 14.0
    }

    /** Greens to catch to clear the level. */
    private fun need(s: Stage) = 5 + s.level / 3

    /** The heat, climbing a little with each catch toward the next level's. */
    private fun h(s: Stage) = s.heat + HEAT_PER_LEVEL * s.part

    private fun wait(now: Double) {
        state = State.WAIT
        at = now + 700 + rnd() * 2800
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        if (!begun) { begun = true; wait(now) }
        val nu = s.nu
        nu.seek(s.c, dt, 0.5, 0.02)
        nu.glow = if (state == State.GO) 2.0 else 0.5
        if (over) return
        if (state == State.WAIT && now >= at) {
            // After the first one, now and then a red one instead; more often with the heat.
            val h = h(s)
            state = if (count >= 1 && rnd() < min(0.45, 0.15 + 0.2 * h)) State.RED else State.GO
            limit = 220 + 430 * exp(-1.5 * h)
            at = now
        } else if (state == State.GO && now - at > limit) {
            mark = Mark(nu.p, now, false, nu.r + 12)
            lose(s, now)
        } else if (state == State.RED && now - at > 900) {
            wait(now)
        } else if (state == State.AFTER && now - at > 600) {
            wait(now)
        }
    }

    private fun lose(s: Stage, now: Double) {
        left--
        s.out.miss()
        s.out.buzz(24)
        state = State.AFTER
        at = now
        if (left <= 0) {
            over = true
            s.lose()
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val nu = s.nu
        val m = mark
        when {
            state == State.GO -> {
                pen.dot(nu.x, nu.y, nu.r + 10, Ink.GREEN, 0.35)
                pen.ring(nu.x, nu.y, ringR(s, now), Ink.GREEN, 4.0, 0.9)
            }
            state == State.RED -> {
                pen.dot(nu.x, nu.y, nu.r + 10, Ink.RED, 0.35)
                pen.ring(nu.x, nu.y, nu.r + 12, Ink.RED, 4.0, 0.9)
            }
            // Where the ring will close from, faint and still.
            m == null || now - m.t > 260 -> pen.ring(nu.p, s.R * 0.6, Ink.K700, 2.0, 0.8)
        }
        // The last press, brief: a ring where the finger came down, and the closing ring held
        // still where it was then (green caught, red not).
        val t = if (m != null) (now - m.t) / 260 else 1.0
        if (m != null && t < 1) {
            val c = if (m.ok) Ink.GREEN else Ink.RED
            val a = 1 - t
            pen.ring(nu.x, nu.y, m.r, c, 4.0, 0.9 * a)
            if (m.p.x != nu.x || m.p.y != nu.y) pen.ring(m.p, 10 + 8 * t, c, 3.0, 0.9 * a)
        }
        lives(s, pen, left)
        nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        val now = s.now
        if (state == State.GO) {
            mark = Mark(p.copy(), now, true, ringR(s, now))
            caught++
            count++
            s.out.capture()
            s.part = caught.toDouble() / need(s)
            state = State.AFTER
            at = now
            if (caught >= need(s)) {
                caught = 0
                s.clear()
                if (s.over) { over = true; return }
                if (s.level % 3 == 0 && left < 3) left++
            }
            return
        }
        if (state == State.WAIT || state == State.RED) {
            mark = Mark(p.copy(), now, false, s.nu.r + 12)
            lose(s, now)
        }
    }
}
