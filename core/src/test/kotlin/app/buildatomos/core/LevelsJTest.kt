package app.buildatomos.core

import app.buildatomos.core.games.Fit
import app.buildatomos.core.games.Herd
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plays `toy` frame by frame for up to `ms`, letting `bot` play; returns the stage. */
internal fun <T : Toy> runToy(toy: T, dial: Int, goal: Int?, ms: Double = 300_000.0, bot: (T, Stage) -> Unit = { _, _ -> }): Stage {
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

/** Fit and Herd, the new crown games, in levels: a small bot playing them by the crown
 *  clears them, a level takes a while, heat makes them harder, and left alone they are lost. */
class LevelsJTest {
    // ---- Fit ----

    /** Half a second after a ring is due next, turns the shell the nearest way that fits it, no
     *  more than a quick turn a frame (three radians a second). */
    internal fun fitter(): (Fit, Stage) -> Unit {
        var seen: Fit.Ring? = null
        var at = 0.0
        return { t, s ->
            val r = t.lead
            if (r !== seen) { seen = r; at = s.now }
            if (r != null && s.now - at > 450) {
                val goal = t.aims(r).minByOrNull { abs(turn(t.sent, it)) }
                if (goal != null) t.crown(s, clamp(turn(t.sent, goal), -0.05, 0.05))
            }
        }
    }

    // ---- Herd ----

    /** Runs Nu round behind an electron, the handiest one (near Nu's side of the cloud, not far
     *  round the ring), and keeps after it until it's in or a while has gone by; it takes a
     *  moment to pick the next, and looks where they are only a few times a second. */
    internal fun herder(): (Herd, Stage) -> Unit {
        var target: Herd.E? = null
        var since = 0.0
        var look = 0.0
        var aim = 0.0
        return { h, s ->
            fun angle(e: Herd.E) = atan2(e.y - s.c.y, e.x - s.c.x)
            if (target != null && target !in h.free) { target = null; since = s.now }
            if ((target == null && s.now - since > 400) || s.now - since > 2500) {
                target = h.free.minByOrNull { e -> abs(turn(h.at, angle(e))) * 40 - dist(e.x, e.y, s.c.x, s.c.y) }
                since = s.now
            }
            target?.let { e ->
                if (s.now - look > 200) { look = s.now; aim = angle(e) }
                h.crown(s, clamp(turn(h.sent, aim), -0.1, 0.1))
            }
        }
    }

    @Test fun aGoodPlayerWinsAnEasyRun() {
        for ((name, won) in listOf(
            "fit" to (1..5).count { runToy(Fit(), 50, 3, bot = fitter()).result == Result.WON },
            "herd" to (1..5).count { runToy(Herd(), 1, 3, bot = herder()).result == Result.WON },
        )) assertTrue(won >= 4, "$name: won $won of 5 at Very easy")
    }

    @Test fun aGoodPlayerClearsLevelsAtExpert() {
        for ((name, levels) in listOf(
            "fit" to (1..5).map { runToy(Fit(), 100, 7, bot = fitter()).level },
            "herd" to (1..5).map { runToy(Herd(), 90, 7, bot = herder()).level },
        )) assertTrue(levels.count { it >= 2 } >= 3, "$name at Expert reached $levels")
    }

    /** A level takes a while at the easiest: not over in a few seconds, not a slog. */
    @Test fun aLevelTakesAWhileAtTheEasiest() {
        for ((name, ms) in listOf(
            "fit" to (1..3).map { runToy(Fit(), 50, 1, bot = fitter()).now }.average(),
            "herd" to (1..3).map { runToy(Herd(), 1, 1, bot = herder()).now }.average(),
        )) assertTrue(ms in 6_000.0..30_000.0, "$name: first level took $ms ms")
    }

    @Test fun heatMakesThemHarder() {
        assertTrue(Fit.travel(0.9) < Fit.travel(0.0) * 0.6 && Fit.gap(0.9) < Fit.gap(0.0) * 0.7)
        assertTrue(Fit.electrons(0.9) > Fit.electrons(0.0) && Fit.spinners(0.9) > 0 && Fit.spinners(0.0) == 0.0)
        assertTrue(Herd.count(0.9) > Herd.count(0.0) && Herd.each(0.9) < Herd.each(0.0) && Herd.reach(0.9) < Herd.reach(0.0))
        // Nothing breaks deep into free play.
        for (h in listOf(2.5, 13.0)) {
            assertTrue(Fit.travel(h) >= 1400 && Fit.gap(h) >= 0.42 && Fit.electrons(h) <= 5, "fit at $h")
            assertTrue(Herd.count(h) <= 10 && Herd.each(h) >= 2600 && Herd.reach(h) >= 56, "herd at $h")
        }
    }

    /** Left alone, both are lost, and sooner at Expert. */
    @Test fun ignoredTheyAreLost() {
        for ((name, make) in listOf<Pair<String, () -> Toy>>("fit" to { Fit() }, "herd" to { Herd() })) {
            val easy = (1..2).map { runToy(make(), 1, null, 600_000.0) }
            val hard = (1..2).map { runToy(make(), 100, null, 600_000.0) }
            for (s in easy + hard) assertEquals(Result.LOST, s.result, name)
            assertTrue(hard.sumOf { it.now } < easy.sumOf { it.now }, "$name: ${hard.map { it.now }} vs ${easy.map { it.now }}")
        }
    }

    /** Fit's shapes read at a glance: electrons sit on a clock face of 30-degree steps, never
     *  closer than 60 degrees, so where spacings differ they differ by a step or more; and the
     *  ring between two gaps is never a sliver. Uneven shapes come more often as it heats up. */
    @Test fun fitShapesReadAtAGlance() {
        val step = TAU / 12
        for (dial in listOf(1, 30, 50, 70, 90, 100)) repeat(80) {
            val s = Stage()
            s.dial = dial
            val f = Fit()
            f.enter(s)
            val p = f.pattern
            val k = p.size
            val spaces = (0 until k).map { i -> ((p[(i + 1) % k] - p[i]) % TAU + TAU) % TAU }
            for (sp in spaces) {
                assertTrue(sp >= TAU / 6 - 1e-9, "dial $dial: electrons ${Math.toDegrees(sp)} degrees apart")
                assertEquals(0.0, Math.IEEEremainder(sp, step), 1e-9, "dial $dial: off the clock face")
                assertTrue(sp - Fit.gap(s.heat) >= 0.45, "dial $dial: ring between gaps only ${Math.toDegrees(sp - Fit.gap(s.heat))} degrees")
            }
        }
        for (k in 2..5) assertTrue(Fit.shapes(k).all { it.sum() == 12 && it.size == k && it.all { n -> n >= 2 } }, "shapes of $k")
        assertEquals(0.0, Fit.unevenShare(0.0))
        assertEquals(1.0, Fit.unevenShare(0.61))
    }

    /** The crown alone plays them: each turns something by as much as the crown turns. */
    @Test fun theCrownTurnsThem() {
        val f = Fit()
        val s = Stage()
        f.enter(s)
        val a = f.shell
        f.crown(s, 0.5)
        repeat(30) { f.step(s, 16.0, 1000.0 + it * 16) }
        assertEquals(a + 0.5, f.shell, 0.01)
        val h = Herd()
        val t = Stage()
        h.enter(t)
        val b = h.at
        h.crown(t, 0.5)
        repeat(60) { h.step(t, 16.0, 1000.0 + it * 16) }
        assertEquals(b + 0.5, h.at, 0.01)
    }

    /** Herd's Nu keeps off the menu at the top and the counter at the bottom; Fit's rings come in
     *  inside them. */
    @Test fun keepsOffTheMenuAndCounter() {
        val s = Stage()
        val h = Herd()
        h.enter(s)
        assertTrue(h.track(s) + s.nu.r <= 150 - 17 - 22 + 1e-9)
    }

    @Test fun report() {
        if (System.getenv("PRINT") != "1") return
        fun Stage.say() = "${level}${if (result == Result.LOST) "x" else ""}"
        for (dial in listOf(1, 30, 50, 70, 90)) {
            val fit = (1..6).map { runToy(Fit(), dial, null, 200_000.0, fitter()).say() }
            val herd = (1..6).map { runToy(Herd(), dial, null, 200_000.0, herder()).say() }
            println("dial $dial  fit $fit  herd $herd")
        }
        println("first level: fit ${(1..4).map { runToy(Fit(), 1, 1, bot = fitter()).now.toInt() }} herd ${(1..4).map { runToy(Herd(), 1, 1, bot = herder()).now.toInt() }}")
        for ((name, make) in listOf<Pair<String, () -> Toy>>("fit" to { Fit() }, "herd" to { Herd() })) {
            println("ignored $name: ${(1..3).map { runToy(make(), 1, null, 600_000.0).let { "${it.result} ${it.now.toInt()} L${it.level}" } }} / ${(1..3).map { runToy(make(), 100, null, 600_000.0).let { "${it.result} ${it.now.toInt()} L${it.level}" } }}")
        }
    }
}
