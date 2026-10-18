// The list screens: all the games, one atom's page, and Settings. Each is a ScalingLazyColumn,
// scrolled by touch or the crown.

package app.buildatomos.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import app.buildatomos.core.Games
import app.buildatomos.core.Ink
import app.buildatomos.core.Nebula
import app.buildatomos.core.gameIcon
import app.buildatomos.core.scoreAtom
import app.buildatomos.watch.AppModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun WatchList(content: androidx.wear.compose.foundation.lazy.ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScalingLazyColumn(
        Modifier.fillMaxSize().background(Color.Black),
        state = state,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Every game, A to Z: its card icon, its name, and the best as a tiny atom. */
@Composable
fun GamesScreen(model: AppModel, onPlay: (String) -> Unit) {
    model.version
    val pick = model.nuPick().id
    WatchList {
        items(Games.all) { g ->
            PlateRow(onClick = { onPlay(g.id) }) {
                Canvas(Modifier.size(34.dp)) {
                    val pen = ComposePen(this, 34.0)
                    pen.dot(17.0, 17.0, 17.0, Ink.WHITE)
                    pen.gameIcon(g.id, 17.0, 17.0, 20.0, Ink.BLACK)
                }
                Label(g.label, Modifier.weight(1f), size = 15.sp, align = TextAlign.Start, lines = 1)
                if (g.id == pick) {
                    // Nu's pick today.
                    Canvas(Modifier.size(10.dp)) { ComposePen(this, 10.0).dot(5.0, 5.0, 4.0, Ink.ROSE) }
                }
                val best = model.bests[g.id] ?: 0.0
                if (best > 0) Canvas(Modifier.size(28.dp)) {
                    ComposePen(this, 28.0).scoreAtom(14.0, 14.0, 26.0, Games.points(g.id, best), Ink.K300)
                }
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** One atom: its cloud (in color once built), its name, when it was built, and two facts. */
@Composable
fun AtomScreen(model: AppModel, z: Int) {
    model.version
    val atom = model.atlas.atom(z)
    val nebula = remember(z) { Nebula(atom, model.atlas.cloud(z)) }
    val builtOn = model.collection.built[z]
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }
    WatchList {
        item {
            Canvas(Modifier.size(104.dp)) {
                frame
                nebula.draw(ComposePen(this, 104.0), 52.0, 52.0, 48.0, frame / 1e6, if (builtOn != null) 1.0 else 0.0, still = builtOn == null)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("${atom.z}", size = 14.sp, color = Tone.text2)
                Label(atom.name, size = 18.sp, weight = FontWeight.SemiBold)
            }
        }
        item {
            if (builtOn != null) Label("Built: ${LocalDate.ofEpochDay(builtOn).format(DAY)}", size = 13.sp, color = Tone.green)
            else Label("Not built yet", size = 13.sp, color = Tone.text2)
        }
        item {
            val group = atom.group?.let { ", group $it" } ?: ""
            Label("Period ${atom.period}$group, ${atom.block} block", size = 13.sp, color = Tone.text2)
        }
        for (f in listOf(atom.fact1, atom.fact2).filter { it.isNotBlank() }) item {
            Label(f, Modifier.fillMaxWidth().padding(horizontal = 6.dp), size = 14.sp, weight = FontWeight.Normal)
        }
    }
}

/** Sound (off to start), haptics (on to start, and soft), and starting the collection over. */
@Composable
fun SettingsScreen(model: AppModel) {
    model.version
    var asking by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        WatchList {
            item { SwitchRow("Sound", model.sound) { model.sound = it; if (it) model.feel.chime(2) } }
            item { SwitchRow("Haptics", model.haptics) { model.haptics = it; if (it) model.feel.hit(0.5) } }
            item {
                PlateRow(onClick = { asking = true }) {
                    Label("Start the collection over", Modifier.weight(1f), size = 14.sp, color = Color(Ink.RED), align = TextAlign.Start)
                }
            }
            item {
                Label("ATOMOS ${model.versionName}", Modifier.padding(top = 6.dp), size = 13.sp, color = Tone.text2)
            }
        }
        if (asking) Confirm(
            question = "Start the collection over? Every atom you built goes.",
            onNo = { asking = false },
            onYes = { asking = false; model.resetCollection() },
        )
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, set: (Boolean) -> Unit) {
    PlateRow(onClick = { set(!on) }) {
        Label(label, Modifier.weight(1f), size = 15.sp, align = TextAlign.Start)
        Canvas(Modifier.width(36.dp).height(22.dp)) {
            val pen = ComposePen(this, 36.0)
            // The pen's square is 36 across, centered: the switch sits on its middle line.
            val y = 18.0
            pen.rect(1.0, y - 10, 34.0, 20.0, 10.0, if (on) Ink.CYAN else Ink.K700)
            pen.dot(if (on) 25.0 else 11.0, y, 7.0, if (on) Ink.BLACK else Ink.WHITE)
        }
    }
}

/** A destructive action asks first: the question, a gray No and a red Yes. */
@Composable
fun Confirm(question: String, onNo: () -> Unit, onYes: () -> Unit) {
    BackHandler { onNo() }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 26.dp)) {
            Label(question, size = 15.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RoundButton(Ink.K700, onNo) { pen ->
                    pen.line(16.0, 16.0, 32.0, 32.0, Ink.WHITE, 3.5)
                    pen.line(32.0, 16.0, 16.0, 32.0, Ink.WHITE, 3.5)
                }
                RoundButton(Ink.RED, onYes) { pen ->
                    pen.line(14.0, 25.0, 21.0, 32.0, Ink.BLACK, 3.5)
                    pen.line(21.0, 32.0, 34.0, 17.0, Ink.BLACK, 3.5)
                }
            }
        }
    }
}

@Composable
private fun RoundButton(color: Int, onClick: () -> Unit, mark: (ComposePen) -> Unit) {
    Canvas(Modifier.size(48.dp).clickable(onClick = onClick)) {
        val pen = ComposePen(this, 48.0)
        pen.dot(24.0, 24.0, 24.0, color)
        mark(pen)
    }
}
