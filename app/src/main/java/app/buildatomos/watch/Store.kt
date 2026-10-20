// What the watch keeps: the collection, the best levels, the day's quiz and the settings, in one
// small store on the watch. Nothing here is shared with the phone.
//
// Players' saved data: the keys and the JSON shapes below are a contract with every watch that has
// played. Add new keys freely; never rename or reshape one without a migration.

package app.buildatomos.watch

import android.content.Context
import app.buildatomos.core.Collection
import app.buildatomos.core.Difficulty
import app.buildatomos.core.QuizDay
import org.json.JSONArray
import org.json.JSONObject

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("atomos", Context.MODE_PRIVATE)

    private companion object {
        /** {building, electrons, built: {z: day}}: the first collections, counted in electrons.
         *  Read once and moved to COLLECTION. */
        const val COLLECTION_V1 = "atomos.collection.v1"
        /** {building, shells, built: {z: day}}. */
        const val COLLECTION = "atomos.collection.v2"
        /** {"<game>.<level 0..4>": best level reached in free play}, from the five-level watch.
         *  Read once and moved to LEVELS (Difficulty.bestsFromFiveLevels). */
        const val LEVELS_V1 = "atomos.levels.v1"
        /** {"<game>.<level 0..2>": best level reached in free play}. */
        const val LEVELS = "atomos.levels.v2"
        /** {"<game>": difficulty level 0..4 last picked for free play}, from the five-level watch.
         *  Read once and moved to PICKED (Difficulty.pickedFromFiveLevels). */
        const val PICKED_V1 = "atomos.picked.v1"
        /** {"<game>": difficulty level 0..2 last picked for free play}. */
        const val PICKED = "atomos.picked.v2"
        /** {day, answers: [bool], streak, streakDay}. */
        const val QUIZ = "atomos.quiz.v1"
        const val SOUND = "atomos.sound"
        const val HAPTICS = "atomos.haptics"
        const val RUNS = "atomos.runs"
        /** {"<z>": ["<game>", ...]}: the game each shell of an atom was built with, innermost
         *  first; null for a shell built before this was kept. */
        const val SHELL_GAMES = "atomos.shellGames.v1"
    }

    fun loadCollection(): Collection {
        prefs.getString(COLLECTION, null)?.let { raw ->
            try {
                val o = JSONObject(raw)
                return Collection(o.optInt("building", 1).coerceIn(1, 119), o.optInt("shells", 0).coerceAtLeast(0), built(o))
            } catch (_: Exception) {
            }
        }
        val old = prefs.getString(COLLECTION_V1, null) ?: return Collection()
        return try {
            val o = JSONObject(old)
            Collection.fromElectrons(o.optInt("building", 1), o.optInt("electrons", 0), built(o)).also { saveCollection(it) }
        } catch (_: Exception) {
            Collection()
        }
    }

    private fun built(o: JSONObject): MutableMap<Int, Long> {
        val out = mutableMapOf<Int, Long>()
        o.optJSONObject("built")?.let { b -> b.keys().forEach { k -> k.toIntOrNull()?.let { out[it] = b.getLong(k) } } }
        return out
    }

    fun saveCollection(c: Collection) {
        val b = JSONObject()
        c.built.forEach { (z, day) -> b.put(z.toString(), day) }
        val o = JSONObject().put("building", c.building).put("shells", c.shells).put("built", b)
        prefs.edit().putString(COLLECTION, o.toString()).apply()
    }

    private fun intMap(key: String): MutableMap<String, Int> {
        val out = mutableMapOf<String, Int>()
        try {
            val o = JSONObject(prefs.getString(key, null) ?: return out)
            o.keys().forEach { k -> out[k] = o.optInt(k) }
        } catch (_: Exception) {
        }
        return out
    }

    private fun saveIntMap(key: String, m: Map<String, Int>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(key, o.toString()).apply()
    }

    /** The map under `key`; or, the first time, the one under `old` moved over by `move` (and
     *  saved under `key`, so it moves once). `old` is left as it was. */
    private fun intMapMoving(key: String, old: String, move: (Map<String, Int>) -> Map<String, Int>): MutableMap<String, Int> {
        if (prefs.contains(key) || !prefs.contains(old)) return intMap(key)
        val moved = move(intMap(old)).toMutableMap()
        saveIntMap(key, moved)
        return moved
    }

    fun loadLevels() = intMapMoving(LEVELS, LEVELS_V1) { Difficulty.bestsFromFiveLevels(it) }
    fun saveLevels(m: Map<String, Int>) = saveIntMap(LEVELS, m)
    fun loadPicked() = intMapMoving(PICKED, PICKED_V1) { Difficulty.pickedFromFiveLevels(it) }
    fun savePicked(m: Map<String, Int>) = saveIntMap(PICKED, m)

    fun loadShellGames(): MutableMap<Int, MutableList<String?>> {
        val out = mutableMapOf<Int, MutableList<String?>>()
        try {
            val o = JSONObject(prefs.getString(SHELL_GAMES, null) ?: return out)
            o.keys().forEach { k ->
                val z = k.toIntOrNull() ?: return@forEach
                val a = o.optJSONArray(k) ?: return@forEach
                out[z] = MutableList(a.length()) { i -> if (a.isNull(i)) null else a.optString(i) }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun saveShellGames(m: Map<Int, List<String?>>) {
        val o = JSONObject()
        m.forEach { (z, games) -> o.put(z.toString(), JSONArray(games.map { it ?: JSONObject.NULL })) }
        prefs.edit().putString(SHELL_GAMES, o.toString()).apply()
    }

    fun loadQuiz(): QuizDay? = try {
        val o = JSONObject(prefs.getString(QUIZ, null) ?: "")
        val a = o.optJSONArray("answers") ?: JSONArray()
        QuizDay(o.getInt("day"), (0 until a.length()).map { a.getBoolean(it) }, o.optInt("streak"), o.optInt("streakDay", Int.MIN_VALUE))
    } catch (_: Exception) {
        null
    }

    fun saveQuiz(q: QuizDay) {
        val o = JSONObject().put("day", q.day).put("answers", JSONArray(q.answers)).put("streak", q.streak).put("streakDay", q.streakDay)
        prefs.edit().putString(QUIZ, o.toString()).apply()
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
        prefs.edit().remove(COLLECTION).remove(COLLECTION_V1).remove(SHELL_GAMES).apply()
    }
}
