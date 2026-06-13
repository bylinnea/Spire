package no.bylinnea.spire.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import no.bylinnea.spire.R
import no.bylinnea.spire.data.Plant

/**
 * Encapsulates the light-meter card on the plant detail screen: reads the
 * ambient light sensor, shows lux / foot-candle values, and compares the
 * reading against the plant's stated light preference.
 *
 * Owns the sensor listener lifecycle, the host activity must call [stop] from
 * onPause. Call [bind] whenever the displayed plant changes.
 */
class LightMeterController(private val activity: Activity) {

    private val sensorManager =
        activity.getSystemService(Activity.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var listener: SensorEventListener? = null

    private fun <T : View> find(id: Int): T = activity.findViewById(id)

    @SuppressLint("SetTextI18n")
    fun bind(plant: Plant) {
        if (lightSensor == null) {
            find<View>(R.id.lightMeterCard).visibility = View.GONE
            return
        }

        val pref = plant.lightPreference
        find<View>(R.id.rowLightPreference).visibility =
            if (!pref.isNullOrBlank()) View.VISIBLE else View.GONE
        if (!pref.isNullOrBlank()) find<TextView>(R.id.detailLightPreference).text = pref

        val temp = plant.temperaturePreference
        find<View>(R.id.rowTemperaturePreference).visibility =
            if (!temp.isNullOrBlank()) View.VISIBLE else View.GONE
        if (!temp.isNullOrBlank()) find<TextView>(R.id.detailTemperaturePreference).text = temp

        val btnMeasure = find<TextView>(R.id.btnMeasureLight)
        val btnStop    = find<TextView>(R.id.btnStopLight)
        val container  = find<View>(R.id.lightReadingContainer)
        val luxView    = find<TextView>(R.id.lightLuxValue)
        val labelView  = find<TextView>(R.id.lightLabel)
        val fcView     = find<TextView>(R.id.lightFcValue)
        val matchView  = find<TextView>(R.id.lightMatchIndicator)

        btnMeasure.setOnClickListener {
            container.visibility  = View.VISIBLE
            btnMeasure.visibility = View.GONE
            listener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                @SuppressLint("SetTextI18n")
                override fun onSensorChanged(event: SensorEvent) {
                    val lux = event.values[0]
                    luxView.text   = "${lux.toInt()} lux"
                    fcView.text    = "(${(lux / 10.764f).toInt()} fc)"
                    labelView.text = luxToLabel(lux)
                    val p = plant.lightPreference
                    if (!p.isNullOrBlank()) {
                        matchView.visibility = View.VISIBLE
                        if (lightLabelMatches(lux, p)) {
                            matchView.text = "✅ Matches this plant's preference"
                            matchView.setTextColor(
                                ContextCompat.getColor(activity, R.color.status_ok_dot))
                        } else {
                            matchView.text = "⚠️ This plant prefers $p"
                            matchView.setTextColor(
                                ContextCompat.getColor(activity, R.color.amber))
                        }
                    } else matchView.visibility = View.GONE
                }
            }
            sensorManager.registerListener(
                listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        btnStop.setOnClickListener { stop() }
    }

    fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
        find<View>(R.id.lightReadingContainer).visibility = View.GONE
        find<TextView>(R.id.btnMeasureLight).visibility   = View.VISIBLE
    }

    private fun luxToLabel(lux: Float) = when {
        lux < 50    -> "Very low light"
        lux < 500   -> "Low light"
        lux < 2500  -> "Medium light"
        lux < 10000 -> "Bright indirect light"
        else        -> "Direct sunlight"
    }

    private fun lightLabelMatches(lux: Float, preference: String): Boolean {
        val p = preference.lowercase()
        return when {
            lux < 50    -> false
            lux < 500   -> p.contains("low")
            lux < 2500  -> p.contains("medium") || p.contains("moderate")
            lux < 10000 -> p.contains("bright") || p.contains("indirect")
            else        -> p.contains("direct") || p.contains("full sun")
        }
    }
}