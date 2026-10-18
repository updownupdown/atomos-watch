// The list screens: every game for free play, one game's page (pick a difficulty, then Play),
// and Settings. Each is a ScalingLazyColumn, scrolled by touch or the crown.

package app.buildatomos.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
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
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.icon
import app.buildatomos.core.gameIcon
import app.buildatomos.core.levels
import app.buildatomos.core.pips
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

/** A game's card icon: black on a white circle. */
@Composable
fun GameIcon(id: String, size: Int) {
    Canvas(Modifier.size(size.dp)) {
        val pen = ComposePen(this, size.toDouble())
        pen.dot(size / 2.0, size / 2.0, size / 2.0, Ink.WHITE)
        pen.gameIcon(id, size / 2.0, size / 2.0, size * 0.6, Ink.BLACK)
    }
}

/** Every game, A to Z, for free play: its icon, its name, Nu's pick marked with a rose dot. */
@Composable
fun GamesScreen(model: AppModel, onGame: (String) -> Unit) {
    model.version
    val pick = model.nuPick().id
    WatchList {
        items(Games.all) { g ->
            PlateRow(onClick = { onGame(g.id) }) {
                GameIcon(g.id, 34)
                Label(g.label, Modifier.weight(1f), size = 15.sp, align = TextAlign.Start, lines = 1)
                if (g.id == pick) Canvas(Modifier.size(10.dp)) { ComposePen(this, 10.0).dot(5.0, 5.0, 4.0, Ink.ROSE) }
            }
        }
    }
}

/** Rotary pixels per difficulty step on a game's page. */
private const val PICK_PX = 60f

/** One game's page for free play: its icon and name on one row, the difficulty (tap a dot or
 *  turn the crown; the picked dot holds a check), the best level reached at it, and Play near the
 *  foot. */
@Composable
fun FreePlayScreen(model: AppModel, game: Game, onPlay: (Int) -> Unit) {
    model.version
    var level by remember(game.id) { mutableStateOf(model.pickedLevel(game)) }
    var spun by remember { mutableFloatStateOf(0f) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    fun set(l: Int) {
        val to = l.coerceIn(0, Difficulty.LEVELS - 1)
        if (to != level) { level = to; model.feel.buzz(15); model.pick(game, to) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { e ->
                spun += e.verticalScrollPixels
                val steps = (spun / PICK_PX).toInt()
                if (steps != 0) { spun -= steps * PICK_PX; set(level + steps) }
                true
            }
            .focusRequester(fr)
            .focusable(),
    ) {
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameIcon(game.id, 30)
            Label(game.label, size = 18.sp, weight = FontWeight.SemiBold, lines = 1)
        }
        // Across the middle, where the round screen is widest: the level's name and five big
        // dots to pick from, each in its own color; the picked one is filled and holds a check.
        // Each dot's whole cell takes the tap.
        Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Label(Difficulty.WORDS[level], size = 15.sp, color = Color(Difficulty.COLORS[level]), weight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                for (i in 0 until Difficulty.LEVELS) {
                    Canvas(Modifier.weight(1f).aspectRatio(1f).clickable { set(i) }) {
                        val pen = ComposePen(this, 36.0)
                        val c = Difficulty.COLORS[i]
                        if (i == level) {
                            pen.dot(18.0, 18.0, 16.5, c)
                            pen.icon(Icon.CHECK, 18.0, 18.0, 20.0, Ink.BLACK)
                        } else {
                            pen.dot(18.0, 18.0, 13.5, Ink.K800)
                            pen.ring(18.0, 18.0, 12.5, c, 2.8)
                        }
                    }
                }
            }
            val best = model.bestLevel(game, level)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (best > 0) Canvas(Modifier.size(20.dp)) { ComposePen(this, 20.0).levels(10.0, 10.0, 20.0, best, null) }
                Label(if (best > 0) "Best: $best" else "No best yet", size = 13.sp, color = Tone.text2)
            }
        }
        IconMark(
            Icon.PLAY, 50, Ink.BLACK, disc = Ink.WHITE,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).clickable { onPlay(level) },
        )
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
