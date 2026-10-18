package app.buildatomos.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertiesTest {
    private val info = AtomInfo.readAll((File("../app/src/main/assets/atoms-info.json").takeIf { it.exists() } ?: File("app/src/main/assets/atoms-info.json")).readText())

    @Test fun ordinals() {
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "101st", "111th"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 101, 111).map(Crowd::ordinal),
        )
    }

    @Test fun ranksFromTheNearerEnd() {
        // Osmium is the densest measured atom, hydrogen the lightest; tungsten melts highest.
        assertEquals("the densest", Crowd(Property.DENSITY, info, 76).rank)
        assertEquals("the lightest", Crowd(Property.DENSITY, info, 1).rank)
        assertEquals("the highest melting point", Crowd(Property.MELT, info, 74).rank)
        assertEquals("the most electronegative", Crowd(Property.ELECTRONEGATIVITY, info, 9).rank)
        assertEquals("the most common in the crust", Crowd(Property.CRUST, info, 8).rank)
        assertEquals("a prediction", Crowd(Property.DENSITY, info, 108).rank)
        assertNull(Crowd(Property.ELECTRONEGATIVITY, info, 2).rank)
    }

    @Test fun everyAtomSitsOnTheScale() {
        for (p in Property.ALL) for (i in info) {
            val c = Crowd(p, info, i.z)
            val v = c.value ?: continue
            val at = c.place(v)
            assertTrue(at in 0.0..1.0, "${p.label} ${i.z}")
            if (i.z < Property.PREDICTED_FROM && (!p.log || v > 0)) {
                if (v == c.lowest.second) assertEquals(0.0, at)
                if (v == c.highest.second) assertEquals(1.0, at)
            }
            // The superheavies' predictions stay out of everyone else's crowd.
            if (i.z < Property.PREDICTED_FROM) assertTrue(c.all.none { it.first >= Property.PREDICTED_FROM })
        }
    }
}
