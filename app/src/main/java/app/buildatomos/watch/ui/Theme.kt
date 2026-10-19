// Outfit, pure black, the phone's palette.

package app.buildatomos.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.buildatomos.core.Atom
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.icon
import app.buildatomos.watch.R

val Outfit = FontFamily(
    Font(R.font.outfit_300, FontWeight.Light),
    Font(R.font.outfit_400, FontWeight.Normal),
    Font(R.font.outfit_500, FontWeight.Medium),
    Font(R.font.outfit_600, FontWeight.SemiBold),
)

object Tone {
    val text = Color(Ink.WHITE)
    val text2 = Color(Ink.K300)
    val text3 = Color(Ink.K400)
    /** Text on a light button: rose, white or a level's color. */
    val ink = Color(Ink.K975)
    val plate = Color(Ink.K900)
    val rose = Color(Ink.ROSE)
    val cyan = Color(Ink.CYAN)
    val green = Color(Ink.GREEN)
    val chip = Color(Ink.K800)
}

/** Black or white, whichever reads on `fill` (the phone's inkOn). */
fun inkOn(fill: Int): Color {
    fun ch(v: Int): Double { val c = v / 255.0; return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
    val l = 0.2126 * ch(fill shr 16 and 0xff) + 0.7152 * ch(fill shr 8 and 0xff) + 0.0722 * ch(fill and 0xff)
    return if (l > 0.28) Color.Black else Color.White
}

@Composable
fun AtomosTheme(content: @Composable () -> Unit) = MaterialTheme(content = content)

@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    color: Color = Tone.text,
    weight: FontWeight = FontWeight.Medium,
    align: TextAlign = TextAlign.Center,
    lines: Int = Int.MAX_VALUE,
) = Text(text, modifier, color = color, fontSize = size, fontFamily = Outfit, fontWeight = weight, textAlign = align, maxLines = lines)

/** A full-width rounded row on a K900 plate, the watch's list item. */
@Composable
fun PlateRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(Tone.plate, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** A section's name over its cards, in small caps like the phone's Home. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        color = Tone.text2, fontSize = 13.sp, fontFamily = Outfit, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp, textAlign = TextAlign.Center,
    )
}

/** One of the app's icons, `size` across, on a disc of `disc` if there is one. */
@Composable
fun IconMark(icon: Icon, size: Int, color: Int = Ink.WHITE, disc: Int? = null, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size.dp)) {
        val pen = ComposePen(this, size.toDouble())
        val h = size / 2.0
        if (disc != null) {
            pen.dot(h, h, h, disc)
            pen.icon(icon, h, h, size * 0.55, color)
        } else pen.icon(icon, h, h, size.toDouble(), color)
    }
}

/** A rounded button with an icon and a label: rose for Build and Next, white for Play, a plate
 *  for everything else. */
@Composable
fun Pill(label: String, icon: Icon?, onClick: () -> Unit, modifier: Modifier = Modifier, fill: Color = Tone.chip, ink: Color = Tone.text) {
    Row(
        modifier.height(40.dp).background(fill, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Canvas(Modifier.size(18.dp)) { ComposePen(this, 18.0).icon(icon, 9.0, 9.0, 18.0, ink.toArgb()) }
        Label(label, size = 15.sp, color = ink, weight = FontWeight.SemiBold, lines = 1)
    }
}

/** An atom's plate, as on the phone: a rounded square in its color, its number small at the top
 *  and its symbol large. */
@Composable
fun IdPlate(atom: Atom, size: Int, modifier: Modifier = Modifier) {
    val ink = inkOn(atom.color)
    Column(
        modifier.size(size.dp).background(Color(atom.color), RoundedCornerShape((size / 5).dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("${atom.z}", color = ink, fontSize = (size * 0.24).sp, fontFamily = Outfit, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(atom.symbol, color = ink, fontSize = (size * 0.42).sp, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** An atom's card, as on its Build page and on the Atlas page's edge: a soft square, in the
 *  atom's color once `lit` (built), else `dark` with the symbol in the atom's color; its number
 *  small at the top left if `number`. */
@Composable
fun AtomCard(atom: Atom, size: Int, lit: Boolean, modifier: Modifier = Modifier, number: Boolean = true, dark: Color = Tone.plate) {
    val ink = if (lit) inkOn(atom.color) else Color(atom.color)
    Box(modifier.size(size.dp).background(if (lit) Color(atom.color) else dark, RoundedCornerShape((size * 0.3).dp))) {
        if (number) Text(
            "${atom.z}", Modifier.align(Alignment.TopStart).padding(start = (size * 0.13).dp, top = (size * 0.05).dp),
            color = if (lit) ink else Tone.text2, fontSize = 13.sp, fontFamily = Outfit, fontWeight = FontWeight.Medium, maxLines = 1,
        )
        Text(
            atom.symbol,
            if (number) Modifier.align(Alignment.BottomCenter).padding(bottom = (size * 0.08).dp) else Modifier.align(Alignment.Center),
            color = ink, fontSize = (size * 0.48).coerceAtLeast(13.0).sp, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

/** The focus that takes the crown's turns on a screen that reads the crown itself. It asks for
 *  focus whenever its screen is the one showing: the navigation host moves focus between screens
 *  as they come and go, so a screen asking once when it first draws can lose it for good. */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun rememberCrownFocus(): FocusRequester = rememberActiveFocusRequester()
