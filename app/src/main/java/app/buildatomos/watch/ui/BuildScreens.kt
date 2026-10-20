// Build: the collection. The Build screen shows every atom two ways, switched with a bare icon
// at its foot as in the Atlas: the table (every atom in its place, built ones in their own color,
// the one being built ringed in rose, the picked one named on a gray card over it, and how many
// are built as a ring round the bezel) and the dial (the atoms as plates round the rim, the crown
// turning them past the top, the picked one's cloud in the middle). In both the crown moves a
// pick, a tap on an atom picks it, and a tap on the picked one opens its Build page: a list the
// crown scrolls, every shell on a card with the progress track down the right.

package app.buildatomos.watch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import app.buildatomos.core.ATOMS
import app.buildatomos.core.AtomDial
import app.buildatomos.core.Difficulty
import app.buildatomos.core.Game
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.Nebula
import app.buildatomos.core.Pt
import app.buildatomos.core.SCREEN
import app.buildatomos.core.Standing
import app.buildatomos.core.TAU
import app.buildatomos.core.Words
import app.buildatomos.core.icon
import app.buildatomos.core.says
import app.buildatomos.core.share
import app.buildatomos.core.shellCounts
import app.buildatomos.core.standing
import app.buildatomos.watch.AppModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Rotary pixels per atom on the Build screen and an atom's Build page. */
private const val TURN_PX = 40f

/** The Build screen's views. */
private object View {
    const val TABLE = 0
    const val DIAL = 1
    const val COUNT = 2
}

/** How far atom z's cloud is filled in, 0..1. */
fun AppModel.share(z: Int): Double = collection.share(z)

/** A frame clock for a screen that draws atoms. */
@Composable
fun rememberFrame(): () -> Long {
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }
    return { frame }
}

/** The Build screen: every atom, as the table or the dial, the icon at the foot switching
 *  between them. The view is kept for the session. */
@Composable
fun CollectionScreen(model: AppModel, onAtom: (Int) -> Unit) {
    model.version
    val view = model.buildView.coerceIn(0, View.COUNT - 1)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (view == View.DIAL) DialView(model, onAtom) else TableView(model, onAtom)
        BareIcon(
            if (view == View.DIAL) Icon.TABLE else Icon.WHEEL, 20.dp,
            { model.buildView = if (view == View.DIAL) View.TABLE else View.DIAL; model.feel.buzz(15) },
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), color = Ink.K300,
        )
    }
}

/** The atom the Build screen has picked: the one being built until the crown moves or a tap
 *  picks another. */
private fun AppModel.dialAtom(): Int = buildFocus.takeIf { it in 1..ATOMS } ?: buildingZ

private fun AppModel.pickAtom(z: Int) {
    val to = z.coerceIn(1, ATOMS)
    if (to != dialAtom()) { buildFocus = to; feel.buzz(15) }
}

/** The table, laid out as the Atlas's: every atom in its place, built ones in their own color,
 *  the one being built ringed in rose, the rest dark, and the picked one (the one being built
 *  until the crown or a tap picks another) ringed in white and named on the gray card above.
 *  How many are built shows as a ring round the bezel. */
@Composable
private fun TableView(model: AppModel, onAtom: (Int) -> Unit) {
    val c = model.collection
    val z = model.dialAtom()
    val measurer = rememberTextMeasurer()
    AtomPicker(model, onTap = tap@{ p ->
        val at = model.dialAtom()
        if (inTableCard(p)) { onAtom(at); return@tap }
        val hit = Grid.hit(model.atlas.atoms, p)
        if (hit == at) onAtom(at) else if (hit != 0) model.pickAtom(hit)
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val pen = ComposePen(this)
            val r = SCREEN / 2 - 5
            pen.ring(SCREEN / 2, SCREEN / 2, r, Ink.K800, 5.0)
            val share = c.built.size / ATOMS.toDouble()
            if (share > 0) pen.arc(SCREEN / 2, SCREEN / 2, r, -PI / 2, TAU * share, Ink.WHITE, 5.0)
            pen.tableCard(measurer, model.atlas.atom(z))
            val cell = Grid.CELL
            for (a in model.atlas.atoms) {
                val (col, row) = tableSpot(a.z, a.group, a.period)
                val q = Grid.at(col, row)
                when (c.standing(a.z)) {
                    Standing.BUILT -> pen.rect(q.x - cell / 2 + 0.8, q.y - cell / 2 + 0.8, cell - 1.6, cell - 1.6, 2.0, a.color)
                    Standing.BUILDING -> pen.rectRing(q.x - cell / 2 + 1.6, q.y - cell / 2 + 1.6, cell - 3.2, cell - 3.2, 1.6, Ink.ROSE, 1.8)
                    Standing.LATER -> pen.rect(q.x - cell / 2 + 0.8, q.y - cell / 2 + 0.8, cell - 1.6, cell - 1.6, 2.0, Ink.K800)
                }
                if (a.z == z) pen.rectRing(q.x - cell / 2 - 0.4, q.y - cell / 2 - 0.4, cell + 0.8, cell + 0.8, 2.6, Ink.WHITE, 1.6)
            }
        }
    }
}

/** The dial: the atoms as plates round the rim, the picked one at the top, its cloud, name and
 *  state in the middle. A tap on a plate turns the dial there, and a tap on the picked atom opens
 *  its Build page. */
@Composable
private fun DialView(model: AppModel, onAtom: (Int) -> Unit) {
    val z = model.dialAtom()
    val pos by animateFloatAsState(z.toFloat(), tween(200), label = "dial")
    val frame = rememberFrame()
    val measurer = rememberTextMeasurer()
    val nebula = remember(z) { Nebula(model.atlas.atom(z), model.atlas.cloud(z)) }
    AtomPicker(model, onTap = { p ->
        val at = model.dialAtom()
        val hit = AtomDial.hit(p.x, p.y, pos.toDouble())
        if (AtomDial.middle(p.x, p.y) || hit == at) onAtom(at) else if (hit != 0) model.pickAtom(hit)
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val pen = ComposePen(this)
            AtomDial.draw(pen, pen.words(measurer), model.atlas.atoms, model.collection, pos.toDouble(), z, nebula, frame() / 1e6)
        }
    }
}

/** The whole screen for a view: the crown steps the picked atom, and taps come to `onTap` in
 *  court units. */
@Composable
private fun AtomPicker(model: AppModel, onTap: (Pt) -> Unit, content: @Composable () -> Unit) {
    var spun by remember { mutableFloatStateOf(0f) }
    val tap by rememberUpdatedState(onTap)
    val fr = rememberCrownFocus()
    Box(
        Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { e ->
                spun += e.verticalScrollPixels
                val steps = (spun / TURN_PX).toInt()
                if (steps != 0) {
                    spun -= steps * TURN_PX
                    model.pickAtom(model.dialAtom() + steps)
                }
                true
            }
            .focusRequester(fr)
            .focusable()
            .pointerInput(Unit) { detectTapGestures { o -> tap(court(o, size)) } },
    ) { content() }
}

/** A point on a square-centered screen of `size` pixels, in court units. */
private fun court(o: Offset, size: IntSize): Pt {
    val side = min(size.width, size.height).toFloat()
    val k = side / SCREEN
    return Pt(((o.x - (size.width - side) / 2) / k).toDouble(), ((o.y - (size.height - side) / 2) / k).toDouble())
}

/** The core's drawings' text, in Outfit. */
private fun ComposePen.words(measurer: TextMeasurer) = Words { s, x, y, size, color, bold, alpha ->
    text(measurer, s, x, y, size, color, if (bold) FontWeight.SemiBold else FontWeight.Medium, alpha)
}

/** One atom's Build page: a list the crown scrolls. Its cloud, symbol and name, and how many of
 *  its shells are built; then every shell, innermost first, on its own gray card, with a progress
 *  track down the right: a green check by each built shell (and green line up to it), a rose dot
 *  by the next, an empty ring by each one to come. A built shell's card shows the game it was
 *  built with (a tap plays it again, at that shell's difficulty); the next shell's card is taller,
 *  with today's game and the rose Build; a later shell's card, its difficulty and a lock. Before
 *  its turn, the atom being built and the way to it; then the way into its Atlas page and the
 *  atoms either side. */
@Composable
fun AtomBuildScreen(
    model: AppModel,
    z: Int,
    onBuild: () -> Unit,
    onPlay: (Game, Int) -> Unit,
    onAtlas: (Int) -> Unit,
    onAtom: (Int) -> Unit,
) {
    model.version
    val frame = rememberFrame()
    val atom = model.atlas.atom(z)
    val c = model.collection
    val st = c.standing(z)
    val counts = shellCounts(z, model.atlas.subs(z))
    val nebula = remember(z) { Nebula(atom, model.atlas.cloud(z)) }
    val done = when (st) {
        Standing.BUILT -> counts.size
        Standing.BUILDING -> c.shells
        Standing.LATER -> 0
    }
    val run = if (st == Standing.BUILDING) model.buildRun() else null
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // No gap between items: each shell's row carries its own, so the track runs on unbroken.
        QuietList(state, side = 14.dp, top = 22.dp, bottom = 30.dp, gap = 0.dp, shrink = false) {
            item {
                Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.size(58.dp)) {
                        val share = c.share(z)
                        nebula.draw(ComposePen(this, 58.0), 29.0, 29.0, 27.0, frame() / 1e6, share, still = share <= 0)
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Label(atom.symbol, size = 22.sp, color = Color(atom.color), weight = FontWeight.SemiBold, lines = 1)
                            Label(atom.name, size = 16.sp, weight = FontWeight.SemiBold, lines = 1)
                        }
                        Label(
                            if (st == Standing.BUILT) "Built" else "$done of ${counts.size} shells built",
                            size = 13.sp, color = if (st == Standing.BUILT) Tone.green else Tone.text2, weight = FontWeight.Normal, align = TextAlign.Start, lines = 1,
                        )
                    }
                }
            }
            items(counts.size) { i ->
                val dial = Difficulty.buildDial(z, i)
                val shell = when {
                    i < done -> ShellState.BUILT
                    run != null && i == done -> ShellState.NEXT
                    else -> ShellState.LATER
                }
                ShellRow(i, counts.size, shell, belowBuilt = i + 1 < done, electrons = counts[i]) {
                    when (shell) {
                        ShellState.BUILT -> {
                            val game = model.shellGame(z, i)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                    .then(if (game != null) Modifier.clickable { onPlay(game, Difficulty.level(dial)) } else Modifier),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (game != null) GameIcon(game.id, 30.dp)
                                Column(Modifier.weight(1f)) {
                                    Label(game?.label ?: "Built", size = 15.sp, color = if (game != null) Tone.text else Tone.green, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 1)
                                    Label(Difficulty.word(dial), size = 12.sp, color = Color(Difficulty.color(dial)), align = TextAlign.Start, lines = 1)
                                }
                                TinyAtom(Difficulty.goal(dial), Difficulty.goal(dial), 20.dp, Ink.GREEN)
                            }
                        }
                        ShellState.NEXT -> {
                            val r = run!!
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GameIcon(r.game.id, 38.dp)
                                Column(Modifier.weight(1f)) {
                                    Label(r.game.label, size = 16.sp, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 1)
                                    Label(
                                        "${Difficulty.word(dial)} · Reach ${Difficulty.goal(dial)} levels", size = 12.sp,
                                        color = Color(Difficulty.color(dial)), align = TextAlign.Start, lines = 1,
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(Ink.ROSE)).clickable(onClick = onBuild),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            ) {
                                Canvas(Modifier.size(16.dp)) { ComposePen(this, 24.0).icon(Icon.PLAY, 12.0, 12.0, 24.0, Ink.BLACK) }
                                Label("Build", size = 15.sp, color = Color.Black, weight = FontWeight.SemiBold, lines = 1)
                            }
                        }
                        ShellState.LATER -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Canvas(Modifier.size(16.dp)) { ComposePen(this, 24.0).icon(Icon.LOCK, 12.0, 12.0, 24.0, Ink.K500) }
                                Label(
                                    "${Difficulty.word(dial)} · ${Difficulty.goal(dial)} levels", size = 13.sp,
                                    color = Tone.text3, weight = FontWeight.Normal, align = TextAlign.Start, lines = 1,
                                )
                            }
                        }
                    }
                }
            }
            if (st == Standing.LATER && !c.done) item {
                val now = model.atlas.atom(model.buildingZ)
                Plate(Modifier.padding(top = 2.dp, bottom = 10.dp)) {
                    Para("Atoms are built in order, from hydrogen up.", inside = true)
                    PlateLink(Icon.SHELLS, "Go to ${now.name}", null) { onAtom(now.z) }
                }
            }
            item { Plate(Modifier.padding(top = 2.dp, bottom = 10.dp)) { PlateLink(Icon.ATLAS, "View in Atlas", null) { onAtlas(z) } } }
            item { Neighbors(model, z, onAtom) }
        }
        Scroll(state)
    }
}

private enum class ShellState { BUILT, NEXT, LATER }

/** How far down a shell's row its title sits, where its stop on the track is drawn. */
private val STOP_Y = 19.dp

/** One shell: its gray card (its name and electrons small at the top, in line on every card, then
 *  `content`), and beside it on the right its stretch of the progress track: the stop, green with
 *  a check once built, rose for the next, an empty ring to come, and the line on to the stops
 *  either side, green as far as the shells are built. The row's foot is part of the line, so the
 *  stops join up. */
@Composable
private fun ShellRow(i: Int, of: Int, state: ShellState, belowBuilt: Boolean, electrons: Int, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            Modifier.weight(1f).padding(bottom = 8.dp).clip(RoundedCornerShape(22.dp)).background(Color(Ink.K900))
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Label(
                "Shell ${SHELL_NAMES[i]} · $electrons electron${if (electrons == 1) "" else "s"}", size = 12.sp,
                color = if (state == ShellState.NEXT) Color(Ink.ROSE) else Tone.text2, align = TextAlign.Start, lines = 1,
            )
            content()
        }
        Canvas(Modifier.width(26.dp).fillMaxHeight()) {
            val pen = ComposePen(this, 26.0, wide = true)
            val k = 26.0 / size.width
            val stop = STOP_Y.toPx() * k
            val foot = size.height * k
            val x = 15.0
            val built = state == ShellState.BUILT
            // In from the stop above, and on down to the one below.
            if (i > 0) pen.line(x, 0.0, x, stop, if (built) Ink.GREEN else Ink.K700, 2.4)
            if (i < of - 1) pen.line(x, stop, x, foot, if (belowBuilt) Ink.GREEN else Ink.K700, 2.4)
            when (state) {
                ShellState.BUILT -> {
                    pen.dot(x, stop, 8.0, Ink.GREEN)
                    pen.icon(Icon.CHECK, x, stop, 12.0, Ink.BLACK)
                }
                ShellState.NEXT -> {
                    pen.dot(x, stop, 11.0, Ink.ROSE, 0.25)
                    pen.dot(x, stop, 7.5, Ink.ROSE)
                }
                ShellState.LATER -> {
                    pen.dot(x, stop, 7.0, Ink.BLACK)
                    pen.ring(x, stop, 6.0, Ink.K500, 2.0)
                }
            }
        }
    }
}

/** The shells' letters, innermost first. */
private const val SHELL_NAMES = "KLMNOPQ"

/** The atoms either side, each on a small plate with a chevron toward it. */
@Composable
private fun Neighbors(model: AppModel, z: Int, onAtom: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (n in listOf(z - 1, z + 1)) {
            if (n !in 1..ATOMS) { Spacer(Modifier.weight(1f)); continue }
            val a = model.atlas.atom(n)
            val back = n < z
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(Ink.K900)).clickable { onAtom(n) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                if (back) Canvas(Modifier.size(14.dp)) { ComposePen(this, 24.0).icon(Icon.BACK, 12.0, 12.0, 24.0, Ink.K300) }
                Label("$n", size = 13.sp, color = Tone.text2, weight = FontWeight.Normal, lines = 1)
                Label(a.symbol, size = 16.sp, color = Color(a.color), weight = FontWeight.SemiBold, lines = 1)
                if (!back) Canvas(Modifier.size(14.dp)) { ComposePen(this, 24.0).icon(Icon.CHEVRON, 12.0, 12.0, 24.0, Ink.K300) }
            }
        }
    }
}

@Composable
fun Para(text: String, inside: Boolean = false, color: Color = Tone.text) {
    Label(
        text, Modifier.fillMaxWidth().padding(horizontal = if (inside) 0.dp else 6.dp),
        size = 14.sp, color = color, weight = FontWeight.Normal, align = if (inside) TextAlign.Start else TextAlign.Center,
    )
}
