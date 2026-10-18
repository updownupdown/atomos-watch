package app.buildatomos.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionTest {
    @Test fun buildsShellByShell() {
        val c = Collection()
        assertEquals(1, c.need)
        assertEquals(1, c.win(10))
        assertEquals(2, c.building)
        assertEquals(2, c.win(10))
        assertEquals(2, c.need) // lithium
        assertNull(c.win(11))
        assertEquals(0.5, c.progress, 1e-9)
        assertEquals(3, c.win(11))
        assertEquals(setOf(1, 2, 3), c.built.keys)
    }

    @Test fun theWholeTableTakes620Wins() {
        val c = Collection()
        var wins = 0
        while (!c.done) { c.win(1); wins++ }
        assertEquals(620, wins)
        assertEquals(ATOMS, c.built.size)
        assertNull(c.win(2))
    }

    @Test fun electronsBecomeShells() {
        assertEquals(1, Collection.fromElectrons(6, 3, mutableMapOf()).shells) // carbon half built
        assertEquals(0, Collection.fromElectrons(6, 2, mutableMapOf()).shells)
        assertEquals(0, Collection.fromElectrons(1, 0, mutableMapOf()).shells)
        assertEquals(6, Collection.fromElectrons(118, 117, mutableMapOf()).shells)
        assertTrue(Collection.fromElectrons(119, 0, mutableMapOf()).done)
    }

    @Test fun shellsOfAnAtom() {
        assertEquals(listOf(2, 4), shellCounts(6, listOf("1s2", "2s2", "2p2")).toList())
        assertEquals(listOf(2, 8, 14, 2), shellCounts(26, listOf("1s2", "2s2", "2p6", "3s2", "3p6", "3d6", "4s2")).toList())
    }

    @Test fun atomInfoReads() {
        val f = File("../app/src/main/assets/atoms-info.json").takeIf { it.exists() } ?: File("app/src/main/assets/atoms-info.json")
        val info = AtomInfo.readAll(f.readText())
        assertEquals(ATOMS, info.size)
        assertEquals("Gas", info[0].phase)
        assertEquals("Solid", info[25].phase)
        assertEquals("Liquid", info[79].phase)
        assertEquals("noble gas", info[1].kind)
        assertTrue(info.all { it.headline.isNotBlank() })
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
        for (goal in listOf(3, 7, null)) for (lv in listOf(0, 2, 200)) m.levels(150.0, 150.0, 44.0, lv, goal, 0.5)
        m.pips(150.0, 150.0, 40.0, 55)
        for (g in listOf("pulse", "orbit", "tune", "recall", "x")) m.gameIcon(g, 150.0, 150.0, 30.0, Ink.BLACK)
    }
}
