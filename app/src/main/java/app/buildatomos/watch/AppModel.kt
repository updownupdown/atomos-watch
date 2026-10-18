// The app's state: the collection, the bests and the settings, kept in the Store, with a
// version that bumps on every change so the screens redraw.

package app.buildatomos.watch

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.buildatomos.core.Collection
import app.buildatomos.core.Games
import app.buildatomos.watch.tile.BuildComplicationService
import app.buildatomos.watch.tile.NuTileService
import java.time.LocalDate

/** What a run paid into the collection, for Home to show once. */
class Payout(val electrons: Int, val fromZ: Int, val fromProgress: Double, val built: List<Int>)

class AppModel(private val context: Context) {
    val store = Store(context)
    val atlas by lazy { Atlas(context) }
    val feel = Feel(context, store)
    val collection: Collection = store.loadCollection()
    val bests: MutableMap<String, Double> = store.loadBests()

    /** Bumps whenever something the screens show changes. */
    var version by mutableIntStateOf(0)
        private set
    /** The last run's payout, until Home has shown it. */
    var payout by mutableStateOf<Payout?>(null)

    var sound: Boolean
        get() { version; return store.sound }
        set(v) { store.sound = v; version++ }
    var haptics: Boolean
        get() { version; return store.haptics }
        set(v) { store.haptics = v; version++ }

    fun today(): Long = LocalDate.now().toEpochDay()

    /** The game Nu wants to play now: today's pick, then a different one after each run. */
    fun nuPick() = Games.all[((Games.all.indexOf(Games.nuPick(today())) + store.runs) % Games.all.size)]

    /** A run ended at `raw` in game `id`'s own count; `bestBefore` was the best before it.
     *  Pays its electrons into the collection and returns how many. */
    fun finishRun(id: String, raw: Double, bestBefore: Double): Int {
        val points = Games.points(id, raw)
        val newBest = raw > bestBefore && bestBefore > 0
        val n = Collection.electronsFor(points, newBest)
        val fromZ = collection.building
        val fromP = collection.progress
        val built = collection.add(n, today())
        store.saveCollection(collection)
        store.saveBests(bests)
        store.runs = store.runs + 1
        if (n > 0) payout = Payout(n, fromZ, fromP, built)
        version++
        refreshWatchFace()
        return n
    }

    fun resetCollection() {
        store.clearCollection()
        collection.building = 1
        collection.electrons = 0
        collection.built.clear()
        payout = null
        version++
        refreshWatchFace()
    }

    /** Asks the tile and the complication to show the new state. */
    private fun refreshWatchFace() {
        try {
            TileService.getUpdater(context).requestUpdate(NuTileService::class.java)
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, BuildComplicationService::class.java)).requestUpdateAll()
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile private var one: AppModel? = null
        fun get(context: Context): AppModel = one ?: synchronized(this) { one ?: AppModel(context.applicationContext).also { one = it } }
    }
}
