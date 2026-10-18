package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Flash (the phone's src/nu/court.ts). Seven atoms like the logo's nucleus: one in the middle,
 * six round it. One lights up at a time; tap it while it's lit and Nu darts to it. Now and then
 * one lights red: leave it. A missed cyan, or a tap on the wrong one, costs one of three lives.
 * On the watch it plays as on the phone; the lives are the kit's smaller dots, and the soft
 * shadow round a lit atom is the kit's flat glow.
 */
class Flash : Toy {
    private var nodes = listOf<Pt>()
    private var lit = -1
    private var red = false
    private var litAt = 0.0
    private var gap = 700.0
    private var hits = 0
    private var left = 3
    private var over = false
    private var flyTo: Pt? = null
    /** A ring round an atom just tapped or missed: when, and its color. */
    private class Mark(val at: Double, val c: Int)
    private val flash = mutableMapOf<Int, Mark>()
    private var lastLit = -1

    override fun enter(s: Stage) {
        s.nu.r = 8.0
        layout(s)
        s.score(0)
    }

    private fun layout(s: Stage) {
        val r = s.R * 0.55
        nodes = listOf(s.c.copy()) + (0 until 6).map { i ->
            val a = -Math.PI / 2 + i * TAU / 6
            Pt(s.c.x + cos(a) * r, s.c.y + sin(a) * r)
        }
    }

    /** How long a light stays on: shorter as you go. */
    private fun window() = max(480.0, 1300.0 - hits * 22)

    private fun miss(s: Stage, now: Double, at: Int) {
        left--
        flash[at] = Mark(now, Ink.RED)
        s.out.miss()
        s.out.buzz(25)
        if (left <= 0) {
            over = true
            s.score(hits)
            finish(s, "flash", hits)
        }
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        s.nu.seek(flyTo ?: Pt(s.c.x, s.c.y + s.R * 0.82), dt, 1.1, 0.05)
        if (over) return
        if (lit >= 0) {
            if (now - litAt > window()) {
                // Faded: a missed cyan costs a life; a red left alone is right.
                val was = lit
                lit = -1
                gap = 250 + rnd() * 450
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
        red = hits > 4 && rnd() < 0.18
        litAt = now
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val r = s.R * 0.14
        nodes.forEachIndexed { i, p ->
            pen.dot(p, r, Ink.K800)
            val f = flash[i]
            val ft = if (f != null) clamp(1 - (now - f.at) / 500, 0.0, 1.0) else 0.0
            if (i == lit) {
                val t = clamp((now - litAt) / window(), 0.0, 1.0)
                val c = if (red) Ink.RED else Ink.CYAN
                val lr = r * (0.92 - 0.25 * t)
                pen.glow(p.x, p.y, lr, c, 1 - 0.55 * t)
                pen.dot(p, lr, c, 1 - 0.55 * t)
            } else pen.ring(p, r, Ink.K500, 2.0, 0.6)
            if (f != null && ft > 0) pen.ring(p, r + 4, f.c, 4.0, ft)
        }
        lives(s, pen, left)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        val now = s.now
        val i = nodes.indexOfFirst { dist(it, p) < s.R * 0.2 }
        if (i < 0) return
        flyTo = nodes[i]
        if (i == lit && !red) {
            hits++
            lit = -1
            gap = 200 + rnd() * 400
            flash[i] = Mark(now, Ink.GREEN)
            s.fx.burst(nodes[i], 6, Ink.CYAN, 0.1)
            // Pitched by where the atom sits, and a little by chance.
            s.out.hit((i + rnd() * 0.8) / 6)
            s.score(hits)
            return
        }
        if (i == lit) { lit = -1; gap = 500.0 }
        miss(s, now, i)
    }
}
