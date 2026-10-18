// The watch's games, A to Z. Most are Nu's park games from the phone, fitted to the watch; Core,
// Dial and Lanes are the watch's own. All are played on the round court in levels; `crown` marks
// the ones the crown steers.

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
        Game("core", "Core", crown = true) { Core() },
        Game("dial", "Dial", crown = true) { Dial() },
        Game("dive", "Dive") { Dive() },
        Game("dodge", "Dodge", crown = true) { Dodge() },
        Game("echo", "Echo") { Echo() },
        Game("flash", "Flash") { Flash() },
        Game("fuse", "Fuse", crown = true) { Fuse() },
        Game("gate", "Gate", crown = true) { Gate() },
        Game("grow", "Grow") { Grow() },
        Game("hop", "Hop", crown = true) { Hop() },
        Game("hue", "Hue", crown = true) { Hue() },
        Game("juggle", "Juggle") { Juggle() },
        Game("land", "Land") { Land() },
        Game("lanes", "Lanes", crown = true) { Lanes() },
        Game("more", "More", crown = true) { More() },
        Game("orbit", "Orbit", crown = true) { Orbit() },
        Game("order", "Order") { Order() },
        Game("pairs", "Pairs") { Pairs() },
        Game("pop", "Pop", crown = true) { Pop() },
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
    fun nuPick(day: Long): Game = all[(((day * STRIDE + 3) % all.size + all.size) % all.size).toInt()]

    /** How far Nu's pick moves on each day: 5 or more, and sharing no factor with the number of
     *  games, so every game comes round in turn. */
    private val STRIDE: Long = generateSequence(5L) { it + 1 }.first { gcd(it, all.size.toLong()) == 1L }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    /** The game a shell of an atom is built with: Nu's pick, moved on by every run played, so
     *  shell after shell brings a different game. */
    fun forRun(day: Long, runs: Int): Game = all[((all.indexOf(nuPick(day)) + runs) % all.size + all.size) % all.size]
}
