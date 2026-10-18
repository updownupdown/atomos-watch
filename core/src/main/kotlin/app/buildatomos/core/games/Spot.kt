package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spot (the phone's src/nu/field.ts). A field of atoms, all alike but one: a touch bigger, or a
 * touch off in color. Tap it before the rim runs out; each find makes the field bigger and the
 * difference smaller. A wrong tap or the rim running out costs a life, and Nu shows you which
 * it was.
 * On the watch the atoms sit on a loose honeycomb in the middle of the round court, a finger's
 * width apart, so each is still a fair tap: the field grows to 19 atoms, not the phone's 30, and
 * the atoms keep the phone's size. A tap takes the nearest atom. Nu waits at the bottom, under
 * the field, as the lives are at the top.
 */
class Spot : Toy {
    private class Atom(val x: Double, val y: Double, var r: Double, var c: Int)
    private var atoms = listOf<Atom>()
    private var odd = 0
    private var count = 0
    private var left = 3
    /** When this field was dealt (set on the first frame after it). */
    private var start: Double? = null
    private var showAt: Double? = null
    private var over = false

    private fun home(s: Stage) = Pt(s.c.x, s.c.y + s.R - 18)

    override fun enter(s: Stage) {
        s.nu.r = 7.0
        s.nu.at(home(s))
        deal(s)
        s.score(0)
    }

    private fun deal(s: Stage) {
        val n = min(SPOTS.size, 6 + count * 2)
        val base = 15.0
        val hue = Ink.NOTES[count % 4]
        val diff = max(0.08, 0.4 - count * 0.025)
        // The n places nearest the middle (in a random order round each ring), the whole set
        // moved back to the middle, each nudged so it doesn't look ruled.
        val take = SPOTS.sortedBy { hypot(it.x, it.y) + rnd() * PITCH * 0.2 }.take(n)
        val mx = take.sumOf { it.x } / n
        val my = take.sumOf { it.y } / n
        atoms = take.map {
            Atom(
                s.c.x + it.x - mx + (rnd() - 0.5) * PITCH * 0.3,
                s.c.y + 2 + it.y - my + (rnd() - 0.5) * PITCH * 0.3,
                base, hue,
            )
        }
        odd = floor(rnd() * n).toInt()
        val o = atoms[odd]
        if (rnd() < 0.5) o.r = base * (1 + diff) else o.c = shade(hue, diff)
        start = null
        showAt = null
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        val o = atoms[odd]
        val begun = start ?: now.also { start = it }
        // Showing the odd one (after a find or a miss): Nu circles it.
        showAt?.let { t ->
            val a = now / 200
            nu.seek(Pt(o.x + cos(a) * (o.r + 10), o.y + sin(a) * (o.r + 10)), dt, 1.0, 0.05)
            if (now - t > 1100 && !over) deal(s)
            s.rim = 0.0
            return
        }
        nu.seek(home(s), dt, 0.6, 0.02)
        if (over) return
        val left = 1 - (now - begun) / SPOT_MS
        s.rim = clamp(left, 0.0, 1.0)
        if (left <= 0) miss(s, now)
    }

    private fun miss(s: Stage, now: Double) {
        left--
        showAt = now
        s.fx.ring(atoms[odd].let { Pt(it.x, it.y) }, Ink.RED, 16.0, 40.0, 600.0)
        s.out.miss()
        s.out.buzz(22)
        if (left <= 0) {
            over = true
            s.score(count)
            finish(s, "spot", count)
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        for (a in atoms) pen.dot(a.x, a.y, a.r, a.c)
        lives(s, pen, left)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (over || showAt != null) return
        // The nearest atom the tap is on (the phone takes the first; here they can be close).
        var hit = -1
        var near = Double.MAX_VALUE
        atoms.forEachIndexed { i, a ->
            val d = dist(a.x, a.y, p.x, p.y)
            if (d < a.r + 8 && d < near) { hit = i; near = d }
        }
        if (hit < 0) return
        if (hit != odd) { miss(s, s.now); return }
        count++
        showAt = s.now
        s.fx.ring(atoms[hit].let { Pt(it.x, it.y) }, Ink.CYAN, 16.0, 40.0, 500.0)
        s.out.capture()
        s.score(count)
    }

    private companion object {
        const val SPOT_MS = 6000.0
        /** How far apart the atoms' middles are: a 15-unit atom with room round it to tap. */
        const val PITCH = 46.0
        /** The honeycomb's places, from its middle: the middle, its six, and the twelve round them. */
        val SPOTS: List<Pt> = buildList {
            for (j in -2..2) for (i in -3..3) {
                val p = Pt((i + if (j % 2 != 0) 0.5 else 0.0) * PITCH, j * PITCH * sqrt(3.0) / 2)
                if (hypot(p.x, p.y) <= 2 * PITCH + 0.1) add(p)
            }
        }
    }
}

/** A color a little lighter: `k` of the way to white. */
private fun shade(argb: Int, k: Double): Int {
    fun mix(v: Int) = (v + (255 - v) * k).roundToInt()
    val r = mix((argb shr 16) and 255)
    val g = mix((argb shr 8) and 255)
    val b = mix(argb and 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
