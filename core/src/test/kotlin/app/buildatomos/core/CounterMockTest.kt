package app.buildatomos.core

import java.io.File
import kotlin.test.Test

/** Mockups of the level counter's options over real game frames, when MOCK=1. */
class CounterMockTest {
    private val out = File(System.getenv("RENDER_DIR") ?: "build/mock")

    @Test fun counters() {
        if (System.getenv("MOCK") != "1") return
        for (id in listOf("orbit", "flash", "recall")) {
            val g = Games.byId(id)!!
            for ((style, free) in listOf("a" to false, "a" to true, "b" to false, "b" to true, "c" to true)) {
                val s = play(g, ms = 7000.0, rate = 2.0, seed = 3, dial = 30, goal = if (free) null else 5)
                val pen = SvgPen()
                val t = g.make()
                val st = Stage(); st.dial = 30; st.goal = if (free) null else 5
                t.enter(st)
                var now = 1000.0
                val r = seeded(3)
                while (now < 7000.0 && !st.over) {
                    now += 16.0; st.now = now
                    if (r() < 0.03) { val p = Pt(st.c.x + (r() * 2 - 1) * 100, st.c.y + (r() * 2 - 1) * 100); t.down(st, p); t.up(st, p) }
                    st.waiting?.let { go -> st.waiting = null; go() }
                    t.step(st, 16.0, now)
                }
                t.draw(st, pen, now)
                pen.dot(150.0, 17.0, 15.0, Ink.BLACK, 0.7)
                pen.mark(150.0, 17.0, 24.0)
                val level = if (free) 13 else 2
                val part = 0.6
                when (style) {
                    "a" -> {
                        // With the time left round it, as for a game that keeps time.
                        pen.timer(150.0, 272.0, 17.5, if (free) 0.2 else 0.6)
                        pen.levels(150.0, 272.0, 32.0, level, if (free) null else 5, part)
                    }
                    "b" -> {
                        pen.rect(150.0 - 22, 262.0, 44.0, 20.0, 10.0, Ink.K900)
                        pen.text(if (free) "$level" else "$level/5", 150.0, 272.0, 13.0, Ink.WHITE, 600)
                    }
                    "c" -> {
                        // Free play: electrons filling shells 2, 8, 8 as levels go (an atom of the level's number).
                        val shells = intArrayOf(2, 8, 8)
                        var left = level
                        pen.dot(150.0, 272.0, 3.0, Ink.WHITE)
                        for ((i, n) in shells.withIndex()) {
                            val rr = 6.0 + i * 5.0
                            pen.ring(150.0, 272.0, rr, Ink.K700, 1.0)
                            for (j in 0 until n) {
                                val a = -Math.PI / 2 + j * TAU / n
                                val on = j < left
                                pen.dot(150 + kotlin.math.cos(a) * rr, 272 + kotlin.math.sin(a) * rr, 1.6, if (on) Ink.CYAN else Ink.K700)
                            }
                            left = maxOf(0, left - n)
                        }
                    }
                }
                pen.save(File(out, "$style-${if (free) "free" else "atom"}-$id.svg"))
                check(s.level >= 0)
            }
        }
    }
}
