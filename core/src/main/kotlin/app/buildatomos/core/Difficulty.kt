// The difficulty dial, as on the phone (src/puzzles/difficulty.ts): one scale from 1 to 100 for
// every game, seen in five levels, Very easy (1 to 20), Easy, Medium, Hard and Expert (81 to 100),
// each with its color. A run for an atom is played at that atom's dial; free play at the level
// the player picks.

package app.buildatomos.core

import kotlin.math.pow
import kotlin.math.roundToInt

object Difficulty {
    const val LEVELS = 5
    val WORDS = listOf("Very easy", "Easy", "Medium", "Hard", "Expert")
    /** L1..L5: blue, green, yellow, orange, red. */
    val COLORS = intArrayOf(0xFF58A6FF.toInt(), 0xFF4FD67A.toInt(), 0xFFF4D545.toInt(), 0xFFF59A3C.toInt(), 0xFFF05A4A.toInt())

    /** The level (0 Very easy .. 4 Expert) a dial falls in. */
    fun level(dial: Int): Int = ((dial.coerceIn(1, 100) - 1) / 20)

    /** The dial free play uses for a level: the middle of its band. */
    fun dialOf(level: Int): Int = level.coerceIn(0, LEVELS - 1) * 20 + 10

    fun word(dial: Int) = WORDS[level(dial)]
    fun color(dial: Int) = COLORS[level(dial)]

    /** The dial an atom's boards are dealt at on the phone: 5 for hydrogen, 24 for iron, 41 for
     *  xenon, 58 for radon, 75 for oganesson. */
    fun atomDial(z: Int): Int {
        val t = (z.coerceIn(1, ATOMS) - 1) / 117.0
        return (5 + 70 * t.pow(0.85)).roundToInt()
    }

    /** The dial for building shell `shell` (0 the innermost) of atom `z`: the atom's own dial, and
     *  a little more for each shell further out, so an atom's outer shell is its hardest. */
    fun buildDial(z: Int, shell: Int): Int = (atomDial(z) + 2 * shell).coerceIn(1, 100)

    /** Levels to clear to win a run at `dial`: 3 at Very easy up to 7 at Expert. */
    fun goal(dial: Int): Int = 3 + level(dial)
}
