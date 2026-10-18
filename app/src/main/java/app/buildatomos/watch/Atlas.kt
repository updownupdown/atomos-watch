// The 118 atoms and their clouds, read once from the baked assets (tools/gen-atoms.mts).

package app.buildatomos.watch

import android.content.Context
import app.buildatomos.core.Atom
import app.buildatomos.core.Cloud
import org.json.JSONArray

class Atlas(context: Context) {
    val atoms: List<Atom>
    val clouds: List<Cloud>

    init {
        val json = context.assets.open("atoms.json").bufferedReader().use { it.readText() }
        val a = JSONArray(json)
        atoms = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Atom(
                z = o.getInt("z"),
                symbol = o.getString("s"),
                name = o.getString("n"),
                period = o.getInt("p"),
                group = if (o.isNull("g")) null else o.getInt("g"),
                block = o.getString("b"),
                color = (0xFF000000 or o.getString("hue").removePrefix("#").toLong(16)).toInt(),
                fact1 = o.optString("f1"),
                fact2 = o.optString("f2"),
            )
        }
        clouds = Cloud.readAll(context.assets.open("atoms.bin").use { it.readBytes() })
    }

    fun atom(z: Int): Atom = atoms[(z - 1).coerceIn(0, atoms.size - 1)]
    fun cloud(z: Int): Cloud = clouds[(z - 1).coerceIn(0, clouds.size - 1)]
}
