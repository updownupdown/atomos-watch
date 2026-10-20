// The look: calm, on black, with each part of a screen on its own soft plate and room between;
// buttons named under their discs where a word helps; numbers as the app's tiny atoms; one
// accent a screen. Lists are Wear's ScalingLazyColumn under its scroll arc; the screens that
// don't scroll are laid out on a face 240 across, scaled to the watch.

package app.buildatomos.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import app.buildatomos.core.Game
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

/** The list every scrolling screen uses, its items shrinking and fading toward the edges (only
 *  fading, if not `shrink`: for a list whose items line up with each other, as an atom's Build
 *  page's progress track does). */
@Composable
fun QuietList(
    state: ScalingLazyListState,
    side: Dp = 16.dp,
    top: Dp = 24.dp,
    bottom: Dp = 24.dp,
    gap: Dp = 8.dp,
    autoCentering: AutoCenteringParams? = AutoCenteringParams(),
    shrink: Boolean = true,
    content: ScalingLazyListScope.() -> Unit,
) {
    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        scalingParams = if (shrink) ScalingLazyColumnDefaults.scalingParams() else ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f),
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

/** A soft plate that groups one part of a screen (a section of Home, a game in the list, a part
 *  of an atom's page): the dark K900 rounded round its content. */
@Composable
fun Plate(
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(Ink.K900)).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** A hairline between the parts of a plate. */
@Composable
fun PlateDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(Ink.K800)))
}

/** A plate's title, small and gray over what it holds. */
@Composable
fun PlateTitle(text: String) {
    Label(text, Modifier.fillMaxWidth().padding(start = 4.dp), size = 13.sp, color = Tone.text2, align = TextAlign.Start, lines = 1)
}

/** A way into a screen on a plate: its icon, a word and a count after it, and a chevron at the
 *  end; the whole row takes the tap. */
@Composable
fun PlateLink(icon: Icon, label: String, count: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(18.dp)) { ComposePen(this, 24.0).icon(icon, 12.0, 12.0, 24.0, Ink.K200) }
        Label(label, size = 15.sp, lines = 1)
        if (count != null) Label(count, size = 13.sp, color = Tone.text3, weight = FontWeight.Normal, lines = 1)
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(14.dp)) { ComposePen(this, 24.0).icon(Icon.CHEVRON, 12.0, 12.0, 24.0, Ink.K400) }
    }
}

/** A round button with its word under it, so a row of them reads at a glance (the run's menu and
 *  cards, the confirm): a disc of `fill` with `icon` in `ink`, and `label` in gray. */
@Composable
fun LabeledDisc(size: Dp, fill: Int, icon: Icon, label: String, onClick: () -> Unit, ink: Int = Ink.BLACK, text: TextUnit = 13.sp) {
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(Modifier.size(size)) {
            val pen = ComposePen(this, 100.0)
            pen.dot(50.0, 50.0, 50.0, fill)
            pen.icon(icon, 50.0, 50.0, 48.0, ink)
        }
        Label(label, size = text, color = Tone.text2, lines = 1)
    }
}

/** A rounded button with its word, for the main action where a disc alone would be unclear: rose
 *  for Build, with `icon` before the word if it has one. */
@Composable
fun Pill(label: String, fill: Int, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Icon? = null, ink: Int = Ink.BLACK) {
    Row(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(Color(fill)).clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Canvas(Modifier.size(18.dp)) { ComposePen(this, 24.0).icon(icon, 12.0, 12.0, 24.0, ink) }
        Label(label, size = 16.sp, color = Color(ink), weight = FontWeight.SemiBold, lines = 1)
    }
}

/** A game's card icon, as on the phone: its icon black on a white disc, `size` across; Nu's pick
 *  has a rose dot at its top right. */
@Composable
fun GameIcon(id: String, size: Dp, nu: Boolean = false) {
    Canvas(Modifier.size(size)) {
        val pen = ComposePen(this, 100.0)
        pen.dot(50.0, 50.0, 50.0, Ink.WHITE)
        pen.gameIcon(id, 50.0, 50.0, 60.0, Ink.BLACK)
        if (nu) {
            // Cut out of the white by the plate's own color, so it reads as a dot on the icon.
            pen.dot(84.0, 16.0, 20.0, Ink.K900)
            pen.dot(84.0, 16.0, 13.0, Ink.ROSE)
        }
    }
}

/** A game on a card inside a plate: its icon, its name and its difficulty's pips. The whole card
 *  plays it. */
@Composable
fun GameCard(game: Game, dial: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(Ink.K800)).clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GameIcon(game.id, 36.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(game.label, size = 15.sp, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 1)
            Pips(dial)
        }
    }
}
