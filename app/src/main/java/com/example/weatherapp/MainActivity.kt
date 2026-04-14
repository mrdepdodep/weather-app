package com.example.weatherapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var statusText: TextView
    private lateinit var locationText: TextView
    private lateinit var temperatureText: TextView
    private lateinit var conditionText: TextView
    private lateinit var windText: TextView
    private lateinit var weatherIconText: TextView
    private lateinit var refreshButton: Button
    private lateinit var settingsButton: ImageButton
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val preferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.rootLayout)
        statusText = findViewById(R.id.statusText)
        locationText = findViewById(R.id.locationText)
        temperatureText = findViewById(R.id.temperatureText)
        conditionText = findViewById(R.id.conditionText)
        windText = findViewById(R.id.windText)
        weatherIconText = findViewById(R.id.weatherIconText)
        refreshButton = findViewById(R.id.refreshButton)
        settingsButton = findViewById(R.id.settingsButton)

        refreshButton.setOnClickListener {
            loadWeatherForCurrentSettings()
        }
        settingsButton.setOnClickListener {
            showSettingsDialog(force = false)
        }

        val savedCity = getSavedCity()
        if (savedCity.isBlank()) {
            showSettingsDialog(force = true)
        } else {
            locationText.text = getString(R.string.location_label, savedCity)
            loadWeatherForCurrentSettings()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        ioExecutor.shutdown()
    }

    private fun showSettingsDialog(force: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val cityInput = dialogView.findViewById<EditText>(R.id.cityInput)
        val unitGroup = dialogView.findViewById<RadioGroup>(R.id.unitGroup)

        cityInput.setText(getSavedCity())
        val savedUnit = getSavedUnit()
        unitGroup.check(if (savedUnit == UNIT_FAHRENHEIT) R.id.unitFahrenheit else R.id.unitCelsius)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(dialogView)
            .setCancelable(!force)
            .setPositiveButton(R.string.settings_save, null)
            .apply {
                if (!force) {
                    setNegativeButton(R.string.settings_cancel, null)
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val city = cityInput.text.toString().trim()
                if (city.isBlank()) {
                    cityInput.error = getString(R.string.settings_city_required)
                    return@setOnClickListener
                }

                val selectedUnit = if (unitGroup.checkedRadioButtonId == R.id.unitFahrenheit) {
                    UNIT_FAHRENHEIT
                } else {
                    UNIT_CELSIUS
                }

                preferences.edit()
                    .putString(KEY_CITY, city)
                    .putString(KEY_UNIT, selectedUnit)
                    .apply()

                locationText.text = getString(R.string.location_label, city)
                loadWeatherForCurrentSettings()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun loadWeatherForCurrentSettings() {
        val city = getSavedCity()
        if (city.isBlank()) {
            showSettingsDialog(force = true)
            return
        }

        statusText.text = getString(R.string.status_loading)
        locationText.text = getString(R.string.location_label, city)
        val useFahrenheit = getSavedUnit() == UNIT_FAHRENHEIT

        ioExecutor.execute {
            val geocodeResult = geocodeCity(city)
            if (geocodeResult == null) {
                runOnUiThread { statusText.text = getString(R.string.status_city_not_found) }
                return@execute
            }

            val weatherResult = loadWeather(
                latitude = geocodeResult.latitude,
                longitude = geocodeResult.longitude,
                useFahrenheit = useFahrenheit
            )

            if (weatherResult == null) {
                runOnUiThread { statusText.text = getString(R.string.status_failed_weather) }
                return@execute
            }

            val unitSymbol = if (useFahrenheit) "F" else "C"
            val temp = weatherResult.temperature
            val weatherCode = weatherResult.weatherCode
            val wind = weatherResult.windSpeed
            val tempString = if (temp.isNaN()) "—°$unitSymbol" else String.format(Locale.US, "%.0f°%s", temp, unitSymbol)
            val windString = if (wind.isNaN()) "— m/s" else String.format(Locale.US, "%.1f m/s", wind)
            val condition = mapWeatherCode(weatherCode)

            runOnUiThread {
                locationText.text = getString(R.string.location_label, geocodeResult.displayName)
                temperatureText.text = tempString
                conditionText.text = condition
                windText.text = windString
                statusText.text = getString(R.string.status_updated)
                applyWeatherVisuals(weatherCode)
            }
        }
    }

    private fun geocodeCity(city: String): GeoResult? {
        var connection: HttpURLConnection? = null
        return try {
            val encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8.toString())
            val url = URL(
                "https://geocoding-api.open-meteo.com/v1/search" +
                    "?name=$encodedCity&count=1&language=en&format=json"
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode !in 200..299) {
                return null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) return null

            val first = results.getJSONObject(0)
            val name = first.optString("name", city)
            val country = first.optString("country", "")
            val displayName = if (country.isBlank()) name else "$name, $country"
            val latitude = first.optDouble("latitude", Double.NaN)
            val longitude = first.optDouble("longitude", Double.NaN)
            if (latitude.isNaN() || longitude.isNaN()) {
                null
            } else {
                GeoResult(displayName = displayName, latitude = latitude, longitude = longitude)
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun loadWeather(latitude: Double, longitude: Double, useFahrenheit: Boolean): WeatherResult? {
        var connection: HttpURLConnection? = null
        return try {
            val unitParam = if (useFahrenheit) "fahrenheit" else "celsius"
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,wind_speed_10m" +
                    "&temperature_unit=$unitParam&wind_speed_unit=ms&timezone=auto"
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode !in 200..299) {
                return null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val temp = current.optDouble("temperature_2m", Double.NaN)
            val weatherCode = current.optInt("weather_code", -1)
            val wind = current.optDouble("wind_speed_10m", Double.NaN)

            WeatherResult(
                temperature = temp,
                weatherCode = weatherCode,
                windSpeed = wind
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun getSavedCity(): String {
        return preferences.getString(KEY_CITY, "").orEmpty()
    }

    private fun getSavedUnit(): String {
        return preferences.getString(KEY_UNIT, UNIT_CELSIUS).orEmpty()
    }

    private fun applyWeatherVisuals(code: Int) {
        val (icon, backgroundRes) = when (code) {
            0 -> "☀" to R.drawable.bg_weather_clear
            1, 2, 3, 45, 48 -> "⛅" to R.drawable.bg_weather_cloudy
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧" to R.drawable.bg_weather_rain
            71, 73, 75, 77, 85, 86 -> "❄" to R.drawable.bg_weather_snow
            95, 96, 99 -> "⛈" to R.drawable.bg_weather_storm
            else -> "⛅" to R.drawable.bg_weather_cloudy
        }

        weatherIconText.text = icon
        rootLayout.setBackgroundResource(backgroundRes)
    }

    private fun mapWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1, 2, 3 -> "Partly Cloudy"
            45, 48 -> "Foggy"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 65, 66, 67 -> "Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Rain Showers"
            85, 86 -> "Snow Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Unknown"
        }
    }

    private data class GeoResult(
        val displayName: String,
        val latitude: Double,
        val longitude: Double
    )

    private data class WeatherResult(
        val temperature: Double,
        val weatherCode: Int,
        val windSpeed: Double
    )

    companion object {
        private const val PREFS_NAME = "weather_prefs"
        private const val KEY_CITY = "city"
        private const val KEY_UNIT = "unit"
        private const val UNIT_CELSIUS = "celsius"
        private const val UNIT_FAHRENHEIT = "fahrenheit"
    }
}
