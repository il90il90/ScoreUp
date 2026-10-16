package com.scoreup.wear

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short, distinct haptics so scoring can be confirmed without looking at the watch. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    fun score(points: Int) = play(if (points >= 3) longArrayOf(0, 30, 60, 30, 60, 30) else if (points == 2) longArrayOf(0, 35, 70, 35) else longArrayOf(0, 40))
    fun undo() = play(longArrayOf(0, 120))
    fun tick() = play(longArrayOf(0, 15))
    fun buzzer() = play(longArrayOf(0, 500))

    private fun play(p: LongArray) { runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(p, -1)) } }
}
