// Build: the collection. The Build screen shows the atom being built on its card, then every
// period with its atoms as plates, built ones in their own color. Each atom has a Build page: the
// atom as far as it has come, View in Atlas, and its shells as a track, one game each, the next
// one with its game, its difficulty and Play.

package app.buildatomos.watch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildatomos.core.ATOMS
import app.buildatomos.core.Difficulty
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.Nebula
import app.buildatomos.core.icon
import app.buildatomos.core.periodOf
import app.buildatomos.core.pips
import app.buildatomos.core.shellCounts
import app.buildatomos.watch.AppModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
/** The shells' letters, K innermost. */
private const val SHELL_LETTERS = "KLMNOPQ"

/** Where an atom stands in the collection. */
private enum class Standing { BUILT, BUILDING, LATER }

private fun AppModel.standing(z: Int) = when {
    collection.isBuilt(z) -> Standing.BUILT
    z == collection.building -> Standing.BUILDING
    else -> Standing.LATER
}

/** How far atom z's cloud is filled in, 0..1. */
fun AppModel.share(z: Int): Double = when {
    collection.isBuilt(z) -> 1.0
    z == collection.building -> collection.progress
    else -> 0.0
}

/** A frame clock for a screen that draws atoms. */
@Composable
fun rememberFrame(): () -> Long {
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }
    return { frame }
}

/** The Build screen: the atom being built, then each period's atoms. */
@Composable
fun CollectionScreen(model: AppModel, onBuild: () -> Unit, onAtom: (Int) -> Unit) {
    model.version
    val frame = rememberFrame()
    val c = model.collection
    WatchList(start = 1) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label("${c.built.size} of $ATOMS built", size = 14.sp)
                Bar(c.built.size / ATOMS.toDouble(), Modifier.padding(top = 4.dp).size(width = 70.dp, height = 4.dp))
            }
        }
        item { BuildCard(model, frame, onBuild, onAtom) }
        for (p in 1..7) item { PeriodPlate(model, p, onAtom) }
    }
}

/** A thin progress bar: cyan over K700. */
@Composable
fun Bar(progress: Double, modifier: Modifier, color: Int = Ink.CYAN) {
    Canvas(modifier) {
        val w = size.width.toDouble() / density
        val pen = ComposePen(this, w, wide = true)
        val h = size.height.toDouble() / density
        pen.rect(0.0, 0.0, w, h, h / 2, Ink.K700)
        if (progress > 0) pen.rect(0.0, 0.0, maxOf(h, w * progress.coerceIn(0.0, 1.0)), h, h / 2, color)
    }
}

/** One period: its name and count, and its atoms as plates five to a row. */
@Composable
private fun PeriodPlate(model: AppModel, period: Int, onAtom: (Int) -> Unit) {
    val atoms = model.atlas.atoms.filter { it.period == period }
    val built = atoms.count { model.collection.isBuilt(it.z) }
    Plate {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("Period $period", Modifier.weight(1f), size = 14.sp, align = TextAlign.Start)
                Label("$built/${atoms.size}", size = 13.sp, color = if (built == atoms.size) Tone.green else Tone.text2)
            }
            for (row in atoms.chunked(5)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (a in row) {
                    val st = model.standing(a.z)
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(if (st == Standing.BUILT) Color(a.color) else Tone.chip, shape)
                            .then(if (st == Standing.BUILDING) Modifier.border(2.dp, Tone.rose, shape) else Modifier)
                            .clickable { onAtom(a.z) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Label(
                            a.symbol, size = 13.sp, lines = 1, weight = FontWeight.SemiBold,
                            color = when (st) { Standing.BUILT -> inkOn(a.color); Standing.BUILDING -> Tone.text; Standing.LATER -> Tone.text2 },
                        )
                    }
                }
                // Keep a short last row's plates the same size.
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The atom being built: its cloud, its name and shells, the next shell's game and difficulty,
 *  and the rose Build. After a won run its cloud fills to the new share; a finished atom shows
 *  green for a moment before the next one takes its place. Tapping the atom opens its Build page. */
@Composable
fun BuildCard(model: AppModel, frame: () -> Long, onBuild: () -> Unit, onAtom: (Int) -> Unit) {
    val c = model.collection
    val payout = model.payout
    var finished by remember { mutableIntStateOf(0) }
    val share = remember { Animatable(c.progress.toFloat()) }
    LaunchedEffect(payout) {
        if (payout == null) { share.snapTo(c.progress.toFloat()); return@LaunchedEffect }
        val need = periodOf(payout.z)
        share.snapTo(payout.shell / need.toFloat())
        delay(250)
        share.animateTo((payout.shell + 1) / need.toFloat(), tween(900))
        if (payout.finished) {
            finished = payout.z
            model.feel.complete()
            delay(2200)
            finished = 0
        }
        // Last: clearing it restarts this with no payout, which settles on the collection's share.
        model.payout = null
    }
    if (c.done && finished == 0) {
        Plate { Label("Every atom built", size = 15.sp, color = Tone.green) }
        return
    }
    val z = if (finished != 0) finished else model.buildingZ
    val atom = model.atlas.atom(z)
    val nebula = remember(z) { Nebula(atom, model.atlas.cloud(z)) }
    val run = model.buildRun()
    Plate {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Canvas(Modifier.size(92.dp).clickable { onAtom(z) }) {
                val now = frame() / 1e6
                val pen = ComposePen(this, 92.0)
                val sh = if (finished != 0) 1.0 else share.value.toDouble()
                nebula.draw(pen, 46.0, 46.0, 42.0, now, sh, still = sh <= 0)
                if (finished != 0) pen.ring(46.0, 46.0, 45.0, Ink.GREEN, 2.0)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Label("$z", size = 13.sp, color = Tone.text2)
                Label(atom.name, size = 16.sp, weight = FontWeight.SemiBold)
            }
            val need = periodOf(z)
            if (finished != 0) Label("Built", size = 13.sp, color = Tone.green)
            else Label("Shells: ${c.shells} of $need", size = 13.sp, color = Tone.text2)
            if (run != null && finished == 0) {
                Row(
                    Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GameIcon(run.game.id, 20)
                    Label(run.game.label, size = 14.sp)
                    Canvas(Modifier.size(width = 30.dp, height = 8.dp)) {
                        ComposePen(this, 30.0, wide = true).pips(15.0, 4.0, 28.0, run.dial)
                    }
                }
                Pill("Build", Icon.PLAY, onBuild, Modifier.padding(top = 8.dp).fillMaxWidth(0.8f), fill = Tone.rose, ink = Color.Black)
            }
        }
    }
}

/** One atom's Build page. */
@Composable
fun AtomBuildScreen(model: AppModel, z: Int, onBuild: () -> Unit, onAtlas: (Int) -> Unit, onAtom: (Int) -> Unit) {
    model.version
    val frame = rememberFrame()
    val atom = model.atlas.atom(z)
    val c = model.collection
    val st = model.standing(z)
    val counts = shellCounts(z, model.atlas.subs(z))
    val nebula = remember(z) { Nebula(atom, model.atlas.cloud(z)) }
    WatchList(start = 1) {
        item {
            Canvas(Modifier.size(104.dp)) {
                val share = model.share(z)
                nebula.draw(ComposePen(this, 104.0), 52.0, 52.0, 48.0, frame() / 1e6, share, still = share <= 0)
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("${atom.z}", size = 14.sp, color = Tone.text2)
                    Label(atom.name, size = 19.sp, weight = FontWeight.SemiBold)
                }
                when (st) {
                    Standing.BUILT -> Label("Built: ${LocalDate.ofEpochDay(c.built[z]!!).format(DAY)}", size = 13.sp, color = Tone.green)
                    Standing.BUILDING -> Label("Shells: ${c.shells} of ${c.need}", size = 13.sp, color = Tone.cyan)
                    Standing.LATER -> Label("Not built yet", size = 13.sp, color = Tone.text2)
                }
            }
        }
        item { Pill("View in Atlas", Icon.CHEVRON, { onAtlas(z) }) }
        if (st == Standing.LATER && !c.done) item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val now = model.atlas.atom(model.buildingZ)
                Para("Atoms are built in order, from hydrogen up. ${now.name} is next.")
                Pill("Go to ${now.name}", null, { onAtom(now.z) })
            }
        }
        for (i in counts.indices) item {
            val state = when {
                st == Standing.BUILT || (st == Standing.BUILDING && i < c.shells) -> Standing.BUILT
                st == Standing.BUILDING && i == c.shells -> Standing.BUILDING
                else -> Standing.LATER
            }
            ShellStep(model, z, i, counts[i], state, onBuild)
        }
        item {
            Para("Each shell is one game: clear its levels to build it. The outer shells are harder.")
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (z > 1) Step("${z - 1} ${model.atlas.atom(z - 1).symbol}", Modifier.weight(1f)) { onAtom(z - 1) }
                if (z < ATOMS) Step("${z + 1} ${model.atlas.atom(z + 1).symbol}", Modifier.weight(1f)) { onAtom(z + 1) }
            }
        }
    }
}

/** One shell on the Build page's track: its number and letter and its electrons; built (a green
 *  check), next (its game and difficulty, and Play), or later (its difficulty, dim). */
@Composable
private fun ShellStep(model: AppModel, z: Int, shell: Int, electrons: Int, state: Standing, onBuild: () -> Unit) {
    val dial = Difficulty.buildDial(z, shell)
    val run = if (state == Standing.BUILDING) model.buildRun() else null
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tone.plate, RoundedCornerShape(26.dp))
            .then(if (state == Standing.BUILDING) Modifier.border(1.5.dp, Tone.rose, RoundedCornerShape(26.dp)).clickable(onClick = onBuild) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(30.dp)) {
                val pen = ComposePen(this, 30.0)
                when (state) {
                    Standing.BUILT -> { pen.dot(15.0, 15.0, 15.0, Ink.GREEN); pen.icon(Icon.CHECK, 15.0, 15.0, 18.0, Ink.BLACK) }
                    Standing.BUILDING -> pen.ring(15.0, 15.0, 14.0, Ink.WHITE, 2.0)
                    Standing.LATER -> pen.dot(15.0, 15.0, 15.0, Ink.K800)
                }
            }
            if (state != Standing.BUILT) Label("${shell + 1}", size = 14.sp, color = if (state == Standing.LATER) Tone.text2 else Tone.text)
        }
        Column(Modifier.weight(1f)) {
            Label("Shell ${SHELL_LETTERS[shell]}: $electrons electron${if (electrons == 1) "" else "s"}", size = 14.sp, align = TextAlign.Start, color = if (state == Standing.LATER) Tone.text2 else Tone.text)
            if (state != Standing.BUILT) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (run != null) {
                    GameIcon(run.game.id, 16)
                    Label(run.game.label, size = 13.sp, lines = 1)
                }
                Canvas(Modifier.size(width = 28.dp, height = 8.dp)) { ComposePen(this, 28.0, wide = true).pips(14.0, 4.0, 26.0, dial) }
            }
        }
        if (state == Standing.BUILDING) IconMark(Icon.PLAY, 34, Ink.BLACK, disc = Ink.ROSE)
    }
}

@Composable
fun Step(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(40.dp).background(Tone.plate, RoundedCornerShape(20.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Label(label, size = 14.sp)
    }
}

@Composable
fun Para(text: String, inside: Boolean = false) {
    Label(text, Modifier.fillMaxWidth().padding(horizontal = if (inside) 0.dp else 6.dp), size = 14.sp, weight = FontWeight.Normal, align = if (inside) TextAlign.Start else TextAlign.Center)
}
