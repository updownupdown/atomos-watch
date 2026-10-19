// The list screens: every game for free play (each with its three difficulties to tap) and
// Settings. Each is a ScalingLazyColumn, scrolled by touch or the crown.

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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import app.buildatomos.core.Difficulty
import app.buildatomos.core.Game
import app.buildatomos.core.Games
import app.buildatomos.core.Ink
import app.buildatomos.core.gameIcon
import app.buildatomos.watch.AppModel

@Composable
fun WatchList(start: Int = 0, content: ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = start)
    ScalingLazyColumn(
        Modifier.fillMaxSize().background(Color.Black),
        state = state,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

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

/** Every game, A to Z, for free play: its icon (Nu's pick marked with a rose dot) and its name,
 *  and under the name its three difficulties as dots, each with its best. A tap on a dot plays
 *  that difficulty; a tap anywhere else on the row, the one last played. */
@Composable
fun GamesScreen(model: AppModel, onPlay: (Game, Int) -> Unit) {
    model.version
    val pick = model.nuPick().id
    WatchList {
        items(Games.all) { g -> GameRow(model, g, nu = g.id == pick, onPlay = onPlay) }
    }
}

/** One game in the games list: see GamesScreen. */
@Composable
private fun GameRow(model: AppModel, game: Game, nu: Boolean, onPlay: (Game, Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tone.plate, RoundedCornerShape(26.dp))
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
            val pen = ComposePen(this, 36.0, wide = true)
            pen.rect(1.0, 1.0, 34.0, 20.0, 10.0, if (on) Ink.CYAN else Ink.K700)
            pen.dot(if (on) 25.0 else 11.0, 11.0, 7.0, if (on) Ink.BLACK else Ink.WHITE)
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
