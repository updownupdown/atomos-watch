package app.buildatomos.core

import app.buildatomos.core.games.Gate
import app.buildatomos.core.games.Grow
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Gate and Grow in levels: a small bot playing well clears them, and heat makes them harder. */
class LevelsETest {
    /** Plays `toy` frame by frame for up to `ms`, letting `bot` play; returns the stage. */
    private fun <T : Toy> run(toy: T, dial: Int, goal: Int?, ms: Double = 300_000.0, bot: (T, Stage) -> Unit = { _, _ -> }): Stage {
        val s = Stage()
        s.dial = dial
        s.goal = goal
        val pen = CheckPen()
        var now = 1000.0
        s.now = now
        toy.enter(s)
        while (now < ms && !s.over) {
            now += 16.0
            s.now = now
            bot(toy, s)
            toy.step(s, 16.0, now)
            toy.draw(s, pen, now)
            s.fx.draw(pen, now, 16.0)
            require(s.part in 0.0..1.0) { "part ${s.part}" }
        }
        return s
    }

    // ---- Gate ----

    /** Steers for the next gate, leaning toward the gate after it as it comes up, and round the
     *  side of a red dot in the way; by a finger held down, or by the crown (no more than a quick
     *  turn each frame). */
    private fun gater(crown: Boolean = false): (Gate, Stage) -> Unit {
        var held = false
        return { g, s ->
            val nu = s.nu
            val gates = g.ahead(s)
            val next = gates.firstOrNull()
            var want = s.c.x
            if (next != null) {
                val cx = next[0]; val gy = next[1]; val w = next[2]
                val half = max(0.0, w / 2 - 8)
                want = if (gates.size > 1 && gy - nu.y < 40) cx + clamp(gates[1][0] - cx, -half * 0.6, half * 0.6) else cx
                val reds = g.reds(s).filter { it.y > nu.y - 16 && it.y < min(gy, g.horizon(s)) }.sortedBy { it.y }
                for (r in reds) {
                    val t = clamp((r.y - nu.y) / max(1.0, gy - nu.y), 0.0, 1.0)
                    val px = nu.x + (want - nu.x) * t
                    if (abs(px - r.x) < 26) { want = if (px >= r.x) r.x + 28 else r.x - 28; break }
                }
            }
            if (crown) {
                val span = s.hw - nu.r - 4
                g.crown(s, clamp((want - g.tx) / (span * 0.8), -0.15, 0.15))
            } else {
                if (!held) { g.down(s, Pt(g.tx, 200.0)); held = true }
                g.move(s, Pt(want, 200.0))
            }
        }
    }

    @Test fun gateClears() {
        val won = (1..5).count { run(Gate(), 1, 3, bot = gater()).result == Result.WON }
        assertTrue(won >= 4, "by touch, won $won of 5 at Very easy")
        val byCrown = (1..5).count { run(Gate(), 1, 3, bot = gater(crown = true)).result == Result.WON }
        assertTrue(byCrown >= 4, "by the crown, won $byCrown of 5 at Very easy")
        val hard = (1..5).count { run(Gate(), 100, 7, bot = gater(crown = true)).level >= 1 }
        assertTrue(hard >= 3, "Expert cleared a level $hard times of 5")
    }

    /** The course keeps its runs on one side: some gates come on the same side as the one before. */
    @Test fun gateRunsOnOneSide() {
        val seen = mutableListOf<Double>()
        var last = Double.NaN
        val bot = gater()
        run(Gate(), 50, null, 120_000.0) { g, s ->
            bot(g, s)
            val n = g.ahead(s).firstOrNull()?.get(0) ?: return@run
            if (n != last) { seen += n - s.c.x; last = n }
        }
        val pairs = seen.zipWithNext()
        // On the same side, or straight down the middle (far in from a gate far out).
        val same = pairs.count { (a, b) -> a * b > 0 || abs(b) < 2 }
        assertTrue(seen.size > 30, "saw ${seen.size} gates")
        assertTrue(same > pairs.size / 6 && same < pairs.size * 2 / 3, "same side $same of ${pairs.size}")
    }

    // ---- Grow ----

    /** Holds a finger down and steers Nu to the handiest food, away from anything bigger near it
     *  and in from the arena's edge. */
    private fun grower(): (Grow, Stage) -> Unit {
        var held = false
        return { g, s ->
            val nu = s.nu
            if (!held) { g.down(s, nu.p); held = true }
            var fx = 0.0
            var fy = 0.0
            val reds = g.motes.filter { it.r >= nu.r * 0.9 }
            /** How close the reds come to the straight way from Nu to (x, y). */
            fun risk(x: Double, y: Double) = reds.sumOf { m ->
                val dx = x - nu.x; val dy = y - nu.y
                val l = max(1.0, dx * dx + dy * dy)
                val t = clamp(((m.x - nu.x) * dx + (m.y - nu.y) * dy) / l, 0.0, 1.0)
                val gap = dist(nu.x + dx * t, nu.y + dy * t, m.x, m.y) - nu.r - m.r
                max(0.0, 20 - gap) * 8
            }
            val food = g.motes.filter { it.inside && it.r < nu.r * 0.9 }.minByOrNull { dist(it.x, it.y, nu.x, nu.y) - it.r * 2 + risk(it.x, it.y) }
            if (food != null) {
                val d = max(1.0, dist(food.x, food.y, nu.x, nu.y))
                fx += (food.x - nu.x) / d
                fy += (food.y - nu.y) / d
            }
            for (m in reds) {
                val d = dist(m.x, m.y, nu.x, nu.y)
                val gap = d - nu.r - m.r
                if (gap > 45 || d < 1) continue
                val k = (45 - gap) / 45 * 3
                fx += (nu.x - m.x) / d * k
                fy += (nu.y - m.y) / d * k
            }
            // In from the edge, where it could be cornered.
            val c = dist(nu.x, nu.y, s.c.x, s.c.y)
            val out = c - (s.R - 30 - nu.r - 30)
            if (out > 0 && c > 1) { fx += (s.c.x - nu.x) / c * out / 15; fy += (s.c.y - nu.y) / c * out / 15 }
            val f = hypot(fx, fy)
            if (f > 1e-6) g.move(s, Pt(nu.x + fx / f * 30, nu.y + fy / f * 30)) else g.move(s, nu.p)
        }
    }

    @Test fun growClears() {
        val won = (1..5).count { run(Grow(), 1, 3, bot = grower()).result == Result.WON }
        assertTrue(won >= 4, "won $won of 5 at Very easy")
        val hard = (1..5).count { run(Grow(), 100, 7, bot = grower()).level >= 1 }
        assertTrue(hard >= 3, "Expert cleared a level $hard times of 5")
    }

    // ---- Both ----

    /** A level takes a while at the easiest: not over in a few seconds, not a slog. */
    @Test fun aLevelTakesAWhileAtTheEasiest() {
        for ((name, ms) in listOf(
            "gate" to (1..3).map { run(Gate(), 1, 1, bot = gater()).now }.average(),
            "grow" to (1..3).map { run(Grow(), 1, 1, bot = grower()).now }.average(),
        )) assertTrue(ms in 6_000.0..30_000.0, "$name: first level took $ms ms")
    }

    @Test fun heatMakesThemHarder() {
        val g = Gate()
        val s = Stage()
        g.enter(s)
        assertTrue(g.speed(1.0) > g.speed(0.0) * 1.5)
        assertTrue(g.width(s, 40.0, 1.0) < g.width(s, 0.0, 1.0) * 0.7)
        assertTrue(g.mineChance(40.0, 1.0) > g.mineChance(0.0, 1.0))
        val w = Grow()
        assertTrue(w.bigShare(40.0) > w.bigShare(0.0) && w.count(1.0) > w.count(0.0) && w.chasers(1.0) > w.chasers(0.0))
        // Nothing breaks deep into free play.
        for (h in listOf(2.5, 13.0)) {
            assertTrue(g.speed(h) < 0.32 && g.width(s, h * 40, 0.7) >= 2 * s.nu.r + 14, "gate at $h")
            assertTrue(w.bigShare(h * 40) <= 0.6 && w.count(h) <= 10, "grow at $h")
        }
    }

    /** Left alone, both are lost, and Gate sooner at Expert. */
    @Test fun ignoredTheyAreLost() {
        val easy = run(Gate(), 1, null, 600_000.0)
        val hard = run(Gate(), 100, null, 600_000.0)
        assertEquals(Result.LOST, easy.result)
        assertEquals(Result.LOST, hard.result)
        assertTrue(hard.now <= easy.now, "gate: ${hard.now} vs ${easy.now}")
        for (dial in listOf(1, 100)) assertEquals(Result.LOST, run(Grow(), dial, null, 600_000.0).result, "grow at $dial")
    }

    @Test fun keepsOffTheMenuAndCounter() {
        // Gate's Nu rides under the menu (150, 17, r 22); Grow's arena keeps inside the counter (150, 272, r 14).
        val s = Stage()
        val g = Gate()
        g.enter(s)
        assertTrue(hypot(s.nu.x - 150, s.nu.y - 17) > 22 + s.nu.r + 10)
        assertTrue(s.c.y + s.R - 30 <= 272 - 14 + 1e-9)
    }
}
