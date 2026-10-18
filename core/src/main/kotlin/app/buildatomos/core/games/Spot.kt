package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
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
 * touch off in color. Tap it before the rim runs out. A wrong tap or the rim running out costs
 * one of three lives, and Nu shows you which it was; the last one lost loses the run.
 * Levels: a level is a few finds (five, and one more every three levels, up to eight). Lives carry
 * from level to level, and every third level cleared gives one back. Heat makes the field bigger
 * (6 atoms at the gentlest, all 19 from Expert on), the odd one closer to the rest (38% off at the
 * gentlest, 18% at Expert, never under 8%) and the rim quicker (7 s down to 2.5 s).
 * On the watch the atoms sit on a loose honeycomb in the middle of the round court, a finger's
 * width apart, so each is still a fair tap: the field grows to 19 atoms, not the phone's 30, and
 * the atoms keep the phone's size. A tap takes the nearest atom. Nu waits at the lower right,
 * off the field and clear of the lives and the level counter along the bottom.
 */
class Spot : Toy {
    private class Atom(val x: Double, val y: Double, var r: Double, var c: Int)
    private var atoms = listOf<Atom>()
    private var odd = 0
    /** Fields dealt in all, for the hue, and finds so far in this level. */
    private var dealt = 0
    private var found = 0
    private var left = 3
    /** How long this field's rim lasts (ms). */
    private var ms = 0.0
    /** When this field was dealt (set on the first frame after it). */
    private var start: Double? = null
    private var showAt: Double? = null

    private fun home(s: Stage) = polar(s, PI / 6, s.R - 18)

    override fun enter(s: Stage) {
        s.nu.r = 7.0
        s.nu.at(home(s))
        deal(s)
    }

    private fun deal(s: Stage) {
        val h = s.heat
        val n = size(h)
        val base = 15.0
        val hue = Ink.NOTES[dealt++ % 4]
        val diff = offBy(h)
        ms = rimMs(h)
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
        s.part = clamp(found.toDouble() / need(s.level), 0.0, 1.0)
        // Showing the odd one (after a find or a miss): Nu circles it.
        showAt?.let { t ->
            val a = now / 200
            nu.seek(Pt(o.x + cos(a) * (o.r + 10), o.y + sin(a) * (o.r + 10)), dt, 1.0, 0.05)
            if (now - t > 1100 && !s.over) deal(s)
            s.rim = 0.0
            return
        }
        nu.seek(home(s), dt, 0.6, 0.02)
        if (s.over) return
        val left = 1 - (now - begun) / ms
        s.rim = clamp(left, 0.0, 1.0)
        if (left <= 0) miss(s, now)
    }

    private fun miss(s: Stage, now: Double) {
        left--
        showAt = now
        s.fx.ring(atoms[odd].let { Pt(it.x, it.y) }, Ink.RED, 16.0, 40.0, 600.0)
        s.out.miss()
        s.out.buzz(22)
        if (left <= 0) s.lose()
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        for (a in atoms) pen.dot(a.x, a.y, a.r, a.c)
        lives(s, pen, left)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (s.over || showAt != null) return
        // The nearest atom the tap is on (the phone takes the first; here they can be close).
        var hit = -1
        var near = Double.MAX_VALUE
        atoms.forEachIndexed { i, a ->
            val d = dist(a.x, a.y, p.x, p.y)
            if (d < a.r + 8 && d < near) { hit = i; near = d }
        }
        if (hit < 0) return
        if (hit != odd) { miss(s, s.now); return }
        showAt = s.now
        s.fx.ring(atoms[hit].let { Pt(it.x, it.y) }, Ink.CYAN, 16.0, 40.0, 500.0)
        s.out.capture()
        if (++found >= need(s.level)) {
            found = 0
            s.clear()
            // Every third level cleared gives a life back.
            if (s.level % 3 == 0 && left < 3) left++
        }
        s.part = clamp(found.toDouble() / need(s.level), 0.0, 1.0)
    }

    internal companion object {
        /** How far apart the atoms' middles are: a 15-unit atom with room round it to tap. */
        const val PITCH = 46.0
        /** The honeycomb's places, from its middle: the middle, its six, and the twelve round them. */
        val SPOTS: List<Pt> = buildList {
            for (j in -2..2) for (i in -3..3) {
                val p = Pt((i + if (j % 2 != 0) 0.5 else 0.0) * PITCH, j * PITCH * sqrt(3.0) / 2)
                if (hypot(p.x, p.y) <= 2 * PITCH + 0.1) add(p)
            }
        }
        /** Finds to clear a level: five, one more every three levels, at most eight. */
        fun need(level: Int) = 5 + min(3, level / 3)
        /** Atoms in the field at this heat: 6 at the gentlest, all 19 from Expert on. */
        fun size(heat: Double) = min(SPOTS.size, 6 + (heat * 13).roundToInt())
        /** How far the odd one is off the rest, as a share: 0.38 at the gentlest, never under 0.08. */
        fun offBy(heat: Double) = max(0.08, 0.38 - heat * 0.2)
        /** How long the rim lasts for one field (ms). */
        fun rimMs(heat: Double) = max(2500.0, 7000.0 - heat * 2000)
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
