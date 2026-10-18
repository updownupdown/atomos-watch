package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Echo (the phone's src/nu/park.ts). Nu flies a tune between five atoms; play it back, one note
 * longer each round. A wrong note loses the run: the court flushes red and shakes, the wrong atom
 * rings red and the right one blinks white.
 * On the watch the run is also lost if the player leaves the tune unplayed for 20 seconds (the
 * phone waits for ever), so a run left on the wrist comes to an end.
 *
 * A level is a tune of three rounds: it starts a few notes long and grows a note a round; play
 * the third back and, after a breath, a new tune starts. Heat makes the tunes start longer (two
 * notes at the easiest, four at Expert, up to eight) and Nu fly them faster.
 */
class Echo : Toy {
    private enum class St { SHOW, LISTEN, WRONG, WAIT, ROUND }
    internal var nodes = listOf<Pt>()
        private set
    private val tune = mutableListOf<Int>()
    private var state = St.WAIT
    private var at = 0
    private val lit = HashMap<Int, Double>()
    private var timer = 0.0
    private var flyTo: Pt? = null
    private var wrongAt = 0.0
    /** The atom wrongly tapped, or -1 when the run ran out of time. */
    private var wrongNode = -1
    /** When the player last played a note (or their turn began). */
    private var since = 0.0
    /** Rounds of this level's tune played back. */
    private var rounds = 0
    /** How fast Nu flies the tune, units/ms: the level's, set when it starts. */
    private var pace = 0.7

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.nu.at(s.c)
        val r = s.R * 0.56
        // One atom at the top, two at the bottom: clear of the menu and the counter.
        nodes = List(5) { i -> polar(s, -PI / 2 + i * TAU / 5, r) }
        level(s)
    }

    /** How many notes a level's tune starts with, and how fast Nu flies it. */
    internal fun length(h: Double) = min(8, 2 + floor(h * 2).toInt())
    internal fun pace(h: Double) = min(1.6, 0.45 + 0.55 * h)

    /** A new tune for the next level. */
    private fun level(s: Stage) {
        tune.clear()
        rounds = 0
        pace = pace(s.heat)
        repeat(length(s.heat) - 1) { add() }
        next(s)
    }

    private fun add() {
        var n = floor(rnd() * 5).toInt()
        if (tune.isNotEmpty() && n == tune.last()) n = (n + 1 + floor(rnd() * 4).toInt()) % 5
        tune += n
    }

    /** One note longer, and Nu flies it. */
    private fun next(s: Stage) {
        add()
        state = St.WAIT
        timer = 900.0
        at = 0
        progress(s)
    }

    private fun progress(s: Stage) {
        s.part = clamp((rounds + at.toDouble() / tune.size) / ROUNDS, 0.0, 1.0)
    }

    /** The atom due next while it's the player's turn, else null. */
    internal val due: Int? get() = if (state == St.LISTEN) tune[at] else null

    private fun light(s: Stage, i: Int, now: Double) {
        lit[i] = now
        s.out.chime(i)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        val to = flyTo
        if (to != null) nu.seek(to, dt, pace, 0.02) else nu.seek(s.c, dt, 0.4, 0.01)
        if (s.over && state != St.WRONG) return
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
                // Walked off: the run is lost, and the note that was due blinks.
                wrongNode = -1
                lose(s, now)
            }
            St.WRONG -> {}
            St.ROUND -> {
                timer -= dt
                if (timer <= 0) { if (rounds >= ROUNDS) level(s) else next(s) }
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
        if (state != St.LISTEN || s.over) return
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
        progress(s)
        if (at >= tune.size) {
            s.fx.burst(nodes[i], 10, Ink.NOTES[i], 0.12)
            rounds++
            // A breath, then one note longer; after the level's last round, a longer one and a
            // new tune.
            state = St.ROUND
            timer = if (rounds >= ROUNDS) 1100.0 else 650.0
            if (rounds >= ROUNDS) s.clear()
        }
    }

    private fun lose(s: Stage, now: Double) {
        s.out.miss()
        state = St.WRONG
        wrongAt = now
        flyTo = null
        s.lose()
    }

    private companion object {
        /** How long the watch waits for a note before the run is lost. */
        const val QUIET = 20_000.0
        /** Rounds in a level: the tune grows a note each. */
        const val ROUNDS = 3
    }
}
