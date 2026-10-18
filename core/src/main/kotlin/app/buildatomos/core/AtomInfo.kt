// What the Atlas tells about each atom, baked from the phone app by tools/gen-info.mts into
// atoms-info.json: its numbers, its state at room temperature, who found it and where its name
// comes from, its isotopes, and its facts and stories.

package app.buildatomos.core

class AtomInfo(
    val z: Int,
    /** The phone's series: "Noble gases", "Transition metals"... */
    val series: String,
    /** Standard atomic weight. */
    val mass: Double,
    /** Mass number of its commonest isotope. */
    val massNumber: Int,
    /** Melting and boiling points in kelvin, density in g/cm³, Pauling electronegativity. */
    val melt: Double?,
    val boil: Double?,
    val density: Double?,
    val electronegativity: Double?,
    /** Year found (null for the ancient ones or unknown), by whom and where. */
    val year: Int?,
    val who: String?,
    val where: String?,
    /** Where its name comes from. */
    val origin: String?,
    val stableIsotopes: Int,
    val knownIsotopes: Int,
    /** Parts per million in Earth's crust and in seawater. */
    val crust: Double?,
    val sea: Double?,
    /** One headline fact and a few more. */
    val headline: String,
    val facts: List<String>,
    /** Where it comes from, and what it is used for. */
    val origins: List<String>,
    val uses: List<String>,
) {
    /** Solid, liquid or gas at room temperature, or null when it sits near a change or is unknown
     *  (the phone's phaseOf). */
    val phase: String? get() {
        val m = melt ?: return null
        val b = boil ?: return null
        return when {
            m > ROOM + 15 -> "Solid"
            b < ROOM - 15 -> "Gas"
            m < ROOM - 15 && b > ROOM + 15 -> "Liquid"
            else -> null
        }
    }

    /** One of the phone's series in the singular: "noble gas", "transition metal". */
    val kind: String get() = KIND[series] ?: series.lowercase()

    companion object {
        const val ROOM = 293.15
        private val KIND = mapOf(
            "Alkali metals" to "alkali metal", "Alkaline earth metals" to "alkaline earth metal",
            "Transition metals" to "transition metal", "Poor metals" to "post-transition metal",
            "Metalloids" to "metalloid", "Nonmetals" to "nonmetal", "Halogens" to "halogen",
            "Noble gases" to "noble gas", "Lanthanides" to "lanthanide", "Actinides" to "actinide",
        )
        val METALS = setOf("Alkali metals", "Alkaline earth metals", "Transition metals", "Poor metals", "Lanthanides", "Actinides")

        /** Reads atoms-info.json. */
        fun readAll(text: String): List<AtomInfo> {
            @Suppress("UNCHECKED_CAST")
            val list = Json.parse(text) as List<Map<String, Any?>>
            fun Map<String, Any?>.d(k: String) = (this[k] as? Double)
            fun Map<String, Any?>.t(k: String) = (this[k] as? String)?.takeIf { it.isNotBlank() }
            @Suppress("UNCHECKED_CAST")
            fun Map<String, Any?>.l(k: String) = (this[k] as? List<String>) ?: emptyList()
            return list.map { o ->
                AtomInfo(
                    z = o.d("z")!!.toInt(),
                    series = o.t("ser") ?: "",
                    mass = o.d("w") ?: 0.0,
                    massNumber = o.d("A")?.toInt() ?: 0,
                    melt = o.d("mp"), boil = o.d("bp"), density = o.d("dens"), electronegativity = o.d("en"),
                    year = o.d("year")?.toInt(), who = o.t("who"), where = o.t("where"), origin = o.t("origin"),
                    stableIsotopes = o.d("stab")?.toInt() ?: 0, knownIsotopes = o.d("nIso")?.toInt() ?: 0,
                    crust = o.d("crust"), sea = o.d("sea"),
                    headline = o.t("h") ?: "", facts = o.l("f"), origins = o.l("from"), uses = o.l("uses"),
                )
            }
        }
    }
}
