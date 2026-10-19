package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** How long Nu takes to drop back to the new wave after a catch. */
private const val BACK_MS = 260.0

/** The calm beat between levels: the next wave waits this long before it spreads. */
private const val CALM_MS = 700.0

/** The first wave waits this long, so the player can settle before it spreads. */
private const val FIRST_MS = 1400.0

/** How much longer each wave takes than it did (and so the room between one and the next): a
 *  quarter. */
private const val SPACING = 1.25

/**
 * Pulse (the phone's src/nu/court.ts). A wave spreads out from the nucleus, Nu riding it at the
 * top. Tap as it crosses the marked band. Each catch moves the band; a miss, or letting the wave
 * go by, loses the run. Nu bounces on a catch as far as the tap was off: dead on, it hardly moves.
 * A level is a set of catches (14, and one more every third level); then a short calm and the
 * next wave. Heat quickens the wave and narrows the band, a little with every catch too, so the
 * window to tap in shrinks from about 710 ms at Easy (580 ms at Medium, 480 ms at Hard) to about
 * 150 ms in late free play: gentler than the phone, for the watch's small court. Each wave is a
 * quarter slower than it first was (`SPACING`), so the waves come further apart.
 * The first wave waits a moment before it spreads.
 * On the phone the court is drawn bigger than the screen, its sides cropped; on the watch it is
 * just the round court. A tap anywhere counts, as on the phone. The band's outer edge stays
 * within 0.8 of the court, so Nu is never under the shell's menu at the top when the tap counts.
 */
class Pulse : Toy {
    private var r = 0.0
    private var speed = 0.09
    private var bandR = 0.0
    private var bandW = 0.0
    /** Catches in this level. */
    private var caught = 0
    private var over = false
    private var hitAt = 0.0
    /** The last catch, for the band's green flash. */
    private var caughtAt = -1e9
    /** The clock isn't known until the first step: Nu's first glide onto the wave starts there. */
    private var begun = false
    /** The wave holds still until then: the calm between levels. */
    private var calm = 0.0
    /** The last catch: where Nu was, and how far off the tap was (0 dead on .. 1 the band's edge). */
    private var from = Pt(0.0, 0.0)
    private var off = 0.0

    /** Catches to clear the level. */
    private fun need(s: Stage) = 14 + s.level / 3

    /** The heat, climbing a little with each catch toward the next level's. */
    private fun h(s: Stage) = s.heat + HEAT_PER_LEVEL * s.part

    override fun enter(s: Stage) {
        s.nu.r = 8.0
        s.nu.scale = 1.0
        place(s)
        r = s.R * 0.1
        // From wherever Nu is onto the wave, smoothly.
        from = s.nu.p
    }

    private fun place(s: Stage) {
        val h = h(s)
        speed = min(0.2, 0.07 + 0.05 * h) / SPACING
        bandW = max(12.0, 37 / (1 + 0.75 * h))
        bandR = s.R * 0.3 + rnd() * max(0.0, s.R * 0.5 - bandW)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        if (!begun) { begun = true; hitAt = now; calm = now + FIRST_MS }
        val nu = s.nu
        if (over) {
            nu.seek(Pt(s.c.x, s.c.y - s.R * 0.1), dt, 0.4, 0.02)
            return
        }
        if (now >= calm) r += speed * dt
        if (r > s.R) {
            // Let it go by: that's a miss too.
            end(s)
            return
        }
        val rideX = s.c.x
        val rideY = s.c.y - r
        val t = now - hitAt
        if (t < BACK_MS) {
            // Back down to the new wave, smoothly, no overshoot.
            val k = 1 - (1 - t / BACK_MS).pow(3)
            nu.x = from.x + (rideX - from.x) * k
            nu.y = from.y + (rideY - from.y) * k
            nu.vx = 0.0
            nu.vy = 0.0
            return
        }
        // Then it bounces on the wave, as much as the tap was off.
        val u = t - BACK_MS
        val amp = (2 + off * 26) * exp(-u / 320)
        val b = amp * abs(sin(u / 70))
        nu.x = rideX
        nu.y = rideY - b
        nu.vx = 0.0
        nu.vy = 0.0
        nu.scale = 1 + if (amp > 1) (0.5 - abs(sin(u / 70))) * 0.012 * amp else 0.0
    }

    private fun end(s: Stage) {
        over = true
        s.nu.scale = 1.0
        s.fx.ring(s.c, Ink.RED, r * 0.9, r + 10, 500.0)
        s.out.miss()
        s.out.buzz(25)
        s.lose()
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val glow = clamp(1 - (now - caughtAt) / 400, 0.0, 1.0)
        // The band: a wide soft ring, green just after a catch.
        pen.ring(s.c, bandR, if (glow > 0) Ink.GREEN else Ink.CYAN, bandW * 2, 0.18 + glow * 0.3)
        pen.ring(s.c, bandR, Ink.CYAN, 2.0, 0.6)
        pen.dot(s.c, 8.0, Ink.K300, 0.85)
        if (!over && now >= calm) pen.ring(s.c, r, Ink.WHITE, 3.0, 0.85)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (over || s.now < calm) return
        if (abs(r - bandR) <= bandW) {
            caught++
            hitAt = s.now
            caughtAt = s.now
            off = abs(r - bandR) / bandW
            from = s.nu.p
            s.fx.ring(s.c, Ink.GREEN, r, r + 16, 450.0)
            s.out.capture()
            s.part = caught.toDouble() / need(s)
            if (caught >= need(s)) {
                caught = 0
                s.clear()
                s.out.buzz(14)
                if (s.over) { over = true; return }
                calm = s.now + CALM_MS
            }
            place(s)
            r = s.R * 0.1
            return
        }
        end(s)
    }
}
