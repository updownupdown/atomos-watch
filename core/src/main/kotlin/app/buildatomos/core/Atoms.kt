// The 118 atoms as the watch knows them, and their clouds. The data is baked from the phone app by
// tools/gen-atoms.mts: names and places, the hue of each atom's plate in the table, two facts,
// and each atom's point cloud sampled from the phone's own hydrogen-like orbitals.

package app.buildatomos.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class Atom(
    val z: Int,
    val symbol: String,
    val name: String,
    val period: Int,
    val group: Int?,
    val block: String,
    /** The color of its plate in the table (the phone's placeColour), ARGB. */
    val color: Int,
    val fact1: String,
    val fact2: String,
)

/** An atom's cloud: points inside the unit sphere (the frame round 85% of them, with room; the
 *  few that reach further drawn in toward its edge), and for each the subshell and orbital it
 *  belongs to. */
class Cloud(val x: FloatArray, val y: FloatArray, val z: FloatArray, val sub: ByteArray, val orb: ByteArray) {
    val size get() = x.size

    /** How far out each point is, 0 at the nucleus and 1 at the frame. */
    val reach: FloatArray by lazy { FloatArray(size) { hypot(hypot(x[it], y[it]), z[it]) } }

    /** The orbitals the light walks: (sub, orb) keys of the outer subshells whose points reach
     *  a fair way out, so their shape shows. */
    val walk: IntArray by lazy {
        val reach = HashMap<Int, Float>()
        for (i in 0 until size) {
            val k = sub[i] * 16 + orb[i]
            reach[k] = max(reach[k] ?: 0f, this.reach[i])
        }
        reach.filter { it.value > 0.35f }.keys.sorted().toIntArray().takeLast(6).toIntArray()
    }

    companion object {
        /** Reads one atom's cloud from the baked bytes at `at`; returns it and where the next starts. */
        fun read(b: ByteArray, at: Int): Pair<Cloud, Int> {
            val n = (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8)
            val x = FloatArray(n); val y = FloatArray(n); val z = FloatArray(n)
            val sub = ByteArray(n); val orb = ByteArray(n)
            var o = at + 2
            for (i in 0 until n) {
                x[i] = b[o] / 127f; y[i] = b[o + 1] / 127f; z[i] = b[o + 2] / 127f
                sub[i] = b[o + 3]; orb[i] = b[o + 4]
                o += 5
            }
            return Cloud(x, y, z, sub, orb) to o
        }

        fun readAll(b: ByteArray): List<Cloud> {
            val out = ArrayList<Cloud>(ATOMS)
            var at = 0
            while (at < b.size && out.size < ATOMS) { val (c, next) = read(b, at); out += c; at = next }
            return out
        }
    }
}

/** Colors, by hue, saturation and lightness (0..1 each), as ARGB. */
object Hsl {
    fun of(color: Int): DoubleArray {
        val r = (color shr 16 and 0xff) / 255.0
        val g = (color shr 8 and 0xff) / 255.0
        val b = (color and 0xff) / 255.0
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        if (mx == mn) return doubleArrayOf(0.0, 0.0, l)
        val d = mx - mn
        val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6 else 0)) / 6
            g -> ((b - r) / d + 2) / 6
            else -> ((r - g) / d + 4) / 6
        }
        return doubleArrayOf(h, s, l)
    }

    fun argb(h: Double, s: Double, l: Double): Int {
        val hh = ((h % 1) + 1) % 1
        fun f(n: Double): Double {
            val k = (n + hh * 12) % 12
            val a = s * min(l, 1 - l)
            return l - a * max(-1.0, min(k - 3, min(9 - k, 1.0)))
        }
        val r = (f(0.0) * 255).toInt(); val g = (f(8.0) * 255).toInt(); val b = (f(4.0) * 255).toInt()
        return (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/**
 * Draws an atom's cloud as the phone's nebula does, flat: one hue for the whole atom, taken from
 * its plate; the light walks its outer orbitals one at a time (4 s each), the lit one brighter
 * and denser; the cloud turns slowly about its upright axis. No gradients, no twinkle.
 *
 * `share` is how much of it is in color, from noon clockwise (the atom being built: the rest is
 * gray and dimmer); 0 is a gray, still atom not yet begun; 1 a built one.
 */
class Nebula(val atom: Atom, val cloud: Cloud) {
    private val hsl = Hsl.of(atom.color)
    private val rest = Hsl.argb(hsl[0], 0.72, 0.6)
    private val restDeep = Hsl.argb(hsl[0], 0.68, 0.46)
    private val lit = Hsl.argb(hsl[0], 0.75, 0.68)
    private val gray = 0xFF9AA2A7.toInt()
    /** The spin: a direction, a speed (slower for heavier atoms) and a first pose, all from z. */
    private val seed = seeded(atom.z * 7919 + 17)().let { (it * 1e6).toInt() }
    private val dir = if (seed and 1 == 1) 1.0 else -1.0
    private val speed = 0.12 + 0.2 / sqrt(atom.period.toDouble())
    private val theta0 = (seed % 1000) / 1000.0 * TAU

    /** Draws the cloud round (cx, cy), `r` its frame's radius, at `now` ms. */
    fun draw(pen: Pen, cx: Double, cy: Double, r: Double, now: Double, share: Double = 1.0, bright: Double = 1.0, still: Boolean = false) {
        val t = now / 1000
        val th = if (still) theta0 else theta0 + dir * speed * t
        val tilt = 0.35 + 0.18 * sin(t * 0.08 + seed % 7)
        val ct = cos(th); val st = sin(th); val cl = cos(tilt); val sl = sin(tilt)
        val walk = cloud.walk
        val litKey = if (share <= 0 || walk.isEmpty()) -1 else walk[((t / 4).toInt()) % walk.size]
        val fade = if (litKey < 0) 0.0 else sin(((t / 4) % 1) * PI).let { min(1.0, it * 2.2) }
        val dot = max(0.7, r / 120.0)
        for (i in 0 until cloud.size) {
            val x0 = cloud.x[i].toDouble(); val y0 = cloud.y[i].toDouble(); val z0 = cloud.z[i].toDouble()
            // Turn about the upright axis, then tip toward the viewer.
            val x1 = x0 * ct + z0 * st
            val z1 = -x0 * st + z0 * ct
            val y1 = y0 * cl - z1 * sl
            val px = cx + x1 * r
            val py = cy - y1 * r
            val rr = hypot(x1, y1)
            val size = dot * (1 + 0.6 * min(1.0, rr))
            val inColor = share >= 1 || (share > 0 && ((atan2(px - cx, -(py - cy)) + TAU) % TAU) / TAU < share)
            val key = cloud.sub[i] * 16 + cloud.orb[i]
            val core = rr < 0.18
            // The outermost points thin out, so the cloud ends in a soft round edge.
            val edge = 1 - 0.5 * smooth((cloud.reach[i] - 0.8) / 0.2)
            if (!inColor) {
                pen.dot(px, py, size, gray, (if (core) 0.45 else 0.26) * 0.7 * bright * edge)
            } else if (key == litKey) {
                pen.dot(px, py, size * 1.1, lit, (0.4 + 0.5 * fade) * bright * edge)
            } else {
                val c = if (i % 3 == 0) restDeep else rest
                pen.dot(px, py, size, c, (if (core) 0.7 else 0.36) * bright * edge * (1 - 0.35 * fade * (if (litKey >= 0) 1 else 0)))
            }
        }
    }
}

/** 0 below 0, 1 above 1, and an S between. */
private fun smooth(t: Double): Double { val c = t.coerceIn(0.0, 1.0); return c * c * (3 - 2 * c) }
