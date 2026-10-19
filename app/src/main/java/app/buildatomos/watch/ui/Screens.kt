// The list screens: every game for free play (each with its three difficulties to tap, and Nu's
// pick at the edge) and Settings. Each is a ScalingLazyColumn under a curved title, scrolled by
// touch or the crown, with the scroll arc on the right.

package app.buildatomos.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import app.buildatomos.core.Difficulty
import app.buildatomos.core.Game
import app.buildatomos.core.Games
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.gameIcon
import app.buildatomos.watch.AppModel
import kotlin.math.PI

/** A game's card icon: black on a white circle; Nu's pick has a rose dot at its top right. */
@Composable
fun GameIcon(id: String, size: Int, nu: Boolean = false) {
    Canvas(Modifier.size(size.dp)) {
        val pen = ComposePen(this, size.toDouble())
        pen.dot(size / 2.0, size / 2.0, size / 2.0, Ink.WHITE)
        pen.gameIcon(id, size / 2.0, size / 2.0, size * 0.6, Ink.BLACK)
        if (nu) {
            // Cut out of the white by the plate's own color, so it reads as a dot on the icon.
            pen.dot(size * 0.83, size * 0.17, size * 0.2, Ink.K900)
            pen.dot(size * 0.83, size * 0.17, size * 0.14, Ink.ROSE)
        }
    }
}

/** How wide each difficulty's dot is to tap, and the dot itself. */
private const val DOT_CELL = 32
private const val DOT = 28

/** A game's difficulties as a row of dots, Easy to Hard, each in its level's color: filled, with
 *  the best level reached there in black, or an empty ring until there is one. A tap on a dot
 *  starts free play at its level. */
@Composable
fun LevelDots(model: AppModel, game: Game, onPlay: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until Difficulty.LEVELS) {
            val best = model.bestLevel(game, i)
            val c = Difficulty.COLORS[i]
            Box(Modifier.size(DOT_CELL.dp).clickable { onPlay(i) }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(DOT.dp)) {
                    val pen = ComposePen(this, DOT.toDouble())
                    val h = DOT / 2.0
                    if (best > 0) pen.dot(h, h, h, c)
                    else {
                        pen.dot(h, h, h - 1, Ink.BLACK)
                        pen.ring(h, h, h - 2.3, c, 2.6)
                    }
                }
                if (best > 0) Label("$best", size = if (best >= 100) 13.sp else 14.sp, color = Color.Black, weight = FontWeight.SemiBold, lines = 1)
            }
        }
    }
}

/** Every game, A to Z, for free play, under Games curved at the top: each a soft pill row with
 *  its icon (Nu's pick marked with a rose dot) and its name, and under the name its three
 *  difficulties as dots, each with its best. A tap on a dot plays that difficulty; a tap anywhere
 *  else on the row, the one last played. Nu's pick is the rose edge button, at its last level. */
@Composable
fun GamesScreen(model: AppModel, onPlay: (Game, Int) -> Unit) {
    model.version
    val pick = model.nuPick()
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val nuButton = @Composable {
            EdgeButton({ onPlay(pick, model.pickedLevel(pick)) }, fill = Tone.rose, pinned = false) { EdgeLabel("Nu's pick", Tone.ink) }
        }
        EdgeScaffold(state, nuButton) { pad ->
            EdgeList(state, side = 12.dp, gap = 5.dp, foot = pad.calculateBottomPadding()) {
                items(Games.all) { g -> GameRow(model, g, nu = g.id == pick.id, onPlay = onPlay) }
            }
        }
        CurvedTitle("Games")
    }
}

/** One game in the games list: see GamesScreen. */
@Composable
private fun GameRow(model: AppModel, game: Game, nu: Boolean, onPlay: (Game, Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tone.plate, RoundedCornerShape(30.dp))
            .clickable { onPlay(game, model.pickedLevel(game)) }
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameIcon(game.id, 34, nu)
        Column(Modifier.weight(1f)) {
            // In line with the first dot's left edge.
            Label(game.label, Modifier.padding(start = ((DOT_CELL - DOT) / 2).dp), size = 15.sp, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 1)
            LevelDots(model, game) { onPlay(game, it) }
        }
    }
}

/** Sound (off to start), haptics (on to start, and soft), and starting the collection over, under
 *  the curved title, with the version curved along the foot. No main action, so no edge button. */
@Composable
fun SettingsScreen(model: AppModel) {
    model.version
    var asking by remember { mutableStateOf(false) }
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        EdgeList(state, edge = false, side = 14.dp, top = 34.dp, gap = 8.dp) {
            item { SwitchRow("Sound", model.sound, { on -> soundMark(on) }) { model.sound = it; if (it) model.feel.chime(2) } }
            item { SwitchRow("Haptics", model.haptics, { hapticsMark() }) { model.haptics = it; if (it) model.feel.hit(0.5) } }
            item {
                PlateRow(onClick = { asking = true }) {
                    Label("Start the collection over", Modifier.weight(1f), size = 14.sp, color = Color(Ink.RED))
                }
            }
        }
        CurvedTitle("Settings")
        val v = model.versionName
        CurvedFoot(if (v.isBlank()) "ATOMOS" else "Version $v")
        EdgeScroll(state)
        if (asking) Confirm(
            question = "Start the collection over? Every atom you built goes.",
            yes = "Start over",
            onNo = { asking = false },
            onYes = { asking = false; model.resetCollection() },
        )
    }
}

/** A setting that is on or off: its mark, its name, and a switch, cyan when on. */
@Composable
private fun SwitchRow(label: String, on: Boolean, draw: ComposePen.(Boolean) -> Unit, set: (Boolean) -> Unit) {
    PlateRow(onClick = { set(!on) }) {
        Canvas(Modifier.size(20.dp)) { ComposePen(this, 24.0).draw(on) }
        Label(label, Modifier.weight(1f), size = 15.sp, align = TextAlign.Start)
        Canvas(Modifier.width(38.dp).height(24.dp)) {
            val pen = ComposePen(this, 38.0, wide = true)
            pen.rect(1.0, 1.0, 36.0, 22.0, 11.0, if (on) Ink.CYAN else Ink.K700)
            pen.dot(if (on) 26.0 else 12.0, 12.0, 8.0, if (on) Ink.BLACK else Ink.K400)
        }
    }
}

/** Sound's mark, on a 24 grid: a speaker, sounding when on, crossed out when off. */
private fun ComposePen.soundMark(on: Boolean) {
    val c = Ink.K200
    poly(doubleArrayOf(4.0, 8.0, 13.0, 13.0, 8.0, 4.0, 4.0), doubleArrayOf(9.5, 9.5, 5.0, 19.0, 14.5, 14.5, 9.5), 7, c, 2.0)
    if (on) {
        arc(13.0, 12.0, 4.0, -PI / 4, PI / 2, c, 2.0)
        arc(13.0, 12.0, 7.5, -PI / 4, PI / 2, c, 2.0)
    } else {
        line(17.0, 9.0, 21.0, 15.0, c, 2.0)
        line(21.0, 9.0, 17.0, 15.0, c, 2.0)
    }
}

/** Haptics' mark, on a 24 grid: a watch buzzing either side. */
private fun ComposePen.hapticsMark() {
    val c = Ink.K200
    rectRing(8.0, 4.0, 8.0, 16.0, 2.5, c, 2.0)
    line(4.0, 9.0, 4.0, 15.0, c, 2.0)
    line(20.0, 9.0, 20.0, 15.0, c, 2.0)
}

/** A destructive action asks first: the question, Cancel as a pill under it, and the action
 *  itself (`yes`) in red at the edge. Back cancels. */
@Composable
fun Confirm(question: String, yes: String, onNo: () -> Unit, onYes: () -> Unit) {
    BackHandler { onNo() }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
        Column(
            Modifier.align(Alignment.Center).padding(start = 26.dp, end = 26.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label(question, size = 15.sp)
            Spacer(Modifier.height(12.dp))
            Pill("Cancel", Icon.EXIT, onNo, fill = Tone.plate)
        }
        EdgeButton(onYes, fill = Color(Ink.RED)) { EdgeLabel(yes, Tone.ink, Icon.CHECK) }
    }
}
