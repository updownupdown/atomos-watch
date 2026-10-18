// Where an atom sits among all of them, property by property, as on the phone's Atlas page
// (src/gallery/visuals.ts, axisStrip): every measured atom's value on one scale (a log scale for
// the ones that span many powers of ten), this atom's place on it, its rank in words ("4th
// densest", "the lightest") and the atoms at the two ends. From rutherfordium on only a few atoms
// have ever been made, so their values are predictions: they are left out of the crowd, but an
// atom's own prediction is still placed on it.

package app.buildatomos.core

import kotlin.math.log10

/** One property the Atlas page compares across atoms. */
class Property(
    val label: String,
    /** The words for the top and the bottom of the ranking: "densest", "lightest". */
    val more: String,
    val less: String,
    /** Shown on a log scale; only values above zero take part. */
    val log: Boolean,
    val get: (AtomInfo) -> Double?,
) {
    companion object {
        /** From here on the bulk properties are predicted, not measured. */
        const val PREDICTED_FROM = 104

        val MELT = Property("Melts", "highest melting point", "lowest melting point", false) { it.melt }
        val BOIL = Property("Boils", "highest boiling point", "lowest boiling point", false) { it.boil }
        val DENSITY = Property("Density", "densest", "lightest", true) { it.density }
        val ELECTRONEGATIVITY = Property("Electronegativity", "most electronegative", "least electronegative", false) { it.electronegativity }
        val CRUST = Property("In Earth's crust", "most common in the crust", "rarest in the crust", true) { it.crust }
        val SEA = Property("In seawater", "most common in seawater", "rarest in seawater", true) { it.sea }

        val ALL = listOf(MELT, BOIL, DENSITY, ELECTRONEGATIVITY, CRUST, SEA)
    }
}

/** One property across every atom, seen from atom `z`. */
class Crowd(val property: Property, infos: List<AtomInfo>, val z: Int) {
    /** This atom's value, or null when it has none. */
    val value: Double? = infos.firstOrNull { it.z == z }?.let(property.get)

    /** Every atom that takes part, as (z, value): the measured ones, and this one. */
    val all: List<Pair<Int, Double>> = infos.mapNotNull { i ->
        val v = property.get(i) ?: return@mapNotNull null
        if (property.log && v <= 0) return@mapNotNull null
        if (i.z >= Property.PREDICTED_FROM && i.z != z) return@mapNotNull null
        i.z to v
    }

    /** The atoms at the bottom and the top. */
    val lowest: Pair<Int, Double> = all.minByOrNull { it.second } ?: (z to 0.0)
    val highest: Pair<Int, Double> = all.maxByOrNull { it.second } ?: (z to 0.0)

    private fun t(v: Double) = if (property.log) log10(v) else v
    private val lo = t(lowest.second)
    private val hi = t(highest.second)

    /** Where `v` sits between the lowest (0) and the highest (1). A value too small for the log
     *  scale (none found) sits at the bottom. */
    fun place(v: Double): Double {
        if (property.log && v <= 0) return 0.0
        return if (hi > lo) ((t(v) - lo) / (hi - lo)).coerceIn(0.0, 1.0) else 0.5
    }

    /** "the densest", "4th densest", "3rd lightest": the rank from whichever end is nearer; "a
     *  prediction" for a superheavy atom, and null when it has no value to rank. */
    val rank: String? get() {
        val v = value ?: return null
        if (z >= Property.PREDICTED_FROM) return "a prediction"
        if (property.log && v <= 0) return "too little to measure"
        val r = all.count { it.second > v } + 1
        val count = all.size
        return if (r <= count / 2) (if (r == 1) "the ${property.more}" else "${ordinal(r)} ${property.more}")
        else {
            val fromBottom = count - r + 1
            if (fromBottom == 1) "the ${property.less}" else "${ordinal(fromBottom)} ${property.less}"
        }
    }

    companion object {
        /** 1st, 2nd, 3rd, 4th, 11th, 12th, 21st... */
        fun ordinal(n: Int): String {
            val tail = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
            return "$n$tail"
        }
    }
}
