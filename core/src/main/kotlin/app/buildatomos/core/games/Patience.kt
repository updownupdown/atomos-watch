package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** The warning before Nu moves by itself: three pale pulses, this long in all (ms). */
internal const val PULSES_MS = 1500.0

/**
 * Nu's patience with a player who has stopped playing, for a game that waits on a move (Cross):
 * after `wait` ms with no touch and no turn of the crown, Nu pulses pale three times, and then the
 * game makes the move itself. It is the signal Fuse uses instead of a clock on the rim: quiet, and
 * never seen by a player who keeps playing. Every touch or turn starts it over.
 */
internal class Patience {
    private var left = Double.MAX_VALUE

    /** Starts the wait over. */
    fun reset(wait: Double) { left = wait }

    /** Stops it until the next reset (while there is nothing to wait for). */
    fun hold() { left = Double.MAX_VALUE }

    /** True once, the frame the wait runs out. */
    fun step(dt: Double): Boolean {
        if (left == Double.MAX_VALUE) return false
        left -= dt
        if (left > 0) return false
        left = Double.MAX_VALUE
        return true
    }

    /** How pale Nu is now, 0..1: three pulses over the wait's last PULSES_MS. */
    val pale: Double get() {
        if (left == Double.MAX_VALUE || left > PULSES_MS) return 0.0
        val t = (PULSES_MS - left) / (PULSES_MS / 3)
        val f = t - floor(t)
        return sin(f * PI).let { it * it }
    }
}

/** Nu drawn pale by `k` (0..1): a white wash over it, for Patience's pulses. */
internal fun Pen.pale(nu: Body, k: Double) {
    if (k <= 0.01 || nu.hidden) return
    dot(nu.x, nu.y, nu.r * nu.scale + 1.5, Ink.WHITE, 0.62 * k)
}
