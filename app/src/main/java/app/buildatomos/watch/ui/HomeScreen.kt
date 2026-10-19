// Home: a list the crown scrolls, each part on its own soft plate with room between. The mark
// and ATOMOS. Build: the atom being built as its cloud, name and shells, the rose Build (which
// opens its Build page), the next shell's game on a card (which plays it), and the way into the
// collection. Atlas: the atom of the day with a fact about it, and the way into every atom.
// Games: the game Nu picked for today (its white disc plays it) and the way into every game.
// Then Nu's three questions on a plate of their own, and Settings.

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
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.buildatomos.core.inkFor
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
        QuietList(state, side = 12.dp, top = 20.dp, gap = 14.dp) {
            item { Wordmark() }
            item {
                Plate {
                    BuildCard(model, frame, onRun, onBuildAtom)
                    PlateDivider()
                    PlateLink(Icon.SHELLS, "Collection", "${model.collection.built.size}/$ATOMS", onCollection)
                }
            }
            item {
                Plate {
                    AtomOfDay(model, onAtom)
                    PlateDivider()
                    PlateLink(Icon.ATLAS, "All atoms", "$ATOMS", onAtlas)
                }
            }
            item {
                Plate {
                    TodayGame(model, onPlay)
                    PlateDivider()
                    PlateLink(Icon.GAMES, "All games", "${Games.all.size}", onGames)
                }
            }
            item { Plate { QuizCard(model, onAtom) } }
            item { Disc(40.dp, Ink.K900, onSettings, icon = Icon.GEAR, ink = Ink.K300) }
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

/** The atom being built: its cloud, filling shell by shell, its name and which shell is next (a
 *  tap opens its Build page); the rose Build, which opens its Build page too; and under it the
 *  next shell's game, name and difficulty on a card, which plays it. After a won run the cloud
 *  fills to the new share; a finished atom rings green for a moment before the next takes its
 *  place. */
@Composable
private fun BuildCard(model: AppModel, frame: () -> Long, onRun: () -> Unit, onAtom: (Int) -> Unit) {
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onAtom(z) }.padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(56.dp)) {
            val pen = ComposePen(this, 60.0)
            val sh = if (finished != 0) 1.0 else share.value.toDouble()
            nebula.draw(pen, 30.0, 30.0, 27.0, frame() / 1e6, sh, still = sh <= 0)
            if (finished != 0) pen.ring(30.0, 30.0, 29.0, Ink.GREEN, 2.0)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(atom.name, size = 17.sp, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 2)
            if (finished != 0) Label("Built", size = 13.sp, color = Tone.green, align = TextAlign.Start, lines = 1)
            else Label("Shell ${c.shells + 1} of $need", size = 13.sp, color = Tone.text2, weight = FontWeight.Normal, align = TextAlign.Start, lines = 1)
        }
    }
    if (run != null) {
        Pill("Build", Ink.ROSE, { onAtom(z) }, Modifier.fillMaxWidth())
        GameCard(run.game, run.dial, onRun)
    }
}

/** The phone's atom of the day: its plate (number over symbol, in its color), its name and what
 *  it is, and one of its facts, a different one each day. Opens its Atlas page. */
@Composable
private fun AtomOfDay(model: AppModel, onAtom: (Int) -> Unit) {
    val day = model.today()
    val z = atomOfDay(day)
    val atom = model.atlas.atom(z)
    val info = model.atlas.info(z)
    val facts = (listOf(info.headline) + info.facts).filter { it.isNotBlank() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onAtom(z) }.padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ink = Color(inkFor(atom.color))
            Column(
                Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(Color(atom.color)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Label("$z", size = 13.sp, color = ink, weight = FontWeight.Normal, lines = 1)
                Label(atom.symbol, size = 18.sp, color = ink, weight = FontWeight.SemiBold, lines = 1)
            }
            Column {
                Label(atom.name, size = 16.sp, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 1)
                Label("Atom of the day", size = 13.sp, color = Tone.text2, weight = FontWeight.Normal, align = TextAlign.Start, lines = 1)
            }
        }
        if (facts.isNotEmpty()) {
            val fact = facts[Math.floorMod(day * 7 + z, facts.size.toLong()).toInt()]
            Label(fact, Modifier.fillMaxWidth().padding(horizontal = 2.dp), size = 14.sp, weight = FontWeight.Normal, align = TextAlign.Start)
        }
    }
}

/** The game Nu picked for today: a white disc with its icon, which plays the difficulty last
 *  played, and its name over its three difficulties as dots with their bests, as in the games
 *  list; a tap on a dot plays that one. */
@Composable
private fun TodayGame(model: AppModel, onPlay: (Game, Int) -> Unit) {
    val game = model.nuPick()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Disc(48.dp, Ink.WHITE, { onPlay(game, model.pickedLevel(game)) }, game = game.id)
        Column {
            Label(game.label, Modifier.padding(start = 2.dp), size = 17.sp, align = TextAlign.Start, lines = 1)
            LevelDots(model, game) { onPlay(game, it) }
        }
    }
}
