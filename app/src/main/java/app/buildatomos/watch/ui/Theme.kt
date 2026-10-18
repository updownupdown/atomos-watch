// Outfit, pure black, the phone's palette.

package app.buildatomos.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.buildatomos.core.Ink
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
    val plate = Color(Ink.K900)
    val rose = Color(Ink.ROSE)
    val cyan = Color(Ink.CYAN)
    val green = Color(Ink.GREEN)
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
