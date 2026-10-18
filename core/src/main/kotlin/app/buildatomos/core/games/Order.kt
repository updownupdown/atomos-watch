package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The most atoms in a round: each stays a fair tap on the watch. */
private const val MOST = 7

/** The pause after a round is done, or after a wrong one, before the next is dealt. */
private const val BREATH_MS = 650.0

/**
 * Order (the phone's src/nu/grove.ts). Atoms of all sizes about the court: tap them in order,
 * smallest first, up to the biggest, before the rim runs out. A wrong one, or the rim running
 * out, costs one of three lives and deals the round again; the last one loses the run.
 * A level is one round all tapped in order. Each level brings one more atom (four at first, at
 * most seven) and sizes closer together. The lives carry from level to level, and every third
 * level cleared gives one back. Heat starts the count and the closeness further along (the dial
 * alone: up to four rounds on at Expert) and shortens the rim a little.
 * On the watch the atoms sit in a disc in the middle of the court, clear of the menu at the top
 * and the lives and level counter at the bottom, and are a little bigger than the phone's (9 at
 * the smallest), a finger's width apart. A tap takes the nearest atom in reach. Nu waits at the
 * lower right and flies to each atom as it is tapped. No crown.
 */
class Order : Toy {
    private class Atom(val x: Double, val y: Double, val r: Double, val c: Int) { var done = false }
    private var atoms = listOf<Atom>()
    private var left = 3
    /** When this round started (set on its first frame), and when it ended (null while playing). */
    private var start: Double? = null
    private var pause: Double? = null
    private var wrongAt = -1e9
    private var flyTo: Pt? = null
    private var ms = 0.0
    private var over = false

    private fun home(s: Stage) = polar(s, PI / 6, s.R - 18)

    /** How far along the phone's rounds this level plays. */
    private fun round(s: Stage) = s.level + ((s.dial - 1) / 99.0 * 4).toInt()

    override fun enter(s: Stage) {
        s.nu.r = 7.0
        s.nu.at(home(s))
        deal(s)
    }

    private fun deal(s: Stage) {
        val k = round(s)
        val n = min(MOST, 4 + k)
        val step = max(1.6, 4.5 - k * 0.35)
        val sizes = (0 until n).map { 9 + it * step }.shuffled()
        // The disc they sit in: off the menu at the top and the lives and counter at the bottom.
        val mid = Pt(s.c.x, s.c.y + 2)
        val room = s.R - 42
        val placed = mutableListOf<Atom>()
        for (r in sizes) {
            var p = mid
            for (t in 0 until 300) {
                val a = rnd() * TAU
                val d = sqrt(rnd()) * (room - r)
                p = Pt(mid.x + cos(a) * d, mid.y + sin(a) * d)
                if (placed.all { dist(it.x, it.y, p.x, p.y) > it.r + r + 16 } && dist(p, home(s)) > r + 20) break
            }
            placed += Atom(p.x, p.y, r, Ink.NOTES[floor(rnd() * 5).toInt()])
        }
        atoms = placed
        ms = (3500 + n * 700) * max(0.65, 1 - s.heat * 0.15)
        start = null
        pause = null
        flyTo = null
        s.rim = 1.0
        s.part = 0.0
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        s.nu.seek(flyTo ?: home(s), dt, 0.8, 0.03)
        if (over) { s.rim = 0.0; return }
        pause?.let { t -> if (now - t > BREATH_MS) deal(s); return }
        val begun = start ?: now.also { start = it }
        val left = 1 - (now - begun) / ms
        s.rim = clamp(left, 0.0, 1.0)
        if (left <= 0) miss(s, now)
    }

    private fun miss(s: Stage, now: Double) {
        left--
        wrongAt = now
        pause = now
        s.out.miss()
        s.out.buzz(22)
        if (left <= 0) { over = true; s.lose() }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val flash = now - wrongAt < 400
        for (a in atoms) {
            if (a.done) { pen.ring(a.x, a.y, a.r, Ink.K500, 2.0, 0.6); continue }
            pen.dot(a.x, a.y, a.r, if (flash) Ink.RED else a.c, if (over) 0.35 else 0.9)
        }
        lives(s, pen, left)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (over || pause != null) return
        val a = atoms.filter { !it.done && dist(it.x, it.y, p.x, p.y) < it.r + 10 }
            .minByOrNull { dist(it.x, it.y, p.x, p.y) } ?: return
        val next = atoms.filter { !it.done }.minBy { it.r }
        if (a !== next) {
            s.fx.ring(Pt(a.x, a.y), Ink.RED, a.r, a.r + 16, 500.0)
            miss(s, s.now)
            return
        }
        a.done = true
        flyTo = Pt(a.x, a.y)
        val k = atoms.count { it.done }
        // Up the chord as the atoms go in order.
        s.out.hit(k.toDouble() / atoms.size)
        s.fx.ring(Pt(a.x, a.y), a.c, a.r, a.r + 14, 400.0)
        s.part = k.toDouble() / atoms.size
        if (k == atoms.size) {
            pause = s.now
            s.clear()
            if (s.over) { over = true; return }
            if (s.level % 3 == 0 && left < 3) left++
        }
    }
}
