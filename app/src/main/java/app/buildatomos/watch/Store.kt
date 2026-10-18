// What the watch keeps: the collection, the games' bests and the settings, in one small store on
// the watch. Nothing here is shared with the phone.
//
// Players' saved data: the keys and the JSON shapes below are a contract with every watch that has
// played. Add new keys freely; never rename or reshape one without a migration.

package app.buildatomos.watch

import android.content.Context
import app.buildatomos.core.Collection
import org.json.JSONObject

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("atomos", Context.MODE_PRIVATE)

    // Keys, versioned by name: atomos.collection.v1 holds {building, electrons, built: {z: day}}.
    private companion object {
        const val COLLECTION = "atomos.collection.v1"
        const val BESTS = "atomos.bests.v1"
        const val SOUND = "atomos.sound"
        const val HAPTICS = "atomos.haptics"
        const val RUNS = "atomos.runs"
    }

    fun loadCollection(): Collection {
        val raw = prefs.getString(COLLECTION, null) ?: return Collection()
        return try {
            val o = JSONObject(raw)
            val built = mutableMapOf<Int, Long>()
            o.optJSONObject("built")?.let { b -> b.keys().forEach { k -> k.toIntOrNull()?.let { built[it] = b.getLong(k) } } }
            Collection(o.optInt("building", 1).coerceIn(1, 119), o.optInt("electrons", 0).coerceAtLeast(0), built)
        } catch (e: Exception) {
            Collection()
        }
    }

    fun saveCollection(c: Collection) {
        val b = JSONObject()
        c.built.forEach { (z, day) -> b.put(z.toString(), day) }
        val o = JSONObject().put("building", c.building).put("electrons", c.electrons).put("built", b)
        prefs.edit().putString(COLLECTION, o.toString()).apply()
    }

    fun loadBests(): MutableMap<String, Double> {
        val out = mutableMapOf<String, Double>()
        val raw = prefs.getString(BESTS, null) ?: return out
        try {
            val o = JSONObject(raw)
            o.keys().forEach { k -> val v = o.optDouble(k); if (v.isFinite()) out[k] = v }
        } catch (_: Exception) {
        }
        return out
    }

    fun saveBests(b: Map<String, Double>) {
        val o = JSONObject()
        b.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(BESTS, o.toString()).apply()
    }

    /** Sound is off until the player turns it on. */
    var sound: Boolean
        get() = prefs.getBoolean(SOUND, false)
        set(v) = prefs.edit().putBoolean(SOUND, v).apply()

    /** Light taps on the wrist, on until the player turns them off. */
    var haptics: Boolean
        get() = prefs.getBoolean(HAPTICS, true)
        set(v) = prefs.edit().putBoolean(HAPTICS, v).apply()

    /** Runs played, all games. */
    var runs: Int
        get() = prefs.getInt(RUNS, 0)
        set(v) = prefs.edit().putInt(RUNS, v).apply()

    fun clearCollection() {
        prefs.edit().remove(COLLECTION).apply()
    }
}
