// Outfit, pure black, the phone's palette.

package app.buildatomos.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
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
    val text3 = Color(Ink.K400)
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

/** The focus that takes the crown's turns on a screen that reads the crown itself. It asks for
 *  focus whenever its screen is the one showing: the navigation host moves focus between screens
 *  as they come and go, so a screen asking once when it first draws can lose it for good. */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun rememberCrownFocus(): FocusRequester = rememberActiveFocusRequester()
