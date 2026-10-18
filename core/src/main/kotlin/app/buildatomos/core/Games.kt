// The watch's games, A to Z. Every game is one of Nu's park games from the phone, played on the
// round court in levels; `crown` marks the ones the crown steers.

package app.buildatomos.core

import app.buildatomos.core.games.*

class Game(
    val id: String,
    val label: String,
    /** The crown steers it. */
    val crown: Boolean = false,
    val make: () -> Toy,
)

object Games {
    val all: List<Game> = listOf(
        Game("blast", "Blast", crown = true) { Blast() },
        Game("dive", "Dive") { Dive() },
        Game("echo", "Echo") { Echo() },
        Game("flash", "Flash") { Flash() },
        Game("halo", "Halo", crown = true) { Halo() },
        Game("juggle", "Juggle") { Juggle() },
        Game("more", "More", crown = true) { More() },
        Game("orbit", "Orbit", crown = true) { Orbit() },
        Game("pulse", "Pulse") { Pulse() },
        Game("rain", "Rain", crown = true) { Rain() },
        Game("ready", "Ready") { Ready() },
        Game("recall", "Recall") { Recall() },
        Game("ripple", "Ripple") { Ripple() },
        Game("shells", "Shells") { Shells() },
        Game("spot", "Spot") { Spot() },
        Game("stick", "Stick") { Stick() },
        Game("tune", "Tune", crown = true) { Tune() },
    ).sortedBy { it.label }

    fun byId(id: String): Game? = all.firstOrNull { it.id == id }

    /** The game Nu fancies on day `day` (days since 1970): the same all day, a different one most days. */
    fun nuPick(day: Long): Game = all[(((day * 5 + 3) % all.size + all.size) % all.size).toInt()]

    /** The game a shell of an atom is built with: Nu's pick, moved on by every run played, so
     *  shell after shell brings a different game. */
    fun forRun(day: Long, runs: Int): Game = all[((all.indexOf(nuPick(day)) + runs) % all.size + all.size) % all.size]
}
