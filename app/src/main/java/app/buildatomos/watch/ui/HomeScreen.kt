// Home, a compact take on the phone's "Today": a list the crown scrolls, under the mark and
// ATOMOS, in sections. Build: the atom being built and the way into the whole collection. Atlas:
// the atom of the day (the phone's) and the way into every atom. Games: the game Nu picked for
// today and Nu's three questions. Settings last. The curved title at the top shows the time in
// Build and the section's name past it, and the edge button follows the section: the rose Build
// (with the next shell's game and difficulty under it), the Atlas, then All games.

package app.buildatomos.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import app.buildatomos.core.ATOMS
import app.buildatomos.core.Game
import app.buildatomos.core.Games
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.atomOfDay
import app.buildatomos.core.mark
import app.buildatomos.watch.AppModel

/** Home's sections, and the first list item of each past Build. */
private const val BUILD = 0
private const val ATLAS = 1
private const val GAMES = 2
private const val ATLAS_FROM = 3
private const val GAMES_FROM = 5

@Composable
fun HomeScreen(
    model: AppModel,
    onRun: () -> Unit,
    onCollection: () -> Unit,
    onBuildAtom: (Int) -> Unit,
    onAtlas: () -> Unit,
    onAtom: (Int) -> Unit,
    onPlay: (Game, Int) -> Unit,
    onGames: () -> Unit,
    onSettings: () -> Unit,
) {
    model.version
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val frame = rememberFrame()
    val built = model.collection.built.size
    val clock = rememberClock()
    val section = when {
        state.centerItemIndex >= GAMES_FROM -> GAMES
        state.centerItemIndex >= ATLAS_FROM -> ATLAS
        else -> BUILD
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        EdgeList(state, top = 26.dp, gap = 8.dp) {
            // Build (keep ATLAS_FROM and GAMES_FROM in step with these).
            item { Wordmark() }
            item { BuildCard(model, frame, onBuildAtom) }
            item { MinorRow("Your collection", "$built/$ATOMS", built / ATOMS.toDouble(), onCollection) }
            // Atlas.
            item { AtomOfDayCard(model, onAtom) }
            item { MinorRow("Explore all atoms", null, null, onAtlas) }
            // Games.
            item { TodayGameCard(model, onPlay) }
            item { QuizCard(model, onAtom) }
            item { Pill("Settings", Icon.GEAR, onSettings, Modifier.padding(top = 4.dp)) }
        }
        CurvedTitle(
            when (section) {
                ATLAS -> "Atlas"
                GAMES -> "Games"
                else -> clock
            },
        )
        EdgeScroll(state)
        when (section) {
            GAMES -> EdgeButton(onGames, fill = Tone.text) {
                EdgeLabel("All games", Tone.ink, count = "${Games.all.size}", countInk = Color(Ink.K700))
            }
            ATLAS -> EdgeButton(onAtlas) {
                EdgeLabel("All atoms", Tone.text, Icon.ATLAS, count = "$ATOMS", countInk = Tone.text2)
            }
            else -> {
                val run = model.buildRun()
                if (run != null) EdgeButton(onRun, fill = Tone.rose, tall = true) {
                    EdgeLabel("Build", Tone.ink)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label(run.game.label, size = 13.sp, color = Tone.ink, lines = 1)
                        InkPips(run.dial, Tone.ink)
                    }
                } else EdgeButton(onCollection) {
                    EdgeLabel("Collection", Tone.text, count = "$built", countInk = Tone.text2)
                }
            }
        }
    }
}

/** The mark, with Nu resting on one of its nucleons as on the phone's header, and ATOMOS beside
 *  it, spaced wide like the phone's titles. */
@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(44.dp)) { ComposePen(this, 44.0).mark(22.0, 22.0, 40.0, Ink.WHITE, nu = 2) }
        Text(
            "ATOMOS", color = Tone.text, fontSize = 20.sp, fontFamily = Outfit, fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp, maxLines = 1,
        )
    }
}

/** The phone's atom of the day: its plate, its name and kind, and its headline. Opens its Atlas
 *  page. */
@Composable
private fun AtomOfDayCard(model: AppModel, onAtom: (Int) -> Unit) {
    val z = atomOfDay(model.today())
    val atom = model.atlas.atom(z)
    val info = model.atlas.info(z)
    Plate(Modifier.clickable { onAtom(z) }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

/** The game Nu picked for today: its icon and name, and its three difficulties as dots with
 *  their bests, as in the games list. A tap on a dot plays that difficulty; anywhere else on the
 *  card, the one last played. */
@Composable
private fun TodayGameCard(model: AppModel, onPlay: (Game, Int) -> Unit) {
    val game = model.nuPick()
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tone.plate, RoundedCornerShape(32.dp))
            .clickable { onPlay(game, model.pickedLevel(game)) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameIcon(game.id, 40)
        Column(Modifier.weight(1f)) {
            Label("Play ${game.label}", Modifier.padding(start = 2.dp), size = 15.sp, weight = FontWeight.SemiBold, align = TextAlign.Start, lines = 1)
            LevelDots(model, game) { onPlay(game, it) }
        }
    }
}

/** A minor row: a soft pill on the plate, its label in the middle, with a progress bar and its
 *  count after it if it has one. */
@Composable
fun MinorRow(label: String, count: String?, progress: Double?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(Tone.plate, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Label(label, size = 15.sp, color = Color(Ink.K200), lines = 1)
        if (progress != null) Bar(progress, Modifier.size(width = 22.dp, height = 4.dp))
        if (count != null) Label(count, size = 13.sp, color = Tone.text2, lines = 1)
    }
}

/** A major card: a soft K900 plate with a large radius. */
@Composable
fun Plate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Tone.plate, RoundedCornerShape(30.dp))
            .then(modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
