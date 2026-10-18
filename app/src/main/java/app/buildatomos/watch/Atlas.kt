// The 118 atoms, their clouds and what the Atlas tells about them, read once from the baked assets
// (tools/gen-atoms.mts and tools/gen-info.mts).

package app.buildatomos.watch

import android.content.Context
import app.buildatomos.core.Atom
import app.buildatomos.core.AtomInfo
import app.buildatomos.core.Cloud
import org.json.JSONArray

class Atlas(private val context: Context) {
    val atoms: List<Atom>
    val clouds: List<Cloud>
    /** Each atom's subshells and their electrons, "1s2", "2s2", "2p2"... */
    val subs: List<List<String>>
    val infos: List<AtomInfo> by lazy { AtomInfo.readAll(context.assets.open("atoms-info.json").bufferedReader().use { it.readText() }) }

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
        subs = (0 until a.length()).map { i -> a.getJSONObject(i).optJSONArray("subs").let { l -> if (l == null) emptyList() else (0 until l.length()).map { l.getString(it) } } }
        clouds = Cloud.readAll(context.assets.open("atoms.bin").use { it.readBytes() })
    }

    fun atom(z: Int): Atom = atoms[(z - 1).coerceIn(0, atoms.size - 1)]
    fun cloud(z: Int): Cloud = clouds[(z - 1).coerceIn(0, clouds.size - 1)]
    fun info(z: Int): AtomInfo = infos[(z - 1).coerceIn(0, infos.size - 1)]
    fun subs(z: Int): List<String> = subs[(z - 1).coerceIn(0, subs.size - 1)]
}
