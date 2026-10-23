package com.scoreup.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.scoreup.core.HeartTracker
import com.scoreup.shared.WearSync.HeartReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Live heart rate from the watch's optical sensor (needs BODY_SENSORS). Runs only between
 * [start] and [stop] (the activity's resume/pause), so it never drains the battery in the background.
 */
class HeartRateMonitor(context: Context) : SensorEventListener {
    private val manager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private var running = false

    private val _readings = MutableStateFlow<HeartReading?>(null)
    /** Latest valid reading; null while stopped or without skin contact. */
    val readings: StateFlow<HeartReading?> = _readings.asStateFlow()

    val available: Boolean get() = sensor != null

    fun start() {
        if (running || sensor == null) return
        running = runCatching { manager!!.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) }.getOrDefault(false)
    }

    fun stop() {
        if (running) runCatching { manager?.unregisterListener(this) }
        running = false
        _readings.value = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) { _readings.value = null; return }
        val bpm = event.values.firstOrNull()?.roundToInt() ?: return
        // The sensor reports 0 while it is still locking on.
        if (bpm in HeartTracker.MIN_BPM..HeartTracker.MAX_BPM) _readings.value = HeartReading(bpm, System.currentTimeMillis())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
