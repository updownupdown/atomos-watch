// Nu's park on Home: a round arena with Nu in it, and the way into every game. Tap the arena and
// Nu flies there (smoothly, never jumping), a ring opening where you tapped; tap Nu and it hops
// and glows. Left alone a while, Nu drifts back to the middle and rests.

package app.buildatomos.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.buildatomos.core.Icon
import app.buildatomos.core.Ink
import app.buildatomos.core.glow
import app.buildatomos.watch.AppModel
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val ARENA = 140.0
private const val HOME = ARENA / 2
private const val ROOM = 58.0
private const val FLY_MS = 650.0
private const val REST_MS = 6000.0

/** Where Nu is and where it is going, kept across frames. */
private class Nu {
    var fromX = HOME; var fromY = HOME
    var toX = HOME; var toY = HOME
    var flewAt = -1e9
    var petAt = -1e9
    var tapX = 0.0; var tapY = 0.0; var tapAt = -1e9
    var arrived = true

    fun at(now: Double): Pair<Double, Double> {
        val t = ((now - flewAt) / FLY_MS).coerceIn(0.0, 1.0)
        val e = t * t * (3 - 2 * t)
        // A gentle sideways bow on the way, so the flight reads as Nu's own.
        val bow = sin(t * PI) * 8
        val dx = toX - fromX; val dy = toY - fromY
        val d = hypot(dx, dy).coerceAtLeast(1e-6)
        return Pair(fromX + dx * e - dy / d * bow, fromY + dy * e + dx / d * bow)
    }

    fun fly(now: Double, x: Double, y: Double) {
        val (px, py) = at(now)
        fromX = px; fromY = py; toX = x; toY = y; flewAt = now; arrived = false
    }
}

@Composable
fun NuPark(model: AppModel, frame: () -> Long, onGames: () -> Unit) {
    val nu = remember { Nu() }
    Plate {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(
                Modifier
                    .size(ARENA.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { o ->
                            val k = min(size.width, size.height) / ARENA
                            val x = o.x / k; val y = o.y / k
                            val now = frame() / 1e6
                            val (nx, ny) = nu.at(now)
                            if (hypot(x - nx, y - ny) < 16) {
                                nu.petAt = now
                                model.feel.capture()
                                return@detectTapGestures
                            }
                            // Keep Nu inside the arena.
                            val dx = x - HOME; val dy = y - HOME
                            val d = hypot(dx, dy)
                            val s = if (d > ROOM) ROOM / d else 1.0
                            nu.tapX = HOME + dx * s; nu.tapY = HOME + dy * s; nu.tapAt = now
                            nu.fly(now, nu.tapX, nu.tapY)
                        }
                    },
            ) {
                val now = frame() / 1e6
                val pen = ComposePen(this, ARENA)
                pen.dot(HOME, HOME, HOME, Ink.K975)
                pen.ring(HOME, HOME, HOME - 1, Ink.K800, 2.0)
                // The ring where the arena was tapped.
                val since = now - nu.tapAt
                if (since in 0.0..700.0) {
                    val t = since / 700
                    pen.ring(nu.tapX, nu.tapY, 4 + 18 * t, Ink.CYAN, 2.0, 0.8 * (1 - t))
                }
                if (!nu.arrived && now - nu.flewAt >= FLY_MS) { nu.arrived = true; model.feel.buzz(15) }
                // Left alone, home to rest.
                if (nu.arrived && (nu.toX != HOME || nu.toY != HOME) && now - nu.flewAt > REST_MS) nu.fly(now, HOME, HOME)
                val (x, y0) = nu.at(now)
                val pet = now - nu.petAt
                val hop = if (pet in 0.0..500.0) sin(pet / 500 * PI) * 10 else 0.0
                val y = y0 - hop
                val breathe = 7.0 + 0.5 * sin(now / 700)
                pen.glow(x, y, breathe, Ink.ROSE, if (pet in 0.0..1200.0) 1.6 else 0.8)
                pen.dot(x, y, breathe, Ink.ROSE)
            }
            Pill("All games", Icon.GAMES, onGames)
        }
    }
}
