package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Stick (the phone's src/nu/lawn.ts). Nu turns in the middle of the court, a few electrons already
 * on its shell. Tap and an electron flies up from the bottom; it sticks to the shell where it
 * lands and turns with Nu. Never onto one already there: that loses the run.
 * Levels: a level is a round, its electrons all placed; the next round starts by itself after a
 * short pause. Each level has one more electron to place than the last, until the shell is full.
 * Heat puts more electrons on the shell to begin with (one at the gentlest, four from about
 * Expert's third level), more to place (six at the gentlest, eight at Expert; never more than
 * twelve on the shell in all) and turns Nu faster (0.19 turns a second at the gentlest, twice
 * that at Expert, at most three times) and less evenly: past the gentlest the turning swells and
 * slackens, more as it heats, and from Hard on it turns back now and then.
 * On the watch it plays the same, tapping anywhere. Two changes: the electrons still to place sit
 * closer together when there are many, so the row stays on the round screen, above the level
 * counter; and Nu doesn't wait forever: left 8 s, the electron goes by itself, the rim counting
 * down its last 3 s.
 */
class Stick : Toy {
    private var rot = 0.0
    /** Which way Nu turns this round, 1 or -1. */
    private var dir = 1.0
    private val stuck = mutableListOf<Double>()
    /** The electron on its way up: how high it is. */
    private var shot: Double? = null
    private var toGo = 5
    /** Electrons to place this round, all told. */
    private var all = 5
    private var nextAt: Double? = null
    /** When the electron at the bottom was ready, for the wait. */
    private var readyAt: Double? = null
    private val er = 8.0

    private fun shell(s: Stage) = s.R * 0.42
    private fun launch(s: Stage) = s.c.y + s.R - 56

    override fun enter(s: Stage) {
        s.nu.r = 16.0
        s.nu.at(s.c)
        newRound(s)
    }

    private fun newRound(s: Stage) {
        val n = already(s.heat, s.level)
        toGo = toPlace(s.heat, s.level, n)
        all = toGo
        dir = if (rnd() < 0.5) -1.0 else 1.0
        stuck.clear()
        for (i in 0 until n) stuck += i.toDouble() / n * TAU + rnd() * 0.6
        shot = null
        nextAt = null
        readyAt = null
    }

    private fun rate(heat: Double, now: Double): Double {
        // Hotter, it speeds up and slows down, and, hotter still, turns back.
        val wave = if (heat >= TURN_BACK) sin(now / 1300) * 1.5 else 1 + sin(now / 900) * 0.6 * clamp((heat - 0.1) / 0.4, 0.0, 1.0)
        return speed(heat) * dir * wave
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        s.nu.seek(s.c, dt, 0.5, 0.02)
        if (s.over) return
        s.part = clamp(1 - toGo.toDouble() / all, 0.0, 1.0)
        rot += rate(s.heat, now) * dt
        nextAt?.let { if (now >= it) { newRound(s); return } }
        val y = shot
        if (y == null) {
            // Waiting on the player: after a while the electron goes by itself.
            if (nextAt == null) {
                val ready = readyAt ?: now.also { readyAt = it }
                val waited = now - ready
                s.rim = if (waited > WAIT - SHOW) clamp((WAIT - waited) / SHOW, 0.0, 1.0) else null
                if (waited >= WAIT) fire(s)
            }
            return
        }
        val up = y - 1.1 * dt
        val end = s.c.y + shell(s)
        if (up > end) { shot = up; return }
        shot = null
        val a = PI / 2 - rot
        val room = 2 * asin(min(1.0, er / shell(s))) * 1.1
        val at = Pt(s.c.x, end)
        if (stuck.any { b -> abs(atan2(sin(a - b), cos(a - b))) < room }) {
            s.fx.ring(at, Ink.RED, er, 34.0, 600.0)
            s.out.miss()
            s.out.buzz(30)
            s.lose()
            return
        }
        stuck += a
        toGo--
        s.fx.ring(at, Ink.CYAN, er, er + 16, 400.0)
        s.out.capture()
        s.part = clamp(1 - toGo.toDouble() / all, 0.0, 1.0)
        if (toGo == 0) { s.clear(); nextAt = now + 700 }
    }

    private fun fire(s: Stage) {
        shot = launch(s)
        readyAt = null
        s.rim = null
        s.out.tick(2)
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val r = shell(s)
        pen.dashed(s.c, r, Ink.K500, 0.6)
        for (a in stuck) pen.dot(s.c.x + cos(a + rot) * r, s.c.y + sin(a + rot) * r, er, Ink.CYAN, 0.9)
        val ly = launch(s)
        val y = shot
        if (y != null) pen.dot(s.c.x, y, er, Ink.CYAN)
        else if (!s.over && nextAt == null) pen.dot(s.c.x, ly, er, Ink.WHITE, 0.9)
        // The electrons still to place, after this one: closer together when there are many, to
        // keep the row inside the circle.
        val rest = max(0, toGo - if (shot == null && nextAt == null) 1 else 0)
        val gap = if (rest > 1) min(12.0, 120.0 / (rest - 1)) else 12.0
        for (i in 0 until rest) pen.dot(s.c.x + (i - (rest - 1) / 2.0) * gap, ly + 18, 2.6, Ink.K300, 0.7)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (s.over || shot != null || nextAt != null) return
        fire(s)
    }

    internal companion object {
        /** How long the electron waits for a tap before it goes by itself, and the rim's share of it. */
        const val WAIT = 8000.0
        const val SHOW = 3000.0
        /** The heat from which Nu's turning turns back now and then. */
        const val TURN_BACK = 0.6
        /** The most electrons on the shell by the end of a round: room enough still to place the last. */
        const val FULL = 12
        /** Electrons on the shell as a round begins: one at the gentlest, at most four. */
        fun already(heat: Double, level: Int) = min(4, 1 + (heat * 2.5).toInt() + level / 2)
        /** Electrons to place in a round: six at the gentlest, one more each level. */
        fun toPlace(heat: Double, level: Int, already: Int) = min(FULL - already, 6 + level + (heat * 2).toInt())
        /** How fast Nu turns, on the whole (radians a ms). */
        fun speed(heat: Double) = 0.0012 + min(0.0023, heat * 0.0012)
    }
}
