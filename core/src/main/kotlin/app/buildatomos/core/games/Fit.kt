package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Nu's shell, where its electrons sit, and how big they are. */
private const val SHELL = 36.0
private const val ER = 4.5
/** Where a ring comes in: inside the menu at the top and the counter at the bottom. */
private const val START = 104.0
private const val RING_W = 6.0
/** The wait before the first ring, and the breath between levels (ms). */
private const val FIRST_MS = 1500.0
private const val CALM_MS = 1200.0
/** A ring resolved stays on the screen this long, fading (ms). */
private const val FADE_MS = 320.0
/** The electrons sit on a clock face of this many steps round the shell (30 degrees each). */
private const val STEPS = 12

/**
 * Fit, made for the watch (the phone has no crown). Nu is a nucleus in the middle with a few
 * electrons on its shell. Rings close in on it from the edge of the court, one after another,
 * each with gaps in the same pattern as the electrons, but turned some other way. Turn the shell
 * with the crown so every electron sits in a gap by the time the ring reaches it, and the ring
 * slips past; an electron left in the way costs one of three lives. The ring due next shows grey,
 * and cyan while the electrons fit it (with a soft click as they come into place), so the turn
 * can be judged by eye before it arrives; the ring after it shows behind, fainter.
 * A level is a set of rings passed: four, and one more every other level, up to eight. Between
 * levels the shell takes a new set of electrons. Lives carry on from level to level, and one comes
 * back every third level.
 * The electrons sit on a clock face of twelve 30-degree steps, never closer than two steps (60
 * degrees; 90 for three, 120 for two), so a shape reads at a glance: where the spacing is uneven
 * it is uneven by a step or more, never by a sliver that takes a squint to see.
 * Heat brings more electrons (two at the gentlest, four at Expert, five past it), spaced unevenly
 * more and more often from a little past Very easy (always from Hard on), so only one turn fits;
 * narrower gaps (52 degrees at the gentlest, 31 at Expert, never under 24); quicker rings (4 s
 * from the edge at the gentlest, 2.2 s at Expert, never under 1.4 s), closer together, so from a
 * little past Medium two are on their way at once; and from Medium some rings that turn as they
 * come.
 * Every ring comes in turned well away from where the shell sits, so a run nobody plays loses a
 * life a ring and ends. A finger twisted round the screen turns the shell too.
 */
class Fit : Toy {
    /** The shell's turn, and where the crown or finger sends it. */
    private var a = -PI / 2
    private var want = -PI / 2
    /** The electrons' places round the shell, from its turn, sorted. */
    internal var pattern = doubleArrayOf(0.0, PI)
        private set
    internal class Ring(var phi: Double, val w: Double, val spin: Double, val born: Double, val ms: Double) {
        /** How far in it has come, 0 at the edge, 1 at the shell. */
        var t = 0.0
        var doneAt: Double? = null
        var ok = false
    }
    internal val rings = mutableListOf<Ring>()
    private var next = FIRST_MS
    private var passed = 0
    private var left = LIVES
    private var calm = 0.0
    /** The electrons fit the ring due next, as of the last frame. */
    private var fitting = false
    /** The finger's angle round the middle, while it twists. */
    private var grab: Double? = null
    private var over = false

    /** The shell's turn now: for the tests' player. */
    internal val shell: Double get() = a
    internal val sent: Double get() = want

    private fun need(s: Stage) = 4 + min(4, (s.level + 1) / 2)
    private fun rad(t: Double) = START - (START - SHELL) * t

    /** The ring due next, if one is on its way. */
    internal val lead: Ring? get() = rings.filter { it.doneAt == null }.maxByOrNull { it.t }

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.nu.at(s.c)
        deal(s.heat)
        s.rim = null
    }

    /** A new set of electrons for the shell: how many, and whether evenly spaced or not. */
    private fun deal(h: Double) {
        val k = electrons(h)
        val all = shapes(k)
        val uneven = all.filter { p -> p.any { it != p[0] } }
        val even = all.firstOrNull { p -> p.all { it == p[0] } }
        val pick = if (even != null && (uneven.isEmpty() || rnd() >= unevenShare(h))) even else uneven[(rnd() * uneven.size).toInt()]
        var at = 0.0
        pattern = DoubleArray(k) { i -> at.also { at += pick[i] * TAU / STEPS } }
    }

    /** True if, with the shell at `at`, every electron sits in a gap of a ring turned `phi`, gaps
     *  `w` wide, with `slack` more room either side. */
    private fun fits(at: Double, phi: Double, w: Double, slack: Double = 0.0): Boolean {
        val tol = w / 2 - 0.5 * ER / SHELL + slack
        return pattern.all { e -> pattern.any { g -> abs(turn(at + e, phi + g)) <= tol } }
    }

    /** Turns of the shell that fit `r` when it arrives, as it turns: for the tests' player. */
    internal fun aims(r: Ring): List<Double> {
        val phi = r.phi + r.spin * (1 - r.t) * r.ms
        return pattern.map { g -> phi + g - pattern[0] }.filter { fits(it, phi, r.w) }
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        a += (want - a) * (1 - exp(-dt / 35))
        s.nu.x = s.c.x
        s.nu.y = s.c.y
        rings.removeAll { d -> d.doneAt?.let { now - it > FADE_MS } ?: false }
        if (over) return
        if (calm > 0) {
            calm -= dt
            if (calm <= 0) { calm = 0.0; next = 300.0 }
            return
        }
        val h = s.heat
        for (r in rings) {
            if (r.doneAt != null) continue
            r.phi += r.spin * dt
            r.t = min(1.0, r.t + dt / r.ms)
        }
        // The click as the electrons come into place for the ring due next.
        val lead = lead
        val fitNow = lead != null && fits(a, lead.phi, lead.w)
        if (fitNow && !fitting) s.out.buzz(12)
        fitting = fitNow
        // Rings reaching the shell: through, or into the electrons.
        for (r in rings.filter { it.doneAt == null && it.t >= 1.0 }) {
            r.doneAt = now
            if (fits(a, r.phi, r.w)) {
                r.ok = true
                passed++
                for (e in pattern) s.fx.burst(polar(s, a + e, SHELL), 4, Ink.GREEN, 0.08)
                s.out.capture()
                s.part = clamp(passed.toDouble() / need(s), 0.0, 1.0)
                if (passed >= need(s)) {
                    passed = 0
                    s.fx.ring(s.c, Ink.GREEN, SHELL, START, 700.0)
                    s.clear()
                    if (s.over) { over = true; return }
                    if (s.level % 3 == 0) left = min(LIVES, left + 1)
                    // The rings still coming fade away, and the shell takes new electrons.
                    for (q in rings) if (q.doneAt == null) q.doneAt = now
                    deal(s.heat)
                    calm = CALM_MS
                    return
                }
            } else {
                left--
                for (e in pattern) {
                    val p = polar(s, a + e, SHELL)
                    if (pattern.none { g -> abs(turn(a + e, r.phi + g)) <= r.w / 2 }) s.fx.ring(p, Ink.RED, ER, ER + 16, 500.0)
                }
                s.out.miss()
                s.out.buzz(22)
                if (left <= 0) { over = true; s.lose(); return }
            }
        }
        next -= dt
        if (next <= 0) {
            val ms = travel(h)
            next = ms * spacing(h)
            val w = gap(h)
            val spin = if (rnd() < spinners(h)) (if (rnd() < 0.5) -1 else 1) * (0.00015 + 0.00035 * min(1.0, h)) * (0.7 + rnd() * 0.3) else 0.0
            // Turned well away from where the shell sits now, as it comes in and when it arrives,
            // so it always takes a turn.
            var phi = rnd() * TAU
            for (i in 0 until 40) {
                if (!fits(want, phi, w, 0.45) && !fits(want, phi + spin * ms, w, 0.45)) break
                phi = rnd() * TAU
            }
            rings += Ring(phi, w, spin, now, ms)
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val c = s.c
        val lead = lead
        for (r in rings) {
            val done = r.doneAt
            val f = if (done != null) clamp(1 - (now - done) / FADE_MS, 0.0, 1.0) else clamp((now - r.born) / 250, 0.0, 1.0)
            if (f <= 0) continue
            val (color, alpha) = when {
                done != null -> (if (r.ok) Ink.GREEN else Ink.RED) to 0.9
                r === lead -> (if (fitting) Ink.CYAN else Ink.K300) to 0.95
                else -> Ink.K500 to 0.75
            }
            val rr = if (done != null && r.ok) SHELL - 10 * (1 - f) else rad(r.t)
            val k = pattern.size
            for (j in 0 until k) {
                val g0 = r.phi + pattern[j] + r.w / 2
                val g1 = r.phi + pattern[(j + 1) % k] + (if (j == k - 1) TAU else 0.0) - r.w / 2
                if (g1 > g0) pen.arc(c.x, c.y, rr, g0, g1 - g0, color, RING_W, alpha * f)
            }
        }
        pen.ring(c, SHELL, Ink.K700, 2.0, 0.9)
        val dim = if (over && s.result == Result.LOST) 0.5 else 1.0
        if (calm <= 0 || calm < CALM_MS * 0.6) {
            val f = if (calm > 0) clamp(1 - calm / (CALM_MS * 0.6), 0.0, 1.0) else 1.0
            for (e in pattern) pen.dot(polar(s, a + e, SHELL), ER, Ink.WHITE, 0.95 * f * dim)
        }
        lives(s, pen, left, LIVES)
        s.nu.draw(pen, now)
    }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        grab = null
        want += by
    }

    override fun down(s: Stage, p: Pt) {
        if (over || dist(p, s.c) < 14) return
        grab = atan2(p.y - s.c.y, p.x - s.c.x)
    }

    override fun move(s: Stage, p: Pt) {
        val g = grab ?: return
        if (dist(p, s.c) < 14) return
        val to = atan2(p.y - s.c.y, p.x - s.c.x)
        want += turn(g, to)
        grab = to
    }

    override fun up(s: Stage, p: Pt) { grab = null }

    internal companion object {
        const val LIVES = 3
        /** Electrons on the shell: two at the gentlest, four at Expert, five past it. */
        fun electrons(heat: Double) = min(5, 2 + floor(max(0.0, heat) * 2.4).toInt())
        /** The fewest clock steps between two electrons next to each other, for `k` of them. */
        fun least(k: Int) = when (k) { 2 -> 4; 3 -> 3; else -> 2 }
        /** Every shape `k` electrons can make: the steps from each to the next, all the way round. */
        fun shapes(k: Int): List<IntArray> {
            val out = mutableListOf<IntArray>()
            fun go(done: List<Int>, left: Int) {
                if (done.size == k - 1) { if (left >= least(k)) out += (done + left).toIntArray(); return }
                for (n in least(k)..left - least(k) * (k - 1 - done.size)) go(done + n, left - n)
            }
            go(emptyList(), STEPS)
            return out
        }
        /** The share of shells spaced unevenly: none at the gentlest, all from Hard on. */
        fun unevenShare(heat: Double) = clamp((heat - 0.2) / 0.4, 0.0, 1.0)
        /** A gap's width (radians). */
        fun gap(heat: Double) = max(0.42, 0.95 - 0.45 * heat)
        /** A ring's time from the edge to the shell (ms). */
        fun travel(heat: Double) = max(1400.0, 4200 - 2200 * heat)
        /** The time between rings, as a share of a ring's travel: under 1, two are on their way. */
        fun spacing(heat: Double) = max(0.55, 1.25 - 0.5 * heat)
        /** The share of rings that turn as they come. */
        fun spinners(heat: Double) = clamp((heat - 0.4) * 0.8, 0.0, 0.6)
    }
}
