// A run of one game. It opens on a card with the game's name, its difficulty and the levels to
// reach, a ring round the rim filling until play starts. Then the court fills the screen: the
// rim, the levels as a tiny atom at the bottom, and the ATOMOS mark at the top, which pauses the
// run and opens its menu (Exit, Resume, Restart; the back button does the same).
// The run ends on a card: what it built or reached, and what next.

package app.buildatomos.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import app.buildatomos.core.COURT_R
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
import app.buildatomos.core.pips
import app.buildatomos.watch.AppModel
import app.buildatomos.watch.RunSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Crown turn per pixel of rotary scroll, in radians: a full turn of a Pixel Watch crown is
 *  roughly a full turn of Nu round its ring. */
private const val CROWN = 0.006
/** How long the opening card shows before play starts (a tap starts it sooner). */
private const val INTRO_MS = 2200L
private const val MID = SCREEN / 2
/** The menu button: the mark at the top. */
private val MENU = Pt(MID, 17.0)
/** The levels counter at the bottom. */
private val COUNTER = Pt(MID, 272.0)

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
    val focus = remember { FocusRequester() }
    val measurer = rememberTextMeasurer()
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
    LaunchedEffect(Unit) { focus.requestFocus() }

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
                        Phase.PAUSED -> {
                            when (menuButton(at)) {
                                0 -> { round++ }
                                1 -> go(Phase.PLAY)
                                2 -> onLeave()
                            }
                            return@awaitEachGesture
                        }
                        Phase.OVER -> {
                            when (overButton(at)) {
                                0 -> onLeave()
                                1 -> if (spec.building != null && s.result == Result.WON) onNextRun() else round++
                            }
                            return@awaitEachGesture
                        }
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
            s.rim?.let { left ->
                pen.arc(s.c.x, s.c.y, COURT_R + 7, -PI / 2, TAU * left.coerceIn(0.0, 1.0), Ink.CYAN, 4.0, 0.8)
            }
            if (phase == Phase.PLAY) {
                pen.dot(MENU.x, MENU.y, 15.0, Ink.BLACK, 0.7)
                pen.mark(MENU.x, MENU.y, 24.0, Ink.WHITE)
                pen.levels(COUNTER.x, COUNTER.y, 32.0, s.level, s.goal, s.part)
                if (s.waiting != null) nextButton(pen)
            }
            val since = (System.currentTimeMillis() - phaseAt).toDouble()
            when (phase) {
                Phase.INTRO -> intro(pen, measurer, spec, since)
                Phase.PAUSED -> menu(pen, measurer, model, spec, s)
                Phase.OVER -> over(pen, measurer, model, spec, run, since)
                Phase.PLAY -> {}
            }
        }
    }
}

private const val NEXT_Y = 232.0

/** The rose Next button, between levels of a round-based game: a skip-forward mark. */
private fun nextButton(pen: ComposePen) {
    pen.dot(MID, NEXT_Y, 22.0, Ink.ROSE)
    pen.icon(Icon.NEXT, MID, NEXT_Y, 24.0, Ink.BLACK)
}

private fun veil(pen: ComposePen, alpha: Double) = pen.dot(MID, MID, SCREEN, Ink.BLACK, alpha)

/** The opening card: the game, its difficulty, and the levels to reach. */
private fun intro(pen: ComposePen, m: TextMeasurer, spec: RunSpec, since: Double) {
    val fade = if (since > INTRO_MS - 300) (INTRO_MS - since) / 300 else 1.0
    veil(pen, 0.92 * fade)
    if (fade <= 0) return
    // Round the rim, how long until play starts: it fills clockwise from the top.
    pen.ring(MID, MID, COURT_R + 7, Ink.K800, 4.0, fade)
    pen.arc(MID, MID, COURT_R + 7, -PI / 2, TAU * (since / INTRO_MS).coerceIn(0.0, 1.0), Ink.WHITE, 4.0, fade)
    pen.dot(MID, 84.0, 30.0, Ink.WHITE, fade)
    pen.gameIcon(spec.game.id, MID, 84.0, 34.0, Ink.BLACK)
    pen.text(m, spec.game.label, MID, 140.0, 24.0, Ink.WHITE, FontWeight.SemiBold, fade)
    pen.text(m, Difficulty.word(spec.dial), MID, 168.0, 15.0, Difficulty.color(spec.dial), alpha = fade)
    pen.pips(MID, 188.0, 46.0, spec.dial)
    val goal = spec.goal
    if (goal != null) {
        pen.levels(MID, 228.0, 40.0, 0, goal)
        pen.text(m, "Reach $goal levels", MID, 260.0, 13.0, Ink.K300, alpha = fade)
    } else {
        pen.text(m, "Free play", MID, 234.0, 13.0, Ink.K300, alpha = fade)
    }
}

/** The menu's row of buttons, across the middle. */
private const val MENU_Y = MID
private const val SIDE_X = 80.0

/** Which of the menu's buttons is at `p`: 0 Restart, 1 Resume, 2 Exit, or -1. */
private fun menuButton(p: Pt): Int = when {
    hypot(p.x - MID, p.y - MENU_Y) < 40 -> 1
    hypot(p.x - (MID - SIDE_X), p.y - MENU_Y) < 32 -> 2
    hypot(p.x - (MID + SIDE_X), p.y - MENU_Y) < 32 -> 0
    else -> -1
}

/** A game's name with its icon in front, the two centered together as one row. */
private fun titleRow(pen: ComposePen, m: TextMeasurer, id: String, label: String, y: Double, size: Double) {
    val r = size * 0.62
    val gap = size * 0.4
    val w = pen.textWidth(m, label, size, FontWeight.SemiBold)
    val left = MID - (r * 2 + gap + w) / 2
    pen.dot(left + r, y, r, Ink.WHITE)
    pen.gameIcon(id, left + r, y, r * 1.2, Ink.BLACK)
    pen.text(m, label, left + r * 2 + gap + w / 2, y, size, Ink.WHITE, FontWeight.SemiBold)
}

/** The menu: the game and its difficulty at the top; Exit, Resume (the big one) and Restart
 *  across the middle, known by their icons; how far the run has come, and the best, at the foot. */
private fun menu(pen: ComposePen, m: TextMeasurer, model: AppModel, spec: RunSpec, s: Stage) {
    veil(pen, 0.9)
    titleRow(pen, m, spec.game.id, spec.game.label, 66.0, 22.0)
    pen.text(m, Difficulty.word(spec.dial), MID, 94.0, 14.0, Difficulty.color(spec.dial))
    pen.dot(MID - SIDE_X, MENU_Y, 26.0, Ink.K800)
    pen.icon(Icon.EXIT, MID - SIDE_X, MENU_Y, 26.0, Ink.WHITE)
    pen.dot(MID, MENU_Y, 36.0, Ink.WHITE)
    pen.icon(Icon.PLAY, MID, MENU_Y, 34.0, Ink.BLACK)
    pen.dot(MID + SIDE_X, MENU_Y, 26.0, Ink.K800)
    pen.icon(Icon.RESTART, MID + SIDE_X, MENU_Y, 28.0, Ink.WHITE)
    pen.levels(MID, 214.0, 34.0, s.level, s.goal, s.part)
    val goal = s.goal
    val foot = if (goal != null) "Level ${s.level} of $goal"
    else {
        val best = model.bestLevel(spec.game, Difficulty.level(spec.dial))
        if (best > 0) "Level ${s.level} · Best: $best" else "Level ${s.level}"
    }
    pen.text(m, foot, MID, 246.0, 14.0, Ink.K300)
}

private fun roundButton(pen: ComposePen, x: Double, y: Double, mark: (Double, Double) -> Unit) {
    pen.dot(x, y, 24.0, Ink.K800)
    mark(x, y)
}

/** Which of the end card's buttons is at `p`: 0 Exit, 1 Again (or Next), or -1. */
private fun overButton(p: Pt): Int = when {
    hypot(p.x - (MID + 34), p.y - 226) < 32 -> 1
    hypot(p.x - (MID - 44), p.y - 226) < 28 -> 0
    else -> -1
}

/** The end card. For an atom: the shell built (or the atom), and Next; or how far the run got,
 *  and Again. For free play: the level reached and the best. */
private fun over(pen: ComposePen, m: TextMeasurer, model: AppModel, spec: RunSpec, run: Run, since: Double) {
    val s = run.stage
    veil(pen, 0.9 * min(1.0, since / 250))
    val won = s.result == Result.WON
    val z = spec.building
    val goal = s.goal
    if (z != null && won) {
        val atom = model.atlas.atom(z)
        val built = model.collection.isBuilt(z)
        pen.levels(MID, 84.0, 64.0, s.level, goal, 0.0, Ink.GREEN)
        pen.text(m, if (built) "${atom.name} built" else "Shell built", MID, 140.0, 20.0, Ink.GREEN, FontWeight.SemiBold)
        val shells = if (built) app.buildatomos.core.periodOf(z) else model.collection.shells
        pen.text(m, "${atom.name}: $shells of ${app.buildatomos.core.periodOf(z)} shells", MID, 166.0, 13.0, Ink.K300)
    } else if (z != null) {
        pen.levels(MID, 84.0, 64.0, s.level, goal)
        pen.text(m, "Try again", MID, 140.0, 20.0, Ink.WHITE, FontWeight.SemiBold)
        pen.text(m, "Level ${s.level} of $goal", MID, 166.0, 13.0, Ink.K300)
    } else {
        pen.levels(MID, 84.0, 64.0, s.level, null, 0.0, if (run.newBest) Ink.CYAN else Ink.WHITE)
        pen.text(m, if (won) "Every level cleared" else "Level ${s.level}", MID, 140.0, 20.0, Ink.WHITE, FontWeight.SemiBold)
        val best = model.bestLevel(spec.game, Difficulty.level(spec.dial))
        pen.text(m, if (run.newBest) "New best" else "Best: $best", MID, 166.0, 13.0, if (run.newBest) Ink.CYAN else Ink.K300)
        if (won && s.level >= MAX_LEVEL) pen.text(m, "of $MAX_LEVEL", MID, 186.0, 13.0, Ink.K300)
    }
    // Exit, and Again (white, a turning arrow) or, after a shell is built, Next (rose).
    roundButton(pen, MID - 44, 226.0) { x, y -> pen.icon(Icon.EXIT, x, y, 24.0, Ink.WHITE) }
    if (z != null && won) {
        pen.dot(MID + 34, 226.0, 28.0, Ink.ROSE)
        pen.icon(Icon.NEXT, MID + 34, 226.0, 28.0, Ink.BLACK)
    } else {
        pen.dot(MID + 34, 226.0, 28.0, Ink.WHITE)
        pen.icon(Icon.RESTART, MID + 34, 226.0, 30.0, Ink.BLACK)
    }
}
