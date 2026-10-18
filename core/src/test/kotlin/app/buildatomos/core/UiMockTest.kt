package app.buildatomos.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The app's icons and the run menu drawn off the watch, when MOCK=1, to look at. */
class UiMockTest {
    private val out = File(System.getenv("RENDER_DIR") ?: "build/mock")

    @Test fun atomOfDayMatchesPhone() {
        // daily.ts: ((index * 47) % 118 + 118) % 118 + 1
        assertEquals(1, atomOfDay(0))
        assertEquals(48, atomOfDay(1))
        assertEquals(((20379L * 47) % 118).toInt() + 1, atomOfDay(20379))
    }

    @Test fun icons() {
        if (System.getenv("MOCK") != "1") return
        val pen = SvgPen()
        Icon.entries.forEachIndexed { i, ic ->
            val x = 60.0 + (i % 4) * 60
            val y = 60.0 + (i / 4) * 64
            pen.dot(x, y, 24.0, Ink.K800)
            pen.icon(ic, x, y, 26.0, Ink.WHITE)
        }
        pen.save(File(out, "icons.svg"))
    }

    @Test fun menu() {
        if (System.getenv("MOCK") != "1") return
        for (free in listOf(false, true)) {
            val g = Games.byId("orbit")!!
            val st = Stage(); st.dial = 30; st.goal = if (free) null else 5
            val t = g.make(); t.enter(st)
            var now = 1000.0
            while (now < 4000.0) { now += 16.0; st.now = now; t.step(st, 16.0, now) }
            val pen = SvgPen()
            t.draw(st, pen, now)
            pen.dot(150.0, 150.0, 150.0, Ink.BLACK, 0.9)
            // Title row: icon and name.
            pen.dot(104.0, 66.0, 15.0, Ink.WHITE); pen.gameIcon("orbit", 104.0, 66.0, 18.0, Ink.BLACK)
            pen.text("Orbit", 150.0 + 12, 66.0, 22.0, Ink.WHITE, 600)
            pen.text(Difficulty.word(30), 150.0, 92.0, 14.0, Difficulty.color(30))
            // Exit, Resume, Restart across the middle.
            pen.dot(70.0, 150.0, 26.0, Ink.K800); pen.icon(Icon.EXIT, 70.0, 150.0, 26.0, Ink.WHITE)
            pen.dot(150.0, 150.0, 36.0, Ink.WHITE); pen.icon(Icon.PLAY, 150.0, 150.0, 34.0, Ink.BLACK)
            pen.dot(230.0, 150.0, 26.0, Ink.K800); pen.icon(Icon.RESTART, 230.0, 150.0, 28.0, Ink.WHITE)
            // The level reached and the best, at the bottom.
            pen.levels(150.0, 214.0, 30.0, if (free) 6 else 2, st.goal, 0.4)
            pen.text(if (free) "Level 6 · Best: 13" else "Level 2 of 5", 150.0, 244.0, 14.0, Ink.K300)
            pen.save(File(out, "menu-${if (free) "free" else "atom"}.svg"))
        }
    }
}
