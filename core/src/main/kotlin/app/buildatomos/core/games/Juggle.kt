package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Juggle's fall: gentle at first, a little stronger with each kick, up to a cap (units/ms²). */
private const val G = 0.0007
private const val G_MORE = 0.00002
private const val G_TOP = 0.0012
/** Juggle moves in small fixed steps, so its arc is the same at any frame rate. */
private const val STEP = 4.0
/** The rim is the floor where its way out points this far down (as on the phone's corners). */
private const val FLOOR_NY = 0.6
/** How long Nu waits in the air for the first kick before it drops by itself (ms). */
private const val WAIT = 4000.0

/**
 * Juggle (the phone's src/nu/yard.ts). Keep Nu in the air: tap near it to kick it up, and off to
 * the side away from where you tapped. Each kick counts; it falls a little faster as you go.
 * On the watch the court is the circle: its rim is the walls and the ceiling, and Nu bounces off
 * it; the bottom part of the rim (where it faces more than about 37 degrees below level, a faint
 * red arc) is the floor, and touching it ends the run. A kick sends Nu up 0.8 of the court's
 * radius (the phone's 0.8 of half the arena), without the phone's 180 floor, which would throw
 * it into the ceiling every time on a screen this small. Left alone at the start, Nu waits four
 * seconds and then drops by itself, so a run nobody plays still ends.
 */
class Juggle : Toy {
    private enum class State { READY, RUN, OVER }
    private val b = Mover(0.0, 0.0)
    private var state = State.READY
    private var count = 0
    private var kickAt = -1e9
    /** The kick's speed, eased into over a few frames rather than all at once. */
    private var kick: Pt? = null
    private var spare = 0.0
    private var waited = 0.0

    override fun enter(s: Stage) {
        s.nu.r = 13.0
        s.nu.at(Pt(s.c.x, s.c.y - s.R * 0.25))
        s.score(0)
    }

    private fun g() = min(G_TOP, G + count * G_MORE)

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        if (state == State.READY) {
            nu.seek(Pt(s.c.x, s.c.y - s.R * 0.25 + sin(now / 400) * 6), dt, 0.5, 0.01)
            b.x = nu.x; b.y = nu.y; b.vx = 0.0; b.vy = 0.0
            waited += dt
            // Nobody kicked: it drops, and can still be kicked on the way down.
            if (waited < WAIT) return
            state = State.RUN
        }
        if (state == State.OVER) return
        spare += dt
        while (spare >= STEP && state == State.RUN) {
            spare -= STEP
            tick(s, STEP, now)
        }
        nu.x = b.x
        nu.y = b.y
        nu.vx = b.vx
        nu.vy = b.vy
        nu.scale += (1 - nu.scale) * min(1.0, dt / 180)
    }

    private fun tick(s: Stage, dt: Double, now: Double) {
        val r = s.nu.r
        val k0 = kick
        if (k0 != null) {
            // Most of the kick lands within about 60 ms.
            val k = 1 - exp(-dt / 22)
            b.vx += (k0.x - b.vx) * k
            b.vy += (k0.y - b.vy) * k
            if (abs(k0.y - b.vy) < 0.01) kick = null
        } else b.vy += g() * dt
        // A little air, so sideways drift settles.
        b.vx *= exp(-dt / 5000)
        b.x += b.vx * dt
        b.y += b.vy * dt
        val (d, _, ny) = edge(s, Pt(b.x, b.y))
        if (d + r > 0 && ny > FLOOR_NY) {
            val p = inside(s, Pt(b.x, b.y), r)
            b.x = p.x; b.y = p.y
            state = State.OVER
            s.fx.ring(Pt(b.x, b.y + r), Ink.RED, 6.0, 34.0, 600.0)
            s.out.miss()
            s.out.buzz(22)
            finish(s, "juggle", count)
            s.score(count)
        } else if (bounceIn(s, b, r, 0.85)) {
            s.out.tick(0)
            // Off the ceiling, the kick's done.
            kick = null
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        // The floor, faintly red along the bottom of the rim: Nu mustn't land there.
        val a0 = asin(FLOOR_NY)
        pen.arc(s.c.x, s.c.y, s.R - 4, a0, Math.PI - 2 * a0, Ink.RED, 8.0, 0.22)
        val since = now - kickAt
        if (since in 0.0..260.0) pen.ring(s.nu.p, s.nu.r + 4 + since / 14, Ink.WHITE, 2.0, 0.5 * (1 - since / 260))
        s.nu.draw(pen, now)
    }

    override fun down(s: Stage, p: Pt) {
        val nu = s.nu
        if (state == State.OVER) return
        if (dist(p, nu.p) > nu.r + 48) return
        state = State.RUN
        // Up about 0.8 of the court's radius, whatever the fall: v² = 2gh.
        val rise = s.R * 0.8
        val vy = -sqrt(2 * g() * rise)
        // A falling Nu stops at once, then eases up to the kick's speed.
        b.vy = min(b.vy, 0.0)
        kick = Pt(clamp(b.vx * 0.5 + (nu.x - p.x) * 0.01, -0.32, 0.32), vy)
        count++
        kickAt = s.now
        nu.scale = 0.88
        s.out.capture()
        if (count % 10 == 0) s.out.complete()
        s.score(count)
    }
}
