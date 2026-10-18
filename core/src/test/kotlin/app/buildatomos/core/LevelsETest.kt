package app.buildatomos.core

import app.buildatomos.core.games.Hop
import app.buildatomos.core.games.Shade
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Hop and Shade in levels: a small bot playing well clears them, a level takes a while, and
 *  heat makes them harder. */
class LevelsETest {
    /** Plays `toy` frame by frame for up to `ms`, letting `bot` act; returns the stage. */
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

    private fun tap(t: Toy, s: Stage, p: Pt) { t.down(s, p); t.up(s, p) }

    /** Steers by the crown, as a player watching the pads would: going up off a plain pad, under
     *  the next pad up; coming down, over the first pad below. */
    private val hopper: (Hop, Stage) -> Unit = { t, s ->
        val on = t.on
        val next = if (t.vy > 0) t.pads.filter { it.y - it.r * 0.6 - s.nu.r >= t.y }.minByOrNull { it.y }
        else if (on != null && !on.spring) t.pads.filter { it.y < on.y && on.y - it.y < 105 }.maxByOrNull { it.y }
        else null
        if (next != null) {
            val d = next.x - t.aim
            if (abs(d) > 0.5) t.crown(s, d / (t.run(s) * 2 / 3))
        }
    }

    /** Looks ahead along the beams before each move: walks for the dot when the way there stays
     *  dark, waits under a plate while it's safe, and else makes for the safest plate. */
    private fun shader(): (Shade, Stage) -> Unit {
        var last = -1e9
        return { t, s ->
            if (s.now - last >= 100) {
                last = s.now
                val nu = s.nu.p
                fun cost(to: Pt): Double {
                    val ms = dist(nu, to) / t.speed + 150
                    var lit = 0.0
                    var a = 0.0
                    while (a <= ms) {
                        val f = clamp(a / ms, 0.0, 1.0)
                        if (t.litAt(s, Pt(nu.x + (to.x - nu.x) * f, nu.y + (to.y - nu.y) * f), a)) lit += 50
                        a += 50
                    }
                    return lit
                }
                val here = t.plates.firstOrNull { dist(it.x, it.y, nu.x, nu.y) < it.size - 3 }
                val safe = here != null && here.wear < 0.6 && (0..8).none { t.litAt(s, nu, it * 100.0) }
                when {
                    cost(t.gem) <= 50 && t.light < 0.7 -> tap(t, s, t.gem)
                    safe -> tap(t, s, nu)
                    else -> {
                        val p = t.plates.filter { it !== here || it.wear < 0.6 }
                            .minByOrNull { cost(Pt(it.x, it.y)) * 10 + it.wear * 400 + dist(it.x, it.y, t.gem.x, t.gem.y) }
                        if (p != null) tap(t, s, Pt(p.x, p.y))
                    }
                }
            }
        }
    }

    @Test fun aGoodPlayerWinsAnEasyRun() {
        val hop = (1..5).count { run(Hop(), 1, 3, bot = hopper).result == Result.WON }
        assertTrue(hop >= 4, "hop won $hop of 5")
        val shade = (1..5).count { run(Shade(), 1, 3, bot = shader()).result == Result.WON }
        assertTrue(shade >= 3, "shade won $shade of 5")
    }

    @Test fun aGoodPlayerClearsLevelsAtExpert() {
        assertTrue(run(Hop(), 100, 7, bot = hopper).level >= 2, "hop")
        // Shade at Expert is hard even for a bot that sees the beams' future: the best of three.
        val shade = (1..3).maxOf { run(Shade(), 100, 7, bot = shader()).level }
        assertTrue(shade >= 1, "shade reached level $shade")
    }

    /** A level takes a while at the easiest: not over in a few seconds, not a slog. */
    @Test fun aLevelTakesAWhileAtTheEasiest() {
        val h = run(Hop(), 1, 1, bot = hopper)
        // The bot loses the odd easy Shade run: the first of three it wins.
        val sh = (1..3).map { run(Shade(), 1, 1, bot = shader()) }.let { r -> r.firstOrNull { it.result == Result.WON } ?: r[0] }
        for ((name, s) in listOf("hop" to h, "shade" to sh)) {
            assertEquals(Result.WON, s.result, name)
            assertTrue(s.now in 5_000.0..45_000.0, "$name: first level took ${s.now} ms")
        }
    }

    @Test fun heatMakesThemHarder() {
        // Hop's pads: smaller, and some drifting, at Expert; never too far apart to reach.
        fun hopAt(dial: Int, level: Int = 0): Triple<Double, Double, Int> {
            val s = Stage()
            s.dial = dial
            repeat(level) { s.clear() }
            val t = Hop()
            t.enter(s)
            val p = t.pads.drop(1)
            val gaps = t.pads.zipWithNext { a, b -> a.y - b.y }
            return Triple(p.map { it.r }.average(), gaps.maxOrNull() ?: 0.0, p.count { it.vx != 0.0 })
        }
        val (r0, g0, m0) = hopAt(1)
        val (r1, _, _) = hopAt(100)
        assertTrue(r1 < r0 && m0 == 0 && g0 < 70, "hop: pads $r0 vs $r1, $m0 drifting, gap $g0")
        val (r2, g2, _) = hopAt(100, 200)
        assertTrue(r2 >= 6.0 && g2 < 105, "hop deep in free play: pads $r2, gap $g2")
        // Shade: more lamps, wider beams, quicker sweeps.
        fun shadeAt(dial: Int, level: Int = 0): Triple<Int, Double, Double> {
            val s = Stage()
            s.dial = dial
            repeat(level) { s.clear() }
            val t = Shade()
            t.enter(s)
            return Triple(t.lampsFor(s), t.beam(s), t.sweep(s))
        }
        val (l0, b0, w0) = shadeAt(1)
        val (l1, b1, w1) = shadeAt(100)
        assertTrue(l1 > l0 && b1 > b0 && w1 > w0, "shade: $l0/$b0/$w0 vs $l1/$b1/$w1")
        val (l2, b2, _) = shadeAt(100, 200)
        assertTrue(l2 <= 6 && b2 <= 0.26, "shade deep in free play: $l2 lamps, beam $b2")
    }

    /** Left alone, each is lost, and well within a few minutes. */
    @Test fun ignoredTheyEnd() {
        for (make in listOf({ Hop() }, { Shade() })) for (dial in listOf(1, 100)) {
            val s = run(make(), dial, null, 600_000.0)
            assertEquals(Result.LOST, s.result, "${make()::class.simpleName} at $dial")
            assertTrue(s.now < 120_000, "${make()::class.simpleName} at $dial took ${s.now}")
        }
    }

    /** Nothing a player must reach sits under the menu (150, 17) or on the counter (150, 272). */
    @Test fun counterAndMenuAreLeftClear() {
        repeat(30) {
            val s = Stage()
            s.dial = 100
            val sh = Shade()
            sh.enter(s)
            for (p in sh.plates) {
                assertTrue(hypot(p.x - 150, p.y - 17) > 22 + p.r && hypot(p.x - 150, p.y - 272) > 14 + p.r, "shade plate on the menu or counter")
            }
            assertTrue(hypot(sh.gem.x - 150, sh.gem.y - 17) > 30 && hypot(sh.gem.x - 150, sh.gem.y - 272) > 24, "shade dot on the menu or counter")
        }
    }
}
