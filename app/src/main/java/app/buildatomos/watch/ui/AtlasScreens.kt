// The Atlas: every atom, two ways, switched with a bare icon at the foot. The wheel: one atom
// named, its symbol large in its color, its number and name, its neighbors' symbols either side,
// and the atoms round the rim as dots, the crown turning them. The table: all 118 in their
// standard places, the crown moving a highlight. Tapping the atom opens its Atlas page, a list
// with no tiles, section headers or fold boxes: its symbol and name, where it sits, Build, the
// atom itself, the headline, its properties as sliders, the facts, its structure, discovery,
// name and isotopes, where it comes from and what it is for, and the atoms either side.

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import app.buildatomos.core.ATOMS
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
fun tableSpot(z: Int, group: Int?, period: Int): Pair<Int, Int> = when (z) {
    in 57..71 -> (3 + z - 57) to 9
    in 89..103 -> (3 + z - 89) to 10
    else -> (group ?: 3) to period
}

/** The Atlas, as the wheel or the table, starting at atom `start`; the icon at the foot switches
 *  between them, keeping the atom. */
@Composable
fun AtlasScreen(model: AppModel, start: Int, startTable: Boolean, onAtom: (Int) -> Unit) {
    model.version
    var z by remember { mutableIntStateOf(start.coerceIn(1, ATOMS)) }
    var table by remember { mutableStateOf(startTable) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AtlasTurner(model, z, { z = it }, onAtom, table)
        BareIcon(
            if (table) Icon.WHEEL else Icon.TABLE, 20.dp, { table = !table },
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), color = Ink.K300,
        )
    }
}

/** The wheel or the table, the crown stepping the atom. On the wheel a tap on the middle opens
 *  the atom and a tap on a neighbor turns to it; on the table a tap on an atom picks it, and a
 *  tap on the picked one (or its name) opens it. */
@Composable
private fun AtlasTurner(model: AppModel, z: Int, onPick: (Int) -> Unit, onAtom: (Int) -> Unit, table: Boolean) {
    val c = model.collection
    val at by rememberUpdatedState(z)
    val pick by rememberUpdatedState(onPick)
    val open by rememberUpdatedState(onAtom)
    val isTable by rememberUpdatedState(table)
    val pos by animateFloatAsState(z.toFloat(), tween(220), label = "wheel")
    var spun by remember { mutableFloatStateOf(0f) }
    val fr = rememberCrownFocus()
    val measurer = rememberTextMeasurer()
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun court(o: Offset): Pt {
        val side = min(size.width, size.height).toFloat()
        val k = side / SCREEN
        return Pt(((o.x - (size.width - side) / 2) / k).toDouble(), ((o.y - (size.height - side) / 2) / k).toDouble())
    }
    fun move(to: Int) {
        val t = to.coerceIn(1, ATOMS)
        if (t != at) { pick(t); model.feel.buzz(15) }
    }
    // The table's cells, in court units.
    val cell = 11.6
    val left = MID - cell * 9
    val top = 112.0
    fun cellAt(col: Int, row: Int) = Pt(left + (col - 0.5) * cell, top + (row - 0.5) * cell + if (row >= 9) -cell * 0.6 else 0.0)

    Box(
        Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { e ->
                spun += e.verticalScrollPixels
                val steps = (spun / STEP_PX).toInt()
                if (steps != 0) {
                    spun -= steps * STEP_PX
                    // The wheel's rim turns the same way as the crown; the table steps on like a list.
                    move(if (isTable) at + steps else at - steps)
                }
                true
            }
            .focusRequester(fr)
            .focusable()
            .pointerInput(Unit) {
                size = this.size
                detectTapGestures { o ->
                    val p = court(o)
                    if (!isTable) {
                        when {
                            hypot(p.x - 64, p.y - 150) < 32 -> move(at - 1)
                            hypot(p.x - 236, p.y - 150) < 32 -> move(at + 1)
                            abs(p.x - MID) < 70 && abs(p.y - 150) < 80 -> open(at)
                        }
                        return@detectTapGestures
                    }
                    if (abs(p.y - 70) < 18) { open(at); return@detectTapGestures }
                    var hit = 0
                    for (a in model.atlas.atoms) {
                        val (col, row) = tableSpot(a.z, a.group, a.period)
                        val q = cellAt(col, row)
                        if (abs(p.x - q.x) <= cell / 2 + 1 && abs(p.y - q.y) <= cell / 2 + 1) hit = a.z
                    }
                    if (hit == at) open(at) else if (hit != 0) move(hit)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pen = ComposePen(this)
            val atom = model.atlas.atom(z)
            if (!table) {
                // The rim: the atoms near this one as small dots in their colors (smaller still
                // until built), turning with the crown, this one large at the top.
                val step = 8.5 * PI / 180
                val r = MID - 12
                for (n in (pos - 18).toInt().coerceAtLeast(1)..(pos + 18).toInt().coerceAtMost(ATOMS)) {
                    val d = n - pos
                    if (abs(d) > 17.6) continue
                    val a = -PI / 2 + d * step
                    val x = MID + cos(a) * r
                    val y = MID + sin(a) * r
                    if (n == z) pen.dot(x, y, 4.6, atom.color)
                    else pen.dot(x, y, if (c.isBuilt(n)) 2.4 else 1.6, model.atlas.atom(n).color)
                }
                pen.text(measurer, "$z", MID, 92.0, 17.0, Ink.K200)
                pen.text(measurer, atom.symbol, MID, 146.0, 80.0, atom.color, FontWeight.SemiBold)
                pen.text(measurer, atom.name, MID, 204.0, 22.0, Ink.WHITE)
                if (z > 1) pen.text(measurer, model.atlas.atom(z - 1).symbol, 64.0, 150.0, 19.0, Ink.K400)
                if (z < ATOMS) pen.text(measurer, model.atlas.atom(z + 1).symbol, 236.0, 150.0, 19.0, Ink.K400)
            } else {
                val sw = pen.textWidth(measurer, atom.symbol, 19.0, FontWeight.SemiBold)
                val nw = pen.textWidth(measurer, atom.name, 17.0)
                val x0 = MID - (sw + 7 + nw) / 2
                pen.text(measurer, atom.symbol, x0 + sw / 2, 70.0, 19.0, atom.color, FontWeight.SemiBold)
                pen.text(measurer, atom.name, x0 + sw + 7 + nw / 2, 70.0, 17.0, Ink.WHITE)
                for (a in model.atlas.atoms) {
                    val (col, row) = tableSpot(a.z, a.group, a.period)
                    val q = cellAt(col, row)
                    val color = if (c.isBuilt(a.z)) a.color else Ink.K800
                    pen.rect(q.x - cell / 2 + 0.8, q.y - cell / 2 + 0.8, cell - 1.6, cell - 1.6, 2.0, color)
                    if (a.z == z) pen.rectRing(q.x - cell / 2 - 0.4, q.y - cell / 2 - 0.4, cell + 0.8, cell + 0.8, 2.6, Ink.WHITE, 1.6)
                }
            }
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
private fun ppm(v: Double): String = when {
    v >= 10_000 -> "${trim(round1(v / 10_000))}%"
    v >= 1 -> "${NUM.format(v.roundToInt())} ppm"
    v >= 0.001 -> "${trim(round3(v * 1000))} ppb"
    else -> "Traces"
}
private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
private fun round3(v: Double) = if (v >= 100) v.roundToInt().toDouble() else (v * 10).roundToInt() / 10.0

/** One atom's Atlas page: see the top of this file. */
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
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        QuietList(state, side = 22.dp, top = 22.dp, gap = 10.dp) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(atom.symbol, size = 22.sp, color = color, weight = FontWeight.SemiBold, lines = 1)
                    Label(atom.name, size = 20.sp, lines = 1)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Label(info.series.ifBlank { info.kind }.replaceFirstChar { it.uppercase() }, size = 14.sp, color = color)
                    val where = listOfNotNull(
                        "$z",
                        if (z in 57..71) "Lanthanide" else if (z in 89..103) "Actinide" else atom.group?.let { "Group $it" },
                        "Period ${atom.period}",
                    ).joinToString(" · ")
                    Label(where, size = 13.sp, color = Tone.text3, weight = FontWeight.Normal)
                }
            }
            item {
                val (icon, ink, word) = when {
                    c.isBuilt(z) -> Triple(Icon.CHECK, Ink.GREEN, "Built")
                    z == c.building -> Triple(Icon.PLAY, Ink.ROSE, "Build")
                    else -> Triple(Icon.LOCK, Ink.K400, "Build")
                }
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { onBuild(z) }.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(Modifier.size(16.dp)) { ComposePen(this, 24.0).icon(icon, 12.0, 12.0, 24.0, ink) }
                    Label(word, size = 15.sp, color = Color(ink), lines = 1)
                }
            }
            item {
                Canvas(Modifier.size(104.dp)) {
                    val share = model.share(z)
                    // The Atlas shows every atom in full; a built one turns.
                    nebula.draw(ComposePen(this, 104.0), 52.0, 52.0, 48.0, frame() / 1e6, 1.0, still = share < 1)
                }
            }
            if (info.headline.isNotBlank()) item { Para(info.headline) }
            item { Locator(model, z) { onTable(z) } }
            val crowds = Property.ALL.map { Crowd(it, model.atlas.infos, z) }.filter { it.value != null }
            for (crowd in crowds) item { Slider(crowd, atom.color) }
            if (info.facts.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) { for (f in info.facts) Bullet(f) }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Canvas(Modifier.size(96.dp)) { shells(ComposePen(this, 96.0), counts, atom.color) }
                    val config = listOfNotNull(core?.let { "[${model.atlas.atom(it).symbol}]" }) + outer.map(::subLabel)
                    Label(config.joinToString(" "), size = 14.sp, lines = 2)
                    Label(counts.joinToString(" · "), size = 13.sp, color = Tone.text3, weight = FontWeight.Normal, lines = 1)
                    if (z >= 104) Label("Predicted, not measured", size = 13.sp, color = Tone.text3, weight = FontWeight.Normal)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val ancient = info.year == null || info.who?.contains("ancient", true) == true
                    Label(info.year?.toString() ?: "In antiquity", size = 18.sp, color = color)
                    val by = listOfNotNull(info.who?.takeIf { !(ancient && it.contains("ancient", true)) }, info.where).joinToString(" · ")
                    if (by.isNotBlank()) Para(by, color = Tone.text2)
                }
            }
            info.origin?.let { o -> item { Para(o, color = Tone.text2) } }
            item {
                val lasting = info.stableIsotopes
                Label(
                    when {
                        info.knownIsotopes == 0 -> if (lasting == 0) "No lasting isotopes" else "$lasting lasting isotopes"
                        lasting == 0 -> "None of its ${info.knownIsotopes} isotopes lasts"
                        else -> "$lasting of ${info.knownIsotopes} isotopes last"
                    },
                    size = 14.sp, color = Tone.text2, weight = FontWeight.Normal,
                )
            }
            if (info.origins.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) { for (f in info.origins.take(3)) Bullet(f) }
            }
            if (info.uses.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) { for (f in info.uses.take(3)) Bullet(f) }
            }
            item { Neighbors(model, z, onAtom) }
        }
        Scroll(state)
    }
}

/** The atoms either side, as a chevron and a symbol each, the way they lie. */
@Composable
private fun Neighbors(model: AppModel, z: Int, onAtom: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (z > 1) Neighbor(model.atlas.atom(z - 1).symbol, before = true) { onAtom(z - 1) }
        Spacer(Modifier.weight(1f))
        if (z < ATOMS) Neighbor(model.atlas.atom(z + 1).symbol, before = false) { onAtom(z + 1) }
    }
}

@Composable
private fun Neighbor(symbol: String, before: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (before) Canvas(Modifier.size(14.dp)) { ComposePen(this, 24.0).icon(Icon.BACK, 12.0, 12.0, 24.0, Ink.K300) }
        Label(symbol, size = 16.sp, color = Tone.text2, lines = 1)
        if (!before) Canvas(Modifier.size(14.dp)) { ComposePen(this, 24.0).icon(Icon.CHEVRON, 12.0, 12.0, 24.0, Ink.K300) }
    }
}

/** A property's value as the page writes it: temperatures in °C, amounts in ppm or ppb. */
private fun show(p: Property, v: Double): String = when (p) {
    Property.MELT, Property.BOIL -> celsius(v)
    Property.DENSITY -> "${sig3(v)} g/cm³"
    Property.CRUST, Property.SEA -> ppm(v)
    else -> trim(v)
}

/** Three significant figures, written out in full: 0.0000899, 22.6, 7870. */
private fun sig3(v: Double): String = BigDecimal(v).round(MathContext(3)).stripTrailingZeros().toPlainString()

/** One property as a slider: its name and this atom's value, then a track from the lowest atom
 *  to the highest with this one as a dot in its color. */
@Composable
private fun Slider(crowd: Crowd, color: Int) {
    val p = crowd.property
    val v = crowd.value ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(p.label, size = 14.sp, color = Tone.text2, weight = FontWeight.Normal, lines = 1)
            Label(show(p, v), size = 14.sp, lines = 1)
        }
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val w = size.width.toDouble() / density
            val pen = ComposePen(this, w, wide = true)
            val r = 5.0
            pen.rect(0.0, 4.0, w, 4.0, 2.0, Ink.K800)
            pen.dot(r + crowd.place(v) * (w - r * 2), 6.0, r, color)
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

/** The little table with this atom lit and its period picked out; tapping it opens the Atlas's
 *  table there. */
@Composable
private fun Locator(model: AppModel, z: Int, onClick: () -> Unit) {
    val me = model.atlas.atom(z)
    Canvas(Modifier.fillMaxWidth(0.86f).aspectRatio(18f / 9.5f).clickable(onClick = onClick)) {
        val pen = ComposePen(this, 18.0, wide = true)
        for (a in model.atlas.atoms) {
            val (col, row) = tableSpot(a.z, a.group, a.period)
            val y = if (row >= 9) row - 1.5 else row - 1.0
            val lit = a.period == me.period && row < 9
            pen.rect(col - 1 + 0.08, y + 0.08, 0.84, 0.84, 0.16, if (lit) Ink.K500 else Ink.K700)
        }
        val (col, row) = tableSpot(z, me.group, me.period)
        val y = if (row >= 9) row - 1.5 else row - 1.0
        pen.rect(col - 1 - 0.15, y - 0.15, 1.3, 1.3, 0.3, Ink.WHITE)
        pen.rect(col - 1 + 0.02, y + 0.02, 0.96, 0.96, 0.2, me.color)
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
