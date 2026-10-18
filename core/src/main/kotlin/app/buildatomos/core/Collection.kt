// The watch's own collection: the atoms built on the watch, from hydrogen up, one at a time.
// An atom is built shell by shell: every run won for it builds its next electron shell, so
// hydrogen takes one win, carbon two, iron four and oganesson seven (620 for the whole table).
// Each shell is played at the atom's dial (Difficulty.buildDial). Nothing here is shared with the
// phone.

package app.buildatomos.core

import kotlin.math.floor

const val ATOMS = 118

/** The period an atom sits in, which is the number of its electron shells. */
fun periodOf(z: Int): Int = when {
    z <= 2 -> 1
    z <= 10 -> 2
    z <= 18 -> 3
    z <= 36 -> 4
    z <= 54 -> 5
    z <= 86 -> 6
    else -> 7
}

/** Electrons in each shell of atom `z` (the Bohr picture the phone's Shells view draws). */
fun shellCounts(z: Int, subs: List<String>): IntArray {
    val out = IntArray(periodOf(z))
    for (s in subs) {
        val n = s.takeWhile { it.isDigit() }.toIntOrNull() ?: continue
        val count = s.dropWhile { it.isDigit() }.drop(1).toIntOrNull() ?: continue
        if (n in 1..out.size) out[n - 1] += count
    }
    return out
}

class Collection(
    /** The atom being built (1..118), or 119 once the table is done. */
    var building: Int = 1,
    /** Shells of it already built. */
    var shells: Int = 0,
    /** Each built atom and the day it was built (days since 1970). */
    val built: MutableMap<Int, Long> = mutableMapOf(),
) {
    val done get() = building > ATOMS

    /** Shells the atom being built has. */
    val need get() = if (done) 1 else periodOf(building)

    /** How far the atom being built is, 0..1. */
    val progress get() = if (done) 1.0 else shells.toDouble() / need

    /** The dial and the goal of the next run for it. */
    val dial get() = Difficulty.buildDial(minOf(building, ATOMS), shells)
    val goal get() = Difficulty.goal(dial)

    /** A run for the atom was won: builds its next shell. Returns the atom if that finished it. */
    fun win(day: Long): Int? {
        if (done) return null
        shells++
        if (shells < need) return null
        val z = building
        built[z] = day
        building++
        shells = 0
        return z
    }

    fun isBuilt(z: Int) = z in built

    companion object {
        /** The first collections counted electrons (z of them for atom z); each becomes the shells
         *  it had come to, rounded down. */
        fun fromElectrons(building: Int, electrons: Int, built: MutableMap<Int, Long>): Collection {
            val z = building.coerceIn(1, ATOMS + 1)
            val shells = if (z > ATOMS) 0 else floor(electrons.toDouble() / z * periodOf(z)).toInt().coerceIn(0, periodOf(z) - 1)
            return Collection(z, shells, built)
        }
    }
}
