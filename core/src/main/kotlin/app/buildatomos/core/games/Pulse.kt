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

/**
 * Pulse (the phone's src/nu/court.ts). A wave spreads out from the nucleus, Nu riding it at the
 * top. Tap as it crosses the marked band. Each catch moves the band, narrows it and quickens the
 * wave; a miss, or letting the wave go by, ends the run. Nu bounces on a catch as far as the tap
 * was off: dead on, it hardly moves.
 * On the phone the court is drawn bigger than the screen, its sides cropped; on the watch it is
 * just the round court. A tap anywhere counts, as on the phone.
 */
class Pulse : Toy {
    private var r = 0.0
    private var speed = 0.09
    private var bandR = 0.0
    private var bandW = 0.0
    private var streak = 0
    private var over = false
    private var hitAt = 0.0
    /** The clock isn't known until the first step: Nu's first glide onto the wave starts there. */
    private var begun = false
    /** The last catch: where Nu was, and how far off the tap was (0 dead on .. 1 the band's edge). */
    private var from = Pt(0.0, 0.0)
    private var off = 0.0

    override fun enter(s: Stage) {
        s.nu.r = 8.0
        s.nu.scale = 1.0
        place(s)
        r = s.R * 0.1
        // From wherever Nu is onto the wave, smoothly.
        from = s.nu.p
        hitAt = s.now
        s.score(0)
    }

    private fun place(s: Stage) {
        bandW = max(10.0, 34 - streak * 1.5)
        bandR = s.R * (0.32 + rnd() * 0.52)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        if (!begun) { begun = true; hitAt = now }
        val nu = s.nu
        if (over) {
            nu.seek(Pt(s.c.x, s.c.y - s.R * 0.1), dt, 0.4, 0.02)
            return
        }
        r += speed * dt
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
        s.score(streak)
        finish(s, "pulse", streak)
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        val glow = if (streak > 0) clamp(1 - (now - hitAt) / 400, 0.0, 1.0) else 0.0
        // The band: a wide soft ring, green just after a catch.
        pen.ring(s.c, bandR, if (glow > 0) Ink.GREEN else Ink.CYAN, bandW * 2, 0.18 + glow * 0.3)
        pen.ring(s.c, bandR, Ink.CYAN, 2.0, 0.6)
        pen.dot(s.c, 8.0, Ink.K300, 0.85)
        if (!over) pen.ring(s.c, r, Ink.WHITE, 3.0, 0.85)
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        if (over) return
        if (abs(r - bandR) <= bandW) {
            streak++
            hitAt = s.now
            off = abs(r - bandR) / bandW
            from = s.nu.p
            s.fx.ring(s.c, Ink.GREEN, r, r + 16, 450.0)
            s.out.capture()
            if (streak % 10 == 0) { s.out.complete(); s.out.buzz(14) }
            speed = min(0.26, speed + 0.006)
            place(s)
            r = s.R * 0.1
            s.score(streak)
            return
        }
        end(s)
    }
}
