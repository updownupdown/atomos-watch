// The Edge look, after Wear OS's Material 3 Expressive: a screen's main action is an edge button,
// a wide pill at the foot whose bottom follows the round screen; the screen's title curves along
// the top edge; lists shrink and fade toward the edges (ScalingLazyColumn does that) under Wear's
// position arc on the right. Built here from Compose's own shapes and Wear's curved text, so the
// app keeps to Wear Compose 1.4.

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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.curvedText
import app.buildatomos.core.Difficulty
import app.buildatomos.core.Icon
import app.buildatomos.core.Pen
import app.buildatomos.core.icon
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.acos
import kotlin.math.sqrt

/** The edge shapes are worked out on a face 240 across, the mockup's, then scaled to the screen. */
private const val FACE = 240f
private const val MID = 120f
/** The button's foot: the screen's own circle, right at the rim. */
private const val FOOT = 120f
/** The flat top runs this far either side of the middle before the corners round it off. */
private const val FLAT = 38f
/** How far a corner drops from the top before it meets the foot. */
private const val DROP = 24.5f
/** The top of a one-line button, and of a two-line one: as tall as when the foot sat 10 in from
 *  the rim, the whole button lowered onto it. */
private const val TOP = 192f
private const val TOP_TALL = 186f
/** Half the gap between the two halves of a split button. */
private const val SPLIT = 3f

/** Where the foot is under x. */
private fun footY(x: Float) = MID + sqrt(FOOT * FOOT - (x - MID) * (x - MID))

/** The foot's angle under x, in degrees clockwise from three o'clock, as Path.arcTo takes it. */
private fun footAngle(x: Float) = Math.toDegrees(acos(((x - MID) / FOOT).toDouble())).toFloat()

/** How far either side of the middle the button reaches, where its corners meet the foot. */
private fun reach(top: Float): Float {
    val low = top + DROP - MID
    return sqrt(FOOT * FOOT - low * low)
}

/** The part of an edge button from `from` to `to` across the face (the whole of it, or one side
 *  of a split one): a flat top with soft corners, and a foot on the screen's circle. A side that
 *  stops short of the corner is cut straight down to the foot. */
private class EdgeShape(private val top: Float, private val from: Float, private val to: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val k = size.width / (to - from)
        fun x(u: Float) = (u - from) * k
        fun y(u: Float) = (u - top) * k
        val low = top + DROP
        val r = reach(top)
        val left = MID - r
        val right = MID + r
        val cutLeft = from > left + 0.5f
        val cutRight = to < right - 0.5f
        val ring = Rect(x(MID - FOOT), y(MID - FOOT), x(MID + FOOT), y(MID + FOOT))
        val p = Path()
        p.moveTo(x(if (cutLeft) from else MID - FLAT), y(top))
        p.lineTo(x(if (cutRight) to else MID + FLAT), y(top))
        val start: Float
        if (cutRight) {
            p.lineTo(x(to), y(footY(to)))
            start = footAngle(to)
        } else {
            p.cubicTo(x(MID + FLAT + 16), y(top), x(right - 6), y(top + 8), x(right), y(low))
            start = footAngle(right)
        }
        val end = footAngle(if (cutLeft) from else left)
        p.arcTo(ring, start, end - start, false)
        if (!cutLeft) p.cubicTo(x(left + 6), y(top + 8), x(MID - FLAT - 16), y(top), x(MID - FLAT), y(top))
        p.close()
        return Outline.Generic(p)
    }
}

/** One edge shape placed on the screen: `unit` is a face unit, (ox, oy) the round screen's top
 *  left. */
@Composable
private fun EdgePart(
    unit: Dp, ox: Dp, oy: Dp, top: Float, from: Float, to: Float,
    fill: Color, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit,
) {
    val shape = remember(top, from, to) { EdgeShape(top, from, to) }
    Box(
        Modifier
            .offset(x = ox + unit * from, y = oy + unit * top)
            .size(width = unit * (to - from), height = unit * (MID + FOOT - top))
            .clip(shape)
            .background(fill)
            .clickable(onClick = onClick),
        content = content,
    )
}

/** A screen's main action, hugging the bottom curve of the screen. It fills its parent (the whole
 *  screen) to find the circle, so it goes last in the screen's Box; only the button takes taps.
 *  `tall` makes room for a second line. Rose for Build, white for Play and All games, the plate
 *  for the rest. */
@Composable
fun EdgeButton(
    onClick: () -> Unit,
    fill: Color = Tone.plate,
    tall: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = min(maxWidth, maxHeight)
        val unit = side / FACE
        val top = if (tall) TOP_TALL else TOP
        val r = reach(top)
        EdgePart(unit, (maxWidth - side) / 2, (maxHeight - side) / 2, top, MID - r, MID + r, fill, onClick) {
            Column(
                Modifier.fillMaxWidth().padding(top = unit * (if (tall) 7f else 11f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
                content = content,
            )
        }
    }
}

/** The edge button split down the middle into two, each its own tap (the Atlas page's atoms
 *  either side). A side that is null is left out, the other keeping its place. */
@Composable
fun EdgeSplit(
    onLeft: (() -> Unit)?,
    onRight: (() -> Unit)?,
    left: @Composable RowScope.() -> Unit,
    right: @Composable RowScope.() -> Unit,
    fill: Color = Tone.plate,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = min(maxWidth, maxHeight)
        val unit = side / FACE
        val ox = (maxWidth - side) / 2
        val oy = (maxHeight - side) / 2
        val r = reach(TOP)
        fun row(m: Modifier) = m.fillMaxWidth().padding(top = unit * 10f)
        if (onLeft != null) EdgePart(unit, ox, oy, TOP, MID - r, MID - SPLIT, fill, onLeft) {
            Row(
                row(Modifier.padding(start = unit * 8f)),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = left,
            )
        }
        if (onRight != null) EdgePart(unit, ox, oy, TOP, MID + SPLIT, MID + r, fill, onRight) {
            Row(
                row(Modifier.padding(end = unit * 8f)),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = right,
            )
        }
    }
}

/** An edge button's line: an icon if it has one, the label, and a count after it if it has
 *  one. */
@Composable
fun EdgeLabel(text: String, ink: Color, icon: Icon? = null, count: String? = null, countInk: Color = ink) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Canvas(Modifier.size(18.dp)) { ComposePen(this, 18.0).icon(icon, 9.0, 9.0, 18.0, ink.toArgb()) }
        Label(text, size = 15.sp, color = ink, weight = FontWeight.SemiBold, lines = 1)
        if (count != null) Label(count, size = 13.sp, color = countInk, lines = 1)
    }
}

/** A difficulty's pips in one ink, for the face of a colored button: the level's lit, the rest
 *  faint. */
@Composable
fun InkPips(dial: Int, ink: Color) {
    val n = Difficulty.LEVELS
    Canvas(Modifier.size(width = (n * 9).dp, height = 7.dp)) {
        val pen = ComposePen(this, n * 9.0, wide = true)
        val lit = Difficulty.level(dial) + 1
        for (i in 0 until n) pen.dot(4.5 + i * 9, 3.5, 3.0, ink.toArgb(), if (i < lit) 1.0 else 0.3)
    }
}

/** A screen's title, curved along the top edge, `inset` in from the rim. */
@Composable
fun CurvedTitle(text: String, inset: Dp = 6.dp, color: Color = Tone.text2) {
    CurvedLayout(Modifier.fillMaxSize().padding(inset)) {
        curvedText(text, color = color, fontSize = 14.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium)
    }
}

/** A quiet line curved along the bottom edge, reading left to right. */
@Composable
fun CurvedFoot(text: String, inset: Dp = 6.dp, color: Color = Tone.text3) {
    CurvedLayout(Modifier.fillMaxSize().padding(inset), anchor = 90f, angularDirection = CurvedDirection.Angular.Reversed) {
        curvedText(text, color = color, fontSize = 13.sp, fontFamily = Outfit, fontWeight = FontWeight.Normal)
    }
}

/** The time, as the watch writes it (12 or 24 hours), changing on the minute. */
@Composable
fun rememberClock(): String {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    return remember(now, pattern) { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now)) }
}

/** The scroll arc on the right edge: Wear's own position indicator, following the list. */
@Composable
fun EdgeScroll(state: ScalingLazyListState) {
    PositionIndicator(state)
}

/** A list under a curved title, its foot left clear of the edge button (`edge`). */
@Composable
fun EdgeList(
    state: ScalingLazyListState,
    edge: Boolean = true,
    side: Dp = 12.dp,
    top: Dp = 30.dp,
    gap: Dp = 6.dp,
    autoCentering: AutoCenteringParams? = AutoCenteringParams(),
    content: ScalingLazyListScope.() -> Unit,
) {
    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = side, end = side, top = top, bottom = if (edge) 56.dp else 30.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        autoCentering = autoCentering,
        content = content,
    )
}

/** The Rows view's mark: eight dots in three rows, the last short. */
fun Pen.rowsMark(cx: Double, cy: Double, size: Double, color: Int) {
    val k = size / 24
    for (i in 0 until 8) dot(cx + ((i % 3) * 7 - 7) * k, cy + ((i / 3) * 6 - 6) * k, 2.0 * k, color)
}

/** A list's mark: three soft bars. */
fun Pen.listMark(cx: Double, cy: Double, size: Double, color: Int) {
    val k = size / 24
    for (i in 0..2) rect(cx - 8 * k, cy + (i * 6 - 7.5) * k, 16 * k, 3 * k, 1.5 * k, color)
}
