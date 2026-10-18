// The app's state: the collection, the best levels, the day's quiz and the settings, kept in the
// Store, with a version that bumps on every change so the screens redraw.

package app.buildatomos.watch

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.buildatomos.core.ATOMS
import app.buildatomos.core.Collection
import app.buildatomos.core.Difficulty
import app.buildatomos.core.Game
import app.buildatomos.core.Games
import app.buildatomos.core.Result
import app.buildatomos.watch.tile.BuildComplicationService
import app.buildatomos.watch.tile.NuComplicationService
import app.buildatomos.watch.tile.NuTileService
import java.time.LocalDate

/** What a run is for: building the next shell of atom `building`, at its dial and toward its goal,
 *  or free play (`building` null, no goal) at a picked dial. */
class RunSpec(val game: Game, val dial: Int, val goal: Int?, val building: Int?) {
    val free get() = building == null
}

/** What a won run built, for Home to show once: the shell it added to atom `z` (0 the innermost),
 *  and whether that finished the atom. */
class Payout(val z: Int, val shell: Int, val finished: Boolean)

class AppModel(private val context: Context) {
    val store = Store(context)
    val atlas by lazy { Atlas(context) }
    val feel = Feel(context, store)
    val collection: Collection = store.loadCollection()
    /** Best level reached in free play, by "<game>.<difficulty level>". */
    private val levels: MutableMap<String, Int> = store.loadLevels()
    /** The difficulty level last picked for each game in free play. */
    private val picked: MutableMap<String, Int> = store.loadPicked()

    /** Bumps whenever something the screens show changes. */
    var version by mutableIntStateOf(0)
        private set
    /** The last won run's payout, until Home has shown it. */
    var payout by mutableStateOf<Payout?>(null)

    var sound: Boolean
        get() { version; return store.sound }
        set(v) { store.sound = v; version++ }
    var haptics: Boolean
        get() { version; return store.haptics }
        set(v) { store.haptics = v; version++ }

    val versionName: String by lazy {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
    }

    fun today(): Long = LocalDate.now().toEpochDay()

    /** The game Nu wants to play next: today's pick, moved on by every run played. */
    fun nuPick(): Game { version; return Games.forRun(today(), store.runs) }

    /** The next run for the atom being built (null once every atom is). */
    fun buildRun(): RunSpec? {
        val c = collection
        if (c.done) return null
        return RunSpec(nuPick(), c.dial, c.goal, c.building)
    }

    fun freeRun(game: Game, level: Int = pickedLevel(game)): RunSpec = RunSpec(game, Difficulty.dialOf(level), null, null)

    fun pickedLevel(game: Game): Int { version; return picked[game.id] ?: 1 }
    fun pick(game: Game, level: Int) {
        picked[game.id] = level.coerceIn(0, Difficulty.LEVELS - 1)
        store.savePicked(picked)
        version++
    }

    /** The best level reached in free play in `game` at difficulty `level`, 0 for none. */
    fun bestLevel(game: Game, level: Int): Int { version; return levels["${game.id}.$level"] ?: 0 }

    /** A run is over, `result` after clearing `cleared` levels. Builds a shell for a run won for an
     *  atom; keeps the best for free play. Returns true for a new best in free play. */
    fun finishRun(run: RunSpec, result: Result, cleared: Int): Boolean {
        store.runs = store.runs + 1
        var newBest = false
        val z = run.building
        if (z != null) {
            if (result == Result.WON && collection.building == z) {
                val shell = collection.shells
                val done = collection.win(today())
                store.saveCollection(collection)
                payout = Payout(z, shell, done != null)
            }
        } else {
            val key = "${run.game.id}.${Difficulty.level(run.dial)}"
            if (cleared > (levels[key] ?: 0)) {
                newBest = (levels[key] ?: 0) > 0
                levels[key] = cleared
                store.saveLevels(levels)
            }
        }
        version++
        refreshWatchFace()
        return newBest
    }

    fun resetCollection() {
        store.clearCollection()
        collection.building = 1
        collection.shells = 0
        collection.built.clear()
        payout = null
        version++
        refreshWatchFace()
    }

    /** The building atom, clamped for a finished table. */
    val buildingZ get() = minOf(collection.building, ATOMS)

    /** Asks the tile and the complications to show the new state. */
    private fun refreshWatchFace() {
        try {
            TileService.getUpdater(context).requestUpdate(NuTileService::class.java)
            for (cls in listOf(BuildComplicationService::class.java, NuComplicationService::class.java)) {
                ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, cls)).requestUpdateAll()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile private var one: AppModel? = null
        fun get(context: Context): AppModel = one ?: synchronized(this) { one ?: AppModel(context.applicationContext).also { one = it } }
    }
}
