// The Atlas: every atom, two ways. The wheel shows one atom large, with the whole table as ticks
// round the rim (built ones in their own color); the crown steps through them. The table shows
// all 118 in their standard places, the crown moving a highlight. Tapping the atom opens its
// Atlas page, a watch-sized take on the phone's: its plate and where it sits, Build, the atom
// itself, the headline and facts, its name and discovery, its structure, properties and
// isotopes, and where it comes from and what it is for.

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildatomos.core.ATOMS
import app.buildatomos.core.Atom
import app.buildatomos.core.Crowd
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.Nebula
import app.buildatomos.core.Property
import app.buildatomos.core.Pt
import app.buildatomos.core.SCREEN
import app.buildatomos.core.TAU
import app.buildatomos.core.icon
import app.buildatomos.core.shellCounts
import app.buildatomos.watch.AppModel
import java.math.BigDecimal
import java.math.MathContext
import java.text.NumberFormat
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
fun AtlasScreen(model: AppModel, start: Int, startTable: Boolean, onAtom: (Int) -> Unit) {
    model.version
    val c = model.collection
    var z by remember { mutableIntStateOf(start.coerceIn(1, ATOMS)) }
    var table by remember { mutableStateOf(startTable) }
    var spun by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableLongStateOf(0L) }
    val fr = rememberCrownFocus()
    val measurer = rememberTextMeasurer()
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }
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
            pen.dot(MID, 262.0, 16.0, Ink.K800)
            pen.icon(if (table) Icon.WHEEL else Icon.TABLE, MID, 262.0, 20.0, Ink.WHITE)
        }
    }
}

private val NUM = NumberFormat.getNumberInstance(Locale.US)
private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"
private val NOBLE = intArrayOf(2, 10, 18, 36, 54, 86)

private fun celsius(k: Double) = "${NUM.format((k - 273.15).roundToInt())} °C"
private fun sup(n: String) = n.map { SUP[it - '0'] }.joinToString("")
/** "2p2" → "2p²". */
private fun subLabel(s: String): String {
    val head = s.takeWhile { !it.isLetter() } + s.first { it.isLetter() }
    return head + sup(s.substring(head.length))
}
private fun blockColor(s: String) = when (s.firstOrNull { it.isLetter() }) { 's' -> Ink.S; 'p' -> Ink.P; 'd' -> Ink.D; else -> Ink.F }
private fun ppm(v: Double): String = when {
    v >= 10_000 -> "${trim(round1(v / 10_000))}%"
    v >= 1 -> "${NUM.format(v.roundToInt())} ppm"
    v >= 0.001 -> "${trim(round3(v * 1000))} ppb"
    else -> "Traces"
}
private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
private fun round3(v: Double) = if (v >= 100) v.roundToInt().toDouble() else (v * 10).roundToInt() / 10.0

/** One atom's Atlas page. */
@Composable
fun AtomScreen(model: AppModel, z: Int, onBuild: (Int) -> Unit, onTable: (Int) -> Unit, onAtom: (Int) -> Unit) {
    model.version
    val atom = model.atlas.atom(z)
    val info = model.atlas.info(z)
    val subs = model.atlas.subs(z)
    val counts = shellCounts(z, subs)
    val nebula = remember(z) { Nebula(atom, model.atlas.cloud(z)) }
    val c = model.collection
    val frame = rememberFrame()
    val color = Color(atom.color)
    // Its noble-gas core, and the subshells past it: "[He] 2s² 2p²".
    val core = NOBLE.lastOrNull { it < z }
    val coreSubs = core?.let { model.atlas.subs(it).toSet() } ?: emptySet()
    val outer = subs.filter { it !in coreSubs }
    WatchList(start = 1) {
        item { IdPlate(atom, 60) }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label(atom.name, size = 20.sp, weight = FontWeight.SemiBold)
                Label(info.series.ifBlank { info.kind }, size = 14.sp, color = color)
                val where = listOfNotNull(
                    if (z in 57..71) "Lanthanide" else if (z in 89..103) "Actinide" else atom.group?.let { "Group $it" },
                    "Period ${atom.period}",
                ).joinToString(" · ")
                Label(where, size = 13.sp, color = Tone.text2)
            }
        }
        item {
            when {
                c.isBuilt(z) -> Pill("Built", Icon.CHECK, { onBuild(z) }, ink = Tone.green)
                z == c.building -> Pill("Build", Icon.PLAY, { onBuild(z) }, fill = Tone.rose, ink = Color.Black)
                else -> Pill("Build", Icon.LOCK, { onBuild(z) }, ink = Tone.text2)
            }
        }
        item {
            Canvas(Modifier.size(110.dp)) {
                val share = model.share(z)
                // The Atlas shows every atom in full; a built one turns.
                nebula.draw(ComposePen(this, 110.0), 55.0, 55.0, 50.0, frame() / 1e6, 1.0, still = share < 1)
            }
        }
        item {
            Tiles(
                "Weight" to (if (info.mass > 0) trim(info.mass) else "[${info.massNumber}]"),
                "Block" to atom.block,
                "At room temperature" to (info.phase ?: "Unknown"),
                "Electrons" to "$z",
            )
        }
        item { Locator(model, z) { onTable(z) } }
        if (info.headline.isNotBlank()) item { Para(info.headline) }
        if (info.facts.isNotEmpty()) item { Section(model, "Facts", "${info.facts.size}") { for (f in info.facts) Bullet(f) } }
        info.origin?.let { o -> item { Section(model, "The name", null) { Para(o, inside = true) } } }
        item {
            Section(model, "Discovered", info.year?.toString() ?: "Ancient") {
                val ancient = info.year == null || info.who?.contains("ancient", true) == true
                Label(info.year?.toString() ?: "In antiquity", size = 22.sp, weight = FontWeight.SemiBold, color = color, align = TextAlign.Start)
                val by = listOfNotNull(info.who?.takeIf { !(ancient && it.contains("ancient", true)) }, info.where).joinToString(" · ")
                if (by.isNotBlank()) Para(by, inside = true)
            }
        }
        item {
            Section(model, "Structure", "${counts.size} shell${if (counts.size == 1) "" else "s"}") {
                Canvas(Modifier.size(104.dp).align(Alignment.CenterHorizontally)) { shells(ComposePen(this, 96.0), counts, atom.color) }
                // The shells as K 2, L 8, M 4.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    for ((i, n) in counts.withIndex()) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(Tone.chip, RoundedCornerShape(8.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Label("$n", size = 14.sp, weight = FontWeight.SemiBold)
                        Label("KLMNOPQ"[i].toString(), size = 13.sp, color = Tone.text2)
                    }
                }
                // The configuration as colored pills: the noble-gas core, then the outer subshells.
                FlowPills(listOfNotNull(core?.let { "[${model.atlas.atom(it).symbol}]" to Ink.K700 }) + outer.map { subLabel(it) to blockColor(it) })
                if (z >= 104) Label("Predicted, not measured: only a few atoms have ever been made.", size = 13.sp, color = Tone.text2, align = TextAlign.Start)
            }
        }
        item {
            Section(model, "Properties", info.phase) {
                val crowds = remember(z) { Property.ALL.map { Crowd(it, model.atlas.infos, z) }.filter { it.value != null } }
                if (crowds.isEmpty()) Label("Not measured yet.", size = 14.sp, color = Tone.text2, align = TextAlign.Start)
                else {
                    Label("Each dot is an atom, in its block's color. ${atom.name} is the white one.", size = 13.sp, color = Tone.text2, align = TextAlign.Start)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (crowd in crowds) PropertyStrip(model, crowd)
                    }
                }
            }
        }
        item {
            Section(model, "Isotopes", "${info.stableIsotopes} lasting") {
                KeyValue("Lasting", if (info.stableIsotopes == 0) "None" else "${info.stableIsotopes}")
                if (info.knownIsotopes > 0) KeyValue("Known", "${info.knownIsotopes}")
                if (info.knownIsotopes > 0) {
                    // Lasting against all known, as a bar.
                    Bar(info.stableIsotopes / info.knownIsotopes.toDouble(), Modifier.padding(top = 2.dp).fillMaxWidth().height(5.dp), Ink.GREEN)
                    Label(
                        if (info.stableIsotopes == 0) "Every one of its isotopes decays."
                        else "${info.stableIsotopes} of ${info.knownIsotopes} never decay.",
                        size = 13.sp, color = Tone.text2, align = TextAlign.Start,
                    )
                }
            }
        }
        if (info.origins.isNotEmpty()) item { Section(model, "Made in", null) { for (f in info.origins.take(3)) Bullet(f) } }
        if (info.uses.isNotEmpty()) item { Section(model, "Used for", null) { for (f in info.uses.take(3)) Bullet(f) } }
        // The atoms either side, as their plates, each behind a chevron pointing its way.
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (z > 1) Neighbor(model.atlas.atom(z - 1), before = true) { onAtom(z - 1) }
                if (z < ATOMS) Neighbor(model.atlas.atom(z + 1), before = false) { onAtom(z + 1) }
            }
        }
    }
}

/** A property's value as the page writes it: temperatures in °C, amounts in ppm or ppb. The
 *  strip's ends leave off the density's unit (`short`), to fit both on one row. */
private fun show(p: Property, v: Double, short: Boolean = false): String = when (p) {
    Property.MELT, Property.BOIL -> celsius(v)
    Property.DENSITY -> if (short) sig3(v) else "${sig3(v)} g/cm³"
    Property.CRUST, Property.SEA -> ppm(v)
    else -> trim(v)
}

/** Three significant figures, written out in full: 0.0000899, 22.6, 7870. */
private fun sig3(v: Double): String = BigDecimal(v).round(MathContext(3)).stripTrailingZeros().toPlainString()

/** The blocks' lanes in a property's strip, top to bottom. */
private const val LANES = "spdf"

/** One property, as on the phone: its name and this atom's value, its rank in words, then every
 *  measured atom as a dot along the scale in its block's lane and color, this one larger and
 *  white with a guide through the lanes, and the atoms at the two ends named under it. */
@Composable
private fun PropertyStrip(model: AppModel, crowd: Crowd) {
    val p = crowd.property
    val v = crowd.value ?: return
    val me = model.atlas.atom(crowd.z)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KeyValue(p.label, show(p, v))
        crowd.rank?.let { Label(it.replaceFirstChar { c -> c.uppercase() }, size = 13.sp, align = TextAlign.Start) }
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val w = size.width.toDouble() / density
            val h = size.height.toDouble() / density
            val pen = ComposePen(this, w, wide = true)
            val pad = 5.0
            val lane = (h - 4) / LANES.length
            fun x(at: Double) = pad + at * (w - pad * 2)
            fun y(block: String) = 2 + lane * (LANES.indexOf(block.firstOrNull() ?: 'f').coerceAtLeast(0) + 0.5)
            for ((oz, ov) in crowd.all) if (oz != crowd.z) {
                val a = model.atlas.atom(oz)
                pen.dot(x(crowd.place(ov)), y(a.block), 1.8, blockColor(a.block))
            }
            val mx = x(crowd.place(v))
            pen.line(mx, 1.0, mx, h - 1, Ink.K500, 2.0)
            pen.dot(mx, y(me.block), 4.4, Ink.WHITE)
        }
        Row(Modifier.fillMaxWidth()) {
            val (lz, lv) = crowd.lowest
            val (hz, hv) = crowd.highest
            Label("${model.atlas.atom(lz).symbol} ${show(p, lv, short = true)}", Modifier.weight(1f), size = 13.sp, color = Tone.text2, align = TextAlign.Start, lines = 1)
            Label("${model.atlas.atom(hz).symbol} ${show(p, hv, short = true)}", size = 13.sp, color = Tone.text2, align = TextAlign.End, lines = 1)
        }
    }
}

/** A neighbor on the table: its plate, with a chevron on the side it lies. */
@Composable
private fun Neighbor(atom: Atom, before: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (before) IconMark(Icon.BACK, 18, Ink.K300)
        IdPlate(atom, 52)
        if (!before) IconMark(Icon.CHEVRON, 18, Ink.K300)
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

/** Four facts at a glance, two by two. */
@Composable
private fun Tiles(vararg kv: Pair<String, String>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in kv.toList().chunked(2)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((k, v) in row) Column(
                Modifier.weight(1f).background(Tone.plate, RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label(v, size = 15.sp, weight = FontWeight.SemiBold, lines = 1)
                Label(k, size = 13.sp, color = Tone.text2, lines = 2)
            }
        }
    }
}

/** The little table with this atom lit and its period picked out; tapping it opens the Atlas's
 *  table there. */
@Composable
private fun Locator(model: AppModel, z: Int, onClick: () -> Unit) {
    val me = model.atlas.atom(z)
    Canvas(Modifier.fillMaxWidth(0.86f).aspectRatio(18f / 9.5f).clickable(onClick = onClick)) {
        val pen = ComposePen(this, 18.0, wide = true)
        for (a in model.atlas.atoms) {
            val (col, row) = place(a.z, a.group, a.period)
            val y = if (row >= 9) row - 1.5 else row - 1.0
            val lit = a.period == me.period && row < 9
            pen.rect(col - 1 + 0.08, y + 0.08, 0.84, 0.84, 0.16, if (lit) Ink.K500 else Ink.K700)
        }
        val (col, row) = place(z, me.group, me.period)
        val y = if (row >= 9) row - 1.5 else row - 1.0
        pen.rect(col - 1 - 0.15, y - 0.15, 1.3, 1.3, 0.3, Ink.WHITE)
        pen.rect(col - 1 + 0.02, y + 0.02, 0.96, 0.96, 0.2, me.color)
    }
}

/** Pills in rows, wrapping, three to a row. */
@Composable
private fun FlowPills(pills: List<Pair<String, Int>>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in pills.chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
            for ((label, fill) in row) Box(Modifier.background(Color(fill), RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                Label(label, size = 14.sp, weight = FontWeight.SemiBold, color = inkOn(fill), lines = 1)
            }
        }
    }
}

/** A section of the page that folds: its name (and a word of what is inside) on a row with a
 *  chevron; tapped, it opens or closes. Which sections are open is kept while stepping from atom
 *  to atom. */
@Composable
private fun Section(model: AppModel, title: String, summary: String?, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val open = title in model.atlasOpen
    Column(
        Modifier.fillMaxWidth().background(Tone.plate, RoundedCornerShape(20.dp)),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { model.atlasOpen = if (open) model.atlasOpen - title else model.atlasOpen + title }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Label(title, Modifier.weight(1f), size = 15.sp, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 1)
            if (!open && summary != null) Label(summary, size = 13.sp, color = Tone.text2, lines = 1)
            Canvas(Modifier.size(14.dp)) {
                val pen = ComposePen(this, 14.0)
                // Down when closed, up when open.
                val y0 = if (open) 9.0 else 5.0
                val y1 = if (open) 5.0 else 9.0
                pen.poly(doubleArrayOf(3.0, 7.0, 11.0), doubleArrayOf(y0, y1, y0), 3, Ink.K300, 1.8)
            }
        }
        if (open) Column(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            content = content,
        )
    }
}

/** A label and its value on one row: "Melts" on the left, "3,527 °C" on the right. */
@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, Modifier.weight(1f), size = 14.sp, weight = FontWeight.Normal, align = TextAlign.Start, color = Tone.text2)
        Label(value, size = 14.sp, weight = FontWeight.SemiBold, align = TextAlign.End)
    }
}

/** A line in a list, with a small dot in front. */
@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.padding(top = 7.dp).size(5.dp)) { ComposePen(this, 5.0).dot(2.5, 2.5, 2.5, Ink.K400) }
        Label(text, size = 14.sp, weight = FontWeight.Normal, align = TextAlign.Start)
    }
}
