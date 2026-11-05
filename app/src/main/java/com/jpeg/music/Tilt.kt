package com.jpeg.music

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/** Device tilt from the gravity sensor. x: right edge down = +. y: relative forward/back tilt. Both in -1..1. */
object Tilt : SensorEventListener {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    private var sm: SensorManager? = null
    private var on = false
    private var fx = 0f
    private var fz = 0f
    private var base = Float.NaN

    fun start(ctx: Context) {
        if (on) return
        val m = ctx.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = m.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm = m
        on = m.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm?.unregisterListener(this)
        on = false
        base = Float.NaN
        fx = 0f; fz = 0f
        x = 0f; y = 0f
    }

    override fun onSensorChanged(e: SensorEvent) {
        val sign = if (Look.gyroInvert) -1f else 1f
        fx += 0.15f * ((-e.values[0] / 9.81f) - fx)
        val z = e.values[2]
        if (base.isNaN()) base = z
        base += 0.004f * (z - base)
        fz += 0.15f * (((z - base) / 5f) - fz)
        x = (fx * sign).coerceIn(-1f, 1f)
        y = (fz * sign).coerceIn(-1f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
