// The games list and Settings, quiet. The games list: every game as its bare icon and its name,
// with its three difficulties as dots under the name, and Nu's pick marked with a rose dot.
// Settings: icons for words, the reset as red text, the version.

package app.buildatomos.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
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
import app.buildatomos.core.icon
import app.buildatomos.watch.AppModel
import kotlin.math.PI

/** How wide each difficulty's dot is to tap, and the dot itself. */
private const val DOT_CELL = 30
private const val DOT = 24

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
                    else pen.ring(h, h, h - 1.6, c, 2.2)
                }
                if (best > 0) Label("$best", size = 13.sp, color = Color.Black, weight = FontWeight.SemiBold, lines = 1)
            }
        }
    }
}

/** Every game, A to Z, for free play: see the top of this file. A tap on a dot plays that
 *  difficulty; a tap anywhere else on the row, the one last played. */
@Composable
fun GamesScreen(model: AppModel, onPlay: (Game, Int) -> Unit) {
    model.version
    val pick = model.nuPick()
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        QuietList(state, side = 18.dp, gap = 2.dp) {
            items(Games.all) { g -> GameRow(model, g, nu = g.id == pick.id, onPlay = onPlay) }
        }
        Scroll(state)
    }
}

/** One game in the games list: see GamesScreen. */
@Composable
private fun GameRow(model: AppModel, game: Game, nu: Boolean, onPlay: (Game, Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onPlay(game, model.pickedLevel(game)) }
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GameMark(game.id, 28.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // In line with the first dot's left edge.
                Label(game.label, Modifier.padding(start = ((DOT_CELL - DOT) / 2).dp), size = 17.sp, align = TextAlign.Start, lines = 1)
                if (nu) Canvas(Modifier.size(8.dp)) { ComposePen(this, 8.0).dot(4.0, 4.0, 4.0, Ink.ROSE) }
            }
            LevelDots(model, game) { onPlay(game, it) }
        }
    }
}

/** Settings on one screen: the gear at the top; sound (off to start) and haptics (on to start,
 *  and soft) as their marks with a switch each; starting the collection over in red; the
 *  version. */
@Composable
fun SettingsScreen(model: AppModel) {
    model.version
    var asking by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Face { u ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(u * 26)) { ComposePen(this, 24.0).icon(Icon.GEAR, 12.0, 12.0, 24.0, Ink.K300) }
                SwitchRow(Modifier.padding(top = u * 12), model.sound, { on -> soundMark(on) }) { model.sound = it; if (it) model.feel.chime(2) }
                SwitchRow(Modifier.padding(top = u * 6), model.haptics, { hapticsMark() }) { model.haptics = it; if (it) model.feel.hit(0.5) }
                Label(
                    "Start over",
                    Modifier.padding(top = u * 8).clip(RoundedCornerShape(16.dp)).clickable { asking = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                    size = u.text(16), color = Color(Ink.RED), lines = 1,
                )
                val v = model.versionName
                Label(v.ifBlank { "ATOMOS" }, Modifier.padding(top = u * 4), size = u.text(14), color = Tone.text3, weight = FontWeight.Normal, lines = 1)
            }
        }
        if (asking) Confirm(
            question = "Start the collection over? Every atom you built goes.",
            onNo = { asking = false },
            onYes = { asking = false; model.resetCollection() },
        )
    }
}

/** A setting that is on or off: its mark, then a switch, cyan when on. The whole row toggles. */
@Composable
private fun SwitchRow(modifier: Modifier, on: Boolean, draw: ComposePen.(Boolean) -> Unit, set: (Boolean) -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).clickable { set(!on) }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Canvas(Modifier.size(24.dp)) { ComposePen(this, 24.0).draw(on) }
        Canvas(Modifier.width(44.dp).height(26.dp)) {
            val pen = ComposePen(this, 44.0, wide = true)
            pen.rect(1.0, 1.0, 42.0, 24.0, 12.0, if (on) Ink.CYAN else Ink.K700)
            pen.dot(if (on) 30.0 else 14.0, 13.0, 9.0, if (on) Ink.BLACK else Ink.K400)
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

/** A destructive action asks first: the question, then a bare cross to cancel and a red disc
 *  with a check to go ahead. Back cancels. */
@Composable
fun Confirm(question: String, onNo: () -> Unit, onYes: () -> Unit) {
    BackHandler { onNo() }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
        Face { u ->
            Column(Modifier.padding(horizontal = u * 30), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(question, size = u.text(15))
                Row(
                    Modifier.padding(top = u * 14),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(u * 20),
                ) {
                    BareIcon(Icon.EXIT, 22.dp, onNo, color = Ink.K200)
                    Disc(u * 50, Ink.RED, onYes, icon = Icon.CHECK)
                }
            }
        }
    }
}
