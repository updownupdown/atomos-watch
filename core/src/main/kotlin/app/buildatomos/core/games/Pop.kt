package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** An atom's radius, and the rows' height in the cluster's honeycomb. */
private const val AR = 10.0
private val ROW_H = AR * sqrt(3.0)
/** The honeycomb's slots across (most are outside the circle) and down (the last are past the line). */
private const val COLS = 15
private const val ROWS = 12
/** The top row's height, under the menu, and the line the cluster mustn't reach. */
private const val TOP = 40.0
private const val LINE = 210.0
/** A shot's speed (units/ms) and the small steps it moves in (ms). */
private const val SPEED = 0.55
private const val SUB = 4.0
/** How far the aim swings either side of straight up (rad). */
private const val SWING = 1.25
/** The share of the shot clock left when its small ring round the next atom shows. */
private const val CUE = 0.4
/** The atoms' four colours: blue, green, yellow and violet, far apart so none is taken for
 *  another on the small screen. */
private val COLOURS = intArrayOf(0xFF4F8FF0.toInt(), 0xFF3FCF86.toInt(), 0xFFF2CC48.toInt(), 0xFFC266EC.toInt())

/**
 * Pop (the phone's src/nu/grove.ts). A cluster of atoms in four colours hangs from the top; Nu
 * waits at the bottom with the next atom and fires it up at them. It sticks where it lands; three
 * or more of a colour together pop, and whatever then no longer hangs from the top row falls.
 * Every few shots the cluster comes down a row, and if it reaches the line above Nu the run is
 * lost. Clear the cluster and four fresh rows come in.
 * A level is a number of atoms popped or dropped: 40, and four more each level, up to 80; the
 * cluster carries on from level to level. Heat starts the cluster a row deeper (four rows from
 * the middle of the dial, three below), brings it down in fewer shots (every nine at the easiest,
 * every six at Expert, down to every four past it) and shortens the shot clock.
 * On the watch the crown aims: turn it to swing the short aim guide from Nu, and tap anywhere to
 * fire. A finger dragged on the screen aims at itself too, and fires when it lets go. The
 * cluster is a honeycomb clipped to the round court, a dome under the menu, and shots glance off
 * the rim. A shot clock runs, quietly: for its last part a small ring round the next atom, by Nu,
 * empties, and if it runs out Nu fires by itself where it aims, so a run nobody plays fills up
 * and ends. The colours are the watch's own (blue, green, yellow, violet), further apart than
 * the phone's orbital ones.
 */
class Pop : Toy {
    /** The colour in each slot, -1 for none. */
    private val grid = Array(ROWS) { IntArray(COLS) { -1 } }
    /** Which rows sit half a step over: flips as rows come in at the top. */
    private var shift = 0
    private var load = 0
    private var aim = -PI / 2
    private class Shot(x: Double, y: Double, vx: Double, vy: Double, val c: Int) : Mover(x, y, vx, vy)
    private var shot: Shot? = null
    private class Drop(val x: Double, var y: Double, var vy: Double, val c: Int)
    private val drops = mutableListOf<Drop>()
    private var shots = 0
    /** Atoms popped or dropped this level. */
    private var count = 0
    /** How long Nu has waited with an atom ready (ms). */
    private var wait = 0.0
    private var over = false
    private var spare = 0.0
    private var press: Pt? = null
    private var dragging = false

    private fun home(s: Stage) = Pt(s.c.x, s.c.y + 92)
    private fun need(s: Stage) = 40 + min(s.level, 10) * 4
    private fun every(s: Stage) = clamp(9 - s.heat * 3, 4.0, 9.0).roundToInt()
    private fun limit(s: Stage) = clamp(6500 - s.heat * 2500, 3000.0, 6500.0)
    private fun colour(c: Int) = COLOURS[c]

    private fun off(j: Int) = if ((j + shift) % 2 == 1) AR else 0.0
    private fun x(s: Stage, j: Int, i: Int) = s.c.x + (i - (COLS - 1) / 2.0) * 2 * AR + off(j) - AR / 2
    private fun y(j: Int) = TOP + j * ROW_H
    /** A slot inside the round court. */
    private fun valid(s: Stage, j: Int, i: Int) =
        j in 0 until ROWS && i in 0 until COLS && dist(x(s, j, i), y(j), s.c.x, s.c.y) <= s.R - AR - 1
    private fun has(j: Int, i: Int) = j in 0 until ROWS && i in 0 until COLS && grid[j][i] >= 0

    private fun around(j: Int, i: Int): List<IntArray> {
        val o = if (off(j) > 0) 1 else 0
        return listOf(
            intArrayOf(j, i - 1), intArrayOf(j, i + 1),
            intArrayOf(j - 1, i - 1 + o), intArrayOf(j - 1, i + o),
            intArrayOf(j + 1, i - 1 + o), intArrayOf(j + 1, i + o),
        )
    }

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.nu.at(home(s))
        repeat(if (s.heat < 0.5) 3 else 4) { addRow(s) }
        load = pick()
    }

    /** The next atom: a colour still in the cluster. */
    private fun pick(): Int {
        val have = grid.flatMap { r -> r.filter { it >= 0 } }.distinct()
        return if (have.isEmpty()) (rnd() * 4).toInt().coerceIn(0, 3) else have[(rnd() * have.size).toInt().coerceIn(0, have.size - 1)]
    }

    /** A new row at the top, pushing the cluster down. */
    private fun addRow(s: Stage) {
        for (j in ROWS - 1 downTo 1) grid[j] = grid[j - 1]
        grid[0] = IntArray(COLS) { -1 }
        shift = (shift + 1) % 2
        for (j in 0 until ROWS) for (i in 0 until COLS) if (grid[j][i] >= 0 && !valid(s, j, i)) grid[j][i] = -1
        for (i in 0 until COLS) if (valid(s, 0, i)) grid[0][i] = (rnd() * 4).toInt().coerceIn(0, 3)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        nu.seek(home(s), dt, 0.6, 0.04)
        for (d in drops) { d.vy += 0.0012 * dt; d.y += d.vy * dt }
        drops.removeAll { it.y > s.c.y + s.R + 20 }
        if (over) return
        val q = shot
        if (q == null) {
            wait += dt
            if (wait >= limit(s)) fire(s)
            spare = 0.0
            return
        }
        spare += dt
        while (spare >= SUB && shot != null) {
            spare -= SUB
            fly(s, q)
        }
    }

    private fun fly(s: Stage, q: Shot) {
        q.x += q.vx * SUB
        q.y += q.vy * SUB
        if (q.y <= TOP) { stick(s, q); return }
        for (j in 0 until ROWS) for (i in 0 until COLS) {
            if (grid[j][i] >= 0 && dist(x(s, j, i), y(j), q.x, q.y) < AR * 1.8) { stick(s, q); return }
        }
        if (edge(s, Pt(q.x, q.y)).first + AR > 0) {
            // High on the dome it sticks to the rim; lower down it glances off.
            if (q.y < TOP + ROW_H) { stick(s, q); return }
            bounceIn(s, q, AR, 1.0)
            s.out.tick(0)
        }
        if (q.y > home(s).y + 30) { shot = null; done(s) }
    }

    private fun fire(s: Stage) {
        if (over || shot != null) return
        val nu = s.nu
        shot = Shot(nu.x, nu.y, cos(aim) * SPEED, sin(aim) * SPEED, load)
        wait = 0.0
        s.out.tick(2)
    }

    /** The shot sticks in the nearest free slot; then pops and falls. */
    private fun stick(s: Stage, q: Shot) {
        shot = null
        var bj = -1
        var bi = -1
        var bd = Double.MAX_VALUE
        for (j in 0 until ROWS) for (i in 0 until COLS) {
            if (grid[j][i] >= 0 || !valid(s, j, i)) continue
            val d = dist(x(s, j, i), y(j), q.x, q.y)
            if (d < bd) { bd = d; bj = j; bi = i }
        }
        if (bj < 0) { done(s); return }
        grid[bj][bi] = q.c
        s.out.tick(1)
        // The same colour touching.
        val same = mutableSetOf(bj * COLS + bi)
        val todo = ArrayDeque(listOf(intArrayOf(bj, bi)))
        while (todo.isNotEmpty()) {
            val (j, i) = todo.removeLast()
            for ((a, b) in around(j, i)) if (has(a, b) && grid[a][b] == q.c && same.add(a * COLS + b)) todo += intArrayOf(a, b)
        }
        var popped = 0
        if (same.size >= 3) {
            for (k in same) {
                val j = k / COLS; val i = k % COLS
                s.fx.burst(Pt(x(s, j, i), y(j)), 4, colour(q.c), 0.08)
                grid[j][i] = -1
            }
            popped += same.size
        }
        // Whatever no longer hangs from the top row falls.
        val held = mutableSetOf<Int>()
        val go = ArrayDeque<IntArray>()
        for (i in 0 until COLS) if (grid[0][i] >= 0) { held += i; go += intArrayOf(0, i) }
        while (go.isNotEmpty()) {
            val (j, i) = go.removeLast()
            for ((a, b) in around(j, i)) if (has(a, b) && held.add(a * COLS + b)) go += intArrayOf(a, b)
        }
        for (j in 0 until ROWS) for (i in 0 until COLS) {
            if (grid[j][i] < 0 || (j * COLS + i) in held) continue
            drops += Drop(x(s, j, i), y(j), 0.0, grid[j][i])
            grid[j][i] = -1
            if (popped > 0) popped++
        }
        if (popped > 0) {
            count += popped
            s.out.chime(min(4, popped - 3).coerceAtLeast(0))
            if (popped >= 6) s.out.buzz(12)
        }
        done(s)
    }

    /** After every shot: the cluster may come down, the level may be done, or the run lost. */
    private fun done(s: Stage) {
        shots++
        if (grid.all { r -> r.all { it < 0 } }) { s.fx.ring(Pt(s.c.x, TOP + 30), Ink.CYAN, 10.0, 60.0); repeat(4) { addRow(s) } }
        else if (shots % every(s) == 0) addRow(s)
        if (count >= need(s)) {
            count = 0
            s.clear()
            if (s.over) { over = true; return }
        }
        s.part = clamp(count.toDouble() / need(s), 0.0, 1.0)
        load = pick()
        // Down to the line?
        for (j in 0 until ROWS) for (i in 0 until COLS) if (grid[j][i] >= 0 && y(j) + AR > LINE) {
            over = true
            s.fx.ring(Pt(x(s, j, i), y(j)), Ink.RED, AR, AR + 24, 700.0)
            s.out.miss()
            s.out.buzz(26)
            s.lose()
            return
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        // The line it mustn't reach, red once the cluster is a row away.
        var low = 0.0
        for (j in 0 until ROWS) if (grid[j].any { it >= 0 }) low = y(j) + AR
        val half = sqrt(max(0.0, s.R * s.R - (LINE - s.c.y) * (LINE - s.c.y))) - 10
        val near = !over && low + ROW_H > LINE
        pen.line(s.c.x - half, LINE, s.c.x + half, LINE, if (near) Ink.RED else Ink.K500, 2.0, if (near) 0.5 + 0.2 * sin(now / 90) else 0.6)
        for (j in 0 until ROWS) for (i in 0 until COLS) if (grid[j][i] >= 0) pen.dot(x(s, j, i), y(j), AR - 1, colour(grid[j][i]), if (over) 0.4 else 0.95)
        for (d in drops) pen.dot(d.x, d.y, AR - 1, colour(d.c), 0.6)
        val nu = s.nu
        shot?.let { pen.dot(it.x, it.y, AR - 1, colour(it.c)) }
        if (shot == null && !over) {
            // The aim guide: a short line from Nu.
            val dx = cos(aim); val dy = sin(aim)
            pen.line(nu.x + dx * (nu.r + 6), nu.y + dy * (nu.r + 6), nu.x + dx * 60, nu.y + dy * 60, Ink.WHITE, 2.0, 0.45)
            pen.dot(nu.x + 20, nu.y + 4, AR - 1, colour(load))
            // The shot clock's last part: a small ring round the next atom, emptying.
            val left = 1 - wait / limit(s)
            if (left < CUE) pen.arc(nu.x + 20, nu.y + 4, AR + 3, -PI / 2, TAU * clamp(left / CUE, 0.0, 1.0), Ink.K300, 2.5, 0.7)
        }
        nu.draw(pen, now)
    }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        aim = clamp(aim + by * 0.8, -PI / 2 - SWING, -PI / 2 + SWING)
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        press = p
        dragging = false
    }

    override fun move(s: Stage, p: Pt) {
        val at = press ?: return
        if (!dragging && dist(p, at) > 10) dragging = true
        if (!dragging) return
        val nu = s.nu
        if (p.y < nu.y - 6) aim = clamp(atan2(p.y - nu.y, p.x - nu.x), -PI / 2 - SWING, -PI / 2 + SWING)
    }

    override fun up(s: Stage, p: Pt) {
        if (press == null) return
        move(s, p)
        press = null
        dragging = false
        fire(s)
    }
}
