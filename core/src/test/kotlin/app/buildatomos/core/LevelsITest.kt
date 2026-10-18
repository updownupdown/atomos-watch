package app.buildatomos.core

import app.buildatomos.core.games.Cross
import app.buildatomos.core.games.Fill
import app.buildatomos.core.games.Poise
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cross, Fill and Poise, the new touch games, in levels: a small bot playing them by taps clears
 *  them, a level takes a while, heat makes them harder, and left alone they are lost. */
class LevelsITest {
    private fun tap(t: Toy, s: Stage, p: Pt) { t.down(s, p); t.up(s, p) }

    // ---- Cross ----

    /** Waits on its row until the next one is clear at Nu's place for a while, then taps beyond
     *  Nu. If something is coming at it where it stands and the way on isn't clear, it hops back,
     *  or on if that is clear for a moment, or else steps aside the way the electron is going (so
     *  as not to cross it). A tap no more often than every eighth of a second. */
    internal fun crosser(): (Cross, Stage) -> Unit {
        var last = 0.0
        return { c, s ->
            val nu = s.nu
            val settled = abs(nu.y - c.rowY(s, c.row)) < 3 && abs(nu.x - c.x) < 3
            if (settled && !c.safeNow && s.now - last > 125) {
                val dir = if (c.up) 1 else -1
                val x = c.x
                fun clear(row: Int, at: Double, ms: Double, from: Double = 0.0) = row in 0..6 && !c.danger(s, row, at, from, ms, 4.0)
                fun fits(at: Double) = abs(at - s.c.x) <= c.reach(s, c.row)
                val next = c.row + dir
                // On, if the next row is clear a while and Nu won't be stuck in it: it's a bank or
                // still, it stays clear longer, or the row after it clears soon.
                val on = clear(next, x, 650.0) && (c.lanes.getOrNull(next) == null || clear(next, x, 1100.0) ||
                    clear(next + dir, x, 900.0, 250.0))
                if (on) { tap(c, s, Pt(nu.x, nu.y - dir * 40)); last = s.now }
                else if (c.danger(s, c.row, x, 0.0, 350.0, 2.0)) {
                    last = s.now
                    val way = c.lanes[c.row]?.v?.let { if (it > 0) 1.0 else -1.0 } ?: 1.0
                    when {
                        clear(c.row - dir, x, 500.0) -> tap(c, s, Pt(nu.x, nu.y + dir * 40))
                        clear(c.row + dir, x, 300.0) -> tap(c, s, Pt(nu.x, nu.y - dir * 40))
                        fits(x + 28 * way) && clear(c.row, x + 28 * way, 400.0) -> tap(c, s, Pt(nu.x + way * 40, nu.y))
                    }
                }
            }
        }
    }

    // ---- Poise ----

    /** A moment after Nu brings an atom, taps over the place that leaves the beam nearest level,
     *  in a free spot on the beam (so it doesn't land on an atom and roll off it). */
    internal fun poiser(): (Poise, Stage) -> Unit {
        var seen: Poise.W? = null
        var at = 0.0
        return { p, s ->
            val a = p.hand
            if (a !== seen) { seen = a; at = s.now }
            if (a != null && s.now - at > 300 && s.now - at < 340) {
                val (x0, x1) = p.span(s, a.r)
                val t0 = p.torque(s)
                val px = p.pivot(s).x
                var best = px
                var score = Double.MAX_VALUE
                var x = x0
                while (x <= x1) {
                    var k = abs(t0 + a.m * (x - px) / p.len(s))
                    // Onto an atom already there, it would roll off somewhere else.
                    for (b in p.on) {
                        val d = abs(p.world(s, b).x - x)
                        if (d < a.r + b.r + 2) k += 0.6
                    }
                    if (k < score) { score = k; best = x }
                    x += 2
                }
                tap(p, s, Pt(best, 60.0))
            }
        }
    }

    // ---- Fill ----

    /** Taps where a bubble has the most room, away from the reds, and sets it with a second tap
     *  just before a red would touch it. */
    internal fun filler(): (Fill, Stage) -> Unit {
        var last = 0.0
        return { f, s ->
            val g = f.grow
            val a = f.arena(s)
            if (g == null) {
                if (s.now - last > 350) {
                    var best: Pt? = null
                    var score = 14.0
                    var y = s.c.y - a
                    while (y <= s.c.y + a) {
                        var x = s.c.x - a
                        while (x <= s.c.x + a) {
                            val room = min(a - dist(x, y, s.c.x, s.c.y), f.set.minOfOrNull { dist(x, y, it.x, it.y) - it.r } ?: a)
                            val gap = f.reds.minOf { dist(x, y, it.x, it.y) - 4.5 }
                            val k = min(room, gap * 0.8)
                            if (room > 10 && gap > 24 && k > score) { score = k; best = Pt(x, y) }
                            x += 6
                        }
                        y += 6
                    }
                    best?.let { tap(f, s, it); last = s.now }
                }
            } else {
                val grow = Fill.grows(s.heat)
                val soon = f.reds.any { r ->
                    (0..5).any { i ->
                        val t = i * 16.0
                        hypot(r.x + r.vx * t - g.x, r.y + r.vy * t - g.y) < g.r + grow * t + 4.5 + 3
                    }
                }
                if (soon) { tap(f, s, s.c); last = s.now }
            }
        }
    }

    @Test fun aGoodPlayerWinsAnEasyRun() {
        for ((name, won) in listOf(
            "cross" to (1..5).count { runToy(Cross(), 1, 3, bot = crosser()).result == Result.WON },
            "fill" to (1..5).count { runToy(Fill(), 1, 3, bot = filler()).result == Result.WON },
            "poise" to (1..5).count { runToy(Poise(), 1, 3, bot = poiser()).result == Result.WON },
        )) assertTrue(won >= 4, "$name: won $won of 5 at Very easy")
    }

    @Test fun aGoodPlayerClearsLevelsAtExpert() {
        for ((name, levels) in listOf(
            "cross" to (1..5).map { runToy(Cross(), 90, 7, bot = crosser()).level },
            "fill" to (1..5).map { runToy(Fill(), 90, 7, bot = filler()).level },
            "poise" to (1..5).map { runToy(Poise(), 90, 7, bot = poiser()).level },
        )) assertTrue(levels.count { it >= 2 } >= 3, "$name at Expert reached $levels")
    }

    /** A level takes a while at the easiest: not over in a few seconds, not a slog. */
    @Test fun aLevelTakesAWhileAtTheEasiest() {
        for ((name, ms) in listOf(
            "cross" to (1..3).map { runToy(Cross(), 1, 1, bot = crosser()).now }.average(),
            "fill" to (1..3).map { runToy(Fill(), 1, 1, bot = filler()).now }.average(),
            "poise" to (1..3).map { runToy(Poise(), 1, 1, bot = poiser()).now }.average(),
        )) assertTrue(ms in 6_000.0..30_000.0, "$name: first level took $ms ms")
    }

    @Test fun heatMakesThemHarder() {
        assertTrue(Cross.pace(0.9) > Cross.pace(0.0) * 1.6 && Cross.gap(0.9) < Cross.gap(0.0) * 0.8)
        assertTrue(Cross.patience(0.9) < Cross.patience(0.0))
        assertTrue(Fill.count(0.9) > Fill.count(0.0) && Fill.speed(0.9) > Fill.speed(0.0) * 1.5 && Fill.goal(0.9) > Fill.goal(0.0))
        assertTrue(Poise.biggest(0.9) > Poise.biggest(0.0) && Poise.patience(0.9) < Poise.patience(0.0))
        val p = Poise()
        val s = Stage()
        s.dial = 1
        p.enter(s)
        val loose = Stage().also { it.dial = 90 }
        assertTrue(p.stiff(loose) < p.stiff(s))
        // Nothing breaks deep into free play.
        for (h in listOf(2.5, 13.0)) {
            assertTrue(Cross.pace(h) <= 0.11 && Cross.gap(h) >= 48, "cross at $h")
            assertTrue(Fill.count(h) <= 7 && Fill.goal(h) <= 0.66 && Fill.speed(h) <= 0.13, "fill at $h")
            assertTrue(Poise.biggest(h) <= 13, "poise at $h")
        }
    }

    /** Left alone, all three are lost, and sooner at Expert. */
    @Test fun ignoredTheyAreLost() {
        for ((name, make) in listOf<Pair<String, () -> Toy>>("cross" to { Cross() }, "fill" to { Fill() }, "poise" to { Poise() })) {
            val easy = (1..2).map { runToy(make(), 1, null, 600_000.0) }
            val hard = (1..2).map { runToy(make(), 100, null, 600_000.0) }
            for (s in easy + hard) assertEquals(Result.LOST, s.result, name)
            assertTrue(hard.sumOf { it.now } < easy.sumOf { it.now }, "$name: ${hard.map { it.now }} vs ${easy.map { it.now }}")
        }
    }

    /** Cross's banks keep Nu off the menu and the counter; Fill's arena and Poise's beam and Nu
     *  keep inside them too. */
    @Test fun keepsOffTheMenuAndCounter() {
        val s = Stage()
        val c = Cross()
        c.enter(s)
        assertTrue(c.rowY(s, 6) - s.nu.r > 17 + 22 + 4)
        assertTrue(c.rowY(s, 0) + s.nu.r < 272 - 14 - 4)
        val f = Fill()
        f.enter(s)
        assertTrue(s.c.y + f.arena(s) <= 272 - 14 + 1e-9 && s.c.y - f.arena(s) >= 17 + 22 - 1e-9)
    }

    @Test fun report() {
        if (System.getenv("PRINT") != "1") return
        fun Stage.say() = "${level}${if (result == Result.LOST) "x" else ""}"
        for (dial in listOf(1, 30, 50, 70, 90)) {
            val cross = (1..6).map { runToy(Cross(), dial, null, 200_000.0, crosser()).say() }
            val fill = (1..6).map { runToy(Fill(), dial, null, 200_000.0, filler()).say() }
            val poise = (1..6).map { runToy(Poise(), dial, null, 200_000.0, poiser()).say() }
            println("dial $dial  cross $cross  fill $fill  poise $poise")
        }
        println("first level: cross ${(1..4).map { runToy(Cross(), 1, 1, bot = crosser()).now.toInt() }} fill ${(1..4).map { runToy(Fill(), 1, 1, bot = filler()).now.toInt() }} poise ${(1..4).map { runToy(Poise(), 1, 1, bot = poiser()).now.toInt() }}")
        for ((name, make) in listOf<Pair<String, () -> Toy>>("cross" to { Cross() }, "fill" to { Fill() }, "poise" to { Poise() })) {
            println("ignored $name: ${(1..3).map { runToy(make(), 1, null, 600_000.0).let { "${it.result} ${it.now.toInt()} L${it.level}" } }} / ${(1..3).map { runToy(make(), 100, null, 600_000.0).let { "${it.result} ${it.now.toInt()} L${it.level}" } }}")
        }
    }
}
