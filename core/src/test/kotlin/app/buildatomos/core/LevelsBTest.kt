package app.buildatomos.core

import app.buildatomos.core.games.Halo
import app.buildatomos.core.games.Juggle
import app.buildatomos.core.games.More
import app.buildatomos.core.games.Orbit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Halo, Juggle, More and Orbit in levels: small bots that play by what they see (the dots each
 * game draws, and Nu) clear levels, and heat makes each game harder.
 */
class LevelsBTest {
    /** What a game drew in one frame: its dots. */
    private class Seen : Pen {
        class Dot(val x: Double, val y: Double, val r: Double, val color: Int, val alpha: Double)
        val dots = mutableListOf<Dot>()
        override fun dot(x: Double, y: Double, r: Double, color: Int, alpha: Double) { dots += Dot(x, y, r, color, alpha) }
        override fun ring(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double) {}
        override fun arc(x: Double, y: Double, r: Double, start: Double, sweep: Double, color: Int, width: Double, alpha: Double) {}
        override fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double, alpha: Double) {}
        override fun poly(xs: DoubleArray, ys: DoubleArray, n: Int, color: Int, width: Double, alpha: Double) {}
        override fun dashed(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double, n: Int) {}
        override fun rect(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, alpha: Double) {}
        override fun rectRing(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, width: Double, alpha: Double) {}
        fun of(color: Int, r: Double) = dots.filter { it.color == color && abs(it.r - r) < 1e-6 }
        fun of(color: Int) = dots.filter { it.color == color }
    }

    /** Plays `t` frame by frame for up to `ms`; after each frame `bot` acts on what was drawn. */
    private fun run(t: Toy, dial: Int, goal: Int?, ms: Double, bot: (Stage, Seen) -> Unit = { _, _ -> }): Stage {
        val s = Stage()
        s.dial = dial
        s.goal = goal
        t.enter(s)
        var now = 1000.0
        s.now = now
        while (now < 1000 + ms && !s.over) {
            now += 16.0
            s.now = now
            t.step(s, 16.0, now)
            val seen = Seen()
            t.draw(s, seen, now)
            assertTrue(s.part in 0.0..1.0, "part ${s.part}")
            bot(s, seen)
        }
        return s
    }

    private fun angle(s: Stage, x: Double, y: Double) = atan2(y - s.c.y, x - s.c.x)
    private fun dist(s: Stage, d: Seen.Dot) = dist(d.x, d.y, s.c.x, s.c.y)
    /** How far ahead of `from` angle `to` is, going round the way `dir` says (0..2π). */
    private fun ahead(from: Double, to: Double, dir: Int) = (((to - from) * dir) % TAU + TAU) % TAU

    // ---- Halo: runs round to the photon that will cross the ring next. ----

    private fun halo(dial: Int, goal: Int?, ms: Double): Stage {
        val g = Halo()
        val track = COURT_R * 0.72
        return run(g, dial, goal, ms) { s, seen ->
            val next = seen.of(Ink.CYAN, 4.5).filter { it.alpha == 1.0 && dist(s, it) < track + 1 }.maxByOrNull { dist(s, it) }
            if (next != null) g.move(s, polar(s, angle(s, next.x, next.y), track))
        }
    }

    @Test fun haloClearsLevels() {
        val s = halo(1, 3, 180_000.0)
        assertEquals(Result.WON, s.result, "Very easy: ${s.level} levels")
        assertTrue(halo(100, 7, 180_000.0).level >= 1, "Expert clears a level")
    }

    @Test fun haloHeatThrowsHarder() {
        val easy = run(Halo(), 1, null, 600_000.0).now
        val hard = run(Halo(), 100, null, 600_000.0).now
        assertTrue(hard < easy * 0.8, "left alone, lost at $easy (dial 1) and $hard (dial 100)")
    }

    // ---- Juggle: kicks Nu up as it falls, nudging it back toward the middle. ----

    private fun juggle(dial: Int, goal: Int?, ms: Double): Stage {
        val g = Juggle()
        var last = -1e9
        return run(g, dial, goal, ms) { s, _ ->
            val nu = s.nu
            val first = last < 0 && s.now > 1300
            if (s.now - last > 200 && (first || (nu.vy > 0.02 && nu.y > s.c.y + 20))) {
                val p = Pt(nu.x + clamp((nu.x - s.c.x) * 0.4, -20.0, 20.0), nu.y)
                g.down(s, p); g.up(s, p)
                last = s.now
            }
        }
    }

    @Test fun juggleClearsLevels() {
        val s = juggle(1, 3, 180_000.0)
        assertEquals(Result.WON, s.result, "Very easy: ${s.level} levels")
        assertTrue(juggle(100, 7, 180_000.0).level >= 1, "Expert clears a level")
    }

    @Test fun juggleHeatFallsHarder() {
        fun kick(dial: Int): Double {
            val g = Juggle()
            var fastest = 0.0
            var kicked = false
            run(g, dial, null, 400.0) { s, _ ->
                if (!kicked) { g.down(s, s.nu.p); kicked = true }
                fastest = minOf(fastest, s.nu.vy)
            }
            return -fastest
        }
        assertTrue(kick(100) > kick(1) * 1.2, "the kick up: ${kick(1)} at dial 1, ${kick(100)} at dial 100")
    }

    // ---- More: counts the atoms each side and taps the side with more. ----

    private fun more(dial: Int, goal: Int?, ms: Double): Stage {
        val g = More()
        var field = ""
        var since = 0
        return run(g, dial, goal, ms) { s, seen ->
            val atoms = seen.of(Ink.CYAN)
            if (atoms.isEmpty() || seen.of(Ink.GREEN).isNotEmpty()) return@run
            val key = "${atoms[0].x.toInt()},${atoms[0].y.toInt()}"
            if (key != field) { field = key; since = 0 }
            // A look of half a second, then the tap.
            if (++since == 30) {
                val left = atoms.count { it.x < s.c.x }
                g.down(s, Pt(s.c.x + if (left > atoms.size - left) -60.0 else 60.0, s.c.y))
            }
        }
    }

    @Test fun moreClearsLevels() {
        assertEquals(Result.WON, more(1, 3, 180_000.0).result)
        assertEquals(Result.WON, more(100, 7, 300_000.0).result)
    }

    @Test fun moreHeatHurries() {
        fun rim(dial: Int) = run(More(), dial, null, 1000.0).rim ?: 1.0
        assertTrue(rim(100) < rim(1) - 0.1, "rim after a second: ${rim(1)} at dial 1, ${rim(100)} at dial 100")
    }

    // ---- Orbit: hops rings for the next photon, and away from antiparticles ahead. ----

    private fun orbit(dial: Int, goal: Int?, ms: Double): Stage {
        val g = Orbit()
        val rads = doubleArrayOf(COURT_R * 0.72, COURT_R * 0.42)
        fun laneOf(r: Double) = if (abs(r - rads[1]) < abs(r - rads[0])) 1 else 0
        var dir = 1
        var last = -1e9
        return run(g, dial, goal, ms) { s, seen ->
            val r = dist(s.nu.x, s.nu.y, s.c.x, s.c.y)
            val lane = laneOf(r)
            // Mid-hop, or just acted: wait.
            val a = angle(s, s.nu.x, s.nu.y)
            if (abs(r - rads[lane]) > 2 || s.now - last < 250) return@run
            // How far ahead the nearest antiparticle on a ring is (one just behind counts as here).
            fun danger(l: Int) = seen.of(Ink.RED, 6.0).filter { laneOf(dist(s, it)) == l }
                .minOfOrNull { ahead(a, angle(s, it.x, it.y), dir).let { d -> if (d > TAU - 0.4) 0.0 else d } } ?: TAU
            val photon = seen.of(Ink.CYAN, 5.0).filter { it.alpha == 1.0 }.minByOrNull { ahead(a, angle(s, it.x, it.y), dir) }
            val other = 1 - lane
            fun hop() { g.down(s, polar(s, a, rads[other])); last = s.now }
            if (danger(lane) < 1.0) {
                if (danger(other) > 1.0) hop()
                else { dir = -dir; g.crown(s, dir * 0.2); last = s.now }
            } else if (photon != null && laneOf(dist(s, photon)) == other &&
                danger(other) > maxOf(1.5, ahead(a, angle(s, photon.x, photon.y), dir) + 0.6)) hop()
        }
    }

    @Test fun orbitClearsLevels() {
        assertEquals(Result.WON, orbit(1, 3, 180_000.0).result)
        assertTrue(orbit(100, 7, 180_000.0).level >= 1, "Expert clears a level")
    }

    @Test fun orbitHeatRunsFaster() {
        fun speed(dial: Int): Double {
            var a0 = 0.0
            var a1 = 0.0
            run(Orbit(), dial, null, 2000.0) { s, _ ->
                val a = angle(s, s.nu.x, s.nu.y)
                if (s.now < 2500) a0 = a else a1 = a
            }
            return abs(turn(a0, a1)) / 500
        }
        assertTrue(speed(100) > speed(1) * 1.5, "Nu round the ring: ${speed(1)} at dial 1, ${speed(100)} at dial 100")
    }
}
