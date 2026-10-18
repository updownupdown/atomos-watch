// Home, a compact take on the phone's "Today": a list the crown scrolls, in sections, each named
// in small caps. Build: the atom being built and the way into the whole collection. Atlas: the
// atom of the day (the phone's) and the way into every atom. Games: the game Nu picked for today
// and Nu's three questions. Nu's park: Nu to play with, and every game. Settings last.

package app.buildatomos.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import app.buildatomos.core.ATOMS
import app.buildatomos.core.Difficulty
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.atomOfDay
import app.buildatomos.core.icon
import app.buildatomos.core.mark
import app.buildatomos.core.pips
import app.buildatomos.watch.AppModel

@Composable
fun HomeScreen(
    model: AppModel,
    onRun: () -> Unit,
    onCollection: () -> Unit,
    onBuildAtom: (Int) -> Unit,
    onAtlas: () -> Unit,
    onAtom: (Int) -> Unit,
    onGame: (String) -> Unit,
    onGames: () -> Unit,
    onSettings: () -> Unit,
) {
    model.version
    val state = rememberScalingLazyListState(initialCenterItemIndex = 2)
    val frame = rememberFrame()
    val built = model.collection.built.size
    ScalingLazyColumn(
        Modifier.fillMaxSize().background(Color.Black),
        state = state,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Canvas(Modifier.size(32.dp)) { ComposePen(this, 32.0).mark(16.0, 16.0, 28.0, Ink.WHITE) } }

        item { SectionLabel("Build") }
        item { BuildCard(model, frame, onRun, onBuildAtom) }
        item { MinorRow("Your collection", "$built/$ATOMS", built / ATOMS.toDouble(), onCollection) }

        item { SectionLabel("Atlas") }
        item { AtomOfDayCard(model, onAtom) }
        item { MinorRow("Explore all atoms", null, null, onAtlas) }

        item { SectionLabel("Games") }
        item { TodayGameCard(model, onGame) }
        item { QuizCard(model, onAtom) }

        item { SectionLabel("Nu's park") }
        item { NuPark(model, frame, onGames) }

        item { Pill("Settings", Icon.GEAR, onSettings, Modifier.padding(top = 8.dp)) }
    }
}

/** The phone's atom of the day: its plate, its name and kind, and its headline. Opens its Atlas
 *  page. */
@Composable
private fun AtomOfDayCard(model: AppModel, onAtom: (Int) -> Unit) {
    val z = atomOfDay(model.today())
    val atom = model.atlas.atom(z)
    val info = model.atlas.info(z)
    Plate {
        Column(Modifier.fillMaxWidth().clickable { onAtom(z) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("Atom of the day", size = 13.sp, color = Tone.text2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IdPlate(atom, 56)
                Column {
                    Label(atom.name, size = 17.sp, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 1)
                    Label(info.kind.replaceFirstChar { it.uppercase() }, size = 13.sp, color = Color(atom.color), align = TextAlign.Start, lines = 2)
                }
            }
            if (info.headline.isNotBlank()) Label(info.headline, size = 14.sp, weight = FontWeight.Normal, lines = 4)
        }
    }
}

/** The game Nu picked for today, at the difficulty last played: opens its page for free play. */
@Composable
private fun TodayGameCard(model: AppModel, onGame: (String) -> Unit) {
    val game = model.nuPick()
    val level = model.pickedLevel(game)
    val best = model.bestLevel(game, level)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tone.plate, RoundedCornerShape(26.dp))
            .clickable { onGame(game.id) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameIcon(game.id, 38)
        Column(Modifier.weight(1f)) {
            Label("Play ${game.label}", size = 15.sp, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Canvas(Modifier.size(width = 28.dp, height = 8.dp)) { ComposePen(this, 28.0, wide = true).pips(14.0, 4.0, 26.0, Difficulty.dialOf(level)) }
                if (best > 0) Label("Best: $best", size = 13.sp, color = Tone.text2)
            }
        }
        IconMark(Icon.PLAY, 32, Ink.BLACK, disc = Ink.WHITE)
    }
}

/** A minor row, text only on black, with a progress bar and its count on the right if it has
 *  one, and a chevron. */
@Composable
fun MinorRow(label: String, count: String?, progress: Double?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label(label, Modifier.weight(1f), size = 15.sp, align = TextAlign.Start, lines = 1)
        if (progress != null) Bar(progress, Modifier.size(width = 22.dp, height = 4.dp))
        if (count != null) Label(count, size = 13.sp, color = Tone.text2)
        Canvas(Modifier.size(14.dp)) { ComposePen(this, 14.0).icon(Icon.CHEVRON, 7.0, 7.0, 14.0, Ink.K400) }
    }
}

/** A major card: a rounded K900 plate. */
@Composable
fun Plate(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().background(Tone.plate, RoundedCornerShape(26.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
