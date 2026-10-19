// A run of one game. It opens on a card: the game's name curved at the top, its icon in a ring
// that fills in the difficulty's color until play starts, the difficulty and the levels to reach,
// and Start at the edge. Then the court fills the screen: the levels as a tiny atom at the bottom
// (with the time left, for a game that keeps time, as a small ring round it), and the ATOMOS mark
// at the top, which pauses the run (the back button does the same): Resume at the edge, Exit and
// Restart as pills, the level and the best. The run ends on a card: what it built or reached,
// Next at the edge after a shell is built, and Again and Exit.

package app.buildatomos.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildatomos.core.Difficulty
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.MAX_LEVEL
import app.buildatomos.core.Pt
import app.buildatomos.core.Result
import app.buildatomos.core.SCREEN
import app.buildatomos.core.Stage
import app.buildatomos.core.TAU
import app.buildatomos.core.Toy
import app.buildatomos.core.gameIcon
import app.buildatomos.core.icon
import app.buildatomos.core.levels
import app.buildatomos.core.mark
import app.buildatomos.core.periodOf
import app.buildatomos.core.timer
import app.buildatomos.watch.AppModel
import app.buildatomos.watch.RunSpec
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min

/** Crown turn per pixel of rotary scroll, in radians: a full turn of a Pixel Watch crown is
 *  roughly a full turn of Nu round its ring. */
private const val CROWN = 0.006
/** How long the opening card shows before play starts (Start, a tap or the crown start it
 *  sooner). */
private const val INTRO_MS = 2200L
private const val MID = SCREEN / 2
/** The menu button: the mark at the top. */
private val MENU = Pt(MID, 17.0)
/** The levels counter at the bottom, and the radius of the timer round it. */
private val COUNTER = Pt(MID, 272.0)
private const val TIMER_R = 17.5
/** The cards' veil over the court. */
private const val VEIL = 0.92f

private enum class Phase { INTRO, PLAY, PAUSED, OVER }

private class Run(val spec: RunSpec, model: AppModel) {
    val stage = Stage(model.feel).also { it.dial = spec.dial; it.goal = spec.goal }
    val toy: Toy = spec.game.make()
    var newBest = false
    var shownLevel = 0
}

@Composable
fun GameScreen(model: AppModel, spec: RunSpec, onLeave: () -> Unit, onNextRun: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val run = remember(round) { Run(spec, model).also { r -> r.stage.now = 1000.0; r.toy.enter(r.stage) } }
    var phase by remember(round) { mutableStateOf(Phase.INTRO) }
    var phaseAt by remember(round) { mutableLongStateOf(System.currentTimeMillis()) }
    var frame by remember { mutableLongStateOf(0L) }
    val focus = rememberCrownFocus()
    fun go(p: Phase) { phase = p; phaseAt = System.currentTimeMillis() }

    BackHandler {
        when (phase) {
            Phase.PLAY -> go(Phase.PAUSED)
            Phase.PAUSED -> go(Phase.PLAY)
            else -> onLeave()
        }
    }

    LaunchedEffect(run) {
        var last = withFrameNanos { it }
        var overAt = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = ((t - last) / 1e6).coerceIn(0.0, 50.0)
                last = t
                frame = t
                val s = run.stage
                if (phase == Phase.INTRO && System.currentTimeMillis() - phaseAt > INTRO_MS) go(Phase.PLAY)
                if (phase == Phase.PLAY && !s.over) {
                    s.now += dt
                    run.toy.step(s, dt, s.now)
                    if (s.level > run.shownLevel) {
                        run.shownLevel = s.level
                        s.fx.ring(COUNTER, Ink.CYAN, 10.0, 34.0, 700.0)
                    }
                    val result = s.result
                    if (result != null) {
                        run.newBest = model.finishRun(spec, result, s.level)
                        overAt = System.currentTimeMillis()
                    }
                }
                // Let the end play out a moment before the card.
                if (phase == Phase.PLAY && s.over && System.currentTimeMillis() - overAt > 800) go(Phase.OVER)
            }
        }
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    fun court(o: Offset): Pt {
        val side = min(size.width, size.height).toFloat()
        val k = side / SCREEN
        return Pt(((o.x - (size.width - side) / 2) / k).toDouble(), ((o.y - (size.height - side) / 2) / k).toDouble())
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { e ->
                // A turn of the crown on the opening card starts play at once, as a tap does.
                if (phase == Phase.INTRO) go(Phase.PLAY)
                if (phase == Phase.PLAY) run.toy.crown(run.stage, e.verticalScrollPixels * CROWN)
                true
            }
            .focusRequester(focus)
            .focusable()
            .pointerInput(run) {
                size = this.size
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val at = court(down.position)
                    val s = run.stage
                    when (phase) {
                        Phase.INTRO -> { go(Phase.PLAY); return@awaitEachGesture }
                        // The menu and the end card take their own taps.
                        Phase.PAUSED, Phase.OVER -> return@awaitEachGesture
                        Phase.PLAY -> {}
                    }
                    if (hypot(at.x - MENU.x, at.y - MENU.y) < 22) { go(Phase.PAUSED); return@awaitEachGesture }
                    val next = s.waiting
                    if (next != null && hypot(at.x - MID, at.y - NEXT_Y) < 30) {
                        s.waiting = null
                        next()
                        return@awaitEachGesture
                    }
                    run.toy.down(s, at)
                    var pos = down.position
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        pos = c.position
                        if (!c.pressed) break
                        if (phase == Phase.PLAY) run.toy.move(s, court(pos))
                        c.consume()
                    }
                    run.toy.up(s, court(pos))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame
            val pen = ComposePen(this)
            val s = run.stage
            run.toy.draw(s, pen, s.now)
            s.fx.draw(pen, s.now, if (phase == Phase.PLAY) 16.0 else 0.0)
            if (phase == Phase.PLAY) {
                pen.dot(MENU.x, MENU.y, 15.0, Ink.BLACK, 0.7)
                pen.mark(MENU.x, MENU.y, 24.0, Ink.WHITE)
                // The time left (Stage.rim), quietly, round the counter rather than the whole rim.
                s.rim?.let { left -> pen.timer(COUNTER.x, COUNTER.y, TIMER_R, left) }
                pen.levels(COUNTER.x, COUNTER.y, 32.0, s.level, s.goal, s.part)
                if (s.waiting != null) nextButton(pen)
            }
        }
        when (phase) {
            Phase.INTRO -> IntroCard(spec, { frame; ((System.currentTimeMillis() - phaseAt) / INTRO_MS.toDouble()).coerceIn(0.0, 1.0) }) { go(Phase.PLAY) }
            Phase.PAUSED -> PauseCard(model, spec, run.stage, onExit = onLeave, onResume = { go(Phase.PLAY) }, onRestart = { round++ })
            Phase.OVER -> OverCard(model, spec, run, onExit = onLeave, onAgain = { round++ }, onNext = onNextRun)
            Phase.PLAY -> {}
        }
    }
}

private const val NEXT_Y = 232.0

/** The rose Next button, between levels of a round-based game: a skip-forward mark. */
private fun nextButton(pen: ComposePen) {
    pen.dot(MID, NEXT_Y, 22.0, Ink.ROSE)
    pen.icon(Icon.NEXT, MID, NEXT_Y, 24.0, Ink.BLACK)
}

/** The opening card: the game's name curved at the top; its icon in a ring that fills, in the
 *  difficulty's color, as the wait runs out (`waited`, 0..1); the difficulty and the levels to
 *  reach; Start at the edge, which skips the wait. */
@Composable
private fun IntroCard(spec: RunSpec, waited: () -> Double, onStart: () -> Unit) {
    val color = Difficulty.color(spec.dial)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = VEIL))) {
        Column(
            Modifier.align(Alignment.Center).padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Canvas(Modifier.size(96.dp)) {
                val pen = ComposePen(this, 96.0)
                pen.ring(48.0, 48.0, 44.0, Ink.K800, 4.0)
                pen.arc(48.0, 48.0, 44.0, -PI / 2, TAU * waited(), color, 4.0)
                pen.dot(48.0, 48.0, 33.0, Ink.WHITE)
                pen.gameIcon(spec.game.id, 48.0, 48.0, 38.0, Ink.BLACK)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(Difficulty.word(spec.dial), size = 16.sp, color = Color(color), weight = FontWeight.SemiBold, lines = 1)
                val goal = spec.goal
                Label(if (goal != null) "Goal: $goal" else "Free play", size = 14.sp, color = Tone.text2, lines = 1)
            }
        }
        CurvedTitle(spec.game.label)
        EdgeButton(onStart, fill = Tone.text) { EdgeLabel("Start", Tone.ink) }
    }
}

/** The pause menu: Paused curved at the top; the game, its difficulty and how far the run has
 *  come (and the best); Exit and Restart as pills; Resume at the edge. */
@Composable
private fun PauseCard(model: AppModel, spec: RunSpec, s: Stage, onExit: () -> Unit, onResume: () -> Unit, onRestart: () -> Unit) {
    val goal = s.goal
    val foot = if (goal != null) "Level ${s.level} of $goal"
    else {
        val best = model.bestLevel(spec.game, Difficulty.level(spec.dial))
        if (best > 0) "Level ${s.level} · Best: $best" else "Level ${s.level}"
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = VEIL))) {
        Column(
            Modifier.align(Alignment.Center).padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameIcon(spec.game.id, 30)
                Label(spec.game.label, size = 18.sp, weight = FontWeight.SemiBold, lines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(Difficulty.word(spec.dial), size = 14.sp, color = Color(Difficulty.color(spec.dial)), weight = FontWeight.SemiBold, lines = 1)
                Label(foot, size = 14.sp, color = Tone.text2, lines = 1)
            }
            CardPills(Modifier.padding(top = 6.dp)) {
                CardPill("Exit", Icon.EXIT, onExit)
                CardPill("Restart", Icon.RESTART, onRestart)
            }
        }
        CurvedTitle("Paused")
        EdgeButton(onResume, fill = Tone.text, tall = true) { EdgeLabel("Resume", Tone.ink, Icon.PLAY) }
    }
}

/** The end card, fading in over the court. For an atom: the shell built (or the atom), with Next
 *  at the edge; or how far the run got. For free play: the level reached and the best. Then Again
 *  (not after a shell is built: that run is done) and Exit. */
@Composable
private fun OverCard(model: AppModel, spec: RunSpec, run: Run, onExit: () -> Unit, onAgain: () -> Unit, onNext: () -> Unit) {
    val s = run.stage
    val won = s.result == Result.WON
    val z = spec.building
    val goal = s.goal
    val next = z != null && won
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(250)) }
    // What the card says: the tiny atom's color, the headline and its color, and the line under.
    val atomColor: Int
    val head: String
    val headColor: Color
    val sub: String
    var subColor = Tone.text2
    if (z != null && won) {
        val atom = model.atlas.atom(z)
        val built = model.collection.isBuilt(z)
        val need = periodOf(z)
        atomColor = Ink.GREEN
        head = if (built) "${atom.name} built" else "Shell built"
        headColor = Tone.green
        sub = "${atom.name}: ${if (built) need else model.collection.shells} of $need shells"
    } else if (z != null) {
        atomColor = Ink.CYAN
        head = "Try again"
        headColor = Tone.text
        sub = "Level ${s.level} of $goal"
    } else {
        atomColor = if (run.newBest) Ink.CYAN else Ink.WHITE
        head = if (won) "Every level cleared" else "Level ${s.level}"
        headColor = Tone.text
        val best = model.bestLevel(spec.game, Difficulty.level(spec.dial))
        sub = if (run.newBest) "New best" else "Best: $best"
        if (run.newBest) subColor = Tone.cyan
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(Color.Black.copy(alpha = VEIL))) {
        Column(
            Modifier.align(Alignment.Center).padding(bottom = if (next) 34.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Canvas(Modifier.size(60.dp)) {
                ComposePen(this, 60.0).levels(30.0, 30.0, 60.0, s.level, if (z != null) goal else null, 0.0, atomColor)
            }
            Label(head, Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp), size = 18.sp, color = headColor, weight = FontWeight.SemiBold, lines = 2)
            Label(sub, size = 14.sp, color = subColor, lines = 1)
            if (z == null && won && s.level >= MAX_LEVEL) Label("of $MAX_LEVEL", size = 13.sp, color = Tone.text2)
            CardPills(Modifier.padding(top = 8.dp)) {
                if (!next) CardPill("Again", Icon.RESTART, onAgain)
                CardPill("Exit", Icon.EXIT, onExit)
            }
        }
        CurvedTitle(spec.game.label)
        if (next) EdgeButton(onNext, fill = Tone.rose, tall = true) { EdgeLabel("Next", Tone.ink, Icon.NEXT) }
    }
}

/** A row of the cards' pills, sharing the width of the screen's middle. */
@Composable
private fun CardPills(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        content = content,
    )
}

/** A soft pill on the plate: an icon and a word. */
@Composable
private fun RowScope.CardPill(label: String, icon: Icon, onClick: () -> Unit) {
    Row(
        Modifier
            .weight(1f, fill = false)
            .height(44.dp)
            .background(Tone.plate, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        Canvas(Modifier.size(16.dp)) { ComposePen(this, 16.0).icon(icon, 8.0, 8.0, 16.0, Tone.text.toArgb()) }
        Label(label, size = 14.sp, weight = FontWeight.Medium, lines = 1)
    }
}
