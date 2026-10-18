// Home: the atom being built. Its cloud is in color as far as it has come (from noon,
// clockwise), the rim the same; the rose Next starts the game Nu wants to play. Nu rests on the
// cloud's edge (tap it). Turn the crown to go through the collection, one atom a step; tap the
// atom to read about it. Games and Settings sit either side of Next.

package app.buildatomos.watch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import app.buildatomos.core.ATOMS
import app.buildatomos.core.Hsl
import app.buildatomos.core.Ink
import app.buildatomos.core.Nebula
import app.buildatomos.core.Pt
import app.buildatomos.core.SCREEN
import app.buildatomos.core.TAU
import app.buildatomos.watch.AppModel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Rotary pixels per atom when turning through the collection. */
private const val STEP_PX = 48f
private const val MID = SCREEN / 2
private const val CLOUD_Y = 116.0
private const val CLOUD_R = 74.0
private const val NAME_Y = 208.0
private const val BAR_Y = 248.0
private const val SIDE_X = 66.0
/** Back to the atom being built this long after the crown stops. */
private const val BROWSE_MS = 6000L
/** Nu's spot on the cloud's edge, up and to the right. */
private val NU = Pt(MID + cos(-PI / 4) * CLOUD_R * 1.02, CLOUD_Y + sin(-PI / 4) * CLOUD_R * 1.02)

@Composable
fun HomeScreen(model: AppModel, onNext: () -> Unit, onGames: () -> Unit, onSettings: () -> Unit, onAtom: (Int) -> Unit) {
    val v = model.version
    val c = model.collection
    val building = if (c.done) ATOMS else c.building
    /** The atom the crown has turned to, or null for the one being built. */
    var focus by remember { mutableStateOf<Int?>(null) }
    var lastTurn by remember { mutableLongStateOf(0L) }
    var spun by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableLongStateOf(0L) }
    var petAt by remember { mutableLongStateOf(0L) }
    val shown = focus ?: building
    val atom = model.atlas.atom(shown)
    val nebula = remember(shown) { Nebula(atom, model.atlas.cloud(shown)) }
    val fr = remember { FocusRequester() }
    val measurer = rememberTextMeasurer()

    // The last run's payout: the rim grows from where it was; an atom built turns it green.
    val rim = remember { Animatable(c.progress.toFloat()) }
    var builtNow by remember { mutableIntStateOf(0) }
    LaunchedEffect(v) {
        val payout = model.payout
        if (payout != null) {
            model.payout = null
            rim.snapTo(payout.fromProgress.toFloat())
            if (payout.built.isNotEmpty()) {
                rim.animateTo(1f, tween(800))
                builtNow = payout.built.last()
                model.feel.complete()
                delay(2400)
                builtNow = 0
                rim.snapTo(0f)
            }
        }
        rim.animateTo(c.progress.toFloat(), tween(800))
    }

    LaunchedEffect(Unit) {
        fr.requestFocus()
        while (true) {
            withFrameNanos { frame = it }
            if (focus != null && System.currentTimeMillis() - lastTurn > BROWSE_MS) focus = null
        }
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    fun court(o: Offset): Pt {
        val side = min(size.width, size.height).toFloat()
        val k = side / SCREEN
        return Pt(((o.x - (size.width - side) / 2) / k).toDouble(), ((o.y - (size.height - side) / 2) / k).toDouble())
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { e ->
                spun += e.verticalScrollPixels
                val steps = (spun / STEP_PX).toInt()
                if (steps != 0) {
                    spun -= steps * STEP_PX
                    val to = ((focus ?: building) + steps).coerceIn(1, ATOMS)
                    if (to != shown) model.feel.buzz(15)
                    focus = if (to == building && focus == null) null else to
                    lastTurn = System.currentTimeMillis()
                }
                true
            }
            .focusRequester(fr)
            .focusable()
            .pointerInput(Unit) {
                size = this.size
                detectTapGestures { o ->
                    val p = court(o)
                    when {
                        hypot(p.x - NU.x, p.y - NU.y) < 20 && focus == null -> {
                            petAt = System.currentTimeMillis()
                            model.feel.capture()
                        }
                        hypot(p.x - MID, p.y - CLOUD_Y) < CLOUD_R + 8 || abs(p.y - NAME_Y) < 14 -> onAtom(shown)
                        p.y > BAR_Y - 26 && abs(p.x - MID) < 36 -> onNext()
                        p.y > BAR_Y - 26 && p.x < MID - 36 -> onGames()
                        p.y > BAR_Y - 26 && p.x > MID + 36 -> onSettings()
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame
            val pen = ComposePen(this)
            val now = frame / 1e6
            val browsing = focus != null
            if (browsing) {
                // The whole table round the rim, each built atom a tick in its own color.
                for (z in 1..ATOMS) {
                    val a = -PI / 2 + (z - 0.5) / ATOMS * TAU
                    val here = z == shown
                    val r0 = if (here) 132.0 else 139.0
                    val color = if (here) Ink.WHITE else if (c.isBuilt(z)) model.atlas.atom(z).color else Ink.K800
                    pen.line(MID + cos(a) * r0, MID + sin(a) * r0, MID + cos(a) * 145, MID + sin(a) * 145, color, if (here) 3.5 else 2.0)
                }
            } else {
                val ringColor = if (builtNow != 0) Ink.GREEN else Hsl.of(atom.color).let { Hsl.argb(it[0], 0.7, 0.62) }
                pen.ring(MID, MID, 144.0, Ink.K900, 5.0)
                val sweep = TAU * rim.value.coerceIn(0f, 1f)
                if (sweep > 0.01) pen.arc(MID, MID, 144.0, -PI / 2, sweep, ringColor, 5.0)
            }

            val share = when {
                browsing -> if (c.isBuilt(shown)) 1.0 else 0.0
                builtNow != 0 -> 1.0
                else -> rim.value.toDouble()
            }
            nebula.draw(pen, MID, CLOUD_Y, CLOUD_R, now, share, still = share <= 0)

            // The number and the name; an atom not built yet reads in gray.
            val dim = browsing && !c.isBuilt(shown)
            pen.text(measurer, "${atom.z}  ${atom.name}", MID, NAME_Y, 17.0, if (dim) Ink.K300 else Ink.WHITE, FontWeight.SemiBold)
            if (builtNow != 0) pen.text(measurer, "Built", MID, NAME_Y + 19, 13.0, Ink.GREEN)

            if (!browsing) {
                // Nu; petted, it glows a little warmer and hops.
                val since = (System.currentTimeMillis() - petAt) / 1000.0
                val warm = if (since < 1.5) 1 + (1.5 - since) else 1.0
                val hop = if (since < 0.6) sin(since / 0.6 * PI) * 7 else 0.0
                pen.dot(NU.x, NU.y - hop, 7.0 * 2.1, Ink.ROSE, 0.07 * warm)
                pen.dot(NU.x, NU.y - hop, 7.0 * 1.55, Ink.ROSE, 0.14 * warm)
                pen.dot(NU.x, NU.y - hop, 7.0, Ink.ROSE)

                if (builtNow == 0) {
                    nextPill(pen, MID, BAR_Y)
                    gamesButton(pen, MID - SIDE_X, BAR_Y)
                    settingsButton(pen, MID + SIDE_X, BAR_Y)
                }
            }
        }
    }
}

/** The rose Next: a pill with the skip-forward mark. */
private fun nextPill(pen: ComposePen, x: Double, y: Double) {
    pen.rect(x - 32, y - 17, 64.0, 34.0, 17.0, Ink.ROSE)
    pen.line(x - 8, y - 8, x + 2, y, Ink.BLACK, 3.6)
    pen.line(x + 2, y, x - 8, y + 8, Ink.BLACK, 3.6)
    pen.line(x + 8, y - 8, x + 8, y + 8, Ink.BLACK, 3.6)
}

/** All the games: four dots on a plate. */
private fun gamesButton(pen: ComposePen, x: Double, y: Double) {
    pen.dot(x, y, 17.0, Ink.K900)
    for (i in 0..3) pen.dot(x + (i % 2 - 0.5) * 9, y + (i / 2 - 0.5) * 9, 2.8, Ink.WHITE)
}

/** Settings: a switch on a plate. */
private fun settingsButton(pen: ComposePen, x: Double, y: Double) {
    pen.dot(x, y, 17.0, Ink.K900)
    pen.rectRing(x - 9, y - 5, 18.0, 10.0, 5.0, Ink.WHITE, 2.0)
    pen.dot(x + 4, y, 2.6, Ink.WHITE)
}
