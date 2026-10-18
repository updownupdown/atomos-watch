package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** How long the light takes to fill the ring (ms), and to empty it again in the dark. */
private const val FILL = 1000.0
private const val EMPTY = 4000.0
/** How long a shade plate lasts with Nu under it (ms). */
private const val WEAR = 7500.0
/** The game's pace (the sweeps, the dots' time), as on the phone. */
private const val PACE = 0.8
/** A beam's half width at the start and at its widest (radians), and how far it swings each way. */
private const val BEAM = 0.13
private const val WIDE = 0.26
private const val SWING = 0.95
/** Nu's top speed (units/ms): slow, so a dash through a beam costs. The court is crossed in
 *  about two seconds, as the phone's arena is across its width. */
private const val SPEED = 0.14
/** A dot let run out fills this much of the ring, and it stays filled until the level is done. */
private const val SCORCH = 0.34
/** The phone's counts that bring another lamp (it starts with three). */
private val MORE = intArrayOf(5, 11, 18)
/** Where the lamps stand on the rim, in the order they come (degrees, clockwise from three
 *  o'clock): the sides first, then the four diagonals, clear of the menu and the level counter. */
private val AT = doubleArrayOf(180.0, 0.0, -50.0, 130.0, -130.0, 50.0)

/**
 * Shade (the phone's src/nu/heath.ts). Searchlights on the rim sweep slowly to and fro, each at
 * its own pace. Nu walks, slowly (a dash through a beam costs), to the green dots. In a beam, and
 * not under one of the grey shade plates, the ring round Nu fills, quickly; out of the light it
 * empties slowly. Full, and the run is over. A plate wears thin while Nu hides under it and grows
 * back once it's left, and each dot has its time on the rim, a generous one: wait for the gap,
 * then go. The dots sit well away from Nu, so each one is a route to plan.
 * A level is a set of dots: five, and one more every other level, up to eight. Heat brings what
 * the phone's count brought: more lamps (three, up to six), wider beams and quicker sweeps, and a
 * little less time for each dot.
 * On the watch the arena is the round court and the lamps stand on its rim, off the menu and the
 * level counter. Nu goes where the finger touches and keeps going there once it lifts, so a route
 * is a few taps (a plate, the next plate, the dot) and no finger has to sit on the screen over
 * the beams; sliding still leads Nu about. The crown isn't used: the route is a path in two
 * dimensions, and taps on the plates say it better than one turning wheel could.
 * A dot let run out fills the ring by a third, as on the phone, but on the watch that third
 * stays (a wider mark on the ring) until the level is done: three run out in a level and the run
 * is over, so a run nobody plays still ends.
 */
class Shade : Toy {
    internal class Lamp(val x: Double, val y: Double, val aim: Double, var phase: Double, val pace: Double)
    internal class Plate(val x: Double, val y: Double, val r: Double) {
        /** Worn thin while Nu hides under it, 0..1. */
        var wear = 0.0
        val size get() = r * (1 - 0.8 * wear)
    }
    internal val lamps = mutableListOf<Lamp>()
    internal val plates = mutableListOf<Plate>()
    internal var gem = Pt(0.0, 0.0)
        private set
    /** The dot's time left and its whole time (ms). */
    private var gemLeft = 0.0
    private var gemMs = 1.0
    /** Where Nu is walking to, if anywhere. */
    private var want: Pt? = null
    /** The ring round Nu: how much light it has taken, 0..1, and the part of it that stays. */
    internal var light = 0.0
        private set
    private var scorch = 0.0
    private var lit = false
    /** Dots this level. */
    private var got = 0
    private var over = false

    private fun need(s: Stage) = 5 + min(s.level, 6) / 2

    /** How far into the phone's run this is: its count of dots, which made it harder there,
     *  about 14 at Expert, and climbing with each dot toward the next level. */
    private fun depth(s: Stage) = (s.heat + HEAT_PER_LEVEL * s.part) * 14

    internal fun beam(s: Stage) = min(WIDE, BEAM + depth(s) * 0.004)
    internal fun sweep(s: Stage) = (0.0003 + min(depth(s), 30.0) * 0.00002) * PACE
    internal fun lampsFor(s: Stage) = 3 + MORE.count { depth(s) >= it }
    internal val speed get() = SPEED

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        layout(s)
    }

    private fun layout(s: Stage) {
        plates.clear()
        var tries = 0
        while (plates.size < 4 && tries++ < 600) {
            val r = 24 + rnd() * 8
            val p = anywhere(s, r + 16)
            // Off the menu and the counter, and apart from each other.
            if (abs(p.y - s.c.y) > s.R - r - 34) continue
            if (plates.any { dist(p.x, p.y, it.x, it.y) < r + it.r + 26 }) continue
            plates += Plate(p.x, p.y, r)
        }
        // Nu starts under the plate nearest the middle.
        val home = plates.minByOrNull { dist(it.x, it.y, s.c.x, s.c.y) }
        s.nu.at(if (home != null) Pt(home.x, home.y) else s.c.copy())
        while (lamps.size < lampsFor(s)) addLamp(s)
        newGem(s)
    }

    private fun addLamp(s: Stage) {
        val i = lamps.size
        val a = AT[i % AT.size] * PI / 180
        val p = polar(s, a, s.R - 6)
        // It starts swung away to one side, clear of the middle.
        val phase = (if (i % 2 == 1) 1 else -1) * PI / 2 + (rnd() - 0.5) * 0.6
        lamps += Lamp(p.x, p.y, atan2(s.c.y - p.y, s.c.x - p.x), phase, 0.75 + rnd() * 0.5)
    }

    private fun gemSpot(s: Stage): Pt {
        repeat(40) {
            val p = anywhere(s, 26.0)
            if (abs(p.y - s.c.y) < s.R - 40) return p
        }
        return s.c.copy()
    }

    private fun newGem(s: Stage) {
        // Well away from Nu, and not under a plate: the first spot far enough, else the farthest.
        val far = s.R * 0.9
        var p = gemSpot(s)
        var best = -1.0
        for (i in 0 until 60) {
            val q = gemSpot(s)
            if (plates.any { dist(it.x, it.y, q.x, q.y) < it.r + 6 }) continue
            val d = dist(q, s.nu.p)
            if (d > best) { p = q; best = d }
            if (d > far) break
        }
        gem = p
        // Time to wait for a gap: about twice what a straight walk would take, and a bit.
        val walk = dist(p, s.nu.p) / SPEED
        gemMs = max(7000.0, min(19000.0, 2 * walk + (9000 - depth(s) * 150) / PACE))
        gemLeft = gemMs
        s.rim = 1.0
    }

    /** Where a lamp's beam points `ahead` ms from now. */
    private fun angle(s: Stage, l: Lamp, ahead: Double = 0.0) = l.aim + sin(l.phase + sweep(s) * l.pace * ahead) * SWING

    private fun inBeam(s: Stage, p: Pt, ahead: Double = 0.0): Boolean {
        val b = beam(s)
        return lamps.any { l -> abs(turn(angle(s, l, ahead), atan2(p.y - l.y, p.x - l.x))) < b }
    }

    /** The plate Nu, `r` round, is under at `p`, if any. */
    private fun cover(p: Pt, r: Double) = plates.firstOrNull { dist(p.x, p.y, it.x, it.y) < it.size - r * 0.3 }

    /** Would Nu be in the light at `p`, `ahead` ms from now (the plates as they are now)? */
    internal fun litAt(s: Stage, p: Pt, ahead: Double = 0.0) = inBeam(s, p, ahead) && cover(p, s.nu.r) == null

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        if (over) { nu.seek(nu.p, dt); return }
        val w = sweep(s)
        for (l in lamps) l.phase += w * l.pace * dt
        val to = want
        if (to != null) {
            nu.seek(inside(s, to, nu.r + 2), dt, SPEED, 0.025)
            val p = inside(s, nu.p, nu.r)
            nu.x = p.x; nu.y = p.y
        } else nu.seek(nu.p, dt)
        // A plate wears thin while Nu hides under it, and slowly grows back once it's left.
        val under = cover(nu.p, nu.r)
        for (q in plates) q.wear = clamp(q.wear + (if (q === under) dt / WEAR else -dt / (2 * WEAR)), 0.0, 1.0)
        val bright = inBeam(s, nu.p) && under == null
        if (bright && !lit) { s.out.tick(1); s.out.buzz(8) }
        lit = bright
        light = clamp(light + (if (lit) dt / FILL else -dt / EMPTY), scorch, 1.0)
        if (light >= 1) { burn(s); return }
        // Each dot has its time, on the rim.
        gemLeft -= dt
        s.rim = clamp(gemLeft / gemMs, 0.0, 1.0)
        if (gemLeft <= 0) {
            scorch = min(1.0, scorch + SCORCH)
            light = min(1.0, light + SCORCH)
            s.fx.ring(gem, Ink.RED, 6.0, 30.0, 500.0)
            s.out.miss()
            if (light >= 1) { burn(s); return }
            newGem(s)
            return
        }
        if (dist(nu.p, gem) < nu.r + 9) {
            got++
            s.out.capture()
            s.fx.ring(gem, Ink.GREEN, 6.0, 30.0, 500.0)
            s.part = clamp(got.toDouble() / need(s), 0.0, 1.0)
            if (got >= need(s)) {
                got = 0
                s.clear()
                if (s.over) { over = true; s.rim = null; return }
                // The marks of dots let go heal; another lamp if the heat calls for one.
                scorch = 0.0
                while (lamps.size < min(AT.size, lampsFor(s))) addLamp(s)
            }
            newGem(s)
        }
    }

    /** The ring is full: the run is over. */
    private fun burn(s: Stage) {
        over = true
        want = null
        s.rim = null
        s.fx.ring(s.nu.p, Ink.RED, s.nu.r, s.nu.r + 34, 700.0)
        s.out.miss()
        s.out.buzz(24)
        s.lose()
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val nu = s.nu
        // The shade plates, under everything, as big as they are now.
        for (q in plates) { pen.dot(q.x, q.y, q.size, Ink.K500, 0.55); pen.ring(q.x, q.y, q.size, Ink.K300, 2.0, 0.35) }
        // The beams: a flat translucent red wedge, and a narrower, stronger one down its middle.
        val far = 2.5 * s.R
        val b = beam(s)
        val xs = DoubleArray(3)
        val ys = DoubleArray(3)
        for (l in lamps) {
            val a = angle(s, l)
            for ((half, alpha) in listOf(b to 0.14, b * 0.45 to 0.2)) {
                xs[0] = l.x; ys[0] = l.y
                xs[1] = l.x + cos(a - half) * far; ys[1] = l.y + sin(a - half) * far
                xs[2] = l.x + cos(a + half) * far; ys[2] = l.y + sin(a + half) * far
                pen.fill(xs, ys, 3, Ink.RED, alpha)
            }
        }
        for (l in lamps) { pen.dot(l.x, l.y, 6.0, Ink.RED, 0.95); pen.ring(l.x, l.y, 10.0, Ink.RED, 2.0, 0.5) }
        if (!over) {
            val pulse = 1 + 0.12 * sin(now / 220)
            pen.dot(gem, 6 * pulse, Ink.GREEN, 0.95)
            pen.ring(gem, 11 * pulse, Ink.GREEN, 2.0, 0.4)
        }
        // The ring round Nu: how much light it has taken, the part that stays a little wider.
        if (light > 0.005) {
            val r = nu.r + 9
            pen.ring(nu.x, nu.y, r, Ink.K500, 3.0, 0.6)
            if (scorch > 0) pen.arc(nu.x, nu.y, r, -PI / 2, TAU * min(scorch, light), Ink.RED, 5.0, 0.95)
            pen.arc(nu.x, nu.y, r, -PI / 2, TAU * light, Ink.RED, 3.0, 0.95)
        }
        nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) { if (!over) want = p.copy() }
    override fun move(s: Stage, p: Pt) { if (!over && want != null) want = p.copy() }
}
