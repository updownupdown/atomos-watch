package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The biggest rank; two of these fuse into nothing, for a bonus. */
private const val TOP_RANK = 8
/** Gravity (units/ms², times the size scale), the physics' small step (ms), and how long the
 *  pile may stay over the line before the run is lost (ms). */
private const val G = 0.0016
private const val SUB = 4.0
private const val OVER_MS = 1500.0
/** The wait after a drop before Nu has the next atom (ms). */
private const val COOL = 450.0
/** The phone's size scale at its smallest, which suits the watch's court. */
private const val SZ = 0.6
/** Nu's line along the top, and the bowl's flat floor, above the level counter. */
private const val HOLD_Y = 50.0
private const val FLOOR = 256.0

/**
 * Fuse (the phone's src/nu/brook.ts): Suika, Nu's way. Nu carries an atom along the top; drop it
 * and it falls into the bowl, rolling and settling. Two of the same size (and colour) that touch
 * fuse into one of the next size up; two of the biggest fuse into nothing. A small dot beside Nu
 * shows the atom after this one. If the pile stays above the line for a second and a half, the
 * run is lost.
 * A level is a number of fusions: 14, and one more each level, up to 24; the pile carries on from
 * level to level. Heat lowers the line (less room), brings in bigger atoms to drop sooner and
 * shortens the drop clock.
 * On the watch the crown moves Nu left and right along the top (a radian of crown is 45 units),
 * and a tap anywhere drops; a finger dragged across still positions Nu, and drops when it lets
 * go. The bowl is the lower part of the round court, so atoms roll on its curve, with a flat
 * floor just above the level counter. A drop clock runs round the rim: when it runs out Nu drops
 * the atom where it is, and each drop made that way, with no touch or crown between, makes the
 * next clock shorter, so a run nobody plays fills the bowl and ends.
 */
class Fuse : Toy {
    private class Atom(x: Double, y: Double, vx: Double, vy: Double, val rank: Int, val born: Double, val drop: Double) : Mover(x, y, vx, vy) {
        val r = size(rank)
    }
    private val atoms = mutableListOf<Atom>()
    private var held: Int? = 0
    private var after = 0
    private var aimX = 0.0
    private var dropping = false
    private var readyAt = 0.0
    private var danger = 0.0
    /** Fusions this level. */
    private var count = 0
    private var over = false
    /** How long Nu has held this atom (ms), and drops made by the clock in a row. */
    private var wait = 0.0
    private var idle = 0
    private var press: Pt? = null
    private var dragging = false

    private fun need(s: Stage) = 14 + min(s.level, 10)
    private fun lineY(s: Stage) = min(140.0, 126 + s.heat * 8)
    private fun heldY(s: Stage) = HOLD_Y + s.nu.r + 3 + size(held ?: after)
    private fun limit(s: Stage) = max(1200.0, clamp(5000 - s.heat * 1500, 2500.0, 5000.0) * 0.8.pow(idle))
    /** Half the court across at height `y`. */
    private fun half(s: Stage, y: Double) = sqrt(max(0.0, s.R * s.R - (y - s.c.y) * (y - s.c.y)))

    /** A new atom to drop: small ones mostly, a size bigger now and then once it's hot. */
    private fun pick(s: Stage): Int {
        val most = if (s.heat >= 0.6) 4 else 3
        return (rnd().pow(1.2) * (most + 1)).toInt().coerceIn(0, most)
    }

    override fun enter(s: Stage) {
        s.rim = null
        held = pick(s)
        after = pick(s)
        aimX = s.c.x
        s.nu.r = 9.0
        s.nu.at(Pt(s.c.x, HOLD_Y))
    }

    /** Where Nu and the atom it holds can go across, both kept inside the court. */
    private fun clampX(s: Stage, x: Double): Double {
        val r = size(held ?: after)
        val lim = min(half(s, HOLD_Y) - s.nu.r - 4, half(s, heldY(s)) - r - 4)
        return clamp(x, s.c.x - lim, s.c.x + lim)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        if (!over) {
            if (held == null && now >= readyAt) { held = after; after = pick(s); wait = 0.0 }
            aimX = clampX(s, aimX)
            nu.seek(Pt(aimX, HOLD_Y), dt, 1.4 * SZ, 0.03)
            nu.y = HOLD_Y
            if (held != null) {
                wait += dt
                if (wait >= limit(s)) { idle++; drop(s, now) }
                else if (dropping && abs(nu.x - aimX) < 4) drop(s, now)
            }
            s.rim = if (held != null) clamp(1 - wait / limit(s), 0.0, 1.0) else 1.0
        } else nu.seek(nu.p, dt)
        var left = min(dt, 50.0)
        while (left > 0) { physics(s, min(SUB, left)); left -= SUB }
        fuse(s, now)
        if (over) return
        // The pile over the line: settled atoms whose tops are above it.
        val line = lineY(s)
        val high = atoms.any { now - it.drop > 1000 && it.y - it.r < line }
        danger = if (high) danger + dt else max(0.0, danger - dt * 2)
        if (danger >= OVER_MS) {
            over = true
            held = null
            s.rim = null
            for (a in atoms) if (a.y - a.r < line) s.fx.ring(Pt(a.x, a.y), Ink.RED, a.r, a.r + 24, 700.0)
            s.out.miss()
            s.out.buzz(24)
            s.lose()
        }
    }

    private fun drop(s: Stage, now: Double) {
        val rank = held ?: return
        atoms += Atom(s.nu.x, heldY(s), 0.0, 0.05, rank, now, now)
        held = null
        dropping = false
        readyAt = now + COOL
        wait = 0.0
        s.out.tick(1)
    }

    /** One small step of the pile: gravity, then the atoms pushed apart and kept in the bowl. */
    private fun physics(s: Stage, h: Double) {
        val g = G * SZ
        val damp = exp(-h / 2500)
        for (a in atoms) {
            a.vy = min(1.2 * SZ, a.vy + g * h)
            a.vx *= damp
            a.vy *= damp
            a.x += a.vx * h
            a.y += a.vy * h
        }
        for (it in 0 until 4) {
            for (i in atoms.indices) for (j in i + 1 until atoms.size) {
                val a = atoms[i]; val b = atoms[j]
                val dx = b.x - a.x; val dy = b.y - a.y
                val d = hypot(dx, dy).takeIf { it > 0 } ?: 0.01
                val least = a.r + b.r
                if (d >= least) continue
                val nx = dx / d; val ny = dy / d
                val ma = a.r * a.r; val mb = b.r * b.r
                val push = (least - d) * 0.8
                a.x -= nx * push * (mb / (ma + mb)); a.y -= ny * push * (mb / (ma + mb))
                b.x += nx * push * (ma / (ma + mb)); b.y += ny * push * (ma / (ma + mb))
                if (it > 0) continue
                // Their velocities: a soft knock along the line between them, a little rub across it.
                val rel = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                if (rel < 0) {
                    val j2 = -1.1 * rel / (1 / ma + 1 / mb)
                    a.vx -= nx * j2 / ma; a.vy -= ny * j2 / ma
                    b.vx += nx * j2 / mb; b.vy += ny * j2 / mb
                }
                val tx = -ny; val ty = nx
                val slide = ((b.vx - a.vx) * tx + (b.vy - a.vy) * ty) * 0.05
                a.vx += tx * slide * (mb / (ma + mb)); a.vy += ty * slide * (mb / (ma + mb))
                b.vx -= tx * slide * (ma / (ma + mb)); b.vy -= ty * slide * (ma / (ma + mb))
            }
            for (a in atoms) {
                if (bounceIn(s, a, a.r, 0.1)) a.vx *= 0.97
                if (a.y + a.r > FLOOR) { a.y = FLOOR - a.r; if (a.vy > 0) a.vy *= -0.1 }
            }
        }
        // Slow ones settle, so the pile rests still.
        for (a in atoms) if (hypot(a.vx, a.vy) < 0.02 * SZ) { a.vx *= 0.85; a.vy *= 0.85 }
    }

    /** Two of a kind that touch fuse into one a size up (two of the biggest, into nothing). */
    private fun fuse(s: Stage, now: Double) {
        for (i in atoms.indices) for (j in i + 1 until atoms.size) {
            val a = atoms[i]; val b = atoms[j]
            if (a.rank != b.rank || dist(a.x, a.y, b.x, b.y) > a.r + b.r + 1) continue
            val p = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
            atoms.removeAt(j)
            atoms.removeAt(i)
            if (a.rank >= TOP_RANK) {
                s.out.chime(4)
                s.fx.ring(p, Ink.WHITE, a.r, a.r * 2.4, 900.0)
                s.fx.burst(p, 16, colour(a.rank), 0.2)
            } else {
                val rank = a.rank + 1
                atoms += Atom(p.x, p.y, (a.vx + b.vx) / 2, (a.vy + b.vy) / 2 - 0.05, rank, now, min(a.drop, b.drop))
                s.out.capture()
                s.fx.burst(p, 8, colour(rank), 0.12)
                s.fx.ring(p, colour(rank), size(rank) * 0.6, size(rank) + 18, 500.0)
            }
            count++
            if (count >= need(s)) {
                count = 0
                s.clear()
                if (s.over) { over = true; held = null; s.rim = null }
            }
            s.part = clamp(count.toDouble() / need(s), 0.0, 1.0)
            // One fusion at a time; the next is caught on the next frame.
            return
        }
    }

    private fun drawAtom(pen: Pen, x: Double, y: Double, rank: Int, r: Double, alpha: Double = 0.92) {
        pen.dot(x, y, r, colour(rank), alpha)
        // The second round of colours wears a white ring, to tell it from the first.
        if (rank >= 5) pen.ring(x, y, r * 0.55, Ink.WHITE, 2.0, 0.55 * alpha)
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val nu = s.nu
        val line = lineY(s)
        val w = half(s, line) - 8
        // The line: grey, turning red and pulsing as the pile stays over it.
        val d = danger / OVER_MS
        pen.line(s.c.x - w, line, s.c.x + w, line, if (d > 0) Ink.RED else Ink.K500, 2.0, if (d > 0) 0.5 + 0.5 * d * (0.6 + 0.4 * sin(now / 70)) else 0.7)
        // The floor, faint.
        val fw = half(s, FLOOR)
        pen.line(s.c.x - fw + 6, FLOOR, s.c.x + fw - 6, FLOOR, Ink.K700, 2.0, 0.8)
        val h = held
        // Where the held atom will fall.
        if (h != null && !over) {
            val bottom = min(FLOOR, s.c.y + half(s, nu.x)) - 4
            val from = heldY(s) + size(h)
            if (bottom > from) pen.line(nu.x, from, nu.x, bottom, Ink.K800, 2.0, 0.9)
        }
        for (a in atoms) {
            val grow = clamp((now - a.born) / 180, 0.0, 1.0)
            drawAtom(pen, a.x, a.y, a.rank, a.r * (0.75 + 0.25 * grow))
        }
        // The atom Nu carries, and the one after it, small, beside Nu.
        if (h != null && !over) drawAtom(pen, nu.x, heldY(s), h, size(h))
        if (!over) drawAtom(pen, nu.x + nu.r + 9, nu.y, after, 3.5 + after * 0.6, 0.85)
        nu.draw(pen, now)
    }

    override fun crown(s: Stage, by: Double) {
        if (over) return
        idle = 0
        aimX = clampX(s, aimX + by * 45)
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        idle = 0
        press = p
        dragging = false
    }

    override fun move(s: Stage, p: Pt) {
        val at = press ?: return
        if (!dragging && dist(p, at) > 8) dragging = true
        if (dragging && !over) aimX = clampX(s, p.x)
    }

    override fun up(s: Stage, p: Pt) {
        if (press == null) return
        move(s, p)
        press = null
        dragging = false
        if (!over && held != null) dropping = true
    }

    private companion object {
        fun size(rank: Int) = 17 * SZ * (1 + 0.5 * rank)
        fun colour(rank: Int) = Ink.NOTES[rank % 5]
    }
}
