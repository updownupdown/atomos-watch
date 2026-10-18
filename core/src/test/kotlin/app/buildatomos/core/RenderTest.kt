package app.buildatomos.core

import java.io.File
import kotlin.test.Test

/** Writes pictures of every game mid-run and of a few clouds to build/render, when RENDER=1. */
class RenderTest {
    private val out = File(System.getenv("RENDER_DIR") ?: "build/render")
    private val on = System.getenv("RENDER") == "1"

    @Test fun games() {
        if (!on) return
        for (g in Games.all) for (at in listOf(3000.0, 9000.0)) {
            val s = play(g, ms = at, rate = 2.0, seed = 3)
            val pen = SvgPen()
            // play() leaves the game mid-frame; draw one more frame with a fresh game seeded the same.
            val t = g.make()
            val st = Stage()
            t.enter(st)
            var now = 1000.0
            val r = seeded(3)
            while (now < at && !st.over) {
                now += 16.0; st.now = now
                if (r() < 0.03) { val p = Pt(st.c.x + (r() * 2 - 1) * 100, st.c.y + (r() * 2 - 1) * 100); t.down(st, p); t.up(st, p) }
                if (r() < 0.05) t.crown(st, (r() * 2 - 1) * 0.5)
                st.waiting?.let { go -> st.waiting = null; go() }
                t.step(st, 16.0, now)
            }
            t.draw(st, pen, now)
            st.fx.draw(pen, now, 16.0)
            pen.save(File(out, "game-${g.id}-${at.toInt()}.svg"))
        }
    }

    @Test fun clouds() {
        if (!on) return
        val f = File("../app/src/main/assets/atoms.bin")
        val clouds = Cloud.readAll(f.readBytes())
        val hues = mapOf(1 to 0xFF768AC2.toInt(), 6 to 0xFFDCD195.toInt(), 8 to 0xFFEB905C.toInt(), 26 to 0xFFDB7A96.toInt(), 79 to 0xFFC9AF6A.toInt())
        for ((z, color) in hues) for (share in listOf(0.0, 0.6, 1.0)) {
            val pen = SvgPen()
            Nebula(Atom(z, "", "", if (z > 54) 6 else 4, 1, "s", color, "", ""), clouds[z - 1]).draw(pen, 150.0, 130.0, 95.0, 2500.0, share)
            pen.mark(150.0, 270.0, 0.0)
            pen.save(File(out, "cloud-$z-${(share * 10).toInt()}.svg"))
        }
        val pen = SvgPen()
        pen.mark(150.0, 110.0, 110.0, nu = 3)
        pen.levels(90.0, 220.0, 44.0, 2, 5, 0.4)
        pen.levels(150.0, 220.0, 44.0, 37, null)
        pen.pips(210.0, 220.0, 40.0, 55)
        var x = 40.0
        for (g in listOf("halo", "orbit", "pulse", "dive", "flash", "echo", "stick", "ready", "tune")) { pen.dot(x, 270.0, 14.0, Ink.WHITE); pen.gameIcon(g, x, 270.0, 18.0, Ink.BLACK); x += 28 }
        pen.save(File(out, "marks.svg"))
    }
}
