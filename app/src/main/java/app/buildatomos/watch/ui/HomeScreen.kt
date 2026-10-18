// Home, a compact take on the phone's "Today": a list the crown scrolls. At the top the mark
// with Nu on it; then the major cards on plates, the atom being built (its cloud in color as far
// as its shells have come, the game and difficulty of the next shell, and the rose Build) and
// Nu's questions of the day; then the minor rows straight on black: all the atoms (the Atlas),
// all the games (free play) and Settings.

package app.buildatomos.watch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import app.buildatomos.core.ATOMS
import app.buildatomos.core.Difficulty
import app.buildatomos.core.Ink
import app.buildatomos.core.Nebula
import app.buildatomos.core.gameIcon
import app.buildatomos.core.mark
import app.buildatomos.core.pips
import app.buildatomos.watch.AppModel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun HomeScreen(model: AppModel, onBuild: () -> Unit, onAtlas: () -> Unit, onGames: () -> Unit, onSettings: () -> Unit, onAtom: (Int) -> Unit) {
    model.version
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }
    ScalingLazyColumn(
        Modifier.fillMaxSize().background(Color.Black),
        state = state,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Header(model) { frame } }
        item { BuildCard(model, { frame }, onBuild, onAtom) }
        item { QuizCard(model, onAtom) }
        item { MinorRow("Explore all atoms", "${model.collection.built.size}/$ATOMS", model.collection.built.size / ATOMS.toDouble(), onAtlas) }
        item { MinorRow("Explore all games", null, null, onGames) }
        item { MinorRow("Settings", null, null, onSettings) }
    }
}

/** The mark, with Nu resting on one of its nucleons; tapped, Nu glows warmer and hops. */
@Composable
private fun Header(model: AppModel, frame: () -> Long) {
    var petAt by remember { mutableLongStateOf(0L) }
    Canvas(
        Modifier.size(40.dp).clickable { petAt = System.currentTimeMillis(); model.feel.capture() },
    ) {
        frame()
        val pen = ComposePen(this, 40.0)
        pen.mark(20.0, 20.0, 34.0, Ink.WHITE, nu = 2)
        val since = (System.currentTimeMillis() - petAt) / 1000.0
        if (since < 1.2) {
            // The mark's nucleon 2 sits at 90 degrees: just below the middle.
            val hop = if (since < 0.5) sin(since / 0.5 * PI) * 3 else 0.0
            pen.dot(20.0, 20.0 + 34 * 0.01 * 16.6 - hop, 4.0, Ink.ROSE, 0.25 * (1.2 - since))
        }
    }
}

/** The atom being built: its cloud, its name and shells, the next shell's game and difficulty,
 *  and the rose Build. After a won run its cloud fills to the new share; a finished atom shows
 *  green for a moment before the next one takes its place. */
@Composable
private fun BuildCard(model: AppModel, frame: () -> Long, onBuild: () -> Unit, onAtom: (Int) -> Unit) {
    val c = model.collection
    val payout = model.payout
    var finished by remember { mutableIntStateOf(0) }
    val share = remember { Animatable(c.progress.toFloat()) }
    LaunchedEffect(payout) {
        if (payout != null) {
            model.payout = null
            val need = app.buildatomos.core.periodOf(payout.z)
            share.snapTo(payout.shell / need.toFloat())
            delay(250)
            share.animateTo((payout.shell + 1) / need.toFloat(), tween(900))
            if (payout.finished) {
                finished = payout.z
                model.feel.complete()
                delay(2200)
                finished = 0
            }
        }
        share.snapTo(c.progress.toFloat())
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
            val need = app.buildatomos.core.periodOf(z)
            if (finished != 0) Label("Built", size = 13.sp, color = Tone.green)
            else Label("Shells: ${c.shells} of $need", size = 13.sp, color = Tone.text2)
            if (run != null && finished == 0) {
                Row(
                    Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        val pen = ComposePen(this, 20.0)
                        pen.dot(10.0, 10.0, 10.0, Ink.WHITE)
                        pen.gameIcon(run.game.id, 10.0, 10.0, 12.0, Ink.BLACK)
                    }
                    Label(run.game.label, size = 14.sp)
                    Canvas(Modifier.size(width = 30.dp, height = 8.dp)) {
                        ComposePen(this, 30.0, wide = true).pips(15.0, 4.0, 28.0, run.dial)
                    }
                }
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(0.8f)
                        .height(36.dp)
                        .background(Tone.rose, RoundedCornerShape(18.dp))
                        .clickable(onClick = onBuild),
                    contentAlignment = Alignment.Center,
                ) { Label("Build", size = 15.sp, color = Color.Black, weight = FontWeight.SemiBold) }
            }
        }
    }
}

/** A major card: a rounded K900 plate. */
@Composable
fun Plate(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(Tone.plate, RoundedCornerShape(26.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A minor row, text only on black, with a progress bar and its count on the right if it has one. */
@Composable
private fun MinorRow(label: String, count: String?, progress: Double?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label(label, Modifier.weight(1f), size = 15.sp, align = TextAlign.Start, lines = 1)
        if (progress != null) Canvas(Modifier.size(width = 22.dp, height = 4.dp)) {
            val pen = ComposePen(this, 22.0, wide = true)
            pen.rect(0.0, 0.5, 22.0, 3.0, 1.5, Ink.K700)
            pen.rect(0.0, 0.5, 22.0 * progress.coerceIn(0.0, 1.0), 3.0, 1.5, Ink.CYAN)
        }
        if (count != null) Label(count, size = 13.sp, color = Tone.text2)
    }
}
