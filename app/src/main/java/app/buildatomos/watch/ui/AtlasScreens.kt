// The Atlas: every atom, two ways. The wheel shows one atom large, with the whole table as ticks
// round the rim (built ones in their own color); the crown steps through them. The table shows
// all 118 in their standard places, the crown moving a highlight. Tapping the atom opens its
// page: what it is, its shells, its numbers and properties, its isotopes, who found it, and its
// facts and stories.

package app.buildatomos.watch.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildatomos.core.ATOMS
import app.buildatomos.core.Ink
import app.buildatomos.core.Nebula
import app.buildatomos.core.Pt
import app.buildatomos.core.SCREEN
import app.buildatomos.core.TAU
import app.buildatomos.core.shellCounts
import app.buildatomos.watch.AppModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Rotary pixels per atom when turning through the Atlas. */
private const val STEP_PX = 40f
private const val MID = SCREEN / 2

/** Where atom z sits in the table: column 1..18 and row 1..7, the f-block on rows 9 and 10. */
private fun place(z: Int, group: Int?, period: Int): Pair<Int, Int> = when (z) {
    in 57..71 -> (3 + z - 57) to 9
    in 89..103 -> (3 + z - 89) to 10
    else -> (group ?: 3) to period
}

@Composable
fun AtlasScreen(model: AppModel, start: Int, onAtom: (Int) -> Unit) {
    model.version
    val c = model.collection
    var z by remember { mutableIntStateOf(start.coerceIn(1, ATOMS)) }
    var table by remember { mutableStateOf(false) }
    var spun by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableLongStateOf(0L) }
    val fr = remember { FocusRequester() }
    val measurer = rememberTextMeasurer()
    LaunchedEffect(Unit) { fr.requestFocus(); while (true) withFrameNanos { frame = it } }
    val atom = model.atlas.atom(z)
    val nebula = remember(z) { Nebula(atom, model.atlas.cloud(z)) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun court(o: Offset): Pt {
        val side = min(size.width, size.height).toFloat()
        val k = side / SCREEN
        return Pt(((o.x - (size.width - side) / 2) / k).toDouble(), ((o.y - (size.height - side) / 2) / k).toDouble())
    }
    // The table's cells, in court units.
    val cell = 11.6
    val left = MID - cell * 9
    val top = 112.0
    fun cellAt(col: Int, row: Int) = Pt(left + (col - 0.5) * cell, top + (row - 0.5) * cell + if (row >= 9) -cell * 0.6 else 0.0)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { e ->
                spun += e.verticalScrollPixels
                val steps = (spun / STEP_PX).toInt()
                if (steps != 0) {
                    spun -= steps * STEP_PX
                    val to = (z + steps).coerceIn(1, ATOMS)
                    if (to != z) { z = to; model.feel.buzz(15) }
                }
                true
            }
            .focusRequester(fr)
            .focusable()
            .pointerInput(table) {
                size = this.size
                detectTapGestures { o ->
                    val p = court(o)
                    if (hypot(p.x - MID, p.y - 262) < 20) { table = !table; return@detectTapGestures }
                    if (!table) {
                        if (hypot(p.x - MID, p.y - 118) < 80 || abs(p.y - 214) < 16) onAtom(z)
                        return@detectTapGestures
                    }
                    // In the table: a tap on the highlighted atom (or its name) opens it; on another,
                    // moves the highlight there.
                    if (abs(p.y - 70) < 18) { onAtom(z); return@detectTapGestures }
                    var hit = 0
                    for (a in model.atlas.atoms) {
                        val (col, row) = place(a.z, a.group, a.period)
                        val q = cellAt(col, row)
                        if (abs(p.x - q.x) <= cell / 2 + 1 && abs(p.y - q.y) <= cell / 2 + 1) hit = a.z
                    }
                    if (hit == z) onAtom(z) else if (hit != 0) { z = hit; model.feel.buzz(15) }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame
            val pen = ComposePen(this)
            if (!table) {
                for (i in 1..ATOMS) {
                    val a = -PI / 2 + (i - 0.5) / ATOMS * TAU
                    val here = i == z
                    val r0 = if (here) 132.0 else 139.0
                    val color = if (here) Ink.WHITE else if (c.isBuilt(i)) model.atlas.atom(i).color else Ink.K800
                    pen.line(MID + cos(a) * r0, MID + sin(a) * r0, MID + cos(a) * 145, MID + sin(a) * 145, color, if (here) 3.5 else 2.0)
                }
                val built = c.isBuilt(z)
                val share = if (built) 1.0 else if (z == c.building) c.progress else 0.0
                nebula.draw(pen, MID, 118.0, 76.0, frame / 1e6, share, still = share <= 0)
                pen.text(measurer, "${atom.z}  ${atom.name}", MID, 214.0, 17.0, if (built) Ink.WHITE else Ink.K300, FontWeight.SemiBold)
                pen.text(measurer, model.atlas.info(z).kind.replaceFirstChar { it.uppercase() }, MID, 236.0, 13.0, Ink.K300)
            } else {
                pen.text(measurer, "${atom.z}  ${atom.name}", MID, 70.0, 17.0, if (c.isBuilt(z)) Ink.WHITE else Ink.K300, FontWeight.SemiBold)
                pen.text(measurer, "${c.built.size} of $ATOMS built", MID, 92.0, 13.0, Ink.K300)
                for (a in model.atlas.atoms) {
                    val (col, row) = place(a.z, a.group, a.period)
                    val q = cellAt(col, row)
                    val color = if (c.isBuilt(a.z)) a.color else Ink.K800
                    pen.rect(q.x - cell / 2 + 0.8, q.y - cell / 2 + 0.8, cell - 1.6, cell - 1.6, 2.0, color)
                    if (a.z == z) pen.rectRing(q.x - cell / 2 - 0.4, q.y - cell / 2 - 0.4, cell + 0.8, cell + 0.8, 2.6, Ink.WHITE, 1.6)
                }
            }
            // The switch between the wheel and the table: a small table mark, or a ring.
            pen.dot(MID, 262.0, 15.0, Ink.K900)
            if (table) pen.ring(MID, 262.0, 7.0, Ink.WHITE, 2.0)
            else for (i in 0..5) pen.rect(MID - 8 + (i % 3) * 6, 257.0 + (i / 3) * 6, 4.0, 4.0, 1.0, Ink.WHITE)
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val NUM = NumberFormat.getNumberInstance(Locale.US)
private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"

private fun celsius(k: Double) = "${NUM.format((k - 273.15).roundToInt())} °C"
private fun sup(n: String) = n.map { SUP[it - '0'] }.joinToString("")
/** "1s2" → "1s²". */
private fun config(subs: List<String>) = subs.joinToString(" ") { s ->
    val head = s.takeWhile { !it.isLetter() } + s.first { it.isLetter() }
    head + sup(s.substring(head.length))
}

/** One atom's page. */
@Composable
fun AtomScreen(model: AppModel, z: Int, onAtom: (Int) -> Unit) {
    model.version
    val atom = model.atlas.atom(z)
    val info = model.atlas.info(z)
    val subs = model.atlas.subs(z)
    val nebula = remember(z) { Nebula(atom, model.atlas.cloud(z)) }
    val c = model.collection
    val builtOn = c.built[z]
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }
    val color = Color(atom.color)
    WatchList {
        item {
            Canvas(Modifier.size(100.dp)) {
                frame
                val share = if (builtOn != null) 1.0 else if (z == c.building) c.progress else 0.0
                nebula.draw(ComposePen(this, 100.0), 50.0, 50.0, 46.0, frame / 1e6, share, still = share <= 0)
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("${atom.z}", size = 14.sp, color = Tone.text2)
                    Label(atom.name, size = 19.sp, weight = FontWeight.SemiBold)
                    Label(atom.symbol, size = 14.sp, color = color)
                }
                Label(info.kind.replaceFirstChar { it.uppercase() }, size = 13.sp, color = color)
                when {
                    builtOn != null -> Label("Built: ${LocalDate.ofEpochDay(builtOn).format(DAY)}", size = 13.sp, color = Tone.green)
                    z == c.building -> Label("Building: ${c.shells} of ${c.need} shells", size = 13.sp, color = Tone.cyan)
                    else -> Label("Not built yet", size = 13.sp, color = Tone.text2)
                }
            }
        }
        if (info.headline.isNotBlank()) item { Para(info.headline) }
        item {
            Section("Structure") {
                Canvas(Modifier.size(96.dp).align(Alignment.CenterHorizontally)) { shells(ComposePen(this, 96.0), shellCounts(z, subs), atom.color) }
                Row1("Shells", shellCounts(z, subs).joinToString(", "))
                Row1("Configuration", config(subs))
            }
        }
        item {
            Section("Numbers") {
                Row1("Protons", "${atom.z}")
                if (info.massNumber > 0) Row1("Neutrons", "${info.massNumber - atom.z}", "in its commonest isotope")
                Row1("Electrons", "${atom.z}")
                if (info.mass > 0) Row1("Atomic mass", info.mass.toString())
                Row1("Period", "${atom.period}")
                atom.group?.let { if (z !in 57..71 && z !in 89..103) Row1("Group", "$it") }
                Row1("Block", atom.block)
            }
        }
        item {
            Section("Properties") {
                Row1("At room temperature", info.phase ?: "Unknown")
                info.melt?.let { Row1("Melts", celsius(it)) }
                info.boil?.let { Row1("Boils", celsius(it)) }
                info.density?.let { Row1("Density", "${trim(it)} g/cm³") }
                info.electronegativity?.let { Row1("Electronegativity", trim(it)) }
            }
        }
        item {
            Section("Isotopes") {
                Row1("Stable", if (info.stableIsotopes == 0) "None" else "${info.stableIsotopes}")
                if (info.knownIsotopes > 0) Row1("Known", "${info.knownIsotopes}")
            }
        }
        item {
            Section("Discovery") {
                Row1("Found", info.year?.toString() ?: if (info.who?.contains("ancient", true) == true) "In ancient times" else "Unknown")
                info.who?.takeIf { !it.contains("ancient", true) }?.let { Row1("By", it) }
                info.where?.let { Row1("Where", it) }
                info.origin?.let { Row1("Name", it) }
            }
        }
        if (info.facts.isNotEmpty()) item { Section("Facts") { for (f in info.facts) Para(f, inside = true) } }
        if (info.origins.isNotEmpty()) item { Section("Where it comes from") { for (f in info.origins.take(2)) Para(f, inside = true) } }
        if (info.uses.isNotEmpty()) item { Section("Uses") { for (f in info.uses.take(3)) Para(f, inside = true) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (z > 1) Step("${z - 1} ${model.atlas.atom(z - 1).symbol}", Modifier.weight(1f)) { onAtom(z - 1) }
                if (z < ATOMS) Step("${z + 1} ${model.atlas.atom(z + 1).symbol}", Modifier.weight(1f)) { onAtom(z + 1) }
            }
        }
    }
}

private fun trim(v: Double): String = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else v.toString()

/** The atom's shells: rings round a nucleus, each with its electrons evenly round it. */
private fun shells(pen: ComposePen, counts: IntArray, color: Int) {
    val n = counts.size
    pen.dot(48.0, 48.0, 5.0, color)
    for (i in 0 until n) {
        val r = 10.0 + (i + 1) * 36.0 / n
        pen.ring(48.0, 48.0, r, Ink.K700, 1.0)
        val e = counts[i]
        for (j in 0 until e) {
            val a = -PI / 2 + j * TAU / e + i * 0.3
            pen.dot(48 + cos(a) * r, 48 + sin(a) * r, if (e > 18) 1.3 else 1.8, Ink.WHITE)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Tone.plate, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Label(title, size = 13.sp, color = Tone.text2, align = TextAlign.Start)
        content()
    }
}

/** A labeled value: "Melts: 3,527 °C". */
@Composable
private fun Row1(label: String, value: String, note: String? = null) {
    Column {
        Label("$label: $value", size = 14.sp, weight = FontWeight.Normal, align = TextAlign.Start)
        if (note != null) Label(note, size = 13.sp, color = Tone.text2, align = TextAlign.Start)
    }
}

@Composable
private fun Para(text: String, inside: Boolean = false) {
    Label(text, Modifier.fillMaxWidth().padding(horizontal = if (inside) 0.dp else 6.dp), size = 14.sp, weight = FontWeight.Normal, align = if (inside) TextAlign.Start else TextAlign.Center)
}

@Composable
private fun Step(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.background(Tone.plate, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Label(label, size = 14.sp)
    }
}
