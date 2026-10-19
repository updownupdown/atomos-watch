package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The scroll speed at the start and at most (the phone's px/ms), and how far a section's pace
 *  can take it past the most. */
private const val V0 = 0.13
private const val V1 = 0.27
private const val TOP = 1.1
/** The room between gates (the phone's px). */
private const val GAP = 180.0
/** Court units per phone px down the course: the watch shows about this share of the phone's
 *  look ahead, so a gate takes as long to come up as it did there. */
private const val U = 0.45
/** Nu's line, as a share of the court's radius above its middle. */
private const val ROW = 0.55
/** Where the course comes into sight, as a share of the radius below the middle: over the level
 *  counter and the lives. */
private const val HORIZON = 0.75
/** How far a gate's middle counts as through: within its half width less this. */
private const val SLACK = 3.0
/** A post's radius and a red dot's. */
private const val POST = 5.5
private const val MINE = 6.0
/** How wide the bar across a gate is drawn. */
private const val BAR = 3.0
/** After a hit, nothing hurts for this long (Nu blinks). */
private const val SAFE_MS = 1200.0
private const val LIVES = 3
/** How much hotter than the dial the course runs: a notch, so Very easy plays as Easy first did. */
private const val NOTCH = 0.25

/**
 * Gate (the phone's src/nu/brook.ts). Slalom, Nu's way: Nu glides down while the course rolls up
 * past it. Each gate is two posts and a bar between them, the next one green. Through it
 * counts; past it outside the posts costs one of three lives, and so does a red dot. The course
 * comes in sections, each with its own pace and its own colour on the posts and a row of small
 * dots across its start: calm wide gates, rushes of quick close ones, wide swings from side to
 * side and tight slaloms between red dots. The gates come on one side or the other in runs of one to three, near the middle or
 * far out, and a gate on the same side as the last is far in where that one was far out (or the
 * other way round), so it still takes a swing.
 *
 * A level is a set of gates through: ten at first and one more each level, up to twenty. Then the
 * course goes on into a calm section, a breath before the next. The lives carry on from level to
 * level, and one comes back every third level. Heat is the phone's run going on: the course
 * faster, the gates narrower and swinging further out, more red dots, and soon some that drift.
 * It runs a notch hotter than the dial (`NOTCH`): Very easy plays like the phone's tenth gate or
 * so, Expert like its fiftieth (the first few gates of a run stay gentle all the same).
 *
 * On the watch Nu rides a line across the upper part of the round court, under the menu, and the
 * course comes into sight over a horizon low in the circle (fading in, above the level counter
 * and the lives), so it shows about two gates ahead, as long ahead as the phone does. The crown steers: a radian of crown is 0.4 of the way across. A finger slid
 * anywhere still steers too, as on the phone. Either way Nu follows a little behind and swings a
 * touch past where it's sent. Nothing is drawn under the menu, and the gates only show once in
 * through the rim. Left alone, Nu rides down the middle and misses the first wide swing.
 */
class Gate : Toy {
    private enum class Kind(val gap: Double, val w: Double, val off: Double, val v: Double, val mines: Double, val c: Int, val lo: Int, val hi: Int) {
        CALM(1.3, 1.2, 0.45, 0.8, 0.0, Ink.S, 3, 5),
        RUSH(0.72, 1.1, 0.45, 1.35, 0.35, Ink.CYAN, 5, 8),
        WIDE(1.25, 1.15, 1.6, 1.05, 0.6, Ink.F, 4, 6),
        TIGHT(0.95, 0.7, 0.55, 0.85, 1.2, Ink.D, 4, 6),
    }

    private enum class State { AHEAD, PASSED, MISSED }

    /** A gate: its place down the course (world units), its middle and width, its section's kind,
     *  and whether it starts that section. */
    private class Pair(val y: Double, val cx: Double, val w: Double, val kind: Kind, val first: Boolean) {
        var state = State.AHEAD
    }

    private class Mine(var x: Double, val y: Double, var vx: Double, val r: Double) {
        var hit = false
    }

    private val pairs = mutableListOf<Pair>()
    private val mines = mutableListOf<Mine>()
    /** How far down the course Nu is (world units). */
    private var at = 0.0
    private var x = 0.0
    private var vx = 0.0
    /** Where Nu is sent across: by the crown or the finger. */
    internal var tx = 0.0
        private set
    private var pace = Kind.CALM.v
    private var grab: Double? = null
    private var side = 1
    /** Gates still to come on this side before the course swings over, and how far out the last
     *  one was (a share of the room it had). */
    private var stay = 0
    private var lastOff = 0.0
    private var kind = Kind.CALM
    private var run = 0
    /** The next section is a calm one: a level was just cleared. */
    private var calm = false
    /** Gates laid this run: the first few make no red dots and no rushes or slaloms. */
    private var made = 0
    /** Gates through this level. */
    private var through = 0
    private var left = LIVES
    private var safeUntil = 0.0
    private var overAt = 0.0
    private var over = false

    private fun row(s: Stage) = s.c.y - s.R * ROW
    /** The screen's y for a place down the course. */
    private fun sy(s: Stage, y: Double) = row(s) + (y - at)
    /** How far either way Nu can go from the middle. */
    private fun span(s: Stage) = s.hw - s.nu.r - 4
    private fun need(s: Stage) = min(20, 10 + s.level)

    /** The phone's count of gates for the heat: about 46 at Expert, climbing toward the next level. */
    private fun depth(s: Stage) = (s.heat + NOTCH + HEAT_PER_LEVEL * s.part) * 40

    /** The course's speed before a section's pace, in the phone's px/ms: its start at the easiest,
     *  its most a little past Expert, and a little more on into free play. */
    internal fun speed(h: Double) = V0 + (V1 - V0) * min(1.0, (h + NOTCH) * 0.75) + min(0.04, max(0.0, h - 1.34) * 0.01)
    private fun cap(h: Double) = V1 * TOP + min(0.04, max(0.0, h - 1.34) * 0.01)

    /** A gate's width (court units) for a section's factor `k` at the phone's count `n`: never
     *  narrower than Nu and a little room either side. */
    internal fun width(s: Stage, n: Double, k: Double) =
        max(2 * s.nu.r + 14, min(s.hw * 0.8, s.hw * max(0.3, 0.62 - n * 0.007) * k))

    /** How likely a red dot is between two gates of a section with factor `k`. */
    internal fun mineChance(n: Double, k: Double) = min(0.9, (0.35 + n * 0.01) * k)

    override fun enter(s: Stage) {
        s.rim = null
        s.nu.r = 9.0
        x = s.c.x
        tx = x
        side = if (rnd() < 0.5) 1 else -1
        // The first section is a calm one, four gates long.
        kind = Kind.CALM
        run = 4
        s.nu.at(Pt(x, row(s)))
        grow(s)
    }

    /** The next section: never the same kind twice running; rushes and slaloms after a few gates,
     *  and a calm one to start each level. */
    private fun nextKind(calm: Boolean = false) {
        if (calm) kind = Kind.CALM
        else {
            val all = if (made < 6) listOf(Kind.CALM, Kind.WIDE) else Kind.entries
            val pick = all.filter { it != kind }
            kind = pick[floor(rnd() * pick.size).toInt().coerceAtMost(pick.size - 1)]
        }
        run = kind.lo + floor(rnd() * (kind.hi - kind.lo + 1)).toInt()
    }

    /** More gates below, as far as the court shows and a bit. */
    private fun grow(s: Stage) {
        val hw = s.hw
        val far = at + (horizon(s) - row(s)) + 60
        var last = pairs.lastOrNull()?.y ?: (at + 130)
        while (last < far) {
            val n = depth(s)
            val prev = pairs.lastOrNull()
            var first = false
            if (run <= 0) { nextKind(calm); calm = false; first = true }
            run--
            val k = kind
            // The gap before a gate is set by its own section, so a rush starts close.
            val y = last + GAP * U * k.gap
            val w = width(s, n, k.w)
            val reach = hw - w / 2 - 8
            // The sides come in runs of one to three, so the course can't be read as left, right,
            // left. A gate on the same side as the last is far in where that one was far out (and
            // the other way round), so it still takes a swing; wide swings always cross over.
            val same = stay > 0 && k != Kind.WIDE
            var f = min(0.95, (0.18 + min(0.4, n * 0.02) + rnd() * 0.45) * k.off)
            if (same) {
                val step = 0.4 + rnd() * 0.3
                f = if (lastOff + step <= 0.95 && (lastOff < 0.45 || lastOff - step < 0)) lastOff + step else max(0.0, lastOff - step)
                stay--
            } else {
                side = -side
                val roll = rnd()
                stay = if (roll < 0.55) 0 else if (roll < 0.85) 1 else 2
            }
            lastOff = f
            val cx = s.c.x + side * reach * f
            // A red dot between this gate and the last, from the fourth gate on; deeper in they
            // drift. On the same side it sits near the straight line between them, so Nu weaves.
            if (prev != null && made >= 3 && rnd() < mineChance(n, k.mines * if (same) 1.3 else 1.0)) {
                val mx = (prev.cx + cx) / 2 + (rnd() - 0.5) * hw * if (same) 0.2 else 0.5
                val v = if (n >= 12) (if (rnd() < 0.5) -1 else 1) * (0.03 + rnd() * 0.04) * 0.6 else 0.0
                mines += Mine(clamp(mx, s.c.x - hw + MINE + 10, s.c.x + hw - MINE - 10), (prev.y + y) / 2, v, MINE)
            }
            pairs += Pair(y, cx, w, k, first)
            made++
            last = y
        }
        val gone = at - (row(s) - s.c.y + s.R)
        pairs.removeAll { it.y < gone }
        mines.removeAll { it.y < gone }
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        if (over) {
            // The course coasts to a stop.
            at += speed(s.heat) * U * dt * max(0.0, 1 - (now - overAt) / 900)
            return
        }
        // The pace eases toward that of the section of the gate ahead.
        val ahead = pairs.firstOrNull { it.state == State.AHEAD }
        pace += ((ahead?.kind?.v ?: 1.0) - pace) * (1 - exp(-dt / 700))
        at += min(cap(s.heat), speed(s.heat) * pace) * U * dt
        // Nu swings toward where it's sent, a little behind, with a touch of overshoot.
        val w = 0.011
        val damp = 2 * 0.62 * w
        var rest = dt
        while (rest > 0) {
            val h = min(8.0, rest)
            vx += (w * w * (tx - x) - damp * vx) * h
            x += vx * h
            rest -= h
        }
        val lo = s.c.x - span(s)
        val hi = s.c.x + span(s)
        if (x < lo || x > hi) { x = clamp(x, lo, hi); vx = 0.0 }
        nu.x = x
        nu.y = row(s)
        for (m in mines) {
            m.x += m.vx * dt
            if (abs(m.x - s.c.x) > s.hw - m.r - 10) { m.vx = -m.vx; m.x = clamp(m.x, s.c.x - s.hw + m.r + 10, s.c.x + s.hw - m.r - 10) }
        }
        // Gates as Nu crosses them.
        for (p in pairs) {
            if (p.state != State.AHEAD || p.y > at) continue
            if (p.first) section(s, p)
            if (abs(x - p.cx) <= p.w / 2 - SLACK) {
                p.state = State.PASSED
                through++
                s.out.capture()
                // In a rush each gate gets an accent too, its pitch from where across Nu went through.
                if (p.kind == Kind.RUSH) s.out.tick((4 * clamp((x - (s.c.x - s.hw)) / (2 * s.hw), 0.0, 1.0)).toInt())
                s.fx.ring(nu.p, Ink.GREEN, nu.r, nu.r + 22, 500.0)
                s.part = clamp(through.toDouble() / need(s), 0.0, 1.0)
                if (through >= need(s)) { cleared(s); if (over) return; break }
            } else {
                p.state = State.MISSED
                val y = sy(s, p.y)
                s.fx.ring(Pt(p.cx - p.w / 2, y), Ink.RED, 6.0, 24.0, 500.0)
                s.fx.ring(Pt(p.cx + p.w / 2, y), Ink.RED, 6.0, 24.0, 500.0)
                hurt(s, now)
                if (over) return
            }
        }
        // Red dots.
        if (now >= safeUntil) {
            for (m in mines) {
                if (m.hit) continue
                val p = Pt(m.x, sy(s, m.y))
                if (dist(p, nu.p) > nu.r + m.r) continue
                m.hit = true
                s.fx.burst(p, 8, Ink.RED, 0.1)
                hurt(s, now)
                if (over) return
                break
            }
        }
        grow(s)
    }

    /** The level's gates are through: the course still to show goes, and a calm section comes. */
    private fun cleared(s: Stage) {
        through = 0
        s.fx.ring(s.nu.p, Ink.CYAN, s.nu.r + 4, s.hw * 0.9, 900.0)
        s.clear()
        if (s.over) { over = true; overAt = s.now; return }
        if (s.level % 3 == 0) left = min(LIVES, left + 1)
        val bottom = at + (horizon(s) - row(s)) + 4
        pairs.removeAll { it.state == State.AHEAD && it.y > bottom }
        mines.removeAll { it.y > (pairs.lastOrNull()?.y ?: at) }
        run = 0
        calm = true
    }

    /** Nu enters a new section: a wave in its colour and a chime for its kind. */
    private fun section(s: Stage, p: Pair) {
        val c = p.kind.c
        s.fx.ring(s.nu.p, c, s.nu.r + 4, s.hw * 0.7, 900.0)
        s.fx.burst(s.nu.p, 8, c, 0.12)
        s.out.chime(p.kind.ordinal + 1)
    }

    private fun hurt(s: Stage, now: Double) {
        left--
        safeUntil = now + SAFE_MS
        s.out.miss()
        s.out.buzz(20)
        if (left > 0) return
        over = true
        overAt = now
        grab = null
        s.fx.ring(s.nu.p, Ink.RED, s.nu.r, s.nu.r + 30, 700.0)
        s.lose()
    }

    /** Where the course comes into sight, low in the circle and above the level counter. */
    internal fun horizon(s: Stage) = s.c.y + s.R * HORIZON

    /** How much a thing on the screen shows: fading in over the horizon, and only inside the rim
     *  and below the menu. */
    private fun shows(s: Stage, x: Double, y: Double, r: Double): Double {
        if (dist(x, y, s.c.x, s.c.y) > s.R - r || y < s.c.y - s.R * 0.72) return 0.0
        return clamp((horizon(s) - y) / 24, 0.0, 1.0)
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val nu = s.nu
        val next = pairs.firstOrNull { it.state == State.AHEAD }
        // The start of each section: a row of small dots across in its colour.
        for (p in pairs) {
            if (!p.first) continue
            val y = sy(s, p.y - GAP * U * p.kind.gap * 0.3)
            val n = 9
            val a = if (p.state == State.AHEAD) 0.75 else 0.3
            for (i in 0 until n) {
                val dx = s.c.x - s.hw + (i + 0.5) / n * 2 * s.hw
                val f = shows(s, dx, y, 3.0)
                if (f > 0) pen.dot(dx, y, 2.4, p.kind.c, a * f)
            }
        }
        for (m in mines) {
            val y = sy(s, m.y)
            val f = shows(s, m.x, y, m.r)
            if (!m.hit && f > 0) pen.dot(m.x, y, m.r, Ink.RED, 0.95 * f)
        }
        for (p in pairs) {
            val y = sy(s, p.y)
            val ax = p.cx - p.w / 2
            val bx = p.cx + p.w / 2
            val isNext = p === next && !over
            val c = when {
                isNext -> Ink.GREEN
                p.state == State.PASSED -> Ink.WHITE
                p.state == State.MISSED -> Ink.RED
                else -> p.kind.c
            }
            val alpha = if (p.state == State.PASSED) 0.35 else 0.95
            val sa = shows(s, ax, y, POST)
            val sb = shows(s, bx, y, POST)
            bar(s, pen, ax + POST, bx - POST, y, c, if (p.state == State.PASSED) 0.14 else if (isNext) 0.6 else 0.42)
            if (sa > 0) pen.dot(ax, y, POST, c, alpha * sa)
            if (sb > 0) pen.dot(bx, y, POST, c, alpha * sb)
        }
        // Hit a moment ago: Nu blinks.
        nu.hidden = !over && now < safeUntil && floor(now / 110).toInt() % 2 == 0
        nu.draw(pen, now)
        nu.hidden = false
        lives(s, pen, left, LIVES)
    }

    /** The bar across a gate, between its posts' inner edges: solid and flat in the posts' colour,
     *  fainter than they are. Only the part inside the rim and below the menu shows, fading in
     *  over the horizon as the posts do. */
    private fun bar(s: Stage, pen: Pen, x0: Double, x1: Double, y: Double, c: Int, a: Double) {
        if (y < s.c.y - s.R * 0.72 || x1 <= x0) return
        val dy = y - s.c.y
        val room = (s.R - BAR) * (s.R - BAR) - dy * dy
        if (room <= 0) return
        val half = kotlin.math.sqrt(room)
        val lo = max(x0, s.c.x - half)
        val hi = min(x1, s.c.x + half)
        val f = clamp((horizon(s) - y) / 24, 0.0, 1.0)
        if (hi > lo && f > 0) pen.line(lo, y, hi, y, c, BAR, a * f)
    }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        grab = null
        tx = clamp(tx + by * span(s) * 0.8, s.c.x - span(s), s.c.x + span(s))
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        grab = tx - p.x
    }

    override fun move(s: Stage, p: Pt) {
        val g = grab ?: return
        tx = clamp(p.x + g, s.c.x - span(s), s.c.x + span(s))
    }

    override fun up(s: Stage, p: Pt) { grab = null }

    // For the tests: what a player sees.

    /** The gates still ahead, nearest first: their middle, screen y and width. */
    internal fun ahead(s: Stage): List<DoubleArray> =
        pairs.filter { it.state == State.AHEAD }.map { doubleArrayOf(it.cx, sy(s, it.y), it.w) }

    /** The red dots not yet hit, on the screen. */
    internal fun reds(s: Stage): List<Pt> = mines.filter { !it.hit }.map { Pt(it.x, sy(s, it.y)) }
}
