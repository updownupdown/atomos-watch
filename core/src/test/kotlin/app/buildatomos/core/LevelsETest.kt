package app.buildatomos.core

import app.buildatomos.core.games.Dial
import app.buildatomos.core.games.Lanes
import java.io.File
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dial and Lanes, the watch's own crown games, in levels: a player who plays them with the crown
 *  alone clears them, a level takes a while, and heat makes them harder. */
class LevelsETest {
    /** Plays `toy` frame by frame for up to `ms`, letting `bot` turn the crown; returns the stage. */
    private fun <T : Toy> run(toy: T, dial: Int, goal: Int?, ms: Double = 400_000.0, bot: (T, Stage) -> Unit = { _, _ -> }): Stage {
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
            require(s.part in 0.0..1.0) { "part ${s.part}" }
        }
        return s
    }

    /** Turns the crown to the lit notch a third of a second after it lights, at most about 0.7
     *  of a turn a second, leading a drifting notch a little. */
    private fun dialer(): (Dial, Stage) -> Unit {
        var seen: Double? = null
        var at = 0.0
        return { t, s ->
            val g = t.goal
            if (g == null || seen == null || abs(turn(seen!!, g)) > 0.05 + abs(t.goalDrift) * 20) at = s.now
            seen = g
            if (g != null && s.now - at > 330) {
                val d = turn(t.pointer, g + t.goalDrift * 120)
                t.crown(s, clamp(d, -0.07, 0.07))
            }
        }
    }

    /** Looks at what is coming round and picks a ring: one no red will cross while Nu gets there
     *  and sits, the one with the soonest cyan it can still reach if any. Clicks the crown a ring
     *  every 90 ms toward it. */
    private fun laner(): (Lanes, Stage) -> Unit {
        var last = 0.0
        return { t, s ->
            val bits = t.coming
            val on = t.on
            fun danger(l: Int, a: Double, b: Double) = bits.any { it.red && it.lane == l && t.eta(it) in a..b }
            fun travel(l: Int) = abs(l - on) * 110.0
            fun safe(l: Int): Boolean {
                if (danger(l, -260.0, travel(l) + 450)) return false
                val step = (l - on).sign.toInt()
                var j = on
                var k = 0
                while (j != l) { j += step; k++; if (j != l && danger(j, k * 110.0 - 220, k * 110.0 + 220)) return false }
                return true
            }
            val lanes = (0 until t.rings)
            val ok = lanes.filter { safe(it) }
            fun cyan(l: Int) = bits.filter { !it.red && it.lane == l && t.eta(it) in travel(l) + 60..2400.0 }.minOfOrNull { t.eta(it) }
            val target = ok.minByOrNull { l -> (cyan(l) ?: 5000.0) + (if (l == on) -150.0 else 0.0) + travel(l) * 0.5 }
                ?: lanes.minByOrNull { l -> -(bits.filter { it.red && it.lane == l && t.eta(it) > -200 }.minOfOrNull { t.eta(it) } ?: 1e9) }
                ?: on
            if (target != on && s.now - last >= 90) {
                // Clockwise is outward, toward ring 0.
                t.crown(s, if (target < on) 0.3 else -0.3)
                last = s.now
            }
        }
    }

    @Test fun aGoodPlayerWinsAnEasyRun() {
        assertEquals(Result.WON, run(Dial(), 1, 3, bot = dialer()).result, "dial")
        val won = (1..5).count { run(Lanes(), 1, 3, bot = laner()).result == Result.WON }
        assertTrue(won >= 4, "lanes won $won of 5")
    }

    @Test fun aGoodPlayerClearsLevelsAtExpert() {
        assertTrue(run(Dial(), 100, 7, bot = dialer()).level >= 3, "dial")
        assertTrue((1..3).any { run(Lanes(), 100, 7, bot = laner()).level >= 2 }, "lanes")
    }

    /** A level takes a while at the easiest: not over in a few seconds, not a slog. */
    @Test fun aLevelTakesAWhileAtTheEasiest() {
        val d = run(Dial(), 1, 1, bot = dialer())
        val l = run(Lanes(), 1, 1, bot = laner())
        assertEquals(Result.WON, d.result)
        assertEquals(Result.WON, l.result)
        assertTrue(d.now in 6_000.0..30_000.0, "dial: first level took ${d.now} ms")
        assertTrue(l.now in 6_000.0..30_000.0, "lanes: first level took ${l.now} ms")
    }

    /** Free play goes on, a level harder each time, until the player slips. */
    @Test fun freePlayGoesOn() {
        assertTrue(run(Dial(), 1, null, bot = dialer()).level >= 8, "dial")
        assertTrue((1..3).maxOf { run(Lanes(), 1, null, bot = laner()).level } >= 5, "lanes")
    }

    @Test fun heatMakesThemHarder() {
        assertTrue(Dial.width(1.0) < Dial.width(0.0) && Dial.hold(1.0) > Dial.hold(0.0) && Dial.each(1.0) < Dial.each(0.0))
        assertTrue(Dial.drift(0.0) > 0 && Dial.drift(1.0) > Dial.drift(0.0))
        assertTrue(Dial.drift(1.0) > 0 && Dial.notches(6) > Dial.notches(0))
        assertTrue(Lanes.pace(1.0) > Lanes.pace(0.0) && Lanes.every(1.0) < Lanes.every(0.0) && Lanes.reds(1.0) > Lanes.reds(0.0))
        assertTrue(Lanes.rings(0.0) == 3 && Lanes.rings(1.0) == 4)
        assertEquals(0.0, Lanes.gates(0.0))
        assertTrue(Lanes.gates(1.0) > 0)
        // Nothing breaks deep into free play.
        for (h in listOf(2.5, 13.0)) {
            assertTrue(Dial.width(h) >= 0.14 && Dial.hold(h) <= 950 && Dial.each(h) >= 1900 && Dial.drift(h) <= 0.0007 && Dial.notches(255) <= 8, "dial at $h")
            assertTrue(Lanes.pace(h) <= 0.0026 && Lanes.every(h) >= 260 && Lanes.reds(h) < 0.65 && Lanes.gates(h) < 0.25, "lanes at $h")
        }
    }

    /** Left alone, both are lost, and sooner at Expert (on the whole: where the reds fall is chance). */
    @Test fun ignoredTheyAreLost() {
        for (make in listOf({ Dial() }, { Lanes() })) {
            val easy = (1..8).map { run(make(), 1, null, 600_000.0) }
            val hard = (1..8).map { run(make(), 100, null, 600_000.0) }
            assertTrue((easy + hard).all { it.result == Result.LOST })
            val name = make()::class.simpleName
            assertTrue(hard.sumOf { it.now } < easy.sumOf { it.now }, "$name: ${hard.map { it.now }} vs ${easy.map { it.now }}")
        }
    }

    /** However the reds come round, when they reach Nu there is always a ring none of them is on. */
    @Test fun lanesAlwaysLeaveARingFree() {
        for (dial in listOf(30, 100)) run(Lanes(), dial, null, 120_000.0, bot = laner()) { t, _ ->
            val reds = t.coming.filter { it.red }
            var at = 200.0
            while (at < 2500) {
                val blocked = (0 until t.rings).count { l -> reds.any { it.lane == l && abs(t.eta(it) - at) < 100 } }
                assertTrue(blocked < t.rings, "every ring blocked at $at ms")
                at += 40
            }
        }
    }

    private fun <T : Toy> run(toy: T, dial: Int, goal: Int?, ms: Double, bot: (T, Stage) -> Unit, check: (T, Stage) -> Unit) =
        run(toy, dial, goal, ms) { t, s -> bot(t, s); check(t, s) }

    /** One crown click moves Nu one ring, at once; a turn back moves it back. */
    @Test fun lanesClickARingAtATime() {
        val s = Stage()
        s.dial = 1
        val t = Lanes()
        t.enter(s)
        s.now = 2000.0
        assertEquals(1, t.on)
        t.crown(s, 0.16)
        assertEquals(0, t.on)
        t.crown(s, 0.5)
        assertEquals(0, t.on, "the outer ring is the end")
        t.crown(s, -0.16)
        assertEquals(1, t.on)
        t.crown(s, -0.1)
        assertEquals(1, t.on, "less than a step on")
        t.crown(s, -0.2)
        assertEquals(2, t.on)
    }

    /** The icons draw, and (RENDER=1) every game's icon is written out large to look at. */
    @Test fun icons() {
        val check = CheckPen()
        for (id in listOf("dial", "lanes")) check.gameIcon(id, 50.0, 50.0, 30.0, Ink.WHITE)
        assertTrue(check.calls >= 8)
        if (System.getenv("RENDER") != "1") return
        val pen = SvgPen()
        Games.all.forEachIndexed { i, g ->
            val x = 40.0 + (i % 5) * 55
            val y = 40.0 + (i / 5) * 60
            pen.dot(x, y, 22.0, Ink.WHITE)
            pen.gameIcon(g.id, x, y, 28.0, Ink.BLACK)
        }
        pen.save(File(System.getenv("RENDER_DIR") ?: "build/render", "icons.svg"))
    }
}
