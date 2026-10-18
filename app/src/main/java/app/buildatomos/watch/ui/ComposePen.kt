// The core's Pen on a Compose canvas: court units in, pixels out.

package app.buildatomos.watch.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.buildatomos.core.Pen
import app.buildatomos.core.SCREEN
import app.buildatomos.core.TAU
import kotlin.math.max
import kotlin.math.min

/** Draws in court units (SCREEN across) onto the square at the middle of `scope`; or, `wide`,
 *  `units` across the whole width from the top left, for bars and rows of pips. */
class ComposePen(private val scope: DrawScope, units: Double = SCREEN, wide: Boolean = false) : Pen {
    private val k: Float
    private val ox: Float
    private val oy: Float

    init {
        if (wide) {
            k = (scope.size.width / units).toFloat()
            ox = 0f
            oy = 0f
        } else {
            val side = min(scope.size.width, scope.size.height)
            k = (side / units).toFloat()
            ox = (scope.size.width - side) / 2
            oy = (scope.size.height - side) / 2
        }
    }

    private fun X(x: Double) = ox + x.toFloat() * k
    private fun Y(y: Double) = oy + y.toFloat() * k
    private fun L(v: Double) = v.toFloat() * k
    private fun col(c: Int, a: Double) = Color(c).copy(alpha = (Color(c).alpha * a.toFloat()).coerceIn(0f, 1f))

    override fun dot(x: Double, y: Double, r: Double, color: Int, alpha: Double) {
        if (r <= 0 || alpha <= 0) return
        scope.drawCircle(col(color, alpha), L(r), Offset(X(x), Y(y)))
    }

    override fun ring(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double) {
        if (r <= 0 || alpha <= 0) return
        scope.drawCircle(col(color, alpha), L(r), Offset(X(x), Y(y)), style = Stroke(L(width)))
    }

    override fun arc(x: Double, y: Double, r: Double, start: Double, sweep: Double, color: Int, width: Double, alpha: Double) {
        if (r <= 0 || alpha <= 0) return
        val rr = L(r)
        scope.drawArc(
            col(color, alpha),
            Math.toDegrees(start).toFloat(),
            Math.toDegrees(sweep).toFloat(),
            useCenter = false,
            topLeft = Offset(X(x) - rr, Y(y) - rr),
            size = Size(rr * 2, rr * 2),
            style = Stroke(L(width), cap = StrokeCap.Round),
        )
    }

    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double, alpha: Double) {
        if (alpha <= 0) return
        scope.drawLine(col(color, alpha), Offset(X(x1), Y(y1)), Offset(X(x2), Y(y2)), L(width), StrokeCap.Round)
    }

    override fun poly(xs: DoubleArray, ys: DoubleArray, n: Int, color: Int, width: Double, alpha: Double) {
        if (n < 2 || alpha <= 0) return
        val path = Path()
        path.moveTo(X(xs[0]), Y(ys[0]))
        for (i in 1 until n) path.lineTo(X(xs[i]), Y(ys[i]))
        scope.drawPath(path, col(color, alpha), style = Stroke(L(width), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    override fun dashed(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double, n: Int) {
        if (r <= 0 || alpha <= 0) return
        val count = if (n > 0) n else max(6, (TAU * r / 14).toInt())
        val step = L(TAU * r / count)
        scope.drawCircle(
            col(color, alpha), L(r), Offset(X(x), Y(y)),
            style = Stroke(L(width), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(step * 0.55f, step * 0.45f))),
        )
    }

    override fun rect(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, alpha: Double) {
        if (alpha <= 0) return
        scope.drawRoundRect(col(color, alpha), Offset(X(x), Y(y)), Size(L(w), L(h)), CornerRadius(L(radius)))
    }

    override fun rectRing(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, width: Double, alpha: Double) {
        if (alpha <= 0) return
        scope.drawRoundRect(col(color, alpha), Offset(X(x), Y(y)), Size(L(w), L(h)), CornerRadius(L(radius)), style = Stroke(L(width)))
    }

    /** Text centered on (x, y), `size` tall in court units, in Outfit. Not part of the core's Pen:
     *  games draw no text. */
    fun text(measurer: TextMeasurer, s: String, x: Double, y: Double, size: Double, color: Int, weight: FontWeight = FontWeight.Medium, alpha: Double = 1.0) {
        if (s.isEmpty() || alpha <= 0) return
        val sp = (L(size) / (scope.density * scope.fontScale)).sp
        val laid = measurer.measure(s, TextStyle(color = col(color, alpha), fontSize = sp, fontFamily = Outfit, fontWeight = weight))
        scope.drawText(laid, topLeft = Offset(X(x) - laid.size.width / 2f, Y(y) - laid.size.height / 2f))
    }
}
