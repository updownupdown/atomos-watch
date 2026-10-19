// The difficulty dial, as on the phone (src/puzzles/difficulty.ts): one scale from 1 to 100 for
// every game. The watch shows it in three levels, Easy (41 to 60), Medium (61 to 80) and Hard
// (81 to 100), each with its color: the phone's top three, since the two below them were too
// easy to be worth playing. A dial under 41 still plays (the tests use it) and reads as Easy. A
// run for an atom is played at that atom's dial; free play at the level the player picks.

package app.buildatomos.core

import kotlin.math.pow
import kotlin.math.roundToInt

object Difficulty {
    const val LEVELS = 3
    val WORDS = listOf("Easy", "Medium", "Hard")
    /** Green, yellow, red. */
    val COLORS = intArrayOf(0xFF4FD67A.toInt(), 0xFFF4D545.toInt(), 0xFFF05A4A.toInt())

    /** The level (0 Easy .. 2 Hard) a dial falls in. */
    fun level(dial: Int): Int = when {
        dial <= 60 -> 0
        dial <= 80 -> 1
        else -> 2
    }

    /** The dial free play uses for a level: the middle of its band (50, 70, 90). */
    fun dialOf(level: Int): Int = 50 + level.coerceIn(0, LEVELS - 1) * 20

    fun word(dial: Int) = WORDS[level(dial)]
    fun color(dial: Int) = COLORS[level(dial)]

    /** The dial an atom's boards are dealt at: 41 for hydrogen, 54 for iron, 65 for xenon, 77 for
     *  radon, 88 for oganesson, on the phone's curve (which ran from 5 to 75) moved up into the
     *  three levels. */
    fun atomDial(z: Int): Int {
        val t = (z.coerceIn(1, ATOMS) - 1) / 117.0
        return (41 + 47 * t.pow(0.85)).roundToInt()
    }

    /** The dial for building shell `shell` (0 the innermost) of atom `z`: the atom's own dial, and
     *  a little more for each shell further out, so an atom's outer shell is its hardest
     *  (oganesson's seventh at 100). */
    fun buildDial(z: Int, shell: Int): Int = (atomDial(z) + 2 * shell).coerceIn(1, 100)

    /** Levels to clear to win a run at `dial`: 3 at Easy, 4 at Medium, 5 at Hard. */
    fun goal(dial: Int): Int = 3 + level(dial)

    /** The level a save from the five-level watch (0 Very easy .. 4 Expert) stands for now: its
     *  Medium, Hard and Expert are today's Easy, Medium and Hard; its Very easy and Easy have no
     *  level now (null). */
    fun fromFiveLevels(old: Int): Int? = if (old in 2..4) old - 2 else null

    /** Free play's bests from the five-level save (`atomos.levels.v1`, "<game>.<0..4>" to the best
     *  level reached), keyed by today's levels. Bests at Very easy and Easy are dropped: they were
     *  played far easier than anything now. */
    fun bestsFromFiveLevels(old: Map<String, Int>): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        for ((key, best) in old) {
            val dot = key.lastIndexOf('.')
            if (dot <= 0) continue
            val level = key.substring(dot + 1).toIntOrNull()?.let(::fromFiveLevels) ?: continue
            out["${key.substring(0, dot)}.$level"] = best
        }
        return out
    }

    /** The levels last picked for free play from the five-level save (`atomos.picked.v1`, "<game>"
     *  to 0..4), as today's: Very easy and Easy become Easy. */
    fun pickedFromFiveLevels(old: Map<String, Int>): Map<String, Int> =
        old.mapValues { (_, level) -> fromFiveLevels(level.coerceIn(0, 4)) ?: 0 }
}
