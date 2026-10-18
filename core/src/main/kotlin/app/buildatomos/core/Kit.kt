// The games' kit, ported from the phone's Nu park (src/nu/kit.ts): Nu as the games draw it, the
// soft effects, the round court and the helpers every game shares. Everything here is plain
// Kotlin with no Android in it, so the games can be run and tested anywhere; the app draws them
// through a Pen and plays their sounds and taps through Out.
//
// Units: the games work in "court units", the phone's CSS pixels. The watch's screen is SCREEN
// units across whatever its size, so a game ported from the phone keeps its numbers.

package app.buildatomos.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

const val TAU = PI * 2

/** The screen, in court units, and the court inside it (the rim keeps a little room outside). */
const val SCREEN = 300.0
const val COURT_R = 138.0

// The palette (the phone's src/ui/tokens.css), as ARGB.
object Ink {
    const val ROSE = 0xFFFF5AA8.toInt()
    const val CYAN = 0xFF6FD0E6.toInt()
    const val GREEN = 0xFF4FF29A.toInt()
    const val RED = 0xFFE0503A.toInt()
    const val AMBER = 0xFFE8B45C.toInt()
    const val WHITE = 0xFFE4E8EA.toInt()
    const val K200 = 0xFFC5CCD0.toInt()
    const val K300 = 0xFFA3ABAF.toInt()
    const val K400 = 0xFF7A8287.toInt()
    const val K500 = 0xFF50575B.toInt()
    const val K700 = 0xFF353A40.toInt()
    const val K800 = 0xFF2A2E32.toInt()
    const val K900 = 0xFF1F2225.toInt()
    const val K975 = 0xFF121416.toInt()
    const val BLACK = 0xFF000000.toInt()
    /** Orbital colours (s, p, d, f) and the accent: Echo's five atoms. */
    val NOTES = intArrayOf(0xFF5F8AE0.toInt(), 0xFF2EA57A.toInt(), 0xFFC07F1E.toInt(), 0xFFA865D6.toInt(), CYAN)
    val S = 0xFF5F8AE0.toInt()
    val P = 0xFF2EA57A.toInt()
    val D = 0xFFC07F1E.toInt()
    val F = 0xFFA865D6.toInt()
}

class Pt(var x: Double, var y: Double) {
    fun copy() = Pt(x, y)
    override fun toString() = "($x, $y)"
}

fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
fun dist(ax: Double, ay: Double, bx: Double, by: Double) = hypot(ax - bx, ay - by)
fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))
/** The shortest signed turn from angle `a` to angle `b`. */
fun turn(a: Double, b: Double) = atan2(sin(b - a), cos(b - a))
fun rnd() = Random.nextDouble()

/** A small seeded random (the phone's mulberry32), so a seeded layout is the same every time. */
fun seeded(seed: Int): () -> Double {
    var a = seed
    return {
        a += 0x6d2b79f5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }
}

/** What the games draw with. Angles in radians, clockwise from three o'clock (screen space). */
interface Pen {
    fun dot(x: Double, y: Double, r: Double, color: Int, alpha: Double = 1.0)
    fun ring(x: Double, y: Double, r: Double, color: Int, width: Double = 2.0, alpha: Double = 1.0)
    /** An arc with round caps, from `start` turning `sweep` (positive = clockwise). */
    fun arc(x: Double, y: Double, r: Double, start: Double, sweep: Double, color: Int, width: Double = 2.0, alpha: Double = 1.0)
    fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double = 2.0, alpha: Double = 1.0)
    /** An open line through the first `n` points, round caps and joins (one stroke, so a
     *  translucent line doesn't darken where its pieces meet). */
    fun poly(xs: DoubleArray, ys: DoubleArray, n: Int, color: Int, width: Double = 2.0, alpha: Double = 1.0)
    /** A ring in `n` even dashes, so the join doesn't show. */
    fun dashed(x: Double, y: Double, r: Double, color: Int, width: Double = 3.0, alpha: Double = 1.0, n: Int = 0)
    fun rect(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, alpha: Double = 1.0)
    fun rectRing(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, width: Double = 2.0, alpha: Double = 1.0)
}

fun Pen.dot(p: Pt, r: Double, color: Int, alpha: Double = 1.0) = dot(p.x, p.y, r, color, alpha)
fun Pen.ring(p: Pt, r: Double, color: Int, width: Double = 2.0, alpha: Double = 1.0) = ring(p.x, p.y, r, color, width, alpha)
fun Pen.dashed(p: Pt, r: Double, color: Int, alpha: Double = 1.0) = dashed(p.x, p.y, r, color, 3.0, alpha)

/** A flat, soft glow round a dot: a few wider dots of the same color, faint. No gradients. */
fun Pen.glow(x: Double, y: Double, r: Double, color: Int, strength: Double = 1.0) {
    if (strength <= 0) return
    dot(x, y, r * 2.1, color, 0.07 * strength)
    dot(x, y, r * 1.55, color, 0.14 * strength)
}

/** The game's sounds and taps on the wrist. Sounds are off unless the player turns them on; the
 *  app decides. Every call is a hint; none of it changes the game. */
interface Out {
    /** A catch: the tune's next note, a light tick. */
    fun capture() {}
    /** A hit pitched by `x` (0..1). */
    fun hit(x: Double) {}
    /** A miss: a sigh and a soft double tap. */
    fun miss() {}
    /** A tick of the clock or a step (n counts them). */
    fun tick(n: Int) {}
    /** One of a set of notes (Echo's atoms): i from 0. */
    fun chime(i: Int) {}
    /** A level done. */
    fun complete() {}
    /** A bump, in ms of the phone's vibration; the app turns it into a light tap. */
    fun buzz(ms: Int) {}
}

// ---- Nu ----------------------------------------------------------------------------------------

class Body {
    var x = 0.0
    var y = 0.0
    var vx = 0.0
    var vy = 0.0
    var r = 9.0
    /** How much it glows, 1 at rest. */
    var glow = 1.0
    /** A squash for hops and bumps, 1 at rest. */
    var scale = 1.0
    var hidden = false
    private val hist = ArrayDeque<DoubleArray>()

    val p: Pt get() = Pt(x, y)

    fun at(p: Pt) { x = p.x; y = p.y; vx = 0.0; vy = 0.0; hist.clear() }

    /** Steers toward `to` with a top speed (units/ms) and slows as it arrives: no jumps, no jerks. */
    fun seek(to: Pt, dt: Double, top: Double = 0.5, ease: Double = 0.006) {
        val dx = to.x - x
        val dy = to.y - y
        val d = hypot(dx, dy)
        val want = min(top, d * ease * 1.6)
        val tx = if (d > 0.01) dx / d * want else 0.0
        val ty = if (d > 0.01) dy / d * want else 0.0
        val k = 1 - exp(-dt / 90)
        vx += (tx - vx) * k
        vy += (ty - vy) * k
        x += vx * dt
        y += vy * dt
    }

    fun draw(pen: Pen, now: Double) {
        if (hidden) return
        hist.addFirst(doubleArrayOf(x, y, now))
        while (hist.size > 2 && now - hist.last()[2] > 170) hist.removeLast()
        for (i in 1 until hist.size) {
            val f = 1 - i.toDouble() / hist.size
            val a = hist[i - 1]
            val b = hist[i]
            pen.line(a[0], a[1], b[0], b[1], Ink.ROSE, r * 0.9 * f + 0.6, f * 0.9)
        }
        pen.glow(x, y, r * scale, Ink.ROSE, glow)
        pen.dot(x, y, r * scale, Ink.ROSE)
    }
}

/** Soft things that come and go: rings that spread and sparks that drift. */
class Fx(private val clock: () -> Double) {
    private class Spread(val x: Double, val y: Double, val t0: Double, val c: Int, val r0: Double, val r1: Double, val ms: Double)
    private class Spark(var x: Double, var y: Double, var vx: Double, var vy: Double, val t0: Double, val ms: Double, val c: Int)
    private val rings = mutableListOf<Spread>()
    private val sparks = mutableListOf<Spark>()

    fun ring(p: Pt, c: Int = Ink.ROSE, r0: Double = 10.0, r1: Double = 46.0, ms: Double = 900.0) {
        rings += Spread(p.x, p.y, clock(), c, r0, r1, ms)
    }

    fun burst(p: Pt, n: Int = 8, c: Int = Ink.ROSE, speed: Double = 0.12) {
        val now = clock()
        for (i in 0 until n) {
            val a = i.toDouble() / n * TAU + rnd() * 0.5
            val s = speed * (0.6 + rnd() * 0.6)
            sparks += Spark(p.x, p.y, cos(a) * s, sin(a) * s - 0.03, now, 700 + rnd() * 400, c)
        }
    }

    fun clear() { rings.clear(); sparks.clear() }

    fun draw(pen: Pen, now: Double, dt: Double) {
        rings.removeAll { now - it.t0 >= it.ms }
        sparks.removeAll { now - it.t0 >= it.ms }
        for (r in rings) {
            val t = clamp((now - r.t0) / r.ms, 0.0, 1.0)
            pen.ring(r.x, r.y, r.r0 + (r.r1 - r.r0) * (1 - (1 - t) * (1 - t)), r.c, 2.0, 0.55 * (1 - t))
        }
        for (s in sparks) {
            val t = clamp((now - s.t0) / s.ms, 0.0, 1.0)
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.vx *= 0.985
            s.vy *= 0.985
            pen.dot(s.x, s.y, 2.4 * (1 - t * 0.5), s.c, 1 - t)
        }
    }
}

// ---- The court ---------------------------------------------------------------------------------

/** The most levels a run can clear: free play has no goal, so this is where it stops. Games grow
 *  harder with every level (see `heat`), so the last ones are very hard to reach. */
const val MAX_LEVEL = 255

/** How much harder a game plays with every level cleared, on the 0..1 scale of `heat`. */
const val HEAT_PER_LEVEL = 0.05

enum class Result { WON, LOST }

/**
 * Where a game plays: the round court filling the watch's screen. `c` is its middle and `R` its
 * radius, both in court units. The shell (the app) owns the stage: it keeps the clock, draws the
 * rim, the levels and the menu, and shows the card at the end.
 *
 * A run is played in levels. The game calls `clear` when the player finishes one and `lose` when
 * they fail; a run for an atom is won at its `goal`, free play at MAX_LEVEL. The game reads how
 * hard to play from `heat`.
 */
class Stage(val out: Out = object : Out {}) {
    val c = Pt(SCREEN / 2, SCREEN / 2)
    val R = COURT_R
    /** The screen's own middle (the same as the court's on a watch). */
    val m get() = c
    /** Half the screen, for games ported from the phone's rectangular arena: the square in the circle. */
    val hw = R * 0.72
    val hh = R * 0.72
    var now = 0.0
    val nu = Body()
    val fx = Fx { now }
    /** The rim's countdown, 0..1 left, or null for none. */
    var rim: Double? = null
    /** A level is done and the next waits for the player: the shell shows Next, which calls this. */
    var waiting: (() -> Unit)? = null

    /** How hard to play, 1..100: the phone's dial (Difficulty). Set before `enter`. */
    var dial = 30
    /** Levels to clear to win: a run for an atom has one; free play has none. Set before `enter`. */
    var goal: Int? = null
    /** Levels cleared so far. */
    var level = 0
        private set
    /** How far into the level being played, 0..1, for the shell's counter. The game sets it. */
    var part = 0.0
    /** Won or lost, once the run is over. */
    var result: Result? = null
        private set
    val over get() = result != null

    /** How hard the game should play now: 0 at the dial's easiest, 1 at its hardest, and a little
     *  more with every level cleared, without end. Games turn this into speeds and counts. */
    val heat: Double get() = (dial - 1) / 99.0 + level * HEAT_PER_LEVEL

    /** The player finished a level. Wins the run at the goal. */
    fun clear() {
        if (over) return
        level++
        part = 0.0
        out.complete()
        if (level >= (goal ?: MAX_LEVEL)) result = Result.WON
    }

    /** The player failed: the run is lost. */
    fun lose() { if (!over) result = Result.LOST }

    fun prompt(go: () -> Unit) { waiting = go }
}

/** How far `p` is outside the court's edge (negative inside), and the way out there. */
fun edge(s: Stage, p: Pt): Triple<Double, Double, Double> {
    val dx = p.x - s.c.x
    val dy = p.y - s.c.y
    val d = hypot(dx, dy).takeIf { it > 0 } ?: 1.0
    return Triple(d - s.R, dx / d, dy / d)
}

/** Keeps a moving thing inside the court, bouncing it off the rim. True if it bounced. */
fun bounceIn(s: Stage, o: Mover, r: Double, k: Double = 0.8): Boolean {
    val (d, nx, ny) = edge(s, Pt(o.x, o.y))
    val out = d + r
    if (out <= 0) return false
    o.x -= nx * out
    o.y -= ny * out
    val vn = o.vx * nx + o.vy * ny
    if (vn > 0) { o.vx -= (1 + k) * vn * nx; o.vy -= (1 + k) * vn * ny }
    return true
}

/** A point pulled inside the court. */
fun inside(s: Stage, p: Pt, r: Double): Pt {
    val (d, nx, ny) = edge(s, p)
    val out = d + r
    return if (out <= 0) p else Pt(p.x - nx * out, p.y - ny * out)
}

/** A random point in the court, at least `r` in from its edge. */
fun anywhere(s: Stage, r: Double, rand: () -> Double = ::rnd): Pt {
    repeat(40) {
        val p = Pt(s.c.x + (rand() * 2 - 1) * s.R, s.c.y + (rand() * 2 - 1) * s.R)
        if (edge(s, p).first < -r) return p
    }
    return s.c.copy()
}

/** A point at angle `a` and radius `r` from the court's middle. */
fun polar(s: Stage, a: Double, r: Double) = Pt(s.c.x + cos(a) * r, s.c.y + sin(a) * r)

open class Mover(var x: Double, var y: Double, var vx: Double = 0.0, var vy: Double = 0.0)

/** Lives left, as small dots along the bottom of the rim, to the left of the level counter (the
 *  top is the menu's). */
fun lives(s: Stage, pen: Pen, left: Int, of: Int = 3) {
    for (i in 0 until of) {
        val a = PI / 2 + (20 + i * 9) * PI / 180
        val x = s.c.x + cos(a) * (s.R - 6)
        val y = s.c.y + sin(a) * (s.R - 6)
        if (i < left) pen.dot(x, y, 4.0, Ink.WHITE, 0.85) else pen.ring(x, y, 4.0, Ink.K500, 2.0, 0.8)
    }
}

/**
 * One of Nu's games. The shell makes a fresh one for each run, calls `enter`, then `step` and
 * `draw` every frame, and passes on touches and the crown. A game plays in levels: it calls
 * `s.clear()` at the end of each and `s.lose()` when the player fails, reads `s.heat` for how hard
 * to play, and sets `s.part` to show how far into the level the player is. It never restarts
 * itself; the shell shows the card at the end, and Again makes a fresh game.
 */
interface Toy {
    fun enter(s: Stage)
    fun step(s: Stage, dt: Double, now: Double)
    fun draw(s: Stage, pen: Pen, now: Double)
    fun down(s: Stage, p: Pt) {}
    fun move(s: Stage, p: Pt) {}
    fun up(s: Stage, p: Pt) {}
    /** The crown (or a turning bezel) turned by `by` radians, clockwise positive. */
    fun crown(s: Stage, by: Double) {}
}
