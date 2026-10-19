// Build: the collection. The Build screen shows every atom three ways, picked on a switch of three:
// the rows (each period's atoms as plates, eight to a row, built ones in their own color, under
// the curved title, the switch at the top of the list and the rose Build at the edge), the dial
// (the atoms as plates round the rim, the crown turning them past the top, the picked one's cloud
// in the middle) and the shells (the whole table as one atom, a shell for each period, the crown
// stepping through them); those two fill the round screen, so their switch sits in its open foot.
// Each atom has a Build page: its card, its shells as a track and as a ring round the bezel, the
// next shell's game, difficulty and Play, its cloud, its neighbors, and View in Atlas at the edge.

package app.buildatomos.watch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import app.buildatomos.core.ATOMS
import app.buildatomos.core.Atom
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
import app.buildatomos.core.periodOf
import app.buildatomos.core.pips
import app.buildatomos.core.says
import app.buildatomos.core.share
import app.buildatomos.core.shellCounts
import app.buildatomos.core.shellRing
import app.buildatomos.core.standing
import app.buildatomos.watch.AppModel
import app.buildatomos.watch.RunSpec
import kotlinx.coroutines.delay
import kotlin.math.min

/** Rotary pixels per atom on the dial and the shells. */
private const val TURN_PX = 40f

/** The plates to a row in the rows view. */
private const val PER_ROW = 8

/** The Build page's track: shells to a row. */
private const val TRACK_ROW = 4

/** The Build screen's views, in the order its switch shows them. */
private object View {
    const val ROWS = 0
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

/** The Build screen: every atom, as the rows, the dial or the shells. The view is kept for the
 *  session. */
@Composable
fun CollectionScreen(model: AppModel, onBuild: () -> Unit, onAtom: (Int) -> Unit) {
    model.version
    val view = model.buildView
    val pick = { v: Int -> if (v != model.buildView) { model.buildView = v; model.feel.buzz(15) } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (view) {
            View.DIAL -> DialView(model, onAtom)
            View.SHELLS -> ShellsView(model, onAtom)
            else -> RowsView(model, onBuild, onAtom, pick)
        }
        if (view != View.ROWS) ViewSwitch(view, pick, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
    }
}

/** The switch between the views: a soft pill of three, Rows, Dial and Shells, the one showing lit. */
@Composable
private fun ViewSwitch(view: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.background(Tone.plate, RoundedCornerShape(15.dp)).padding(2.dp)) {
        for (v in 0 until View.COUNT) {
            val on = v == view
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (on) Color(Ink.K700) else Tone.plate)
                    .clickable { onPick(v) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val pen = ComposePen(this, 16.0)
                    val ink = if (on) Ink.WHITE else Ink.K300
                    when (v) {
                        View.DIAL -> pen.icon(Icon.WHEEL, 8.0, 8.0, 16.0, ink)
                        View.SHELLS -> pen.icon(Icon.SHELLS, 8.0, 8.0, 16.0, ink)
                        else -> pen.rowsMark(8.0, 8.0, 16.0, ink)
                    }
                }
            }
        }
    }
}

/** One row of plates in the rows view; null leaves a place empty. `first` marks a period's first
 *  row, set a little apart from the period before. */
private class PlateLine(val atoms: List<Atom?>, val first: Boolean)

/** Every period's atoms, eight to a row; hydrogen and helium at the two ends of theirs, as in the
 *  table. */
private fun plateLines(atoms: List<Atom>): List<PlateLine> {
    val lines = mutableListOf<PlateLine>()
    for (p in 1..7) {
        val period = atoms.filter { it.period == p }
        if (p == 1) {
            lines += PlateLine(listOf(period[0]) + List(PER_ROW - 2) { null } + listOf(period[1]), true)
            continue
        }
        for ((i, row) in period.chunked(PER_ROW).withIndex()) lines += PlateLine(row + List(PER_ROW - row.size) { null }, i == 0)
    }
    return lines
}

/** The rows: the switch, then every period's atoms as plates; the count of built ones last. The
 *  rose Build at the edge plays the next shell of the atom being built. */
@Composable
private fun RowsView(model: AppModel, onBuild: () -> Unit, onAtom: (Int) -> Unit, onPick: (Int) -> Unit) {
    val c = model.collection
    val state = rememberScalingLazyListState(initialCenterItemIndex = 2)
    val lines = remember { plateLines(model.atlas.atoms) }
    Box(Modifier.fillMaxSize()) {
        EdgeList(state, side = 18.dp, top = 28.dp, gap = 3.dp) {
            item { ViewSwitch(View.ROWS, onPick, Modifier.padding(bottom = 6.dp)) }
            for (line in lines) item { PlateRowOf(model, line, onAtom) }
            item {
                Column(Modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Label("${c.built.size} of $ATOMS built", size = 14.sp)
                    Bar(c.built.size / ATOMS.toDouble(), Modifier.padding(top = 4.dp).size(width = 70.dp, height = 4.dp))
                }
            }
        }
        CurvedTitle("Build")
        EdgeScroll(state)
        if (model.buildRun() != null) {
            val atom = model.atlas.atom(model.buildingZ)
            // A long name would run off the button; its symbol stands in.
            EdgeButton(onBuild, fill = Tone.rose) { EdgeLabel("Build ${if (atom.name.length <= 9) atom.name else atom.symbol}", Tone.ink) }
        }
    }
}

/** A row of the rows view: built atoms in their own color, the one being built ringed in rose,
 *  the rest dark. A tap opens the atom's Build page. */
@Composable
private fun PlateRowOf(model: AppModel, line: PlateLine, onAtom: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = if (line.first) 4.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (a in line.atoms) {
            if (a == null) { Spacer(Modifier.weight(1f)); continue }
            val st = model.collection.standing(a.z)
            val shape = RoundedCornerShape(30)
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .background(if (st == Standing.BUILT) Color(a.color) else Tone.plate, shape)
                    .then(if (st == Standing.BUILDING) Modifier.border(2.dp, Tone.rose, shape) else Modifier)
                    .clickable { onAtom(a.z) },
                contentAlignment = Alignment.Center,
            ) {
                Label(
                    a.symbol, size = 13.sp, lines = 1,
                    weight = if (st == Standing.LATER) FontWeight.Medium else FontWeight.SemiBold,
                    color = when (st) { Standing.BUILT -> inkOn(a.color); Standing.BUILDING -> Tone.rose; Standing.LATER -> Tone.text2 },
                )
            }
        }
    }
}

/** The atom the dial and the shells have picked: the one being built until the crown moves. */
private fun AppModel.dialAtom(): Int = buildFocus.takeIf { it in 1..ATOMS } ?: buildingZ

private fun AppModel.pickAtom(z: Int) {
    val to = z.coerceIn(1, ATOMS)
    if (to != dialAtom()) { buildFocus = to; feel.buzz(15) }
}

/** The dial: the atoms as plates round the rim, the picked one at the top, its cloud, name and
 *  state in the middle. The crown turns it; a tap on a plate turns it there, and a tap on the
 *  picked atom opens its Build page. */
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
 *  as the nucleus. The crown steps through the atoms; a tap on one picks it, and a tap on the
 *  plate (or the picked atom) opens its Build page. */
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

/** The whole screen for the dial and the shells: the crown steps the picked atom, and taps come
 *  to `onTap` in court units. */
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

/** A thin progress bar: cyan over K700. */
@Composable
fun Bar(progress: Double, modifier: Modifier, color: Int = Ink.CYAN) {
    Canvas(modifier) {
        val w = size.width.toDouble() / density
        val pen = ComposePen(this, w, wide = true)
        val h = size.height.toDouble() / density
        pen.rect(0.0, 0.0, w, h, h / 2, Ink.K700)
        if (progress > 0) pen.rect(0.0, 0.0, maxOf(h, w * progress.coerceIn(0.0, 1.0)), h, h / 2, color)
    }
}

/** An atom's shells as short bars side by side, the first `done` green. */
@Composable
private fun ShellBars(need: Int, done: Int, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width.toDouble() / density
        val h = size.height.toDouble() / density
        val pen = ComposePen(this, w, wide = true)
        val gap = 3.0
        val seg = (w - gap * (need - 1)) / need
        for (i in 0 until need) pen.rect(i * (seg + gap), 0.0, seg, h, h / 2, if (i < done) Ink.GREEN else Ink.K700)
    }
}

/** Home's card for the atom being built: its cloud, filling shell by shell, its name, its shells
 *  so far in words and as bars. After a won run its cloud fills to the new share; a finished atom
 *  shows green for a moment before the next one takes its place. A tap opens its Build page; the
 *  rose Build at the edge plays its next shell. */
@Composable
fun BuildCard(model: AppModel, frame: () -> Long, onAtom: (Int) -> Unit) {
    val c = model.collection
    val payout = model.payout
    var finished by remember { mutableIntStateOf(0) }
    val share = remember { Animatable(c.progress.toFloat()) }
    LaunchedEffect(payout) {
        if (payout == null) { share.snapTo(c.progress.toFloat()); return@LaunchedEffect }
        val need = periodOf(payout.z)
        share.snapTo(payout.shell / need.toFloat())
        delay(250)
        share.animateTo((payout.shell + 1) / need.toFloat(), tween(900))
        if (payout.finished) {
            finished = payout.z
            model.feel.complete()
            delay(2200)
            finished = 0
        }
        // Last: clearing it restarts this with no payout, which settles on the collection's share.
        model.payout = null
    }
    if (c.done && finished == 0) {
        Plate { Label("Every atom built", size = 15.sp, color = Tone.green) }
        return
    }
    val z = if (finished != 0) finished else model.buildingZ
    val atom = model.atlas.atom(z)
    val nebula = remember(z) { Nebula(atom, model.atlas.cloud(z)) }
    val need = periodOf(z)
    Plate(Modifier.clickable { onAtom(z) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(58.dp)) {
                val now = frame() / 1e6
                val pen = ComposePen(this, 58.0)
                val sh = if (finished != 0) 1.0 else share.value.toDouble()
                nebula.draw(pen, 29.0, 29.0, 26.0, now, sh, still = sh <= 0)
                if (finished != 0) pen.ring(29.0, 29.0, 28.0, Ink.GREEN, 2.0)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(atom.name, size = 17.sp, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 1)
                if (finished != 0) Label("Built", size = 13.sp, color = Tone.green, align = TextAlign.Start)
                else Label("Shells ${c.shells} of $need", size = 13.sp, color = Tone.text2, align = TextAlign.Start, lines = 1)
                ShellBars(need, if (finished != 0) need else c.shells, Modifier.padding(top = 4.dp).size(width = 66.dp, height = 5.dp))
            }
        }
    }
}

/** One atom's Build page: a list the crown scrolls under its name, curved at the top; its shells
 *  as a ring round the bezel (built ones green, the next rose), back at the middle of the left
 *  edge, and View in Atlas at the edge. */
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
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ScalingLazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            // Clear of the ring, wider on the left for back; the foot clear of the edge button.
            contentPadding = PaddingValues(start = 42.dp, end = 26.dp, top = 32.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { AtomCard(atom, 56, lit = st == Standing.BUILT) }
            item { ShellTrack(counts.size, st, c.shells) }
            if (run != null) item { NextShell(run, onBuild) }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Canvas(Modifier.size(104.dp)) {
                        val share = c.share(z)
                        nebula.draw(ComposePen(this, 104.0), 52.0, 52.0, 48.0, frame() / 1e6, share, still = share <= 0)
                    }
                    val (says, color) = c.says(z)
                    Label(says, size = 13.sp, color = Color(color))
                }
            }
            if (st == Standing.LATER && !c.done) item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val now = model.atlas.atom(model.buildingZ)
                    Para("Atoms are built in order, from hydrogen up. ${now.name} is next.")
                    Pill("Go to ${now.symbol}", Icon.CHEVRON, { onAtom(now.z) })
                }
            }
            item {
                Para("Each shell is one game: clear its levels to build it. The outer shells are harder.")
            }
            item { Neighbors(model, z, onAtom) }
        }
        Canvas(Modifier.fillMaxSize()) { ComposePen(this).shellRing(counts.size, done, st == Standing.BUILT, next = st == Standing.BUILDING) }
        CurvedTitle(atom.name, inset = 12.dp)
        Box(
            Modifier.align(Alignment.CenterStart).size(width = 40.dp, height = 56.dp).clickable(onClick = onBack),
            contentAlignment = Alignment.CenterEnd,
        ) {
            IconMark(Icon.BACK, 30, Ink.K200, disc = Ink.K900)
        }
        EdgeButton({ onAtlas(z) }) { EdgeLabel("View in Atlas", Tone.cyan, Icon.ATLAS) }
    }
}

/** The atom's shells as a track of discs, four to a row: built ones green with a check, the next
 *  rose and the ones after dark, each with its number. */
@Composable
private fun ShellTrack(need: Int, st: Standing, shells: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in (0 until need).chunked(TRACK_ROW)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in row) {
                val s = when {
                    st == Standing.BUILT || (st == Standing.BUILDING && i < shells) -> Standing.BUILT
                    st == Standing.BUILDING && i == shells -> Standing.BUILDING
                    else -> Standing.LATER
                }
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(28.dp)) {
                        val pen = ComposePen(this, 28.0)
                        when (s) {
                            Standing.BUILT -> { pen.dot(14.0, 14.0, 14.0, Ink.GREEN); pen.icon(Icon.CHECK, 14.0, 14.0, 17.0, Ink.K975) }
                            Standing.BUILDING -> pen.dot(14.0, 14.0, 14.0, Ink.ROSE)
                            Standing.LATER -> pen.dot(14.0, 14.0, 14.0, Ink.K900)
                        }
                    }
                    if (s != Standing.BUILT) Label(
                        "${i + 1}", size = 14.sp, lines = 1,
                        weight = if (s == Standing.BUILDING) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (s == Standing.BUILDING) Tone.ink else Tone.text2,
                    )
                }
            }
        }
    }
}

/** The next shell: a soft pill with a rose Play, its game and its difficulty's pips. Plays it. */
@Composable
private fun NextShell(run: RunSpec, onBuild: () -> Unit) {
    Row(
        Modifier
            .background(Tone.plate, RoundedCornerShape(22.dp))
            .clickable(onClick = onBuild)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        IconMark(Icon.PLAY, 30, Ink.K975, disc = Ink.ROSE)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(run.game.label, size = 15.sp, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 1)
            Canvas(Modifier.size(width = 30.dp, height = 8.dp)) { ComposePen(this, 30.0, wide = true).pips(15.0, 4.0, 28.0, run.dial) }
        }
    }
}

/** The atoms before and after, as their plates with a chevron each. */
@Composable
private fun Neighbors(model: AppModel, z: Int, onAtom: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (z > 1) Neighbor(model.atlas.atom(z - 1), back = true) { onAtom(z - 1) }
        Spacer(Modifier.weight(1f))
        if (z < ATOMS) Neighbor(model.atlas.atom(z + 1), back = false) { onAtom(z + 1) }
    }
}

@Composable
private fun Neighbor(atom: Atom, back: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (back) IconMark(Icon.BACK, 14, Ink.K300)
        IdPlate(atom, 42)
        if (!back) IconMark(Icon.CHEVRON, 14, Ink.K300)
    }
}

@Composable
fun Step(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(40.dp).background(Tone.plate, RoundedCornerShape(20.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Label(label, size = 14.sp)
    }
}

@Composable
fun Para(text: String, inside: Boolean = false) {
    Label(text, Modifier.fillMaxWidth().padding(horizontal = if (inside) 0.dp else 6.dp), size = 14.sp, weight = FontWeight.Normal, align = if (inside) TextAlign.Start else TextAlign.Center)
}
