// A run of one game: the court filling the screen, the rim, the score as a tiny atom, and the
// run-over card with Again. Touches and the crown go to the game; a swipe in from the left edge
// (or the back button) leaves.

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
import androidx.compose.ui.unit.IntSize
import app.buildatomos.core.COURT_R
import app.buildatomos.core.Game
import app.buildatomos.core.Ink
import app.buildatomos.core.Pt
import app.buildatomos.core.SCREEN
import app.buildatomos.core.Stage
import app.buildatomos.core.TAU
import app.buildatomos.core.Toy
import app.buildatomos.core.best
import app.buildatomos.core.gameIcon
import app.buildatomos.core.scoreAtom
import app.buildatomos.core.Games
import app.buildatomos.watch.AppModel
import kotlin.math.PI
import kotlin.math.min

/** Crown turn per pixel of rotary scroll, in radians: a full turn of a Pixel Watch crown is
 *  roughly a full turn of Nu round its ring. */
private const val CROWN = 0.006

private class Run(val game: Game, model: AppModel) {
    val stage = Stage(model.feel, model.bests)
    val toy: Toy = game.make()
    val bestBefore = best(stage, game.id)
    var paid: Int? = null
    var newBest = false
}

@Composable
fun GameScreen(model: AppModel, game: Game, onLeave: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val run = remember(round) {
        Run(game, model).also { r -> r.stage.now = 1000.0; r.toy.enter(r.stage) }
    }
    var frame by remember { mutableLongStateOf(0L) }
    var over by remember(round) { mutableStateOf(false) }
    var waiting by remember(round) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    BackHandler { onLeave() }

    LaunchedEffect(run) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { t ->
                val dt = ((t - last) / 1e6).coerceIn(0.0, 50.0)
                last = t
                val s = run.stage
                s.now += dt
                run.toy.step(s, dt, s.now)
                frame = t
                waiting = s.waiting != null
                val end = s.ended
                if (end != null && run.paid == null) {
                    run.newBest = end > run.bestBefore && run.bestBefore > 0
                    run.paid = model.finishRun(game.id, end, run.bestBefore)
                }
            }
            if (run.paid != null && !over) {
                // Let the miss play out a moment before the card.
                kotlinx.coroutines.delay(700)
                over = true
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
                if (!over) run.toy.crown(run.stage, e.verticalScrollPixels * CROWN)
                true
            }
            .focusRequester(focus)
            .focusable()
            .pointerInput(run, over) {
                size = this.size
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val edge = down.position.x < size.width * 0.1f
                    val s = run.stage
                    if (over) return@awaitEachGesture
                    if (!edge) {
                        val go = s.waiting
                        if (go != null && court(down.position).let { p -> p.y > SCREEN * 0.72 && p.x in SCREEN * 0.3..SCREEN * 0.7 }) {
                            s.waiting = null
                            go()
                            return@awaitEachGesture
                        }
                        run.toy.down(s, court(down.position))
                    }
                    var pos = down.position
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        pos = c.position
                        if (!c.pressed) break
                        if (!edge) run.toy.move(s, court(pos))
                        c.consume()
                    }
                    if (edge) {
                        if (pos.x - down.position.x > size.width * 0.3f) onLeave()
                    } else run.toy.up(s, court(pos))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame
            val pen = ComposePen(this)
            val s = run.stage
            run.toy.draw(s, pen, s.now)
            s.fx.draw(pen, s.now, 16.0)
            // The rim's countdown, if the game keeps one.
            s.rim?.let { left ->
                pen.arc(s.c.x, s.c.y, COURT_R + 7, -PI / 2, TAU * left.coerceIn(0.0, 1.0), Ink.CYAN, 4.0, 0.8)
            }
            if (!over) {
                // The count, as a tiny atom at the bottom.
                pen.scoreAtom(s.c.x, s.c.y + COURT_R - 4, 18.0, Games.points(game.id, s.shown), Ink.K300)
                if (s.waiting != null) nextButton(pen)
            }
        }
        if (over) OverCard(run, game, onAgain = { round++ }, onLeave = onLeave)
    }
}

/** The rose Next button low on the court: a skip-forward mark. */
private fun nextButton(pen: ComposePen) {
    val x = SCREEN / 2
    val y = SCREEN * 0.83
    pen.dot(x, y, 22.0, Ink.ROSE)
    pen.line(x - 7, y - 8, x + 3, y, Ink.BLACK, 4.0)
    pen.line(x + 3, y, x - 7, y + 8, Ink.BLACK, 4.0)
    pen.line(x + 8, y - 8, x + 8, y + 8, Ink.BLACK, 4.0)
}

/** The run is over: the score atom (in the accent if it's a new best), what it paid, and Again. */
@Composable
private fun OverCard(run: Any, game: Game, onAgain: () -> Unit, onLeave: () -> Unit) {
    val r = run as Run
    val points = Games.points(game.id, r.stage.ended ?: 0.0)
    val best = Games.points(game.id, best(r.stage, game.id))
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var end = down.position
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        end = c.position
                        if (!c.pressed) break
                    }
                    val side = min(size.width, size.height).toFloat()
                    val k = side / SCREEN.toFloat()
                    val x = (end.x - (size.width - side) / 2) / k
                    val y = (end.y - (size.height - side) / 2) / k
                    if (end.x - down.position.x > size.width * 0.3f) onLeave()
                    else if (y > SCREEN * 0.68 && x in (SCREEN * 0.3).toFloat()..(SCREEN * 0.7).toFloat()) onAgain()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pen = ComposePen(this)
            val c = SCREEN / 2
            pen.scoreAtom(c, 104.0, 92.0, points, if (r.newBest) Ink.CYAN else Ink.WHITE, ring = if (r.newBest) Ink.CYAN else null)
            // The best, small, beside the game's icon.
            pen.dot(c - 22, 176.0, 12.0, Ink.WHITE)
            pen.gameIcon(game.id, c - 22, 176.0, 14.0, Ink.BLACK)
            pen.scoreAtom(c + 14, 176.0, 26.0, best, Ink.K300)
            // Again: a big white circle with a turning arrow.
            val y = 236.0
            pen.dot(c, y, 30.0, Ink.WHITE)
            pen.arc(c, y, 12.0, -PI * 0.35, PI * 1.6, Ink.BLACK, 4.5)
            val ax = c + 12 * kotlin.math.cos(-PI * 0.35)
            val ay = y + 12 * kotlin.math.sin(-PI * 0.35)
            pen.line(ax, ay, ax - 7, ay - 2, Ink.BLACK, 4.5)
            pen.line(ax, ay, ax + 1, ay - 8, Ink.BLACK, 4.5)
        }
        Electrons(r.paid ?: 0)
    }
}

@Composable
private fun Electrons(n: Int) {
    if (n <= 0) return
    Box(Modifier.fillMaxSize()) {
        Label("+$n", Modifier.align(androidx.compose.ui.Alignment.Center), color = Color(Ink.CYAN))
    }
}
