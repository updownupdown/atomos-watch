package app.buildatomos.core.games

import app.buildatomos.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Orbit (the phone's src/nu/park.ts). Nu runs round two rings like an electron with two shells
 * to choose from. Tap a ring: the one Nu is on turns it round (the ring flashes white), the other
 * hops it across (the ring flashes cyan). Catch the photons; an antiparticle (red) meeting Nu on
 * its ring ends the run, so when two close in from both sides, hop rings. Antiparticles fade
 * away after a while.
 * A level is a set of photons: six at first and one more every other level, up to twelve. A
 * level cleared sweeps the antiparticles away, and the next come after a breath. Heat makes Nu
 * run faster (and a little faster with each photon in a level), and the antiparticles come
 * sooner, more of them at once and quicker on their rings.
 * On the watch the crown can turn Nu round too: turn it the way you want Nu to run. Hops are
 * still a tap. The rings sit in far enough that Nu stays off the menu at the top and the level
 * counter at the bottom.
 */
class Orbit : Toy {
    private var a = -PI / 2
    private var dir = 1
    /** The ring Nu is on (0 outer, 1 inner), and how far through a hop (0 outer .. 1 inner). */
    private var lane = 0
    private var hop = 0.0
    private class Photon(val a: Double, val lane: Int)
    private class Anti(var a: Double, val lane: Int, val dir: Int, val born: Double)
    private val photons = mutableListOf<Photon>()
    private val anti = mutableListOf<Anti>()
    /** Photons caught this level. */
    private var count = 0
    private var over = false
    private var overAt = 0.0
    private var spawn = 2600.0
    /** The ring last tapped, lit for a moment in the colour of what the tap did. */
    private class Lit(val lane: Int, val c: Int, val at: Double)
    private var lit: Lit? = null
    /** How far the crown has turned one way since Nu last turned round. */
    private var wound = 0.0

    private fun rad(s: Stage, lane: Int) = s.R * (0.72 - lane * 0.3)
    private fun need(s: Stage) = 6 + min(s.level, 12) / 2
    /** Nu's speed round the rings (rad/ms): a lap in about six seconds at the easiest, three at
     *  Expert, never faster than a lap in two. */
    private fun speed(s: Stage) = min(0.0031, 0.001 + s.heat * 0.0011 + count * 0.00003)
    /** The most antiparticles out at once. */
    private fun most(s: Stage) = 1 + min(3, floor(s.heat * 2.2).toInt())
    private fun pos(s: Stage, a: Double, lane: Int) = polar(s, a, rad(s, lane))

    override fun enter(s: Stage) {
        s.nu.r = 9.0
        s.nu.at(s.c)
        photons += Photon(a + 1.6, 0)
        photons += Photon(a + 3.2, 1)
    }

    override fun step(s: Stage, dt: Double, now: Double) {
        val nu = s.nu
        if (over) { nu.seek(s.c, dt, 0.4, 0.01); return }
        val speed = speed(s)
        a += dir * speed * dt
        hop += clamp(lane - hop, -dt / 220, dt / 220)
        val r = rad(s, 0) + (rad(s, 1) - rad(s, 0)) * hop
        val at = polar(s, a, r)
        // Joins the ring smoothly, then rides it.
        if (dist(nu.p, at) > 6 && hop == lane.toDouble()) nu.seek(at, dt, 0.7, 0.03) else { nu.x = at.x; nu.y = at.y }
        val onLane = hop.roundToInt()
        fun near(b: Double, lane: Int): Boolean {
            if (lane != onLane || abs(hop - lane) > 0.35) return false
            val d = abs(((a - b) % TAU + TAU + PI) % TAU - PI)
            return d * rad(s, lane) < nu.r + 7
        }
        for (i in photons.indices.reversed()) {
            val q = photons[i]
            if (!near(q.a, q.lane)) continue
            photons.removeAt(i)
            count++
            s.fx.burst(at, 5, Ink.CYAN, 0.08)
            s.out.tick(count % 5)
            // A new photon somewhere ahead, on either ring.
            photons += Photon(a + dir * (1.2 + rnd() * 3.5), if (rnd() < 0.5) 0 else 1)
            if (count >= need(s)) {
                count = 0
                s.clear()
                if (s.over) { over = true; overAt = now; return }
                // The level's antiparticles go, and the next wait a moment.
                for (q in anti) s.fx.burst(pos(s, q.a, q.lane), 6, Ink.RED, 0.06)
                anti.clear()
                spawn = 1600.0
            }
            s.part = count.toDouble() / need(s)
        }
        spawn -= dt
        if (spawn <= 0 && anti.size < most(s)) {
            anti += Anti(a + PI, if (rnd() < 0.5) 0 else 1, if (rnd() < 0.5) 1 else -1, now)
            spawn = (3500 + rnd() * 3000) / (1 + s.heat * 0.8)
        }
        val drift = min(0.8, 0.45 + s.heat * 0.15)
        anti.removeAll { now - it.born >= LIFE }
        for (q in anti) {
            q.a += q.dir * speed * drift * dt
            // Harmless while it fades in.
            if (now - q.born > 700 && near(q.a, q.lane)) {
                over = true
                overAt = now
                s.lose()
                s.fx.ring(at, Ink.RED, 8.0, 32.0, 700.0)
                s.out.miss()
                return
            }
        }
    }

    override fun draw(s: Stage, pen: Pen, now: Double) {
        pen.ring(s.c, rad(s, 0), Ink.K500, 3.0, 0.55)
        val inner = rad(s, 1)
        // The inner ring, told from the outer by a lighter weight rather than dashes.
        pen.ring(s.c, inner, Ink.K500, 2.0, 0.32)
        // The ring just tapped, lit and fading.
        lit?.let { l ->
            val f = clamp(1 - (now - l.at) / 450, 0.0, 1.0)
            if (f > 0) pen.ring(s.c, rad(s, l.lane), l.c, 3 + 2.25 * f, 0.64 * f)
        }
        pen.dot(s.c, 10.0, Ink.K300, 0.5)
        for (q in photons) {
            val p = pos(s, q.a, q.lane)
            pen.glow(p.x, p.y, 5.0, Ink.CYAN, 0.6)
            pen.dot(p, 5.0, Ink.CYAN)
        }
        for (q in anti) {
            val age = now - q.born
            val fade = min(clamp(age / 700, 0.2, 1.0), clamp((LIFE - age) / 1200, 0.0, 1.0))
            pen.dot(pos(s, q.a, q.lane), 6.0, Ink.RED, fade)
        }
        if (over) pen.ring(s.c, 22 + sin((now - overAt) / 300) * 2, Ink.WHITE, 2.0, 0.5)
        s.nu.draw(pen, now)
    }

    /** A tap goes to the nearer ring: Nu's own turns it round, the other hops it across. */
    override fun down(s: Stage, p: Pt) {
        if (over) return
        val d = dist(p, s.c)
        val lane = if (abs(d - rad(s, 1)) < abs(d - rad(s, 0))) 1 else 0
        if (lane == this.lane) {
            turnRound(s, -dir)
        } else {
            this.lane = lane
            lit = Lit(lane, Ink.CYAN, s.now)
            s.out.tick(3)
        }
    }

    /** The crown: a little turn one way (clockwise or not) sets Nu running that way round. */
    override fun crown(s: Stage, by: Double) {
        if (over || by == 0.0) return
        if (by * wound < 0) wound = 0.0
        wound += by
        val want = if (wound > 0) 1 else -1
        if (abs(wound) >= 0.12 && want != dir) turnRound(s, want)
    }

    private fun turnRound(s: Stage, to: Int) {
        dir = to
        wound = 0.0
        lit = Lit(lane, Ink.WHITE, s.now)
        s.out.tick(1)
    }

    private companion object {
        const val LIFE = 16000.0
    }
}
