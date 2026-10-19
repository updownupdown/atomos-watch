// Home, quiet: a list the crown scrolls, with no section labels and no plates. The mark and
// ATOMOS; the atom being built as its cloud, with the rose Build in the middle of it, its name
// and its shells as a tiny atom, and the next shell's game and difficulty; the way into the
// collection. Then the atom of the day and the way into the Atlas; the game Nu picked for today
// (its white disc plays it) and Nu's three questions; every game; and Settings as a bare gear.

package app.buildatomos.watch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import app.buildatomos.core.ATOMS
import app.buildatomos.core.Game
import app.buildatomos.core.Games
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.Nebula
import app.buildatomos.core.atomOfDay
import app.buildatomos.core.icon
import app.buildatomos.core.mark
import app.buildatomos.core.periodOf
import app.buildatomos.watch.AppModel
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    model: AppModel,
    onRun: () -> Unit,
    onCollection: () -> Unit,
    onBuildAtom: (Int) -> Unit,
    onAtlas: () -> Unit,
    onAtom: (Int) -> Unit,
    onPlay: (Game, Int) -> Unit,
    onGames: () -> Unit,
    onSettings: () -> Unit,
) {
    model.version
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val frame = rememberFrame()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        QuietList(state, top = 20.dp, gap = 10.dp) {
            item { Wordmark() }
            item { BuildHero(model, frame, onRun, onBuildAtom) }
            item { LinkRow(Icon.SHELLS, "Collection", "${model.collection.built.size}/$ATOMS", onCollection) }
            item { AtomOfDay(model, onAtom) }
            item { LinkRow(Icon.ATLAS, "All atoms", "$ATOMS", onAtlas) }
            item { TodayGame(model, onPlay) }
            item { QuizCard(model, onAtom) }
            item { LinkRow(Icon.GAMES, "All games", "${Games.all.size}", onGames) }
            item { BareIcon(Icon.GEAR, 22.dp, onSettings, color = Ink.K400) }
        }
        Scroll(state)
    }
}

/** The mark, with Nu resting on one of its nucleons as on the phone's header, and ATOMOS beside
 *  it, spaced wide like the phone's titles. */
@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(36.dp)) { ComposePen(this, 36.0).mark(18.0, 18.0, 33.0, Ink.WHITE, nu = 2) }
        Text(
            "ATOMOS", color = Tone.text, fontSize = 18.sp, fontFamily = Outfit, fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp, maxLines = 1,
        )
    }
}

/** The atom being built: its cloud, filling shell by shell, with the rose Build in the middle
 *  (which plays its next shell); under it its name and its shells as a tiny atom (a tap opens
 *  its Build page), and the next shell's game and difficulty. After a won run the cloud fills to
 *  the new share; a finished atom rings green for a moment before the next takes its place. */
@Composable
private fun BuildHero(model: AppModel, frame: () -> Long, onRun: () -> Unit, onAtom: (Int) -> Unit) {
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
        Label("Every atom built", size = 15.sp, color = Tone.green)
        return
    }
    val z = if (finished != 0) finished else model.buildingZ
    val atom = model.atlas.atom(z)
    val nebula = remember(z) { Nebula(atom, model.atlas.cloud(z)) }
    val need = periodOf(z)
    val run = if (finished == 0) model.buildRun() else null
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val pen = ComposePen(this, 96.0)
                val sh = if (finished != 0) 1.0 else share.value.toDouble()
                nebula.draw(pen, 48.0, 48.0, 44.0, frame() / 1e6, sh, still = sh <= 0)
                if (finished != 0) pen.ring(48.0, 48.0, 46.0, Ink.GREEN, 2.0)
            }
            if (run != null) Disc(46.dp, Ink.ROSE, onRun, icon = Icon.PLAY)
        }
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).clickable { onAtom(z) }.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Label(atom.name, size = 17.sp, lines = 1)
            TinyAtom(if (finished != 0) need else c.shells, need, 20.dp, if (finished != 0) Ink.GREEN else Ink.WHITE)
        }
        if (run != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GameMark(run.game.id, 16.dp, color = Ink.K300)
            Pips(run.dial)
        }
    }
}

/** The phone's atom of the day: its symbol in its color and its name. Opens its Atlas page. */
@Composable
private fun AtomOfDay(model: AppModel, onAtom: (Int) -> Unit) {
    val z = atomOfDay(model.today())
    val atom = model.atlas.atom(z)
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable { onAtom(z) }.padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Label(atom.symbol, size = 17.sp, color = Color(atom.color), lines = 1)
        Label(atom.name, size = 16.sp, color = Tone.text2, weight = FontWeight.Normal, lines = 1)
    }
}

/** The game Nu picked for today: a white disc with its icon, which plays the difficulty last
 *  played, and its name over its three difficulties as dots with their bests, as in the games
 *  list; a tap on a dot plays that one. */
@Composable
private fun TodayGame(model: AppModel, onPlay: (Game, Int) -> Unit) {
    val game = model.nuPick()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Disc(48.dp, Ink.WHITE, { onPlay(game, model.pickedLevel(game)) }, game = game.id)
        Column {
            Label(game.label, Modifier.padding(start = 2.dp), size = 17.sp, align = TextAlign.Start, lines = 1)
            LevelDots(model, game) { onPlay(game, it) }
        }
    }
}

/** A way into a screen: a bare icon, a word and a count after it, gray. */
@Composable
fun LinkRow(icon: Icon, label: String, count: String?, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(20.dp)) { ComposePen(this, 24.0).icon(icon, 12.0, 12.0, 24.0, Ink.WHITE) }
        Label(label, size = 16.sp, lines = 1)
        if (count != null) Label(count, size = 14.sp, color = Tone.text3, weight = FontWeight.Normal, lines = 1)
    }
}
