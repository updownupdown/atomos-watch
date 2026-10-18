package app.buildatomos.core

import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** A pen that writes SVG, for looking at the games and clouds off the watch (RENDER=1). */
class SvgPen(private val size: Double = SCREEN) : Pen {
    private val sb = StringBuilder()
    private fun c(color: Int) = "#%06x".format(color and 0xffffff)
    private fun f(v: Double) = "%.2f".format(v)
    override fun dot(x: Double, y: Double, r: Double, color: Int, alpha: Double) {
        sb.append("<circle cx='${f(x)}' cy='${f(y)}' r='${f(r)}' fill='${c(color)}' fill-opacity='${f(alpha)}'/>")
    }
    override fun ring(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double) {
        sb.append("<circle cx='${f(x)}' cy='${f(y)}' r='${f(r)}' fill='none' stroke='${c(color)}' stroke-width='${f(width)}' stroke-opacity='${f(alpha)}'/>")
    }
    override fun arc(x: Double, y: Double, r: Double, start: Double, sweep: Double, color: Int, width: Double, alpha: Double) {
        val x0 = x + cos(start) * r; val y0 = y + sin(start) * r
        val x1 = x + cos(start + sweep) * r; val y1 = y + sin(start + sweep) * r
        val large = if (kotlin.math.abs(sweep) > Math.PI) 1 else 0
        val sw = if (sweep >= 0) 1 else 0
        sb.append("<path d='M${f(x0)} ${f(y0)} A${f(r)} ${f(r)} 0 $large $sw ${f(x1)} ${f(y1)}' fill='none' stroke='${c(color)}' stroke-width='${f(width)}' stroke-linecap='round' stroke-opacity='${f(alpha)}'/>")
    }
    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double, alpha: Double) {
        sb.append("<line x1='${f(x1)}' y1='${f(y1)}' x2='${f(x2)}' y2='${f(y2)}' stroke='${c(color)}' stroke-width='${f(width)}' stroke-linecap='round' stroke-opacity='${f(alpha)}'/>")
    }
    override fun poly(xs: DoubleArray, ys: DoubleArray, n: Int, color: Int, width: Double, alpha: Double) {
        val pts = (0 until n).joinToString(" ") { "${f(xs[it])},${f(ys[it])}" }
        sb.append("<polyline points='$pts' fill='none' stroke='${c(color)}' stroke-width='${f(width)}' stroke-linecap='round' stroke-linejoin='round' stroke-opacity='${f(alpha)}'/>")
    }
    override fun dashed(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double, n: Int) {
        val k = if (n > 0) n else maxOf(6, (TAU * r / 14).toInt())
        val step = TAU * r / k
        sb.append("<circle cx='${f(x)}' cy='${f(y)}' r='${f(r)}' fill='none' stroke='${c(color)}' stroke-width='${f(width)}' stroke-dasharray='${f(step * 0.55)} ${f(step * 0.45)}' stroke-linecap='round' stroke-opacity='${f(alpha)}'/>")
    }
    override fun rect(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, alpha: Double) {
        sb.append("<rect x='${f(x)}' y='${f(y)}' width='${f(w)}' height='${f(h)}' rx='${f(radius)}' fill='${c(color)}' fill-opacity='${f(alpha)}'/>")
    }
    override fun rectRing(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, width: Double, alpha: Double) {
        sb.append("<rect x='${f(x)}' y='${f(y)}' width='${f(w)}' height='${f(h)}' rx='${f(radius)}' fill='none' stroke='${c(color)}' stroke-width='${f(width)}' stroke-opacity='${f(alpha)}'/>")
    }
    fun save(file: File) {
        file.parentFile.mkdirs()
        val h = size / 2
        file.writeText("<svg xmlns='http://www.w3.org/2000/svg' width='360' height='360' viewBox='0 0 $size $size'><clipPath id='w'><circle cx='$h' cy='$h' r='$h'/></clipPath><circle cx='$h' cy='$h' r='$h' fill='#000'/><g clip-path='url(#w)'>$sb</g></svg>")
    }
}
