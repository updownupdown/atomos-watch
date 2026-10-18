// The watch's own collection: the atoms built on the watch, from hydrogen up, one at a time.
// Every point a run scores is one electron for the atom being built; an atom is built when it
// has all its electrons (1 for hydrogen, 26 for iron, 118 for oganesson). Nothing here is shared
// with the phone.

package app.buildatomos.core

import kotlin.math.max
import kotlin.math.roundToInt

const val ATOMS = 118

class Collection(
    /** The atom being built (1..118), or 119 once the table is done. */
    var building: Int = 1,
    /** Electrons already in it. */
    var electrons: Int = 0,
    /** Each built atom and the day it was built (days since 1970). */
    val built: MutableMap<Int, Long> = mutableMapOf(),
) {
    val done get() = building > ATOMS

    /** How far the atom being built is, 0..1. */
    val progress get() = if (done) 1.0 else electrons.toDouble() / building

    /** Adds electrons; returns the atoms that were built on the way. */
    fun add(n: Int, day: Long): List<Int> {
        var left = n
        val out = mutableListOf<Int>()
        while (left > 0 && !done) {
            val need = building - electrons
            if (left >= need) {
                left -= need
                built[building] = day
                out += building
                building++
                electrons = 0
            } else {
                electrons += left
                left = 0
            }
        }
        return out
    }

    fun isBuilt(z: Int) = z in built

    companion object {
        /** A run's electrons: one per point (10 for a par run), at least one for any score,
         *  twice as many for a new best. */
        fun electronsFor(points: Double, newBest: Boolean): Int {
            if (points <= 0) return 0
            val n = max(1, points.roundToInt())
            return if (newBest) n * 2 else n
        }
    }
}
