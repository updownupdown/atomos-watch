// What a game feels and sounds like on the wrist. Taps are on by default and kept light: a soft
// tick for a catch, a soft double tick for a miss, nothing for the small ticks of a clock. Sound
// is off by default; on, it is a dry pluck from a pentatonic scale (no echo, no loops), pitched by
// what happened, as the phone's hit notes are.

package app.buildatomos.watch

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.buildatomos.core.Out
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class Feel(private val context: Context, private val store: Store) : Out {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private var lastTap = 0L
    private val main = Handler(Looper.getMainLooper())

    /** One light tap, at most one every 70 ms so a busy game doesn't buzz. */
    private fun tap(strength: Float, twice: Boolean = false) {
        if (!store.haptics) return
        val v = vibrator ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastTap < 70) return
        lastTap = now
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val effect = if (v.areAllPrimitivesSupported(tick)) {
            val c = VibrationEffect.startComposition().addPrimitive(tick, strength)
            if (twice) c.addPrimitive(tick, strength, 70)
            c.compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        }
        try { v.vibrate(effect) } catch (_: Exception) { }
    }

    override fun capture() { tap(0.45f); note(walk()) }
    override fun hit(x: Double) { tap(0.4f); note((x.coerceIn(0.0, 1.0) * (NOTES.size - 3)).toInt() + 1) }
    override fun miss() { tap(0.55f, twice = true); note(-1) }
    override fun tick(n: Int) { note(listOf(0, 2, 4)[Math.floorMod(n, 3)], 0.35f) }
    override fun chime(i: Int) { tap(0.35f); note(listOf(1, 3, 5, 7, 9)[Math.floorMod(i, 5)]) }
    override fun complete() {
        tap(0.5f)
        listOf(4, 6, 8).forEachIndexed { k, n -> main.postDelayed({ note(n) }, k * 90L) }
    }
    override fun buzz(ms: Int) { if (ms >= 15) tap(0.35f) }

    // ---- Sound -----------------------------------------------------------------------------------

    /** A pentatonic scale over about two octaves (A minor), then a low note for misses. */
    private val NOTES = listOf(57, 60, 62, 64, 67, 69, 72, 74, 76, 79, 81)
    private val LOW = 45
    private var melody = 4
    private fun walk(): Int {
        melody = (melody + Random.nextInt(-2, 3)).coerceIn(2, NOTES.size - 2)
        return melody
    }

    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>()

    /** Plays scale step `i` (-1 for the low miss note), if sound is on. */
    private fun note(i: Int, volume: Float = 0.6f) {
        if (!store.sound) return
        val p = pool ?: load()
        val id = ids[i] ?: return
        p.play(id, volume, volume, 1, 0, 1f)
    }

    /** Makes the plucks once (short WAVs in the cache) and loads them. */
    private fun load(): SoundPool {
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        pool = p
        val dir = File(context.cacheDir, "plucks").apply { mkdirs() }
        (listOf(-1) + NOTES.indices).forEach { i ->
            val midi = if (i < 0) LOW else NOTES[i]
            val f = File(dir, "n$midi.wav")
            if (!f.exists()) writePluck(f, 440.0 * 2.0.pow((midi - 69) / 12.0), if (i < 0) 0.5 else 0.32)
            ids[i] = p.load(f.path, 1)
        }
        return p
    }

    fun release() { pool?.release(); pool = null; ids.clear() }

    private fun writePluck(file: File, hz: Double, seconds: Double) {
        val rate = 22050
        val n = (rate * seconds).toInt()
        val pcm = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val env = minOf(1.0, t / 0.004) * exp(-t / 0.09)
            val v = (sin(2 * PI * hz * t) + 0.25 * sin(4 * PI * hz * t) + 0.08 * sin(6 * PI * hz * t)) * env * 0.42
            pcm.putShort((v * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort())
        }
        val data = pcm.array()
        val head = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        FileOutputStream(file).use { it.write(head); it.write(data) }
    }
}
