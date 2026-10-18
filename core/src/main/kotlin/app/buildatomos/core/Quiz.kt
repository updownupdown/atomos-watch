// Nu asks: the phone's daily quiz (src/home/daily.ts), three questions a day, each about
// something different: two choices (two atom cards, or two words or years) or four atom cards to
// find one among. Atoms come from the ones most people have heard of, and compared values are
// far apart, so a question is about knowing atoms rather than recalling a number.
//
// A day deals the same questions as on the phone: the same seeded generator, drawing in the
// same order, over the same data (atoms-info.json is baked from the phone's elements.json).
// QuizTest checks this against questions dealt by the phone's own code.

package app.buildatomos.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** What a question asks, and its topic: a day asks about three different topics. In the phone's
 *  order, which the deal draws from. */
enum class QuizType(val id: String, val topic: String) {
    MELT("melt", "heat"), BOIL("boil", "heat"), PHASE("phase", "state"), STATE_TRUE("stateTrue", "state"),
    LIQUID4("liquid4", "state"), DENSE("dense", "density"), FIRST("first", "found"), YEAR("year", "found"),
    ERA("era", "found"), FIRST4("first4", "found"), RADIOACTIVE("radioactive", "nucleus"), NOBLE("noble", "kind"),
    METAL("metal", "kind"), KIND4("kind4", "kind"), CRUST("crust", "where"), SEA("sea", "where"),
    PLACE("place", "name"), LIFE4("life4", "life");

    /** One of the four-card questions, of which a day has at most one. */
    val four get() = id.endsWith("4")
}

/** A choice: an atom's card, or a word or a year on a button. */
sealed interface QuizOption {
    data class Card(val z: Int) : QuizOption
    data class Word(val text: String) : QuizOption
}

/**
 * One of Nu's questions. `options` are two choices or four atom cards; `answer` is the right
 * one's index; `after` is what each card shows once answered ("1,538 °C", "" for none). For a
 * question about one atom, `about` is the atom and `note` a line shown once answered.
 *
 * The text is the phone's, word for word. Over days 0..30000 the longest question is 57
 * characters ("True or false: Potassium is a liquid at room temperature."), the median 33, and
 * one in five is over 40; the longest note is 91 ("Plutonium was found in 1940 by G. T. Seaborg,
 * J. W. Kennedy, E. M. McMillan and A. C. Wahl."), the longest line under a card 34 ("Named
 * after Strontian, in Scotland"), the longest button "Before 1850".
 */
data class QuizQuestion(
    val type: QuizType,
    val text: String,
    val options: List<QuizOption>,
    val answer: Int,
    val after: List<String>,
    val about: Int? = null,
    val note: String? = null,
) {
    /** The atoms it names: its cards, then the one it is about. */
    val atoms: List<Int> get() = options.filterIsInstance<QuizOption.Card>().map { it.z } + listOfNotNull(about)
}

/**
 * Deals the day's questions from the atoms' names (`names[z - 1]`, as atoms.json has them) and
 * the Atlas data (`info`, as AtomInfo.readAll reads it).
 */
class Quiz(private val names: List<String>, info: List<AtomInfo>) {
    private val info = info.sortedBy { it.z }
    private fun at(z: Int) = info[z - 1]
    private fun name(z: Int) = names[z - 1]

    /** The day's questions: each about something different, at most one with four cards, no atom
     *  twice. `day` is the phone's day number, the player's local date as days since 1970
     *  (LocalDate.now().toEpochDay()). */
    fun today(day: Int, count: Int = 3): List<QuizQuestion> {
        val d = Deal(phoneSeeded(day * 3 + 1))
        val out = ArrayList<QuizQuestion>()
        val used = HashSet<Int>()
        var tries = 0
        while (out.size < count && tries < 2000) {
            tries++
            val type = d.pick(TYPES)!!
            if (out.any { it.type.topic == type.topic }) continue
            if (type.four && out.any { it.type.four }) continue
            val q = make(type, d) ?: continue
            val zs = q.atoms
            if (zs.any { it in used }) continue
            used += zs
            out += q
        }
        return out
    }

    /** The day's draws; `pick` draws even from an empty list, as the phone's does. */
    private class Deal(val rnd: () -> Double) {
        fun <T> pick(xs: List<T>): T? = xs.getOrNull(floor(rnd() * xs.size).toInt())
    }

    private fun familiar(isIt: (AtomInfo) -> Boolean) = FAMILIAR.filter { isIt(at(it)) }

    private fun make(type: QuizType, d: Deal): QuizQuestion? = when (type) {
        QuizType.MELT -> compare(type, d, { it.melt }, 1.3,
            "Which has the higher melting point?", "Which has the lower melting point?", ::celsius)
        QuizType.BOIL -> compare(type, d, { it.boil }, 1.3,
            "Which has the higher boiling point?", "Which has the lower boiling point?", ::celsius)
        // Gases left out: their densities are tiny numbers that make the answer obvious.
        QuizType.DENSE -> compare(type, d, { if (it.phase == "Gas") null else it.density }, 1.5,
            "Which is denser?", "Which is less dense?", ::density)
        QuizType.FIRST -> compare(type, d, { it.year?.toDouble() }, 1.0,
            "Which was discovered more recently?", "Which was discovered first?", { it.toInt().toString() })
            ?.takeIf { q -> q.atoms.map { at(it).year!! }.let { (a, b) -> abs(a - b) >= 40 } }
        QuizType.CRUST -> compare(type, d, { it.crust }, 10.0,
            "Which is more common in Earth's crust?", "Which is rarer in Earth's crust?", ::share,
            familiar { (it.crust ?: 0.0) > 0 })
        QuizType.SEA -> compare(type, d, { it.sea }, 10.0,
            "Which is more common in seawater?", "Which is rarer in seawater?", ::share,
            familiar { (it.sea ?: 0.0) > 0 })
        QuizType.RADIOACTIVE -> oneOf(type, d, familiar { it.stableIsotopes == 0 }, familiar { it.stableIsotopes > 0 }, 2,
            "Which is radioactive?",
            { if (it.stableIsotopes == 0) "No stable isotopes" else "${it.stableIsotopes} stable isotope${if (it.stableIsotopes == 1) "" else "s"}" })
        QuizType.NOBLE -> oneOf(type, d, familiar { it.series == "Noble gases" }, familiar { it.series != "Noble gases" }, 2,
            "Which is a noble gas?", ::capKind)
        // The place-named atom may be any of them; the other is a well-known atom named otherwise.
        QuizType.PLACE -> oneOf(type, d, PLACE_NAMES.keys.sorted(), FAMILIAR.filter { it !in PLACE_NAMES }, 2,
            "Which is named after a place?", { e -> PLACE_NAMES[e.z]?.let { "Named after $it" } ?: "" })
        QuizType.KIND4 -> {
            val series = d.pick(listOf("Halogens", "Alkali metals", "Noble gases", "Alkaline earth metals"))!!
            val one = at(familiar { it.series == series }[0])
            oneOf(type, d, familiar { it.series == series }, familiar { it.series != series }, 4,
                "Which of these is ${aKind(one)}?", ::capKind)
        }
        QuizType.LIQUID4 -> oneOf(type, d, familiar { it.phase == "Liquid" }, familiar { it.phase == "Solid" || it.phase == "Gas" }, 4,
            "Which of these is a liquid at room temperature?", { it.phase ?: "" })
        QuizType.FIRST4 -> {
            val found = familiar { it.year != null }
            oneOf(type, d, found, found, 4, "Which of these was discovered first?", { e -> e.year?.let { "Found in $it" } ?: "Ancient" })
                ?.let { q ->
                    val years = q.atoms.map { at(it).year!! }
                    val first = years.min()
                    // The earliest must stand clear of the rest, by 30 years.
                    if (years.count { it < first + 30 } != 1) null else q.copy(answer = years.indexOf(first))
                }
        }
        QuizType.LIFE4 -> oneOf(type, d, LIFE, NOT_LIFE, 4, "Which of these is in every living thing?", { "" },
            "Hydrogen, carbon, nitrogen, oxygen, phosphorus and sulfur are in every living thing.")
        QuizType.PHASE -> {
            // Most atoms are solids: pick the answer's state first, so gases and liquids come up too.
            val p = d.pick(listOf("Solid", "Solid", "Gas", "Gas", "Liquid"))!!
            val e = at(d.pick(familiar { it.phase == p })!!)
            val other = d.pick(PHASES.filter { it != p })!!
            val both = PHASES.filter { it == p || it == other }
            QuizQuestion(type, "What is ${name(e.z).lowercase()} at room temperature?",
                both.map { QuizOption.Word(it) }, if (both[0] == p) 0 else 1, listOf("", ""), e.z,
                "${name(e.z)} melts at ${celsius(e.melt!!)} and boils at ${celsius(e.boil!!)}.")
        }
        QuizType.STATE_TRUE -> {
            val p = d.pick(listOf("Solid", "Gas", "Gas", "Liquid", "Liquid"))!!
            val e = at(d.pick(familiar { it.phase == p })!!)
            // Half the time the statement is true; otherwise it names another state.
            val said = if (d.rnd() < 0.5) p else d.pick(PHASES.filter { it != p })!!
            QuizQuestion(type, "True or false: ${name(e.z)} is a ${said.lowercase()} at room temperature.",
                listOf(QuizOption.Word("True"), QuizOption.Word("False")), if (said == p) 0 else 1, listOf("", ""), e.z,
                "${name(e.z)} is a ${p.lowercase()}: it melts at ${celsius(e.melt!!)} and boils at ${celsius(e.boil!!)}.")
        }
        QuizType.YEAR -> {
            val e = at(d.pick(FAMILIAR)!!)
            val y = e.year
            if (y == null) null else {
                val step = 40 + floor(d.rnd() * 80).toInt()
                val off = step * (if (d.rnd() < 0.5) -1 else 1)
                val other = if (y + off > 2010 || y + off < 1600) y - off else y + off
                if (other > 2010 || other < 1600) null else {
                    val a = min(y, other); val b = max(y, other)
                    QuizQuestion(type, "When was ${name(e.z).lowercase()} discovered?",
                        listOf(QuizOption.Word("$a"), QuizOption.Word("$b")), if (a == y) 0 else 1, listOf("", ""), e.z, foundNote(e))
                }
            }
        }
        QuizType.ERA -> {
            val e = at(d.pick(FAMILIAR)!!)
            val y = e.year
            if (y == null) null else {
                // A round year 20 to 100 years from the real one.
                val line = d.pick(listOf(1700, 1750, 1800, 1850, 1900, 1950).filter { abs(it - y) in 20..100 })
                if (line == null) null else QuizQuestion(type, "Was ${name(e.z).lowercase()} discovered before or after $line?",
                    listOf(QuizOption.Word("Before $line"), QuizOption.Word("After $line")), if (y < line) 0 else 1,
                    listOf("", ""), e.z, foundNote(e))
            }
        }
        QuizType.METAL -> {
            val e = at(d.pick(FAMILIAR)!!)
            if (e.series == "Metalloids") null
            else QuizQuestion(type, "Is ${name(e.z).lowercase()} a metal?", listOf(QuizOption.Word("Yes"), QuizOption.Word("No")),
                if (e.series in AtomInfo.METALS) 0 else 1, listOf("", ""), e.z, "${name(e.z)} is ${aKind(e)}.")
        }
    }

    /** Two atoms compared on a value, the answer the higher or the lower one. */
    private fun compare(
        type: QuizType, d: Deal, value: (AtomInfo) -> Double?, minRatio: Double,
        higher: String, lower: String, show: (Double) -> String, pool: List<Int> = FAMILIAR,
    ): QuizQuestion? {
        val a = d.pick(pool)!!; val b = d.pick(pool)!!
        val va = value(at(a)); val vb = value(at(b))
        if (a == b || va == null || vb == null || max(va, vb) / min(va, vb) < minRatio) return null
        val high = d.rnd() < 0.5
        val answer = if ((va > vb) == high) 0 else 1
        return QuizQuestion(type, if (high) higher else lower, listOf(QuizOption.Card(a), QuizOption.Card(b)), answer, listOf(show(va), show(vb)))
    }

    /** The one atom that is something among others that aren't: two cards, or four. */
    private fun oneOf(
        type: QuizType, d: Deal, yes: List<Int>, no: List<Int>, n: Int, text: String,
        show: (AtomInfo) -> String, note: String? = null,
    ): QuizQuestion? {
        if (yes.isEmpty() || no.size < n - 1) return null
        val zs = mutableListOf(d.pick(yes)!!)
        var tries = 0
        while (zs.size < n && tries < 100) { tries++; val o = d.pick(no)!!; if (o !in zs) zs += o }
        if (zs.size < n) return null
        val y = zs[0]
        zs.sort()
        // Two cards keep a random order; four sit in order of atomic number like the atom of the day.
        if (n == 2 && d.pick(listOf(true, false))!!) zs.reverse()
        return QuizQuestion(type, text, zs.map { QuizOption.Card(it) }, zs.indexOf(y), zs.map { show(at(it)) }, note = note)
    }

    private fun foundNote(e: AtomInfo): String {
        val who = e.who ?: ""
        val by = if (who.isNotEmpty() && !who.contains("ancient", ignoreCase = true)) " by ${people(who)}" else ""
        return "${name(e.z)} was found in ${e.year}$by."
    }

    companion object {
        private val TYPES = QuizType.entries.toList()
        private val PHASES = listOf("Solid", "Liquid", "Gas")

        /** Atoms most people have heard of. */
        val FAMILIAR = listOf(1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 22, 24, 26, 27, 28, 29, 30,
            35, 36, 47, 50, 53, 54, 74, 78, 79, 80, 82, 86, 88, 92, 94)

        /** Atoms named after a place, and the place. */
        val PLACE_NAMES = mapOf(
            12 to "Magnesia, in Greece", 21 to "Scandinavia", 25 to "Magnesia, in Greece", 29 to "Cyprus", 31 to "France (Gallia)",
            32 to "Germany", 38 to "Strontian, in Scotland", 39 to "Ytterby, in Sweden", 44 to "Russia (Ruthenia)", 63 to "Europe",
            65 to "Ytterby, in Sweden", 67 to "Stockholm (Holmia)", 68 to "Ytterby, in Sweden", 69 to "Thule, the far north",
            70 to "Ytterby, in Sweden", 71 to "Paris (Lutetia)", 72 to "Copenhagen (Hafnia)", 75 to "the Rhine", 84 to "Poland",
            87 to "France", 95 to "the Americas", 97 to "Berkeley, California", 98 to "California", 105 to "Dubna, in Russia",
            108 to "Hesse, in Germany", 110 to "Darmstadt, in Germany", 113 to "Japan (Nihon)", 115 to "Moscow", 117 to "Tennessee",
        )

        /** In every living thing: the six elements all life is built from. */
        val LIFE = listOf(1, 6, 7, 8, 15, 16)
        /** Elements no living thing needs. */
        val NOT_LIFE = listOf(2, 10, 18, 36, 54, 86, 79, 78, 82, 80, 92, 94, 88, 22, 13, 3, 47)

        /** The phone's seeded(): mulberry32 with the seed spread first (Math.imul(seed, 2654435761)),
         *  which the core's seeded() leaves out. */
        fun phoneSeeded(seed: Int): () -> Double = seeded(seed * 2654435761L.toInt())

        /** "an alkali metal", "a halogen". */
        fun aKind(e: AtomInfo) = "${if (e.kind.first() in "aeiou") "an" else "a"} ${e.kind}"
        private fun capKind(e: AtomInfo) = e.kind.replaceFirstChar { it.uppercase() }

        /** Discoverers as a sentence reads them: "A, B and C". */
        fun people(who: String): String {
            if (who.contains(" and ")) return who
            val xs = who.split(Regex(",\\s*"))
            return if (xs.size < 2) xs.joinToString("") else xs.dropLast(1).joinToString(", ") + " and " + xs.last()
        }

        /** Kelvin as the phone writes Celsius: "1,538 °C", "−39 °C". */
        fun celsius(k: Double): String {
            val c = k - 273.15
            val r = Math.round(c)
            // JS rounds -0.4 to -0, and writes it with its sign.
            val s = if (c < 0 && r == 0L) "-0" else String.format(java.util.Locale.US, "%,d", r)
            return "${s.replaceFirst(Regex("^-"), "−")} °C"
        }

        /** "7.87 g/cm³", "19.3 g/cm³", "0.53 g/cm³". */
        fun density(d: Double): String {
            val s = if (d >= 1) BigDecimal(d).setScale(if (d >= 10) 1 else 2, RoundingMode.HALF_UP).toPlainString()
            else BigDecimal(d).round(MathContext(2, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()
            return "$s g/cm³"
        }

        /** How much of something there is, from mg/kg: "46%", "56 parts per million". */
        fun share(mgPerKg: Double): String {
            // Two significant figures, then at most four decimals, grouped: the phone's
            // Number(v.toPrecision(2)).toLocaleString('en-US', { maximumFractionDigits: 4 }).
            fun n(v: Double): String {
                val b = BigDecimal(v).round(MathContext(2, RoundingMode.HALF_UP)).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
                val plain = if (b.signum() == 0) "0" else b.toPlainString()
                val whole = plain.substringBefore('.')
                val frac = plain.substringAfter('.', "")
                val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
                return if (frac.isEmpty()) grouped else "$grouped.$frac"
            }
            fun parts(v: Double, per: String) = n(v).let { "$it part${if (it == "1") "" else "s"} per $per" }
            return when {
                mgPerKg >= 1000 -> "${n(mgPerKg / 1e4)}%"
                mgPerKg >= 1 -> parts(mgPerKg, "million")
                else -> parts(mgPerKg * 1000, "billion")
            }
        }
    }
}

/**
 * What the player has answered of Nu's questions on `day` (the phone's day number), right or
 * wrong in order, and their streak: days in a row with the quiz finished, the last such day in
 * `streakDay`. Plain data; the app stores it.
 */
data class QuizDay(val day: Int, val answers: List<Boolean> = emptyList(), val streak: Int = 0, val streakDay: Int = Int.MIN_VALUE) {
    /** The state on `today`: a past day's answers are dropped, the streak kept. */
    fun on(today: Int): QuizDay = if (today == day) this else QuizDay(today, emptyList(), streak, streakDay)

    /** Done with the day's `count` questions. */
    fun done(count: Int = 3) = answers.size >= count

    val right get() = answers.count { it }

    /** The streak to show: it lapses once a whole day passes without the quiz. */
    val liveStreak get() = if (streakDay == day || streakDay == day - 1) streak else 0

    /** One more answer; the last of the day's `count` finishes the quiz. */
    fun answer(right: Boolean, count: Int = 3): QuizDay {
        if (done(count)) return this
        val s = copy(answers = answers + right)
        return if (s.done(count)) s.finish() else s
    }

    /** Counts the quiz finished today (once): the streak grows from yesterday's, or starts at 1. */
    fun finish(): QuizDay {
        if (streakDay == day) return this
        return copy(streak = if (streakDay == day - 1) streak + 1 else 1, streakDay = day)
    }

    /** The phone's line beside "Nu asks": "Streak: 3 days", or "Start a streak". */
    val streakLabel get() = liveStreak.let { if (it > 0) "Streak: $it day${if (it == 1) "" else "s"}" else "Start a streak" }
}
