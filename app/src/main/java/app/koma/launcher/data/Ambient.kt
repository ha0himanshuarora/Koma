package app.koma.launcher.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Built-in background sounds for focus sessions: rain, waves, white noise and city. They are synthesised on
 * the phone (no audio files in the app) as an eight second loop that repeats seamlessly.
 */
object Ambient {
    val names = listOf("RAIN", "WAVES", "NOISE", "CITY")

    /** Index of the sound that is playing, or -1. Compose state, so the UI follows it. */
    var playing by mutableIntStateOf(-1)
        private set

    private const val RATE = 22050
    private const val SECONDS = 8
    private var track: AudioTrack? = null
    private var token = 0
    private val cache = HashMap<Int, ShortArray>()

    fun play(kind: Int) {
        val mine = ++token
        release()
        playing = kind
        Thread {
            val pcm = synchronized(cache) { cache.getOrPut(kind) { generate(kind) } }
            if (mine != token) return@Thread
            try {
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                t.write(pcm, 0, pcm.size)
                t.setLoopPoints(0, pcm.size, -1)
                synchronized(this) {
                    if (mine != token) {
                        t.release()
                        return@Thread
                    }
                    track = t
                }
                t.play()
            } catch (e: Exception) {
            }
        }.start()
    }

    fun stop() {
        token++
        release()
        playing = -1
    }

    private fun release() {
        synchronized(this) {
            track?.let {
                try {
                    it.stop()
                } catch (e: Exception) {
                }
                it.release()
            }
            track = null
        }
    }

    private fun generate(kind: Int): ShortArray {
        val n = RATE * SECONDS
        val rnd = Random(kind * 7919 + 13)
        val out = FloatArray(n)
        val tau = 2.0 * PI
        when (kind) {
            0 -> { // rain: bright hiss plus tiny drops
                var lp = 0f
                for (i in 0 until n) {
                    val w = rnd.nextFloat() * 2f - 1f
                    lp += 0.3f * (w - lp)
                    out[i] = (w - lp) * 0.7f + lp * 0.15f
                }
                repeat(SECONDS * 70) {
                    val start = rnd.nextInt(n - 800)
                    val f = 1800.0 + rnd.nextDouble() * 2600.0
                    val amp = 0.15f + rnd.nextFloat() * 0.25f
                    for (k in 0 until 700) out[start + k] += (sin(tau * f * k / RATE) * exp(-k / 90.0) * amp).toFloat()
                }
            }
            1 -> { // waves: surf that swells and fades twice per loop
                var a = 0f
                var b = 0f
                for (i in 0 until n) {
                    val w = rnd.nextFloat() * 2f - 1f
                    a += 0.04f * (w - a)
                    b += 0.25f * (w - b)
                    val swell = (0.5 - 0.5 * kotlin.math.cos(tau * 2 * i / n)).pow(1.6).toFloat()
                    val env = 0.12f + 0.88f * swell
                    out[i] = (a * 3.2f + (b - a) * 0.9f * swell) * env
                }
            }
            2 -> { // white noise
                for (i in 0 until n) out[i] = (rnd.nextFloat() * 2f - 1f) * 0.6f
            }
            else -> { // city: low traffic rumble, a hum and a few cars going by
                var brown = 0f
                var lp = 0f
                for (i in 0 until n) {
                    val w = rnd.nextFloat() * 2f - 1f
                    brown = (brown + 0.02f * w) / 1.02f
                    lp += 0.08f * (w - lp)
                    val slow = 0.8f + 0.2f * sin(tau * i / n).toFloat()
                    out[i] = brown * 4.0f * slow + sin(tau * 118.0 * i / RATE).toFloat() * 0.05f
                    out[i] += lp * 0.1f
                }
                val passes = listOf(0.12, 0.45, 0.78)
                for (p in passes) {
                    val centre = (p * n).toInt()
                    var l = 0f
                    for (k in -RATE until RATE) {
                        val idx = ((centre + k) % n + n) % n
                        val env = exp(-(k.toDouble() * k) / (RATE.toDouble() * RATE * 0.25)).toFloat()
                        val w = rnd.nextFloat() * 2f - 1f
                        l += 0.12f * (w - l)
                        out[idx] += l * env * 1.2f
                    }
                }
            }
        }
        var peak = 0f
        for (v in out) if (abs(v) > peak) peak = abs(v)
        val scale = if (peak > 0f) 0.8f / peak else 1f
        return ShortArray(n) { (out[it] * scale * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort() }
    }
}
