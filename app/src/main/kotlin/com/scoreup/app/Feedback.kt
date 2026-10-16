package com.scoreup.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.scoreup.app.data.AppSettings

/** Optional arcade sounds + haptics, both user-toggleable. */
class Feedback(context: Context, private val settings: AppSettings) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    private val tones by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }

    fun score(points: Int) {
        if (settings.state.value.haptics) vibe(if (points >= 3) longArrayOf(0, 25, 40, 25) else longArrayOf(0, 30))
        if (settings.state.value.sounds) tones?.startTone(if (points >= 3) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, 90)
    }

    fun tick() {
        if (settings.state.value.haptics) vibe(longArrayOf(0, 12))
    }

    fun undo() {
        if (settings.state.value.haptics) vibe(longArrayOf(0, 15, 50, 15))
        if (settings.state.value.sounds) tones?.startTone(ToneGenerator.TONE_PROP_NACK, 120)
    }

    fun buzzer() {
        if (settings.state.value.haptics) vibe(longArrayOf(0, 400))
        if (settings.state.value.sounds) tones?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900)
    }

    fun win() {
        if (settings.state.value.haptics) vibe(longArrayOf(0, 60, 80, 60, 80, 160))
        if (settings.state.value.sounds) tones?.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 600)
    }

    private fun vibe(pattern: LongArray) {
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }
}
