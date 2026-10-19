package app.buildatomos.core

import app.buildatomos.core.games.Blast
import app.buildatomos.core.games.Dive
import app.buildatomos.core.games.Echo
import app.buildatomos.core.games.Flash
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Blast, Dive, Echo and Flash in levels: a small bot playing well clears them, and heat makes
 *  them harder. */
class LevelsATest {
    /** Plays `toy` frame by frame for up to `ms`, letting `bot` tap; returns the stage. */
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
            require(s.part in 0.0..1.0) { "part ${s.part}" }
        }
        return s
    }

    private fun tap(t: Toy, s: Stage, p: Pt) { t.down(s, p); t.up(s, p) }

    /** Fires at the nearest red, aiming where it will be, a few times a second. */
    private fun blaster(): (Blast, Stage) -> Unit {
        var last = 0.0
        return { t, s ->
            val r = t.reds.minByOrNull { dist(it.x, it.y, s.c.x, s.c.y) }
            if (r != null && s.now - last > 220) {
                val lead = dist(r.x, r.y, s.c.x, s.c.y) / 0.6
                tap(t, s, Pt(r.x + r.vx * lead, r.y + r.vy * lead))
                last = s.now
            }
        }
    }

    /** The same, aiming with the crown instead: turns the pointer onto the red and taps Nu. */
    private fun crownBlaster(): (Blast, Stage) -> Unit {
        var last = 0.0
        return { t, s ->
            val r = t.reds.minByOrNull { dist(it.x, it.y, s.c.x, s.c.y) }
            if (r != null && s.now - last > 220) {
                val lead = dist(r.x, r.y, s.c.x, s.c.y) / 0.6
                val want = atan2(r.y + r.vy * lead - s.nu.y, r.x + r.vx * lead - s.nu.x)
                t.crown(s, turn(t.aim, want))
                tap(t, s, s.nu.p)
                last = s.now
            }
        }
    }

    /** Taps a lit cyan after a quarter second, as a quick hand would. */
    private fun flasher(): (Flash, Stage) -> Unit {
        var seen: Pt? = null
        var at = 0.0
        return { t, s ->
            val p = t.target
            if (p !== seen) { seen = p; at = s.now }
            if (p != null && s.now - at > 250) tap(t, s, p)
        }
    }

    /** Plays the tune back, a note every third of a second. */
    private fun echoer(): (Echo, Stage) -> Unit {
        var last = 0.0
        return { t, s ->
            val i = t.due
            if (i != null && s.now - last > 330) { tap(t, s, t.nodes[i]); last = s.now }
        }
    }

    private val diver: (Dive, Stage) -> Unit = { t, s -> if (t.canDive(s)) tap(t, s, s.c) }

    @Test fun aGoodPlayerWinsAnEasyRun() {
        assertEquals(Result.WON, run(Blast(), 1, 3, bot = blaster()).result, "blast")
        assertEquals(Result.WON, run(Blast(), 1, 3, bot = crownBlaster()).result, "blast by the crown")
        assertEquals(Result.WON, run(Dive(), 1, 3, bot = diver).result, "dive")
        assertEquals(Result.WON, run(Echo(), 1, 3, bot = echoer()).result, "echo")
        assertEquals(Result.WON, run(Flash(), 50, 3, bot = flasher()).result, "flash")
    }

    @Test fun aGoodPlayerClearsLevelsAtExpert() {
        assertTrue(run(Blast(), 100, 7, bot = blaster()).level >= 1, "blast")
        assertTrue(run(Blast(), 100, 7, bot = crownBlaster()).level >= 1, "blast by the crown")
        assertEquals(Result.WON, run(Dive(), 100, 7, bot = diver).result, "dive")
        assertEquals(Result.WON, run(Echo(), 100, 7, bot = echoer()).result, "echo")
        assertTrue(run(Flash(), 100, 7, bot = flasher()).level >= 1, "flash")
    }

    /** A level takes a while at the easiest: not over in a few seconds, not a slog. */
    @Test fun aLevelTakesAWhileAtTheEasiest() {
        fun firstLevel(toy: Toy, bot: (Stage) -> Unit): Double {
            val s = run(toy, 1, 1, bot = { _, st -> bot(st) })
            return s.now
        }
        val b = Blast(); val bb = blaster()
        val bc = Blast(); val bcb = crownBlaster()
        val d = Dive()
        val e = Echo(); val eb = echoer()
        val f = Flash(); val fb = flasher()
        for ((name, ms) in listOf(
            "blast" to firstLevel(b) { bb(b, it) },
            "blast by the crown" to firstLevel(bc) { bcb(bc, it) },
            "dive" to firstLevel(d) { diver(d, it) },
            "echo" to firstLevel(e) { eb(e, it) },
            "flash" to firstLevel(f) { fb(f, it) },
        )) assertTrue(ms in 6_000.0..30_000.0, "$name: first level took $ms ms")
    }

    /** Blast's crown aims and a tap on Nu fires along it; a tap anywhere else still fires at the
     *  tap and puts the pointer away. Without the crown, a tap on Nu fires toward the tap. */
    @Test fun blastAimsByTheCrownOrTheTap() {
        val s = Stage()
        val b = Blast()
        b.enter(s)
        assertTrue(!b.aiming)
        tap(b, s, Pt(s.c.x + 8, s.c.y))
        assertEquals(0.0, b.aim, 1e-9)
        assertTrue(!b.aiming)
        b.crown(s, 0.5)
        assertTrue(b.aiming)
        assertEquals(0.5, b.aim, 1e-9)
        tap(b, s, Pt(s.c.x - 8, s.c.y))
        assertEquals(0.5, b.aim, 1e-9, "a tap on Nu fires along the crown's aim")
        tap(b, s, Pt(s.c.x, s.c.y + 80))
        assertTrue(!b.aiming)
        assertEquals(Math.PI / 2, b.aim, 1e-9)
    }

    @Test fun heatMakesThemHarder() {
        val b = Blast()
        assertTrue(b.speed(1.0) > b.speed(0.0) && b.every(1.0) < b.every(0.0))
        val d = Dive()
        assertTrue(d.shells(1.0) > d.shells(0.0) && d.perShell(1.0) < d.perShell(0.0) && d.spin(1.0) > d.spin(0.0))
        val e = Echo()
        assertTrue(e.length(1.0) > e.length(0.0) && e.pace(1.0) > e.pace(0.0))
        val f = Flash()
        assertTrue(f.window(1.0) < f.window(0.0) && f.redChance(1.0) > f.redChance(0.0))
        // A notch harder than it was: Easy plays as Medium did, Hard at the quickest lights.
        assertTrue(f.window(0.5) in 650.0..720.0 && f.window(0.9) <= 430 && f.redChance(0.5) > 0.2, "flash at Easy ${f.window(0.5)}")
        // Nothing breaks deep into free play.
        for (h in listOf(2.5, 13.0)) {
            assertTrue(b.speed(h) < 0.2 && b.every(h) >= 300, "blast at $h")
            assertTrue(d.shells(h) <= 7 && d.perShell(h) >= 2000 && d.spin(h) <= 0.0025, "dive at $h")
            assertTrue(e.length(h) <= 8 && e.pace(h) <= 1.6, "echo at $h")
            assertTrue(f.window(h) >= 380 && f.redChance(h) < 0.5, "flash at $h")
        }
    }

    /** Left alone, the action games are lost sooner at Expert than at Very easy. */
    @Test fun ignoredTheyFallFasterWhenHot() {
        for (make in listOf({ Blast() }, { Flash() }, { Dive() })) {
            val easy = run(make(), 1, null, 600_000.0)
            val hard = run(make(), 100, null, 600_000.0)
            assertEquals(Result.LOST, easy.result)
            assertEquals(Result.LOST, hard.result)
            assertTrue(hard.now < easy.now, "${make()::class.simpleName}: ${hard.now} vs ${easy.now}")
        }
    }

    /** The part shown on the counter climbs through a level and starts again at the next. */
    @Test fun partClimbsThroughALevel() {
        val f = Flash()
        val fb = flasher()
        var most = 0.0
        val s = run(f, 1, 2, bot = { t, st -> fb(t, st); if (st.level == 0) most = maxOf(most, st.part) })
        assertEquals(Result.WON, s.result)
        assertTrue(most > 0.8, "part reached $most")
    }

    @Test fun counterAndMenuAreLeftClear() {
        // Echo's atoms keep off the menu (150, 17, r 22) and the counter (150, 272, r 14).
        val s = Stage()
        val e = Echo()
        e.enter(s)
        for (p in e.nodes) {
            assertTrue(hypot(p.x - 150, p.y - 17) > 22 + 31, "echo atom $p under the menu")
            assertTrue(hypot(p.x - 150, p.y - 272) > 14 + 31, "echo atom $p on the counter")
        }
    }
}
