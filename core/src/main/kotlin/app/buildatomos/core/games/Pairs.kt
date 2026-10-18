package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The cards' spacing, centre to centre, and their radius (38 across: a fingertip's worth). */
private const val GAP = 50.0
private const val CARD = 19.0

/** How long a pair that doesn't match stays up. */
private const val HOLD_MS = 800.0

/** The pause between a board cleared and the next dealt. */
private const val NEXT_MS = 1100.0

/** The atoms' colours: no rose (Nu's) and no red (danger). */
private val HUES = intArrayOf(Ink.S, Ink.GREEN, Ink.D, Ink.F, Ink.CYAN, Ink.AMBER, Ink.WHITE, Ink.K400)

/**
 * Pairs (the phone's src/nu/arcade.ts). Memory: atom cards lie face down; turn two at a time, and
 * a pair that matches stays up. A level is a board cleared.
 * The phone shows each atom with its symbol on a coloured card. The watch's games have no text,
 * so here an atom is a colour and a mark in black on it: one to four dots (its electrons), and a
 * ring round them on the second four. Every atom on a board has its own colour and its own mark,
 * so a pair is plain to see by either.
 * On the watch the cards are round, 38 across, in rows that fit the circle: 4-4-4 (six pairs) at
 * first, then 3-4-4-3 (seven) and 4-4-4-4 (eight) as heat rises. The rim is the board's clock:
 * a few seconds for each pair, fewer with heat; run out and the run is lost (so a board left
 * alone ends it). A third card tapped while a wrong pair is up turns them back at once. Nu loops
 * round the card just turned and round a pair found. No crown.
 */
class Pairs : Toy {
    private class Card(val p: Pt, val g: Int) {
        var up = false
        var done = false
        var turned = -1e9
        var matched = -1e9
    }
    private var cards = listOf<Card>()
    private val open = mutableListOf<Card>()
    private var hold = 0.0
    private var focus: Pt? = null
    private var cheer: Triple<Double, Pt, Pt>? = null
    /** Time left on the board, and all it had. */
    private var left = 0.0
    private var time = 1.0
    /** Between boards: the next is dealt when this runs out. */
    private var rest = 0.0
    private var over = false

    override fun enter(s: Stage) {
        s.nu.r = 7.0
        s.nu.at(home(s))
        deal(s)
    }

    /** Where Nu waits: at nine o'clock, outside the cards. */
    private fun home(s: Stage) = polar(s, PI, s.R - 20)

    private fun deal(s: Stage) {
        val h = s.heat
        val rows = when {
            h < 0.35 -> intArrayOf(4, 4, 4)
            h < 0.7 -> intArrayOf(3, 4, 4, 3)
            else -> intArrayOf(4, 4, 4, 4)
        }
        val n = rows.sum()
        val glyphs = (0 until HUES.size).shuffled().take(n / 2)
        val deck = (glyphs + glyphs).shuffled()
        val list = mutableListOf<Card>()
        var k = 0
        for ((j, m) in rows.withIndex()) for (i in 0 until m) {
            val x = s.c.x + (i - (m - 1) / 2.0) * GAP
            val y = s.c.y + (j - (rows.size - 1) / 2.0) * GAP
            list += Card(Pt(x, y), deck[k++])
        }
        cards = list
        open.clear()
        hold = 0.0
        focus = null
        // A few seconds a pair: 5.5 at the easiest, 3.9 at Expert, never under 1.8.
        time = n / 2 * max(1800.0, 5500.0 - 1600 * h)
        left = time
        s.rim = 1.0
        s.part = 0.0
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        val ch = cheer?.takeIf { now - it.first < 1100 }
        nu.scale = if (ch != null) 1 + 0.25 * sin((now - ch.first) / 1100 * PI) else 1.0
        nu.glow = if (ch != null) 2.0 else 1.0
        val to = when {
            ch != null -> {
                val (t0, a, b) = ch
                val m = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
                val half = dist(a, b) / 2 + CARD + 9
                val t = (now - t0) / 1100 * TAU
                val ang = atan2(b.y - a.y, b.x - a.x)
                val ex = cos(t) * half
                val ey = sin(t) * (CARD + 9)
                Pt(m.x + ex * cos(ang) - ey * sin(ang), m.y + ex * sin(ang) + ey * cos(ang))
            }
            focus != null -> { val f = focus!!; val t = now / 700; Pt(f.x + cos(t) * (CARD + 9), f.y + sin(t) * (CARD + 9)) }
            else -> home(s)
        }
        nu.seek(inside(s, to, nu.r + 2), dt, if (ch != null) 1.4 else 0.8, if (ch != null) 0.08 else 0.03)
        if (over) return
        if (rest > 0) {
            rest -= dt
            if (rest <= 0) deal(s)
            return
        }
        if (hold > 0) {
            hold -= dt
            if (hold <= 0) shut()
        }
        left -= dt
        s.rim = clamp(left / time, 0.0, 1.0)
        if (left <= 0) {
            over = true
            s.rim = null
            for (c in cards) if (!c.done) { c.up = true; c.turned = now }
            s.fx.ring(nu.p, Ink.RED, nu.r, nu.r + 28, 600.0)
            s.out.miss()
            s.out.buzz(22)
            s.lose()
        }
    }

    private fun shut() {
        for (c in open) c.up = false
        open.clear()
        hold = 0.0
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        for (c in cards) {
            val showing = c.up || c.done
            if (!showing) {
                pen.dot(c.p, CARD, Ink.K900)
                pen.ring(c.p, CARD, Ink.K500, 2.0, 0.7)
                continue
            }
            // It turns over: grows from a sliver to the full card. A pair found glows a moment,
            // then settles at half strength.
            val flip = clamp((now - c.turned) / 180, 0.0, 1.0)
            val r = CARD * (0.6 + 0.4 * flip)
            val glow = if (c.done) clamp(1 - (now - c.matched - 500) / 600, 0.0, 1.0) else 0.0
            val alpha = if (c.done) 0.5 + 0.5 * glow else 1.0
            val color = HUES[c.g]
            if (glow > 0) pen.glow(c.p.x, c.p.y, r, color, glow)
            pen.dot(c.p, r + 3 * glow, color, alpha)
            if (flip > 0.5) mark(pen, c.p, r, c.g, alpha)
        }
        s.nu.draw(pen, now)
    }

    /** Atom `g`'s mark on its card: one to four black dots, and a ring round them on the second four. */
    private fun mark(pen: Pen, p: Pt, r: Double, g: Int, alpha: Double) {
        val n = g % 4 + 1
        val d = r * 0.13
        if (n == 1) pen.dot(p, d * 1.3, Ink.BLACK, alpha)
        else {
            val spread = r * (if (n == 2) 0.3 else 0.34)
            for (i in 0 until n) {
                val a = -PI / 2 + i * TAU / n + (if (n == 2) PI / 2 else 0.0)
                pen.dot(p.x + cos(a) * spread, p.y + sin(a) * spread, d, Ink.BLACK, alpha)
            }
        }
        if (g >= 4) pen.ring(p, r * 0.68, Ink.BLACK, r * 0.09, alpha)
    }

    override fun down(s: Stage, p: Pt) {
        if (over || rest > 0) return
        var best: Card? = null
        var near = CARD + 6
        for (c in cards) {
            val d = dist(c.p, p)
            if (d < near) { near = d; best = c }
        }
        val c = best ?: return
        if (c.up || c.done) return
        // A wrong pair still up: turn it back now, rather than make the player wait.
        if (hold > 0) shut()
        val now = s.now
        c.up = true
        c.turned = now
        focus = c.p
        open += c
        s.out.tick(open.size)
        if (open.size < 2) return
        val (a, b) = open
        if (a.g == b.g) {
            a.done = true; b.done = true
            a.matched = now; b.matched = now
            cheer = Triple(now, a.p, b.p)
            focus = null
            open.clear()
            s.out.capture()
            s.fx.burst(Pt((a.p.x + b.p.x) / 2, (a.p.y + b.p.y) / 2), 8, Ink.ROSE, 0.1)
            s.fx.ring(a.p, HUES[a.g], CARD, CARD + 16, 500.0)
            s.fx.ring(b.p, HUES[b.g], CARD, CARD + 16, 500.0)
            val found = cards.count { it.done }
            s.part = min(1.0, found.toDouble() / cards.size)
            if (found == cards.size) {
                s.fx.burst(s.c, 16, Ink.WHITE, 0.13)
                s.rim = null
                s.clear()
                if (s.over) { over = true; return }
                s.part = 0.0
                rest = NEXT_MS
            }
        } else hold = HOLD_MS
    }
}
