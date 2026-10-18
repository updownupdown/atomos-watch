package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The climb is the phone's, shrunk to the watch: heights by this (so a hop takes the phone's
 *  time, its fall and push shrunk by as much), widths by about half. */
private const val K = 0.6
private const val G = 0.0016 * K
private const val JUMP = 0.8 * K
private const val SPRING = 1.15 * K
/** A pad holds Nu this many times; the next time it lands there, the pad gives way. */
private const val HOLDS = 3
/** How much higher up the phone's climb than the dial Hop plays: a notch, so Very easy plays as
 *  Easy first did. */
private const val NOTCH = 0.25

/**
 * Hop (the phone's src/nu/yard.ts). Nu hops up from pad to pad, and the view climbs with it;
 * steer it left and right. Cyan pads (filled) throw it higher; higher up, some pads drift along
 * a rail (white, with a hub), and the pads grow smaller and further apart.
 * A level is a climb to the green line above: land on a pad past it. The line is about twenty
 * pads up at first, and a little higher every level. Falling to the bottom costs one of three
 * lives (they carry on from level to level, and one comes back every third level): Nu is caught
 * by a pad at the bottom and hops on. Heat brings what the phone's height brought: smaller pads,
 * wider gaps, and from the first steps of Very easy the drifting pads. Hop plays a notch higher
 * up the phone's climb than the dial (`NOTCH`).
 * On the watch Nu climbs in the round court between two unseen walls set in from the sides, and
 * the crown steers it: turned clockwise it sends Nu left, anticlockwise right, a radian of crown
 * a third of the way across. A finger slid
 * anywhere still steers it too, as on the phone. Pads fade in through the rim at the top and out
 * at the bottom, over the level counter. And since a run nobody plays must end, a pad holds Nu
 * three times and gives way on the fourth: left alone, Nu hops in place on its pad until it falls.
 */
class Hop : Toy {
    internal class Pad(var x: Double, val y: Double, val r: Double, var vx: Double, val spring: Boolean) {
        var worn = 0
    }
    /** Pads and Nu in the climb's own space: y goes up as it goes negative; the view's top is at `cam`. */
    internal val pads = mutableListOf<Pad>()
    internal var x = 0.0
        private set
    internal var y = 0.0
        private set
    internal var vy = 0.0
        private set
    private var cam = 0.0
    /** Where this level's climb began, and its line. */
    private var start = 0.0
    private var line = 0.0
    /** The pad Nu last landed on. */
    internal var on: Pad? = null
        private set
    private var grab: Double? = null
    private var want: Double? = null
    /** Moving by the crown: `want` is where it sent Nu, dropped once Nu gets there. */
    private var turning = false
    private var left = 3
    private var over = false

    /** Half the room across, from the middle to the unseen walls. */
    private fun wall(s: Stage) = s.hw - 9
    /** How far Nu's middle runs either way from the court's middle. */
    internal fun run(s: Stage) = wall(s) - s.nu.r
    /** Where Nu is headed now, across. */
    internal val aim get() = want ?: x
    /** The bottom of the view: Nu down here is a fall. */
    private fun floor(s: Stage) = s.c.y + s.hh
    private fun height(s: Stage) = 1000.0 + min(s.level, 10) * 50

    /** How far up the phone's climb this is, in its pixels: the phone grew harder with height,
     *  900 up at the very start, about 4100 up at Expert, and climbing through each level toward
     *  the next. */
    private fun depth(s: Stage) = (s.heat + NOTCH + HEAT_PER_LEVEL * s.part) * 3600

    override fun enter(s: Stage) {
        s.nu.r = 8.0
        x = s.c.x
        y = s.c.y + 20
        start = y
        cam = 0.0
        vy = -0.6 * K
        val first = Pad(x, min(y + 30, floor(s) - 20), 13.0, 0.0, false)
        pads += first
        line = first.y - height(s)
        more(s)
        s.nu.at(Pt(x, y))
    }

    /** Pads up to a screen above the view. */
    private fun more(s: Stage) {
        val top = cam - s.hh * 2
        var last = pads.lastOrNull() ?: return
        while (last.y > top) {
            val d = depth(s)
            val gap = (60 + rnd() * min(110.0, 50 + d * 0.015)) * K
            val moving = d > 1000 && rnd() < 0.3
            val spring = !moving && rnd() < 0.1
            // Plain pads vary a little in size; springs and drifters keep the usual size. Pads
            // shrink with the depth: from 13 round to about 7 at Expert.
            val r = max(11.0, 20 - d * 0.0025) * 0.65 * (if (moving || spring) 1.0 else 0.85 + rnd() * 0.3)
            val px = s.c.x + (rnd() * 2 - 1) * (wall(s) - r - 6)
            val v = if (moving) (if (rnd() < 0.5) -1 else 1) * min(0.05, 0.03 + (d - 900) * 0.000004) else 0.0
            last = Pad(px, last.y - gap, r, v, spring)
            pads += last
        }
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        if (over) { nu.x = x; nu.y = y - cam; return }
        want?.let { w ->
            x += clamp(w - x, -0.45 * dt, 0.45 * dt)
            if (turning && abs(w - x) < 1e-3) { turning = false; want = null }
        }
        x = clamp(x, s.c.x - run(s), s.c.x + run(s))
        for (p in pads) {
            if (p.vx == 0.0) continue
            p.x += p.vx * dt
            val w = wall(s) - p.r
            if (abs(p.x - s.c.x) > w) { p.vx = -p.vx; p.x = clamp(p.x, s.c.x - w, s.c.x + w) }
        }
        // Two half steps, so a fast fall can't pass through a pad.
        for (k in 0..1) {
            val h = dt / 2
            val was = y
            vy += G * h
            y += vy * h
            if (vy <= 0) continue
            val p = pads.firstOrNull { q ->
                val top = q.y - q.r * 0.6 - nu.r
                was <= top && y >= top && abs(x - q.x) < q.r + nu.r * 0.6
            } ?: continue
            val at = Pt(p.x, p.y - cam)
            if (++p.worn > HOLDS) {
                // Worn through: it gives way, and Nu falls on.
                pads.remove(p)
                s.fx.burst(at, 6, Ink.K300, 0.08)
                s.out.buzz(10)
                continue
            }
            y = p.y - p.r * 0.6 - nu.r
            vy = -(if (p.spring) SPRING else JUMP)
            if (p.spring) { s.out.chime(3); s.fx.ring(at, Ink.CYAN, p.r, p.r + 16, 400.0) } else s.out.tick(1)
            nu.scale = 0.8
            landed(s, p)
            if (over) return
            break
        }
        nu.scale += (1 - nu.scale) * min(1.0, dt / 120)
        // The view climbs to keep Nu out of the top of the court.
        val high = s.c.y - s.hh * 0.25
        if (y - cam < high) cam = y - high
        pads.removeAll { it.y - cam > floor(s) + 60 }
        more(s)
        // Down to the bottom: a life, and a pad there to catch Nu.
        if (y - cam + nu.r >= floor(s)) fell(s)
        nu.x = x
        nu.y = y - cam
        nu.vx = 0.0
        nu.vy = 0.0
    }

    private fun landed(s: Stage, p: Pad) {
        on = p
        // Up past the line: the level is done.
        if (p.y <= line) {
            s.fx.ring(Pt(s.c.x, line - cam), Ink.GREEN, 10.0, 70.0, 700.0)
            s.clear()
            if (s.over) { over = true; return }
            if (s.level % 3 == 0 && left < 3) left++
            start = p.y
            line = p.y - height(s)
            s.part = 0.0
            return
        }
        s.part = clamp(max(s.part, (start - p.y) / (start - line)), 0.0, 1.0)
    }

    private fun fell(s: Stage) {
        val nu = s.nu
        left--
        s.fx.ring(Pt(x, floor(s) - nu.r), Ink.RED, nu.r, nu.r + 24, 500.0)
        s.out.miss()
        s.out.buzz(22)
        y = floor(s) - nu.r + cam
        if (left <= 0) {
            over = true
            grab = null
            want = null
            s.lose()
            return
        }
        // Caught at the bottom: a pad under Nu, and a hop on up.
        val catch = Pad(x, floor(s) - 26 + cam, 13.0, 0.0, false)
        pads += catch
        pads.sortByDescending { it.y }
        catch.worn = 1
        on = catch
        y = catch.y - catch.r * 0.6 - nu.r
        vy = -JUMP
        nu.scale = 0.8
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        // This level's line, across the room, once it's in view.
        val ly = line - cam
        if (ly > s.c.y - s.R + 10 && ly < floor(s)) {
            val w = wall(s)
            pen.line(s.c.x - w, ly, s.c.x + w, ly, Ink.GREEN, 2.0, 0.55)
        }
        for (p in pads) {
            val py = p.y - cam
            // In through the rim at the top, out over the counter at the bottom.
            val f = clamp((s.R - 2 - dist(p.x, py, s.c.x, s.c.y)) / 14, 0.0, 1.0) *
                clamp((floor(s) + 14 - py) / 24, 0.0, 1.0) * (1 - 0.18 * p.worn)
            if (f <= 0) continue
            if (p.vx != 0.0) {
                val w = wall(s) - p.r
                pen.line(s.c.x - w, py, s.c.x + w, py, Ink.K500, 3.0, 0.5 * f)
            }
            when {
                p.spring -> { pen.dot(p.x, py, p.r, Ink.CYAN, 0.35 * f); pen.ring(p.x, py, p.r - 1.5, Ink.CYAN, 3.0, 0.95 * f) }
                p.vx != 0.0 -> {
                    pen.dot(p.x, py, p.r, Ink.K800, 0.95 * f)
                    pen.ring(p.x, py, p.r - 1.5, Ink.WHITE, 3.0, 0.9 * f)
                    pen.dot(p.x, py, 3.0, Ink.WHITE, 0.9 * f)
                }
                else -> { pen.dot(p.x, py, p.r, Ink.K800, 0.95 * f); pen.ring(p.x, py, p.r - 1.5, Ink.K300, 3.0, 0.7 * f) }
            }
        }
        lives(s, pen, left)
        s.nu.draw(pen, now)
    }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        grab = null
        val from = if (turning) want ?: x else x
        // Clockwise sends Nu left.
        want = clamp(from - by * run(s) * 2 / 3, s.c.x - run(s), s.c.x + run(s))
        turning = true
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        turning = false
        grab = x - p.x
        want = x
    }

    override fun move(s: Stage, p: Pt) {
        grab?.let { g -> turning = false; want = p.x + g }
    }

    override fun up(s: Stage, p: Pt) {
        grab = null
        if (!turning) want = null
    }
}
