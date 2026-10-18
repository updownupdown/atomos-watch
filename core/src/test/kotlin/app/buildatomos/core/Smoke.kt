package app.buildatomos.core

/** A pen that draws nothing but checks every number it gets is a real one. */
class CheckPen : Pen {
    var calls = 0
    private fun ok(vararg v: Double) { calls++; v.forEach { require(it.isFinite()) { "not finite: ${v.toList()}" } } }
    override fun dot(x: Double, y: Double, r: Double, color: Int, alpha: Double) = ok(x, y, r, alpha)
    override fun ring(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double) = ok(x, y, r, width, alpha)
    override fun arc(x: Double, y: Double, r: Double, start: Double, sweep: Double, color: Int, width: Double, alpha: Double) = ok(x, y, r, start, sweep, width, alpha)
    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double, alpha: Double) = ok(x1, y1, x2, y2, width, alpha)
    override fun poly(xs: DoubleArray, ys: DoubleArray, n: Int, color: Int, width: Double, alpha: Double) { for (i in 0 until n) ok(xs[i], ys[i]); ok(width, alpha) }
    override fun dashed(x: Double, y: Double, r: Double, color: Int, width: Double, alpha: Double, n: Int) = ok(x, y, r, width, alpha)
    override fun rect(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, alpha: Double) = ok(x, y, w, h, radius, alpha)
    override fun rectRing(x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, width: Double, alpha: Double) = ok(x, y, w, h, radius, width, alpha)
}

/**
 * Plays a game for `ms` with random taps, drags and crown turns at about `rate` per second,
 * pressing Next whenever it waits. Returns the stage (ended, or not).
 */
fun play(game: Game, ms: Double = 120_000.0, rate: Double = 3.0, seed: Int = 1, idle: Boolean = false): Stage {
    val r = seeded(seed)
    val s = Stage()
    val t = game.make()
    val pen = CheckPen()
    t.enter(s)
    var now = 1000.0
    s.now = now
    var held = false
    while (now < ms && s.ended == null) {
        val dt = 16.0 + r() * 4
        now += dt
        s.now = now
        if (!idle && r() < rate * dt / 1000) {
            val p = Pt(s.c.x + (r() * 2 - 1) * s.R * 0.9, s.c.y + (r() * 2 - 1) * s.R * 0.9)
            when ((r() * 4).toInt()) {
                0 -> { t.down(s, p); t.up(s, p) }
                1 -> { t.down(s, p); held = true }
                2 -> if (held) { t.move(s, p); if (r() < 0.3) { t.up(s, p); held = false } }
                else -> t.crown(s, (r() * 2 - 1) * 0.8)
            }
        }
        s.waiting?.let { go -> if (r() < 0.05) { s.waiting = null; go() } }
        t.step(s, dt, now)
        t.draw(s, pen, now)
        s.fx.draw(pen, now, dt)
        require(s.shown.isFinite())
    }
    return s
}
