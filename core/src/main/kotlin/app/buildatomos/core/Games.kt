// The watch's games, A to Z, and their points. Every game is one of Nu's park games from the
// phone, played on the round court; `crown` marks the ones the crown steers.

package app.buildatomos.core

import app.buildatomos.core.games.*
import kotlin.math.roundToInt

class Game(
    val id: String,
    val label: String,
    /** About what a fair run reaches, in the game's own count (the phone's src/nu/points.ts). */
    val par: Double,
    /** The crown steers it. */
    val crown: Boolean = false,
    val make: () -> Toy,
)

object Games {
    val all: List<Game> = listOf(
        Game("dive", "Dive", 5.0) { Dive() },
        Game("echo", "Echo", 6.0) { Echo() },
        Game("flash", "Flash", 20.0) { Flash() },
        Game("halo", "Halo", 18.0, crown = true) { Halo() },
        Game("orbit", "Orbit", 12.0, crown = true) { Orbit() },
        Game("pulse", "Pulse", 8.0) { Pulse() },
        Game("ready", "Ready", 10.0) { Ready() },
        Game("recall", "Recall", 5.0) { Recall() },
        Game("shells", "Shells", 4.0) { Shells() },
    ).sortedBy { it.label }

    fun byId(id: String): Game? = all.firstOrNull { it.id == id }

    /** A run's points: 10 for a par run, more or less in proportion, one decimal. */
    fun points(id: String, raw: Double): Double {
        val g = byId(id) ?: return 0.0
        if (!raw.isFinite() || raw <= 0) return 0.0
        return (raw / g.par * 100).roundToInt() / 10.0
    }

    /** The game Nu fancies on day `day` (days since 1970): the same all day, a different one most days. */
    fun nuPick(day: Long): Game = all[(((day * 5 + 3) % all.size + all.size) % all.size).toInt()]
}
