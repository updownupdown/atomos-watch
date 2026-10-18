// Build: the collection. The Build screen shows every atom three ways, switched by the button at
// its foot: the grid (the atom being built on its card, then each period's atoms as plates, built
// ones in their own color), the dial (the atoms as plates round the rim, the crown turning them
// past the top, the picked one's cloud in the middle) and the shells (the whole table as one
// atom, a shell for each period, the crown stepping through them). Each atom has a Build page: its
// cloud with its shells round the bezel, its plate and name, its shells as a track, one game each,
// the next one with its game, its difficulty and Play, its neighbors, and View in Atlas at the foot.

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
import app.buildatomos.core.Difficulty
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
import kotlinx.coroutines.delay
import kotlin.math.min

/** The shells' letters, K innermost. */
private const val SHELL_LETTERS = "KLMNOPQ"

/** Rotary pixels per atom on the dial and the shells. */
private const val TURN_PX = 40f

/** The Build screen's views, in the order its switch steps through them. */
private object View {
    const val GRID = 0
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

/** The Build screen: every atom, as the grid, the dial or the shells, and at the foot the switch
 *  to the next view (it shows that view's icon). The view is kept for the session. */
@Composable
fun CollectionScreen(model: AppModel, onBuild: () -> Unit, onAtom: (Int) -> Unit) {
    model.version
    val view = model.buildView
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (view) {
            View.DIAL -> DialView(model, onAtom)
            View.SHELLS -> ShellsView(model, onAtom)
            else -> GridView(model, onBuild, onAtom)
        }
        val next = (view + 1) % View.COUNT
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .size(40.dp)
                .clickable { model.buildView = next; model.feel.buzz(15) },
            contentAlignment = Alignment.Center,
        ) {
            IconMark(when (next) { View.DIAL -> Icon.WHEEL; View.SHELLS -> Icon.SHELLS; else -> Icon.TABLE }, 30, Ink.WHITE, disc = Ink.K800)
        }
    }
}

/** The grid: the atom being built, then each period's atoms. */
@Composable
private fun GridView(model: AppModel, onBuild: () -> Unit, onAtom: (Int) -> Unit) {
    val frame = rememberFrame()
    val c = model.collection
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        // Room at the foot so the last period clears the switch.
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 52.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label("${c.built.size} of $ATOMS built", size = 14.sp)
                Bar(c.built.size / ATOMS.toDouble(), Modifier.padding(top = 4.dp).size(width = 70.dp, height = 4.dp))
            }
        }
        item { BuildCard(model, frame, onBuild, onAtom) }
        for (p in 1..7) item { PeriodPlate(model, p, onAtom) }
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

/** One period: its name and count, and its atoms as plates five to a row. */
@Composable
private fun PeriodPlate(model: AppModel, period: Int, onAtom: (Int) -> Unit) {
    val atoms = model.atlas.atoms.filter { it.period == period }
    val built = atoms.count { model.collection.isBuilt(it.z) }
    Plate {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("Period $period", Modifier.weight(1f), size = 14.sp, align = TextAlign.Start)
                Label("$built/${atoms.size}", size = 13.sp, color = if (built == atoms.size) Tone.green else Tone.text2)
            }
            for (row in atoms.chunked(5)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (a in row) {
                    val st = model.collection.standing(a.z)
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(if (st == Standing.BUILT) Color(a.color) else Tone.chip, shape)
                            .then(if (st == Standing.BUILDING) Modifier.border(2.dp, Tone.rose, shape) else Modifier)
                            .clickable { onAtom(a.z) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Label(
                            a.symbol, size = 13.sp, lines = 1, weight = FontWeight.SemiBold,
                            color = when (st) { Standing.BUILT -> inkOn(a.color); Standing.BUILDING -> Tone.text; Standing.LATER -> Tone.text2 },
                        )
                    }
                }
                // Keep a short last row's plates the same size.
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The atom being built: its cloud, its name and shells, the rose Build, and under it the next
 *  shell's game and difficulty. After a won run its cloud fills to the new share; a finished atom
 *  shows green for a moment before the next one takes its place. Tapping the atom opens its Build
 *  page. */
@Composable
fun BuildCard(model: AppModel, frame: () -> Long, onBuild: () -> Unit, onAtom: (Int) -> Unit) {
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
    val run = model.buildRun()
    Plate {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Canvas(Modifier.size(92.dp).clickable { onAtom(z) }) {
                val now = frame() / 1e6
                val pen = ComposePen(this, 92.0)
                val sh = if (finished != 0) 1.0 else share.value.toDouble()
                nebula.draw(pen, 46.0, 46.0, 42.0, now, sh, still = sh <= 0)
                if (finished != 0) pen.ring(46.0, 46.0, 45.0, Ink.GREEN, 2.0)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Label("$z", size = 13.sp, color = Tone.text2)
                Label(atom.name, size = 16.sp, weight = FontWeight.SemiBold)
            }
            val need = periodOf(z)
            if (finished != 0) Label("Built", size = 13.sp, color = Tone.green)
            else Label("Shells: ${c.shells} of $need", size = 13.sp, color = Tone.text2)
            if (run != null && finished == 0) {
                Pill("Build", Icon.PLAY, onBuild, Modifier.padding(top = 8.dp).fillMaxWidth(0.8f), fill = Tone.rose, ink = Color.Black)
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GameIcon(run.game.id, 20)
                    Label(run.game.label, size = 14.sp)
                    Canvas(Modifier.size(width = 30.dp, height = 8.dp)) {
                        ComposePen(this, 30.0, wide = true).pips(15.0, 4.0, 28.0, run.dial)
                    }
                }
            }
        }
    }
}

/** One atom's Build page: a list the crown scrolls, its shells as a ring round the bezel (the
 *  built ones lit), and back at the middle of the left edge. */
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
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ScalingLazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            // Wide sides, clear of the ring and of back.
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Canvas(Modifier.size(112.dp)) {
                    val share = c.share(z)
                    nebula.draw(ComposePen(this, 112.0), 56.0, 56.0, 52.0, frame() / 1e6, share, still = share <= 0)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IdPlate(atom, 56)
                    Label(atom.name, size = 18.sp, weight = FontWeight.SemiBold)
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
            for (i in counts.indices) item {
                val shellState = when {
                    st == Standing.BUILT || (st == Standing.BUILDING && i < c.shells) -> Standing.BUILT
                    st == Standing.BUILDING && i == c.shells -> Standing.BUILDING
                    else -> Standing.LATER
                }
                ShellStep(model, z, i, counts[i], shellState, onBuild)
            }
            item {
                Para("Each shell is one game: clear its levels to build it. The outer shells are harder.")
            }
            item { Neighbors(model, z, onAtom) }
            item { Pill("View in Atlas", Icon.ATLAS, { onAtlas(z) }) }
        }
        Canvas(Modifier.fillMaxSize()) { ComposePen(this).shellRing(counts.size, done, st == Standing.BUILT) }
        Box(
            Modifier.align(Alignment.CenterStart).size(width = 34.dp, height = 56.dp).clickable(onClick = onBack),
            contentAlignment = Alignment.CenterEnd,
        ) {
            IconMark(Icon.BACK, 26, Ink.WHITE, disc = Ink.K800)
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
        Modifier.clickable(onClick = onClick).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (back) IconMark(Icon.BACK, 14, Ink.K300)
        IdPlate(atom, 42)
        if (!back) IconMark(Icon.CHEVRON, 14, Ink.K300)
    }
}

/** One shell on the Build page's track: its letter and its electrons; built (a green check), next
 *  (a rose Play, its game and difficulty) or later (its number and difficulty, dim). */
@Composable
private fun ShellStep(model: AppModel, z: Int, shell: Int, electrons: Int, state: Standing, onBuild: () -> Unit) {
    val dial = Difficulty.buildDial(z, shell)
    val run = if (state == Standing.BUILDING) model.buildRun() else null
    val dim = state == Standing.LATER
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tone.plate, RoundedCornerShape(26.dp))
            .then(if (state == Standing.BUILDING) Modifier.border(1.5.dp, Tone.rose, RoundedCornerShape(26.dp)).clickable(onClick = onBuild) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(30.dp)) {
                val pen = ComposePen(this, 30.0)
                when (state) {
                    Standing.BUILT -> { pen.dot(15.0, 15.0, 15.0, Ink.GREEN); pen.icon(Icon.CHECK, 15.0, 15.0, 18.0, Ink.BLACK) }
                    Standing.BUILDING -> { pen.dot(15.0, 15.0, 15.0, Ink.ROSE); pen.icon(Icon.PLAY, 15.0, 15.0, 17.0, Ink.BLACK) }
                    Standing.LATER -> pen.dot(15.0, 15.0, 15.0, Ink.K800)
                }
            }
            if (dim) Label("${shell + 1}", size = 14.sp, color = Tone.text2)
        }
        Column(Modifier.weight(1f)) {
            Label("Shell ${SHELL_LETTERS[shell]}", size = 14.sp, align = TextAlign.Start, lines = 1, color = if (dim) Tone.text2 else Tone.text)
            Label("$electrons electron${if (electrons == 1) "" else "s"}", size = 13.sp, align = TextAlign.Start, color = Tone.text2)
            if (state != Standing.BUILT) {
                if (run != null) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    GameIcon(run.game.id, 16)
                    Label(run.game.label, size = 13.sp, lines = 1)
                }
                Canvas(Modifier.padding(top = 4.dp).size(width = 28.dp, height = 8.dp)) { ComposePen(this, 28.0, wide = true).pips(14.0, 4.0, 26.0, dial) }
            }
        }
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
