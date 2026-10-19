// Build: the collection. The Build screen shows every atom three ways, picked with three bare
// icons at its foot: the table (every atom as a dot in its place, built ones lit in their own
// color, the one being built ringed in rose, and how many are built at the top), the dial (the
// atoms as plates round the rim, the crown turning them past the top, the picked one's cloud in
// the middle) and the shells (the whole table as one atom, a shell for each period). In all three
// the crown moves a pick, a tap on an atom picks it, and a tap on the picked one opens its Build
// page: its number, symbol and name, its shells as the bezel (built ones white, the next rose),
// the rose disc that plays the next shell with its difficulty under it, and the Atlas's book.

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import app.buildatomos.core.ATOMS
import app.buildatomos.core.AtomDial
import app.buildatomos.core.AtomShells
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.Nebula
import app.buildatomos.core.Pt
import app.buildatomos.core.SCREEN
import app.buildatomos.core.Standing
import app.buildatomos.core.Words
import app.buildatomos.core.icon
import app.buildatomos.core.says
import app.buildatomos.core.share
import app.buildatomos.core.shellCounts
import app.buildatomos.core.shellRing
import app.buildatomos.core.standing
import app.buildatomos.watch.AppModel
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Rotary pixels per atom on the Build screen and an atom's Build page. */
private const val TURN_PX = 40f

/** The Build screen's views, in the order its icons show them. */
private object View {
    const val TABLE = 0
    const val DIAL = 1
    const val SHELLS = 2
    const val COUNT = 3
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

/** The Build screen: every atom, as the table, the dial or the shells. The view is kept for the
 *  session. */
@Composable
fun CollectionScreen(model: AppModel, onAtom: (Int) -> Unit) {
    model.version
    val view = model.buildView.coerceIn(0, View.COUNT - 1)
    val pick = { v: Int -> if (v != model.buildView) { model.buildView = v; model.feel.buzz(15) } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (view) {
            View.DIAL -> DialView(model, onAtom)
            View.SHELLS -> ShellsView(model, onAtom)
            else -> TableView(model, onAtom)
        }
        ViewIcons(view, pick, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }
}

/** The three views as bare icons, the one showing in cyan. */
@Composable
private fun ViewIcons(view: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (v in 0 until View.COUNT) {
            Box(Modifier.size(34.dp).clip(CircleShape).clickable { onPick(v) }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(18.dp)) {
                    val pen = ComposePen(this, 18.0)
                    val ink = if (v == view) Ink.CYAN else Ink.K300
                    when (v) {
                        View.DIAL -> pen.icon(Icon.WHEEL, 9.0, 9.0, 18.0, ink)
                        View.SHELLS -> pen.icon(Icon.SHELLS, 9.0, 9.0, 18.0, ink)
                        else -> pen.rowsMark(9.0, 9.0, 18.0, ink)
                    }
                }
            }
        }
    }
}

/** The atom the Build screen has picked: the one being built until the crown moves or a tap
 *  picks another. */
private fun AppModel.dialAtom(): Int = buildFocus.takeIf { it in 1..ATOMS } ?: buildingZ

private fun AppModel.pickAtom(z: Int) {
    val to = z.coerceIn(1, ATOMS)
    if (to != dialAtom()) { buildFocus = to; feel.buzz(15) }
}

/** The table's dots, in court units: a cell's side, its left edge, and its top. */
private const val CELL = 10.6
private val TABLE_LEFT = SCREEN / 2 - CELL * 9
private const val TABLE_TOP = 104.0

/** The middle of the cell at column `col` and row `row` (the f-block on rows 9 and 10). */
private fun cellAt(col: Int, row: Int) = Pt(TABLE_LEFT + (col - 0.5) * CELL, TABLE_TOP + (row - 0.5) * CELL + if (row >= 9) -CELL * 0.5 else 0.0)

/** The table: every atom as a dot in its place, built ones in their own color, the one being
 *  built ringed in rose, the rest dark. At the top, how many are built; once the crown or a tap
 *  picks an atom, its symbol and name instead, and the pick ringed in white. */
@Composable
private fun TableView(model: AppModel, onAtom: (Int) -> Unit) {
    val c = model.collection
    val z = model.dialAtom()
    val picked = model.buildFocus in 1..ATOMS && model.buildFocus != model.buildingZ
    val measurer = rememberTextMeasurer()
    AtomPicker(model, onTap = tap@{ p ->
        val at = model.dialAtom()
        if (abs(p.y - 74) < 18 && abs(p.x - SCREEN / 2) < 80) { onAtom(at); return@tap }
        var hit = 0
        var best = CELL * 0.9
        for (a in model.atlas.atoms) {
            val (col, row) = tableSpot(a.z, a.group, a.period)
            val q = cellAt(col, row)
            val d = hypot(p.x - q.x, p.y - q.y)
            if (d < best) { best = d; hit = a.z }
        }
        if (hit == at) onAtom(at) else if (hit != 0) model.pickAtom(hit)
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val pen = ComposePen(this)
            if (picked) {
                val atom = model.atlas.atom(z)
                val sw = pen.textWidth(measurer, atom.symbol, 22.0, FontWeight.SemiBold)
                val nw = pen.textWidth(measurer, atom.name, 18.0)
                val x0 = SCREEN / 2 - (sw + 8 + nw) / 2
                pen.text(measurer, atom.symbol, x0 + sw / 2, 74.0, 22.0, atom.color, FontWeight.SemiBold)
                pen.text(measurer, atom.name, x0 + sw + 8 + nw / 2, 74.0, 18.0, Ink.K200)
            } else {
                val count = "${c.built.size}"
                val cw = pen.textWidth(measurer, count, 24.0)
                val ow = pen.textWidth(measurer, "of $ATOMS", 17.0)
                val x0 = SCREEN / 2 - (cw + 7 + ow) / 2
                pen.text(measurer, count, x0 + cw / 2, 74.0, 24.0, Ink.WHITE)
                pen.text(measurer, "of $ATOMS", x0 + cw + 7 + ow / 2, 75.0, 17.0, Ink.K400)
            }
            for (a in model.atlas.atoms) {
                val (col, row) = tableSpot(a.z, a.group, a.period)
                val q = cellAt(col, row)
                when (c.standing(a.z)) {
                    Standing.BUILT -> pen.dot(q.x, q.y, 3.6, a.color)
                    Standing.BUILDING -> pen.ring(q.x, q.y, 3.1, Ink.ROSE, 1.8)
                    Standing.LATER -> pen.dot(q.x, q.y, 2.9, Ink.K700)
                }
                if (picked && a.z == z) pen.ring(q.x, q.y, 6.0, Ink.WHITE, 1.6)
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

/** The shells: the table as one atom, each period a shell of its atoms, the picked atom's plate
 *  as the nucleus. A tap on an atom picks it, and a tap on the plate (or the picked atom) opens
 *  its Build page. */
@Composable
private fun ShellsView(model: AppModel, onAtom: (Int) -> Unit) {
    val z = model.dialAtom()
    val measurer = rememberTextMeasurer()
    AtomPicker(model, onTap = { p ->
        val at = model.dialAtom()
        when (val hit = AtomShells.hit(p.x, p.y)) {
            -1, at -> onAtom(at)
            0 -> {}
            else -> model.pickAtom(hit)
        }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val pen = ComposePen(this)
            AtomShells.draw(pen, pen.words(measurer), model.atlas.atoms, model.collection, z)
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

/** One atom's Build page, on one screen: its shells as the bezel (built ones white, the next
 *  rose); its number, its symbol large in its color and its name; then, while it is the one being
 *  built, the rose disc that plays its next shell with that shell's game and difficulty, once
 *  built its cloud, and before then a lock and where it stands. The Atlas's book at the foot
 *  opens its Atlas page; the crown steps to the atoms either side, and the chevron on the left
 *  goes back. */
@Composable
fun AtomBuildScreen(model: AppModel, z: Int, onBuild: () -> Unit, onAtlas: (Int) -> Unit, onAtom: (Int) -> Unit, onBack: () -> Unit) {
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
    var spun by remember { mutableFloatStateOf(0f) }
    val step by rememberUpdatedState(onAtom)
    val at by rememberUpdatedState(z)
    val fr = rememberCrownFocus()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { e ->
                spun += e.verticalScrollPixels
                val steps = (spun / (TURN_PX * 2)).toInt()
                if (steps != 0) {
                    spun = 0f
                    val to = (at + steps.coerceIn(-1, 1)).coerceIn(1, ATOMS)
                    if (to != at) { model.feel.buzz(15); step(to) }
                }
                true
            }
            .focusRequester(fr)
            .focusable(),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            ComposePen(this).shellRing(counts.size, done, st == Standing.BUILT, next = st == Standing.BUILDING, lit = Ink.WHITE)
        }
        Face { u ->
            Column(Modifier.padding(top = u * 12), horizontalAlignment = Alignment.CenterHorizontally) {
                Label("${atom.z}", size = u.text(14), color = Tone.text2, weight = FontWeight.Normal, lines = 1)
                Label(atom.symbol, size = u.text(40), color = Color(atom.color), weight = FontWeight.SemiBold, lines = 1)
                Label(atom.name, Modifier.padding(bottom = u * 8), size = u.text(17), lines = 1)
                when {
                    run != null -> {
                        Disc(u * 50, Ink.ROSE, onBuild, game = run.game.id)
                        Pips(run.dial, Modifier.padding(top = u * 7))
                    }
                    st == Standing.BUILT -> Canvas(Modifier.size(u * 58)) {
                        nebula.draw(ComposePen(this, 58.0), 29.0, 29.0, 27.0, frame() / 1e6, 1.0, still = false)
                    }
                    else -> {
                        Canvas(Modifier.size(u * 26)) { ComposePen(this, 24.0).icon(Icon.LOCK, 12.0, 12.0, 24.0, Ink.K500) }
                        val (says, color) = c.says(z)
                        Label(says, Modifier.padding(top = u * 4), size = u.text(13), color = Color(color), lines = 1)
                    }
                }
                BareIcon(Icon.ATLAS, u * 22, { onAtlas(z) }, Modifier.padding(top = u * 2), color = Ink.K200, touch = u * 40)
            }
            BareIcon(Icon.BACK, u * 22, onBack, Modifier.align(Alignment.CenterStart).padding(start = u * 10), color = Ink.K300)
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
