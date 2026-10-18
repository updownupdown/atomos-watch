package app.buildatomos.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionTest {
    @Test fun buildsInOrder() {
        val c = Collection()
        assertEquals(listOf(1, 2), c.add(3, 10))
        assertEquals(3, c.building)
        assertEquals(0, c.electrons)
        assertEquals(emptyList(), c.add(2, 11))
        assertEquals(2.0 / 3, c.progress, 1e-9)
    }

    @Test fun theWholeTableTakes7021Electrons() {
        val c = Collection()
        c.add(7020, 1)
        assertEquals(118, c.building)
        assertEquals(117, c.electrons)
        c.add(5, 2)
        assertTrue(c.done)
        assertEquals(118, c.built.size)
        assertEquals(emptyList(), c.add(5, 3))
    }

    @Test fun electronsForARun() {
        assertEquals(0, Collection.electronsFor(0.0, false))
        assertEquals(1, Collection.electronsFor(0.3, false))
        assertEquals(10, Collection.electronsFor(10.0, false))
        assertEquals(20, Collection.electronsFor(10.0, true))
    }

    /** The baked clouds read back whole: 118 atoms, every point inside the frame's box. */
    @Test fun bakedCloudsRead() {
        val f = File("../app/src/main/assets/atoms.bin").takeIf { it.exists() } ?: File("app/src/main/assets/atoms.bin")
        val clouds = Cloud.readAll(f.readBytes())
        assertEquals(ATOMS, clouds.size)
        for (c in clouds) {
            assertTrue(c.size in 100..2000, "points: ${c.size}")
            assertTrue(c.walk.isNotEmpty())
        }
    }

    @Test fun nebulaDraws() {
        val f = File("../app/src/main/assets/atoms.bin").takeIf { it.exists() } ?: File("app/src/main/assets/atoms.bin")
        val clouds = Cloud.readAll(f.readBytes())
        val pen = CheckPen()
        for (z in listOf(1, 6, 26, 118)) {
            val a = Atom(z, "X", "X", 4, 1, "s", 0xFF3D7CC4.toInt(), "", "")
            for (share in listOf(0.0, 0.4, 1.0)) Nebula(a, clouds[z - 1]).draw(pen, 150.0, 150.0, 90.0, 12345.0, share)
        }
        val m = CheckPen()
        m.mark(150.0, 150.0, 100.0, nu = 3)
        m.scoreAtom(150.0, 150.0, 44.0, 12.4, ring = Ink.CYAN)
        for (g in listOf("halo", "orbit", "tune", "recall", "x")) m.gameIcon(g, 150.0, 150.0, 30.0, Ink.BLACK)
    }
}
