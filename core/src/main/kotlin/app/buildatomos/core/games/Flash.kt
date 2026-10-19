package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Flash (the phone's src/nu/court.ts). Seven atoms like the logo's nucleus: one in the middle,
 * six round it. One lights up at a time; tap it while it's lit and Nu darts to it. Now and then
 * one lights red: leave it. A missed cyan, or a tap on the wrong one, costs one of three lives;
 * the last one lost loses the run.
 * On the watch it plays as on the phone; the lives are the kit's smaller dots, the soft shadow
 * round a lit atom is the kit's flat glow, and Nu starts low on the right, off the counter.
 *
 * A level is a set of catches: ten, and one more each level. Then the atoms rest for a breath
 * and the next set starts. Lives carry over, and every third level cleared gives one back. Heat
 * keeps each light on for less time (1 s at the gentlest, 0.68 s at Medium's middle, 0.42 s at
 * Expert's and never under 0.4 s), lights the next one sooner and lights more of them red (one in
 * seven at the gentlest, near three in ten at Expert). It plays two and a half notches harder
 * than the phone's: each level plays as the one above it used to.
 */
class Flash : Toy {
    private var nodes = listOf<Pt>()
    private var lit = -1
    private var red = false
    private var litAt = 0.0
    private var gap = 700.0
    /** Catches this level, and how many it takes. */
    private var hits = 0
    private var need = 0
    /** Lights so far this run: the first few are never red. */
    private var shown = 0
    private var left = LIVES
    private var flyTo: Pt? = null
    /** A ring round an atom just tapped or missed: when, and its color. */
    private class Mark(val at: Double, val c: Int)
    private val flash = mutableMapOf<Int, Mark>()
    private var lastLit = -1

    override fun enter(s: Stage) {
        s.nu.r = 8.0
        layout(s)
        s.nu.at(rest(s))
        set(s)
    }

    private fun layout(s: Stage) {
        val r = s.R * 0.55
        nodes = listOf(s.c.copy()) + (0 until 6).map { i ->
            val a = -PI / 2 + i * TAU / 6
            Pt(s.c.x + cos(a) * r, s.c.y + sin(a) * r)
        }
    }

    /** Where Nu waits for the first tap: low on the right, between two atoms and off the counter. */
    private fun rest(s: Stage) = polar(s, PI / 3, s.R * 0.82)

    private fun set(s: Stage) {
        hits = 0
        need = min(20, 10 + s.level)
        s.part = 0.0
    }

    /** How long a light stays on, ms, how much sooner the next comes (a factor on the wait), and
     *  how often one is red; each a notch (a quarter of the heat scale) past the dial. */
    internal fun window(h: Double) = max(400.0, 1170.0 - 650 * (h + NOTCH))
    private fun sooner(h: Double) = clamp(1.03 - 0.35 * (h + NOTCH), 0.45, 1.15)
    internal fun redChance(h: Double) = min(0.35, 0.11 + 0.14 * (h + NOTCH))

    /** The atom to tap now, if one is lit cyan. */
    internal val target: Pt? get() = if (lit >= 0 && !red) nodes[lit] else null

    private fun miss(s: Stage, now: Double, at: Int) {
        left--
        flash[at] = Mark(now, Ink.RED)
        s.out.miss()
        s.out.buzz(25)
        if (left <= 0) s.lose()
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        s.nu.seek(flyTo ?: rest(s), dt, 1.1, 0.05)
        if (s.over) return
        val h = s.heat
        if (lit >= 0) {
            if (now - litAt > window(h)) {
                // Faded: a missed cyan costs a life; a red left alone is right.
                val was = lit
                lit = -1
                gap = (250 + rnd() * 450) * sooner(h)
                if (!red) miss(s, now, was)
            }
            return
        }
        gap -= dt
        if (gap > 0) return
        var k = floor(rnd() * nodes.size).toInt()
        if (k == lastLit) k = (k + 1 + floor(rnd() * 6).toInt()) % 7
        lit = k
        lastLit = k
        red = shown++ >= 4 && rnd() < redChance(h)
        litAt = now
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val r = s.R * 0.14
        val win = window(s.heat)
        nodes.forEachIndexed { i, p ->
            pen.dot(p, r, Ink.K800)
            val f = flash[i]
            val ft = if (f != null) clamp(1 - (now - f.at) / 500, 0.0, 1.0) else 0.0
            if (i == lit) {
                val t = clamp((now - litAt) / win, 0.0, 1.0)
                val c = if (red) Ink.RED else Ink.CYAN
                val lr = r * (0.92 - 0.25 * t)
                pen.glow(p.x, p.y, lr, c, 1 - 0.55 * t)
                pen.dot(p, lr, c, 1 - 0.55 * t)
            } else pen.ring(p, r, Ink.K500, 2.0, 0.6)
            if (f != null && ft > 0) pen.ring(p, r + 4, f.c, 4.0, ft)
        }
        lives(s, pen, left, LIVES)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (s.over) return
        val now = s.now
        val i = nodes.indexOfFirst { dist(it, p) < s.R * 0.2 }
        if (i < 0) return
        flyTo = nodes[i]
        if (i == lit && !red) {
            hits++
            lit = -1
            gap = (200 + rnd() * 400) * sooner(s.heat)
            flash[i] = Mark(now, Ink.GREEN)
            s.fx.burst(nodes[i], 6, Ink.CYAN, 0.1)
            // Pitched by where the atom sits, and a little by chance.
            s.out.hit((i + rnd() * 0.8) / 6)
            s.part = clamp(hits.toDouble() / need, 0.0, 1.0)
            if (hits >= need) cleared(s)
            return
        }
        if (i == lit) { lit = -1; gap = 500.0 }
        miss(s, now, i)
    }

    /** The set is caught: a ring out from the middle, a breath, and the next. */
    private fun cleared(s: Stage) {
        s.fx.ring(s.c, Ink.CYAN, s.R * 0.14, s.R * 0.75, 900.0)
        s.out.buzz(14)
        s.clear()
        if (s.over) return
        if (s.level % 3 == 0) left = min(LIVES, left + 1)
        gap = CALM
        set(s)
    }

    private companion object {
        const val LIVES = 3
        /** The breath between levels, ms. */
        const val CALM = 900.0
        /** How much hotter than the dial it plays: a level's worth. */
        const val NOTCH = 0.25
    }
}
