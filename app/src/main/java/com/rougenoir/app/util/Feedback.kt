package com.rougenoir.app.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.rougenoir.app.data.Prefs

/** Plays common sound and vibration cues when the player enables them in The Study. */
object Feedback {

    private var toneGenerator: ToneGenerator? = null
    private var toneFailed = false

    fun tap(context: Context) {
        tone(context, ToneGenerator.TONE_PROP_BEEP, 24)
        vibrate(context, 8)
    }

    fun select(context: Context) {
        tone(context, ToneGenerator.TONE_PROP_ACK, 34)
        vibrate(context, 12)
    }

    fun mark(context: Context) {
        tone(context, ToneGenerator.TONE_PROP_BEEP2, 30)
        vibrate(context, 12)
    }

    fun eat(context: Context) {
        tone(context, ToneGenerator.TONE_PROP_ACK, 55)
        vibrate(context, 18)
    }

    fun strike(context: Context) {
        tone(context, ToneGenerator.TONE_PROP_BEEP2, 26)
        vibrate(context, 10)
    }

    fun error(context: Context) {
        tone(context, ToneGenerator.TONE_PROP_NACK, 90)
        vibrate(context, 30)
    }

    fun win(context: Context) {
        tone(context, ToneGenerator.TONE_PROP_ACK, 180)
        vibrate(context, 55)
    }

    private fun tone(context: Context, type: Int, durationMs: Int) {
        if (!Prefs.soundEnabled(context) || toneFailed) return
        try {
            val generator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 38)
                .also { toneGenerator = it }
            generator.startTone(type, durationMs)
        } catch (_: RuntimeException) {
            toneFailed = true
        }
    }

    private fun vibrate(context: Context, durationMs: Long) {
        if (!Prefs.hapticsEnabled(context)) return
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, 70))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    // Newer devices expose the default vibrator through a manager. Keep the
    // direct service lookup for older Android versions supported by the app.
    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
