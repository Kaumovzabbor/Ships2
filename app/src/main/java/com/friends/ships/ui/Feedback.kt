package com.friends.ships.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Ovoz va tebranish. Hammasi try/catch ichida: qurilmada bo'lmasa ham o'yin to'xtamaydi. */
class Feedback(ctx: Context) {
    private val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private var tone: ToneGenerator? = null

    private fun gen(): ToneGenerator? {
        if (tone == null) {
            tone = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            } catch (e: Exception) {
                null
            }
        }
        return tone
    }

    fun play(kind: String, sound: Boolean, vibrate: Boolean) {
        if (sound) {
            val t = when (kind) {
                "MISS" -> ToneGenerator.TONE_CDMA_PIP
                "HIT" -> ToneGenerator.TONE_PROP_BEEP2
                "SUNK" -> ToneGenerator.TONE_CDMA_CONFIRM
                "TURN" -> ToneGenerator.TONE_PROP_ACK
                "WIN" -> ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE
                else -> -1
            }
            if (t != -1) try {
                gen()?.startTone(t, 250)
            } catch (_: Exception) {
            }
        }
        if (vibrate) {
            val ms = when (kind) {
                "HIT" -> 60L
                "SUNK" -> 250L
                "WIN" -> 400L
                else -> 0L
            }
            if (ms > 0) try {
                if (Build.VERSION.SDK_INT >= 26) {
                    vib?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib?.vibrate(ms)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        try {
            tone?.release()
        } catch (_: Exception) {
        }
        tone = null
    }
}
