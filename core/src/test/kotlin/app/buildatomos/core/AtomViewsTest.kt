package app.buildatomos.core

import java.io.File
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Build screen's dial and shells: every atom has its own place a finger can find, and with
 *  MOCK=1 they are drawn to build/mock to look at. */
class AtomViewsTest {
    private val out = File(System.getenv("RENDER_DIR") ?: "build/mock")
    private val mid = SCREEN / 2

    @Test fun shellsHaveRoomForEveryAtom() {
        for (a in 1..ATOMS) {
            val p = AtomShells.at(a)
            // Inside the screen, clear of the bezel, and clear of the plate in the middle.
            assertTrue(hypot(p.x - mid, p.y - mid) + AtomShells.DOT < mid - 4, "atom $a off the screen")
            assertTrue(hypot(p.x - mid, p.y - mid) - AtomShells.DOT > AtomShells.PLATE / 2 * 1.42, "atom $a under the plate")
            assertEquals(a, AtomShells.hit(p.x, p.y))
            for (b in a + 1..ATOMS) {
                val q = AtomShells.at(b)
                assertTrue(hypot(p.x - q.x, p.y - q.y) > AtomShells.DOT * 2 + 1, "atoms $a and $b touch")
            }
        }
        assertEquals(-1, AtomShells.hit(mid, mid))
        // The foot is open, for the switch between views.
        assertEquals(0, AtomShells.hit(mid, 272.0))
    }

    @Test fun dialPlatesStayApartAndClearOfTheMiddle() {
        for (pos in listOf(1.0, 26.0, 26.5, 118.0)) {
            val shown = AtomDial.showing(pos).toList()
            for (i in shown) {
                val d = i - pos
                val p = AtomDial.at(d)
                val h = AtomDial.side(d) / 2
                if (AtomDial.alpha(d) > 0.5) assertEquals(i, AtomDial.hit(p.x, p.y, pos))
                assertTrue(hypot(p.x - mid, p.y - AtomDial.CLOUD_Y) > AtomDial.CLOUD_R + h, "plate $i on the cloud")
                for (j in shown) if (j > i) {
                    val q = AtomDial.at(j - pos)
                    val gap = maxOf(kotlin.math.abs(p.x - q.x), kotlin.math.abs(p.y - q.y)) - h - AtomDial.side(j - pos) / 2
                    assertTrue(gap > -0.5, "plates $i and $j overlap at $pos")
                }
            }
            assertTrue(AtomDial.middle(mid, AtomDial.CLOUD_Y))
        }
        // The foot is clear of plates, for the switch between views.
        assertEquals(0, AtomDial.hit(mid, 272.0, 40.0))
    }

    @Test fun mock() {
        if (System.getenv("MOCK") != "1") return
        val atoms = readAtoms()
        val clouds = Cloud.readAll(File("../app/src/main/assets/atoms.bin").readBytes())
        val c = Collection(building = 26, shells = 2, built = (1..25).associateWith { 0L }.toMutableMap())
        fun words(pen: SvgPen) = Words { s, x, y, size, color, bold, alpha -> if (alpha > 0.05) pen.text(s, x, y, size, color, if (bold) 600 else 500) }
        fun switch(pen: SvgPen, icon: Icon) { pen.dot(mid, 272.0, 16.0, Ink.K800); pen.icon(icon, mid, 272.0, 18.0, Ink.WHITE) }

        for (pos in listOf(26.0, 25.6)) {
            val pen = SvgPen()
            AtomDial.draw(pen, words(pen), atoms, c, pos, 26, Nebula(atoms[25], clouds[25]), 2500.0)
            switch(pen, Icon.SHELLS)
            pen.save(File(out, "build-dial-${(pos * 10).toInt()}.svg"))
        }
        val pen = SvgPen()
        AtomShells.draw(pen, words(pen), atoms, c, 26)
        switch(pen, Icon.TABLE)
        pen.save(File(out, "build-shells.svg"))

        // An atom's Build page: its shells round the bezel.
        for ((need, done, built) in listOf(Triple(4, 2, false), Triple(1, 0, false), Triple(6, 6, true))) {
            val p = SvgPen()
            p.shellRing(need, done, built)
            Nebula(atoms[25], clouds[25]).draw(p, mid, 110.0, 70.0, 2500.0, done / need.toDouble())
            p.save(File(out, "build-ring-$need-$done.svg"))
        }
        val icons = SvgPen()
        icons.dot(mid, mid, 40.0, Ink.K800)
        icons.icon(Icon.SHELLS, mid, mid, 48.0, Ink.WHITE)
        icons.save(File(out, "icon-shells.svg"))
    }

    private fun readAtoms(): List<Atom> {
        val text = File("../app/src/main/assets/atoms.json").readText()
        return Regex("""\{"z":(\d+),"s":"([^"]*)","n":"([^"]*)","p":(\d+),"g":(null|\d+),"b":"(\w)","hue":"#([0-9a-fA-F]{6})"""").findAll(text).map { m ->
            val g = m.groupValues
            Atom(g[1].toInt(), g[2], g[3], g[4].toInt(), g[5].toIntOrNull(), g[6], (0xFF000000 or g[7].toLong(16)).toInt(), "", "")
        }.toList().also { assertEquals(ATOMS, it.size) }
    }
}
