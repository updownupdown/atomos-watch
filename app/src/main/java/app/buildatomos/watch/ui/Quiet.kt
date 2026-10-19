// The Quiet look: less on every screen. No titles and no section labels; no plates but the one
// thing to tap, a disc; icons where words aren't needed; numbers as the app's tiny atoms; one
// accent a screen. Lists are Wear's ScalingLazyColumn under its scroll arc; the screens that
// don't scroll are laid out on a face 240 across, scaled to the watch.

package app.buildatomos.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.Pen
import app.buildatomos.core.gameIcon
import app.buildatomos.core.icon
import app.buildatomos.core.levels
import app.buildatomos.core.pips

/** The face the fixed screens are drawn on, in dp, before scaling to the watch. */
const val FACE = 240f

/** A screen that doesn't scroll: `content` gets the size of one face unit, the screen's side over
 *  240, to lay itself out by. */
@Composable
fun Face(content: @Composable BoxScope.(u: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val u = min(maxWidth, maxHeight) / FACE
        content(u)
    }
}

/** A text size on the face, `n` sp on a watch 240 across, scaled with the screen like the rest
 *  but never under 13. */
fun Dp.text(n: Int): TextUnit = maxOf(value * n, minOf(n, 13).toFloat()).sp

/** The list every scrolling screen uses, its items shrinking and fading toward the edges. */
@Composable
fun QuietList(
    state: ScalingLazyListState,
    side: Dp = 16.dp,
    top: Dp = 24.dp,
    bottom: Dp = 24.dp,
    gap: Dp = 8.dp,
    autoCentering: AutoCenteringParams? = AutoCenteringParams(),
    content: ScalingLazyListScope.() -> Unit,
) {
    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = side, end = side, top = top, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(gap),
        autoCentering = autoCentering,
        content = content,
    )
}

/** The scroll arc on the right edge: Wear's own position indicator, following the list. */
@Composable
fun Scroll(state: ScalingLazyListState) {
    PositionIndicator(state)
}

/** The one thing to tap on a screen: a disc of `fill`, `size` across, with `icon` on it in `ink`
 *  (or a game's icon, `game`). */
@Composable
fun Disc(
    size: Dp,
    fill: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Icon? = null,
    game: String? = null,
    ink: Int = Ink.BLACK,
) {
    Canvas(modifier.size(size).clip(CircleShape).clickable(onClick = onClick)) {
        val pen = ComposePen(this, 100.0)
        pen.dot(50.0, 50.0, 50.0, fill)
        if (icon != null) pen.icon(icon, 50.0, 50.0, 48.0, ink)
        if (game != null) pen.gameIcon(game, 50.0, 50.0, 54.0, ink)
    }
}

/** An icon with nothing round it, `size` across, in a round tap target of `touch`. */
@Composable
fun BareIcon(icon: Icon, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, color: Int = Ink.K300, touch: Dp = 44.dp) {
    Box(modifier.size(touch).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) { ComposePen(this, 24.0).icon(icon, 12.0, 12.0, 24.0, color) }
    }
}

/** A game's icon on its own, in white strokes, `size` across. */
@Composable
fun GameMark(id: String, size: Dp, modifier: Modifier = Modifier, color: Int = Ink.WHITE) {
    Canvas(modifier.size(size)) { ComposePen(this, 24.0).gameIcon(id, 12.0, 12.0, 24.0, color) }
}

/** Levels, or a best, as the app's tiny atom: `level` of `goal`, or counted in binary with no
 *  goal. */
@Composable
fun TinyAtom(level: Int, goal: Int?, size: Dp = 22.dp, color: Int = Ink.CYAN, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { ComposePen(this, 44.0).levels(22.0, 22.0, 44.0, level, goal, 0.0, color) }
}

/** A difficulty as its pips, lit to its level in its color. */
@Composable
fun Pips(dial: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 28.dp, height = 8.dp)) { ComposePen(this, 28.0, wide = true).pips(14.0, 4.0, 26.0, dial) }
}

/** The Rows view's mark: eight dots in three rows, the last short. */
fun Pen.rowsMark(cx: Double, cy: Double, size: Double, color: Int) {
    val k = size / 24
    for (i in 0 until 8) dot(cx + ((i % 3) * 7 - 7) * k, cy + ((i / 3) * 6 - 6) * k, 2.0 * k, color)
}
