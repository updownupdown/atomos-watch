package app.buildatomos.core

import app.buildatomos.core.games.Cross
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cross, a touch game, in levels: a small bot playing it by taps clears it, a level takes a
 *  while, heat makes it harder, and left alone it is lost. */
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

    @Test fun aGoodPlayerWinsAnEasyRun() {
        val won = (1..5).count { runToy(Cross(), 50, 3, bot = crosser()).result == Result.WON }
        assertTrue(won >= 4, "cross: won $won of 5 at Easy")
    }

    @Test fun aGoodPlayerClearsLevelsAtHard() {
        val levels = (1..5).map { runToy(Cross(), 100, 7, bot = crosser()).level }
        assertTrue(levels.count { it >= 2 } >= 3, "cross at Hard reached $levels")
    }

    /** A level takes a while at Easy: not over in a few seconds, not a slog. */
    @Test fun aLevelTakesAWhileAtEasy() {
        val ms = (1..3).map { runToy(Cross(), 50, 1, bot = crosser()).now }.average()
        assertTrue(ms in 6_000.0..30_000.0, "cross: first level took $ms ms")
    }

    @Test fun heatMakesItHarder() {
        assertTrue(Cross.pace(0.9) > Cross.pace(0.0) * 1.6 && Cross.gap(0.9) < Cross.gap(0.0) * 0.8)
        assertTrue(Cross.patience(0.9) < Cross.patience(0.0))
        // Nothing breaks deep into free play.
        for (h in listOf(2.5, 13.0)) assertTrue(Cross.pace(h) <= 0.11 && Cross.gap(h) >= 48, "cross at $h")
    }

    /** Left alone, it is lost, and sooner at Hard. */
    @Test fun ignoredItIsLost() {
        val easy = (1..2).map { runToy(Cross(), 50, null, 600_000.0) }
        val hard = (1..2).map { runToy(Cross(), 100, null, 600_000.0) }
        for (s in easy + hard) assertEquals(Result.LOST, s.result)
        assertTrue(hard.sumOf { it.now } < easy.sumOf { it.now }, "${hard.map { it.now }} vs ${easy.map { it.now }}")
    }

    /** A hop lands with only a slight sway past its row, and next to none back: the way it went
     *  reads at a glance. */
    @Test fun aHopBarelySways() {
        // A hop onto the first lane (dealt again until no electron sends Nu back meanwhile).
        repeat(30) {
            val s = Stage()
            val c = Cross()
            s.now = 1000.0
            c.enter(s)
            val to = c.rowY(s, 1)
            tap(c, s, Pt(s.nu.x, s.nu.y - 40))
            var past = 0.0
            var back = 0.0
            for (i in 0 until 60) {
                s.now += 16
                c.step(s, 16.0, s.now)
                past = maxOf(past, to - s.nu.y)
                if (past > 0) back = maxOf(back, s.nu.y - to)
            }
            if (c.row != 1) return@repeat
            assertTrue(past in 1.0..5.5, "swayed $past past the row")
            assertTrue(back < 1.5, "swayed $back back")
            return
        }
        error("every hop was hit")
    }

    /** Cross's banks keep Nu off the menu and the counter. */
    @Test fun keepsOffTheMenuAndCounter() {
        val s = Stage()
        val c = Cross()
        c.enter(s)
        assertTrue(c.rowY(s, 6) - s.nu.r > 17 + 22 + 4)
        assertTrue(c.rowY(s, 0) + s.nu.r < 272 - 14 - 4)
    }

    @Test fun report() {
        if (System.getenv("PRINT") != "1") return
        fun Stage.say() = "${level}${if (result == Result.LOST) "x" else ""}"
        for (dial in listOf(50, 70, 90, 100)) {
            val cross = (1..6).map { runToy(Cross(), dial, null, 200_000.0, crosser()).say() }
            println("dial $dial  cross $cross")
        }
        println("first level: cross ${(1..4).map { runToy(Cross(), 50, 1, bot = crosser()).now.toInt() }}")
        println("ignored cross: ${(1..3).map { runToy(Cross(), 50, null, 600_000.0).let { "${it.result} ${it.now.toInt()} L${it.level}" } }} / ${(1..3).map { runToy(Cross(), 100, null, 600_000.0).let { "${it.result} ${it.now.toInt()} L${it.level}" } }}")
    }
}
