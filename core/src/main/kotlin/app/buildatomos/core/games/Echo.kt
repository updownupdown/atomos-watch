package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * Echo (the phone's src/nu/park.ts). Nu flies a tune between five atoms; play it back, one note
 * longer each round. A wrong note ends the run: the court flushes red and shakes, the wrong atom
 * rings red and the right one blinks white.
 * On the watch the run also ends if the player leaves the tune unplayed for 20 seconds (the
 * phone waits for ever), so a run left on the wrist comes to an end.
 */
class Echo : Toy {
    private enum class St { SHOW, LISTEN, WRONG, WAIT, ROUND }
    private var nodes = listOf<Pt>()
    private val tune = mutableListOf<Int>()
    private var state = St.WAIT
    private var at = 0
    private val lit = HashMap<Int, Double>()
    private var timer = 0.0
    private var flyTo: Pt? = null
    private var wrongAt = 0.0
    /** The atom wrongly tapped, or -1 when the run ran out of time. */
    private var wrongNode = -1
    private var over = false
    /** When the player last played a note (or their turn began). */
    private var since = 0.0

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.nu.at(s.c)
        val r = s.R * 0.56
        nodes = List(5) { i -> polar(s, -PI / 2 + i * TAU / 5, r) }
        tune.clear()
        next(s)
    }

    private fun next(s: Stage) {
        var n = floor(rnd() * 5).toInt()
        if (tune.isNotEmpty() && n == tune.last()) n = (n + 1 + floor(rnd() * 4).toInt()) % 5
        tune += n
        state = St.WAIT
        timer = 900.0
        at = 0
        s.score(tune.size)
    }

    private fun light(s: Stage, i: Int, now: Double) {
        lit[i] = now
        s.out.chime(i)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        val to = flyTo
        if (to != null) nu.seek(to, dt, 0.7, 0.02) else nu.seek(s.c, dt, 0.4, 0.01)
        when (state) {
            St.WAIT -> {
                timer -= dt
                if (timer <= 0) { state = St.SHOW; at = 0; flyTo = nodes[tune[0]] }
            }
            St.SHOW -> {
                val f = flyTo
                if (f != null && dist(nu.p, f) < 5) {
                    light(s, tune[at], now)
                    at++
                    if (at >= tune.size) { state = St.LISTEN; at = 0; flyTo = null; since = now }
                    else flyTo = nodes[tune[at]]
                }
            }
            St.LISTEN -> if (now - since > QUIET) {
                // Walked off: the run ends, and the note that was due blinks.
                wrongNode = -1
                lose(s, now)
            }
            St.WRONG -> {}
            St.ROUND -> {
                timer -= dt
                if (timer <= 0) next(s)
            }
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        // Your turn: the five atoms breathe a little, waiting.
        val wait = if (state == St.LISTEN) 0.08 + 0.06 * sin(now / 400) else 0.0
        nodes.forEachIndexed { i, p ->
            val t = lit[i]
            val on = if (t != null) clamp(1 - (now - t) / 520, 0.0, 1.0) else 0.0
            val c = Ink.NOTES[i]
            pen.glow(p.x, p.y, 22 + on * 4, c, on)
            pen.dot(p, 22 + on * 4, c, 0.28 + wait + 0.64 * on)
            pen.ring(p, 31.0, c, 2.0, 0.45)
        }
        if (state == St.WRONG) {
            // A miss: the court flushes red and shakes, the wrong atom rings red, the right one
            // blinks white.
            val t = now - wrongAt
            val f = clamp(1 - t / 1700, 0.0, 1.0)
            val shake = sin(t / 22) * 7 * clamp(1 - t / 500, 0.0, 1.0)
            pen.dot(s.c.x + shake, s.c.y, s.R, Ink.RED, 0.22 * f)
            if (wrongNode >= 0) pen.ring(nodes[wrongNode], 31 + 8 * (1 - f), Ink.RED, 5.0, f)
            val blink = if (floor(t / 220).toInt() % 2 == 0) 1.0 else 0.25
            pen.ring(nodes[tune[at]], 36.0, Ink.WHITE, 3.0, blink * f)
            s.nu.scale = 0.75 + 0.25 * (1 - f)
            s.nu.x += shake
            s.nu.draw(pen, now)
            s.nu.x -= shake
            return
        }
        s.nu.scale = 1.0
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (state != St.LISTEN || over) return
        val i = nodes.indexOfFirst { dist(p, it) < 36 }
        if (i < 0) return
        val now = s.now
        flyTo = nodes[i]
        since = now
        if (i != tune[at]) {
            lit[i] = now
            s.out.buzz(40)
            wrongNode = i
            lose(s, now)
            return
        }
        light(s, i, now)
        at++
        if (at >= tune.size) {
            setBest(s, "echo", tune.size.toDouble())
            s.fx.burst(nodes[i], 10, Ink.NOTES[i], 0.12)
            // A breath, then one note longer.
            state = St.ROUND
            timer = 650.0
        }
    }

    private fun lose(s: Stage, now: Double) {
        s.out.miss()
        state = St.WRONG
        over = true
        wrongAt = now
        flyTo = null
        finish(s, "echo", tune.size - 1)
    }

    private companion object {
        /** How long the watch waits for a note before the run ends. */
        const val QUIET = 20_000.0
    }
}
