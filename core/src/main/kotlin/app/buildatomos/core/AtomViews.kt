// The Build screen's round drawings, in court units. The dial: the atoms as plates round the rim,
// the picked one at the top and a gap at the foot, its cloud and name in the middle; the crown
// turns it. The shells: the whole table as one atom, each period a shell of its atoms round the
// middle (2, 8, 8, 18, 18, 32, 32), open at the foot, the picked atom's plate as the nucleus.
// And an atom's shells as a ring round the bezel, the ones built lit.

package app.buildatomos.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Text for the few drawings that carry some (the games carry none): `s` centered on (x, y),
 *  `size` tall, semibold when `bold`. */
fun interface Words {
    fun text(s: String, x: Double, y: Double, size: Double, color: Int, bold: Boolean, alpha: Double)
}

/** Where atom z stands: built, being built (the next to build) or later. */
enum class Standing { BUILT, BUILDING, LATER }

fun Collection.standing(z: Int) = when {
    isBuilt(z) -> Standing.BUILT
    z == building -> Standing.BUILDING
    else -> Standing.LATER
}

/** How far atom z's cloud is filled in, 0..1. */
fun Collection.share(z: Int): Double = when {
    isBuilt(z) -> 1.0
    z == building -> progress
    else -> 0.0
}

/** Black or white, whichever reads on `fill` (the phone's inkOn). */
fun inkFor(fill: Int): Int {
    fun ch(v: Int): Double { val c = v / 255.0; return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
    val l = 0.2126 * ch(fill shr 16 and 0xff) + 0.7152 * ch(fill shr 8 and 0xff) + 0.0722 * ch(fill and 0xff)
    return if (l > 0.28) Ink.BLACK else Ink.WHITE
}

/** An atom's plate, `side` across round (x, y): in its own color once built, dark before, with a
 *  rose edge while it is being built; its number over its symbol if `number`. */
fun Pen.atomPlate(atom: Atom, st: Standing, x: Double, y: Double, side: Double, words: Words, number: Boolean = false, alpha: Double = 1.0) {
    val h = side / 2
    val fill = if (st == Standing.BUILT) atom.color else Ink.K800
    rect(x - h, y - h, side, side, side / 5, fill, alpha)
    if (st == Standing.BUILDING) rectRing(x - h + 1.2, y - h + 1.2, side - 2.4, side - 2.4, side / 5 - 1, Ink.ROSE, 2.4, alpha)
    val ink = when (st) {
        Standing.BUILT -> inkFor(atom.color)
        Standing.BUILDING -> Ink.WHITE
        Standing.LATER -> Ink.K300
    }
    if (number) {
        words.text("${atom.z}", x, y - side * 0.2, side * 0.24, ink, false, alpha)
        words.text(atom.symbol, x, y + side * 0.13, side * 0.42, ink, true, alpha)
    } else words.text(atom.symbol, x, y, side * 0.46, ink, true, alpha)
}

/** What the Build screen says of an atom under its name. */
fun Collection.says(z: Int): Pair<String, Int> = when (standing(z)) {
    Standing.BUILT -> "Built" to Ink.GREEN
    Standing.BUILDING -> "Shells: $shells of $need" to Ink.CYAN
    Standing.LATER -> "Not built yet" to Ink.K300
}

object AtomDial {
    /** The rim the plates sit on. */
    const val R = 116.0
    /** The turn from one plate to the next. */
    const val STEP = 24 * PI / 180
    /** How many plates show on each side of the picked one (the last ones fading). */
    const val SIDE = 6.4
    /** The picked atom's cloud (its middle and its frame's radius), its name and its state. */
    const val CLOUD_Y = 128.0
    const val CLOUD_R = 50.0
    const val NAME_Y = 196.0
    const val STATE_Y = 220.0

    /** Where the plate `d` atoms on from the picked one sits (d is fractional while it turns). */
    fun at(d: Double): Pt {
        val a = -PI / 2 + d * STEP
        return Pt(SCREEN / 2 + cos(a) * R, SCREEN / 2 + sin(a) * R)
    }

    /** A plate's side: the picked one, at the top, is the largest. */
    fun side(d: Double) = 34.0 + 14.0 * max(0.0, 1 - abs(d))

    /** How strongly a plate shows: the furthest round fade toward the gap at the foot. */
    fun alpha(d: Double) = ((SIDE - abs(d)) / 1.6).coerceIn(0.0, 1.0)

    /** The plates showing with the dial turned to `pos`. */
    fun showing(pos: Double): IntRange = max(1, ceil(pos - SIDE).toInt())..min(ATOMS, floor(pos + SIDE).toInt())

    /** The atom whose plate is at (x, y), or 0. */
    fun hit(x: Double, y: Double, pos: Double): Int {
        for (i in showing(pos)) {
            val d = i - pos
            val p = at(d)
            if (abs(x - p.x) <= side(d) / 2 + 3 && abs(y - p.y) <= side(d) / 2 + 3) return i
        }
        return 0
    }

    /** True for a tap on the middle: the picked atom's cloud or its name. */
    fun middle(x: Double, y: Double) = hypot(x - SCREEN / 2, y - CLOUD_Y) < CLOUD_R + 8 || (abs(x - SCREEN / 2) < 80 && abs(y - (NAME_Y + STATE_Y) / 2) < 24)

    /** The dial turned to `pos`, `picked` the atom at the top; its cloud is `nebula` at `now`. */
    fun draw(pen: Pen, words: Words, atoms: List<Atom>, c: Collection, pos: Double, picked: Int, nebula: Nebula, now: Double) {
        val share = c.share(picked)
        nebula.draw(pen, SCREEN / 2, CLOUD_Y, CLOUD_R, now, share, still = share <= 0)
        for (i in showing(pos)) {
            val d = i - pos
            val p = at(d)
            pen.atomPlate(atoms[i - 1], c.standing(i), p.x, p.y, side(d), words, alpha = alpha(d))
        }
        val atom = atoms[picked - 1]
        val (state, color) = c.says(picked)
        words.text(atom.name, SCREEN / 2, NAME_Y, 22.0, if (c.isBuilt(picked)) Ink.WHITE else Ink.K200, true, 1.0)
        words.text(state, SCREEN / 2, STATE_Y, 18.0, color, false, 1.0)
    }
}

object AtomShells {
    /** The arc each shell covers, clockwise from the lower left round the top; the foot is open. */
    const val SPAN = 300 * PI / 180
    /** The atoms' dots, and the picked atom's plate in the middle. */
    const val DOT = 5.5
    const val PLATE = 64.0

    private val FIRST = intArrayOf(1, 3, 11, 19, 37, 55, 87, 119)

    /** Period p's shell radius. */
    fun radius(period: Int) = 56.0 + (period - 1) * 13.0

    /** Where atom z sits: on its period's shell, its place in the period spread evenly along it. */
    fun at(z: Int): Pt {
        val p = periodOf(z)
        val n = FIRST[p] - FIRST[p - 1]
        val i = z - FIRST[p - 1]
        val a = PI / 2 + (TAU - SPAN) / 2 + SPAN * (i + 0.5) / n
        val r = radius(p)
        return Pt(SCREEN / 2 + cos(a) * r, SCREEN / 2 + sin(a) * r)
    }

    /** The atom whose dot is nearest (x, y) within reach of a finger, 0 for none, -1 for the plate
     *  in the middle. */
    fun hit(x: Double, y: Double): Int {
        if (abs(x - SCREEN / 2) < PLATE / 2 + 4 && abs(y - SCREEN / 2) < PLATE / 2 + 4) return -1
        var best = 0
        var near = 12.0
        for (z in 1..ATOMS) {
            val p = at(z)
            val d = hypot(x - p.x, y - p.y)
            if (d < near) { near = d; best = z }
        }
        return best
    }

    fun draw(pen: Pen, words: Words, atoms: List<Atom>, c: Collection, picked: Int) {
        for (z in 1..ATOMS) {
            val p = at(z)
            when (c.standing(z)) {
                Standing.BUILT -> pen.dot(p.x, p.y, DOT, atoms[z - 1].color)
                Standing.BUILDING -> { pen.dot(p.x, p.y, DOT, Ink.K800); pen.ring(p.x, p.y, DOT - 1, Ink.ROSE, 2.2) }
                Standing.LATER -> pen.dot(p.x, p.y, DOT, Ink.K700)
            }
        }
        val p = at(picked)
        pen.ring(p.x, p.y, DOT + 3.2, Ink.WHITE, 2.2)
        pen.atomPlate(atoms[picked - 1], c.standing(picked), SCREEN / 2, SCREEN / 2, PLATE, words, number = true)
    }
}

/** An atom's `need` shells as a ring round the bezel, clockwise from the top, the first `done` of
 *  them lit in cyan, or all of them green once the atom is built. */
fun Pen.shellRing(need: Int, done: Int, built: Boolean) {
    val c = SCREEN / 2
    val r = c - 5
    val w = 5.0
    val color = if (built) Ink.GREEN else Ink.CYAN
    if (need <= 1) {
        ring(c, c, r, if (built || done >= 1) color else Ink.K800, w)
        return
    }
    val seg = TAU / need
    val gap = 0.075
    for (i in 0 until need) {
        val lit = built || i < done
        arc(c, c, r, -PI / 2 + i * seg + gap / 2, seg - gap, if (lit) color else Ink.K800, w)
    }
}
