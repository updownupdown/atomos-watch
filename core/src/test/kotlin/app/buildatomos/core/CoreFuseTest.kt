package app.buildatomos.core

import app.buildatomos.core.games.Core
import app.buildatomos.core.games.Fuse
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Core and Fuse in levels: a player who plays them well clears them, and heat makes Core
 *  harder; Fuse drops its atom by itself, after a pale pulse, with no clock on the rim. */
class CoreFuseTest {
    /** Plays `toy` frame by frame for up to `ms`, letting `bot` play; returns the stage. */
    private fun <T : Toy> run(toy: T, dial: Int, goal: Int?, ms: Double = 400_000.0, bot: (T, Stage) -> Unit = { _, _ -> }): Stage {
        val s = Stage()
        s.dial = dial
        s.goal = goal
        val pen = CheckPen()
        var now = 1000.0
        s.now = now
        toy.enter(s)
        while (now < ms && !s.over) {
            now += 16.0
            s.now = now
            bot(toy, s)
            toy.step(s, 16.0, now)
            toy.draw(s, pen, now)
            s.fx.draw(pen, now, 16.0)
            require(s.part in 0.0..1.0) { "part ${s.part}" }
        }
        return s
    }

    // ---- Core ----

    /** How many reds turning ring `g` by `d` from `rot` carries under Nu before its gap takes Nu. */
    private fun redsOnTheWay(t: Core, s: Stage, g: Core.Ring, rot: Double, d: Double): Int {
        val nh = t.nuHalf(s, g)
        val hit = mutableSetOf<Core.Arc>()
        val n = (abs(d) / 0.02).toInt() + 1
        for (i in 0..n) {
            val a = Core.NU - (rot + d * i / n)
            if (g.gaps.any { abs(turn(it.c, a)) < it.half - nh * 0.55 }) break
            g.reds.filter { abs(turn(it.c, a)) < it.half + nh * 0.7 + 0.04 }.forEach { hit += it }
        }
        return hit.size
    }

    /** The best turn of ring `g` from `rot` into a gap: the shortest that carries the fewest reds
     *  under Nu, and how many it carries. */
    private fun bestTurn(t: Core, s: Stage, g: Core.Ring, rot: Double): Pair<Double, Int> =
        g.gaps.flatMap { gap ->
            val d = turn(rot, Core.NU - gap.c)
            listOf(d, d - sign(d) * TAU)
        }.map { it to redsOnTheWay(t, s, g, rot, it) }.minBy { (d, reds) -> reds * 100 + abs(d) }

    /**
     * Turns the ring Nu is on toward its best gap, a quarter of a second after landing and no
     * faster than a quick hand turns the crown (about a third of a turn a second). Just before Nu drops,
     * it looks at the ring below: if Nu would land on a red there, or with reds both ways to its
     * gap, it waits at the gap's edge for the ring below to come round (unless time runs short).
     */
    private fun corer(): (Core, Stage) -> Unit {
        var since = -1.0
        return { t, s ->
            if (!t.steering) since = -1.0
            else {
                if (since < 0) since = s.now
                val g = t.rings[t.k]
                if (s.now - since > 250) {
                    val (d0, _) = bestTurn(t, s, g, g.want)
                    val gap = g.gaps.minBy { abs(turn(g.want + d0, Core.NU - it.c)) }
                    // How far the ring can turn before Nu is in the gap.
                    val edge = max0(abs(d0) - (gap.half - t.nuHalf(s, g) * 0.55) + 0.02)
                    val below = t.rings.getOrNull(t.k + 1)
                    var hold = false
                    if (below != null && edge < 0.12 && (s.rim ?: 0.0) > 0.2) {
                        // Where the ring below will be when Nu lands on it.
                        val lag = abs(g.want - g.rot) * 70 / 0.5 + 300
                        val rot = below.rot + below.drift * lag
                        val a = Core.NU - rot
                        val nh = t.nuHalf(s, below)
                        val onRed = below.reds.any { abs(turn(it.c, a)) < it.half + nh * 0.7 + 0.08 }
                        hold = onRed || bestTurn(t, s, below, rot).second > 0
                    }
                    val step = if (hold) clamp(edge, 0.0, 0.03) else 0.03
                    if (step > 0) t.crown(s, sign(d0) * minOf(step, abs(d0)))
                }
            }
        }
    }

    private fun max0(v: Double) = if (v > 0) v else 0.0

    @Test fun coreClears() {
        val won = (1..5).count { run(Core(), 1, 3, bot = corer()).result == Result.WON }
        assertTrue(won >= 4, "won $won of 5 at Very easy")
        val hard = (1..5).count { run(Core(), 100, 7, bot = corer()).level >= 1 }
        assertTrue(hard >= 3, "Expert cleared a level $hard times of 5")
    }

    // ---- Fuse ----

    /**
     * Drops each atom where it will land on one of its size if it can (the nearer the middle of
     * that one the better), else where it lands lowest, keeping off smaller ones; with a drag,
     * a third of a second after Nu has the atom.
     */
    private fun fuser(): (Fuse, Stage) -> Unit {
        var had: Int? = null
        var since = 0.0
        var done = false
        return { t, s ->
            val h = t.holding
            if (h != had || h == null) { had = h; since = s.now; done = false }
            if (h != null && !done && s.now - since > 330) {
                val rh = Fuse.size(h)
                var best = s.c.x
                var score = -1e9
                var x = s.c.x - 100
                while (x <= s.c.x + 100) {
                    var y = 256.0 - rh
                    var hit: Fuse.Atom? = null
                    for (a in t.pile) {
                        val dx = abs(a.x - x)
                        if (dx >= a.r + rh) continue
                        val cy = a.y - kotlin.math.sqrt((a.r + rh) * (a.r + rh) - dx * dx)
                        if (cy < y) { y = cy; hit = a }
                    }
                    val sc = when {
                        hit != null && hit.rank == h -> 1000 + y * 0.1 - abs(hit.x - x)
                        hit != null && hit.rank < h -> y - 40
                        else -> y
                    }
                    if (sc > score) { score = sc; best = x }
                    x += 4.0
                }
                t.down(s, Pt(s.c.x, 170.0))
                t.move(s, Pt(best, 150.0))
                t.up(s, Pt(best, 150.0))
                done = true
            }
        }
    }

    @Test fun fuseClears() {
        val won = (1..5).count { run(Fuse(), 50, 3, bot = fuser()).result == Result.WON }
        assertTrue(won >= 4, "won $won of 5 at Easy")
        val hard = (1..5).count { run(Fuse(), 100, 7, bot = fuser()).level >= 1 }
        assertTrue(hard >= 3, "Hard cleared a level $hard times of 5")
    }

    /** The pile is lively: an atom set down near the top of a bigger one rolls off it (even dead
     *  on top), and an atom dropped onto the floor bounces back up a little before it rests. */
    @Test fun fuseAtomsRollOffAndBounce() {
        for (off in listOf(0.0, 0.5, 3.0)) {
            val s = Stage()
            val f = Fuse()
            s.now = 1000.0
            f.enter(s)
            val big = Fuse.size(3)
            val small = Fuse.size(1)
            @Suppress("UNCHECKED_CAST") val pile = f.pile as MutableList<Fuse.Atom>
            pile += Fuse.Atom(s.c.x, 256 - big, 0.0, 0.0, 3, 0.0, 0.0)
            pile += Fuse.Atom(s.c.x + off, 256 - 2 * big - small, 0.0, 0.0, 1, 0.0, 0.0)
            var offAt: Double? = null
            var t = 0.0
            while (t < 3000 && offAt == null) {
                t += 16; s.now += 16
                f.step(s, 16.0, s.now)
                if (pile[1].y > 256 - 2 * big) offAt = t
            }
            assertTrue(offAt != null && offAt < 1500, "offset $off: rolled off at $offAt ms")
        }
        val s = Stage()
        val f = Fuse()
        s.now = 1000.0
        f.enter(s)
        @Suppress("UNCHECKED_CAST") val pile = f.pile as MutableList<Fuse.Atom>
        val r = Fuse.size(2)
        pile += Fuse.Atom(s.c.x, 100.0, 0.0, 0.05, 2, 0.0, 0.0)
        var landed = false
        var top = 999.0
        repeat(300) {
            s.now += 16
            f.step(s, 16.0, s.now)
            if (pile[0].y + r >= 255.5) landed = true
            if (landed) top = minOf(top, pile[0].y)
        }
        assertTrue(256 - r - top > 3, "bounced ${256 - r - top} units")
        assertTrue(abs(pile[0].vy) < 0.003 && abs(pile[0].y + r - 256) < 0.5, "at rest after ~5 s")
    }

    /** Left alone, Nu holds the atom, it pulses pale three times at the end of the wait, and Nu
     *  drops it; no clock is shown on the rim. */
    @Test fun fuseDropsAfterThreePalePulses() {
        val s = Stage()
        s.dial = 1
        val f = Fuse()
        f.enter(s)
        val limit = f.limit(s)
        var now = 1000.0
        var peaks = 0
        var up = false
        var droppedAt: Double? = null
        while (droppedAt == null && now < 20_000) {
            now += 16.0
            s.now = now
            f.step(s, 16.0, now)
            f.draw(s, CheckPen(), now)
            assertEquals(null, s.rim, "no clock on the rim")
            val p = f.pale(s)
            if (now - 1000 < limit - 3 * 300 - 16) assertEquals(0.0, p, "pale at ${now - 1000} ms")
            if (p > 0.9 && !up) peaks++
            up = p > 0.9
            if (f.pile.isNotEmpty()) droppedAt = now - 1000
        }
        assertEquals(3, peaks, "pulses before the drop")
        assertTrue(abs(droppedAt!! - limit) <= 32, "dropped at $droppedAt, not $limit")
    }

    /** A player who drops quickly never sees the pulse. */
    @Test fun fuseQuickPlayerSeesNoPulse() {
        var most = 0.0
        val bot = fuser()
        run(Fuse(), 50, null, 90_000.0) { t, s -> bot(t, s); most = maxOf(most, t.pale(s)) }
        assertEquals(0.0, most)
    }

    /** The line sits at the bottom of the biggest atom Nu may hold: the pile can grow that tall. */
    @Test fun fuseLineIsTheHeldAtomsBottom() {
        for ((dial, most) in listOf(1 to 3, 100 to 4)) {
            val s = Stage()
            s.dial = dial
            val f = Fuse()
            f.enter(s)
            assertEquals(50 + s.nu.r + 3 + 2 * Fuse.size(most), f.lineY(s), 1e-9)
            assertTrue(f.lineY(s) < 110, "line at ${f.lineY(s)}")
        }
    }

    /** Core is felt hotter than the dial: Easy (the five-level Medium) plays as Expert used to,
     *  Hard (the old Expert) far past it. */
    @Test fun coreHeat() {
        val medium = (Difficulty.dialOf(0) - 1) / 99.0
        val expert = (Difficulty.dialOf(2) - 1) / 99.0
        assertTrue(Core.pace(medium) in 0.85..0.95, "Easy at ${Core.pace(medium)}")
        assertTrue(Core.pace(expert) >= 1.45, "Hard at ${Core.pace(expert)}")
        assertTrue(Core.pace(0.0) < 0.2)
    }
}
