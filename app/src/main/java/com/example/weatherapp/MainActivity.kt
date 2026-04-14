package com.example.weatherapp

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
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

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                WeatherNotificationScheduler.scheduleDaily(this)
            } else {
                WeatherPrefs.setNotificationEnabled(this, false)
                WeatherNotificationScheduler.cancelDaily(this)
                notifyError(AppErrorType.NOTIFICATION_PERMISSION_DENIED)
            }
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

        if (WeatherPrefs.isNotificationEnabled(this)) {
            ensureNotificationPermissionAndSchedule()
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
        val notificationsSwitch = dialogView.findViewById<SwitchCompat>(R.id.notificationsSwitch)
        val notificationTimeButton = dialogView.findViewById<Button>(R.id.notificationTimeButton)

        cityInput.setText(getSavedCity())
        val savedUnit = getSavedUnit()
        unitGroup.check(if (savedUnit == WeatherPrefs.UNIT_FAHRENHEIT) R.id.unitFahrenheit else R.id.unitCelsius)

        var selectedHour = WeatherPrefs.getNotificationHour(this)
        var selectedMinute = WeatherPrefs.getNotificationMinute(this)
        notificationsSwitch.isChecked = WeatherPrefs.isNotificationEnabled(this)
        notificationTimeButton.isEnabled = notificationsSwitch.isChecked

        fun renderTimeLabel() {
            val label = formatHourMinute(selectedHour, selectedMinute)
            notificationTimeButton.text = getString(R.string.settings_notification_time, label)
        }

        renderTimeLabel()

        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            notificationTimeButton.isEnabled = isChecked
        }

        notificationTimeButton.setOnClickListener {
            TimePickerDialog(
                this,
                { _: TimePicker, hour: Int, minute: Int ->
                    selectedHour = hour
                    selectedMinute = minute
                    renderTimeLabel()
                },
                selectedHour,
                selectedMinute,
                true
            ).show()
        }

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
                    WeatherPrefs.UNIT_FAHRENHEIT
                } else {
                    WeatherPrefs.UNIT_CELSIUS
                }

                WeatherPrefs.setCity(this, city)
                WeatherPrefs.setUnit(this, selectedUnit)
                WeatherPrefs.setNotificationEnabled(this, notificationsSwitch.isChecked)
                WeatherPrefs.setNotificationTime(this, selectedHour, selectedMinute)

                if (notificationsSwitch.isChecked) {
                    ensureNotificationPermissionAndSchedule()
                } else {
                    WeatherNotificationScheduler.cancelDaily(this)
                }

                locationText.text = getString(R.string.location_label, city)
                loadWeatherForCurrentSettings()
                WeatherWidgetProvider.refreshAll(this)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun ensureNotificationPermissionAndSchedule() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            WeatherNotificationScheduler.scheduleDaily(this)
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            WeatherNotificationScheduler.scheduleDaily(this)
        } else {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun loadWeatherForCurrentSettings() {
        val city = getSavedCity()
        if (city.isBlank()) {
            showSettingsDialog(force = true)
            return
        }
        if (!hasInternetConnection()) {
            notifyError(AppErrorType.NO_INTERNET)
            return
        }

        statusText.text = getString(R.string.status_loading)
        locationText.text = getString(R.string.location_label, city)
        val useFahrenheit = getSavedUnit() == WeatherPrefs.UNIT_FAHRENHEIT

        ioExecutor.execute {
            val geocodeResult = geocodeCity(city)
            val geoData = when (geocodeResult) {
                is GeoLookupResult.Success -> geocodeResult.data
                is GeoLookupResult.Error -> {
                    notifyError(geocodeResult.type)
                    return@execute
                }
            }

            val weatherResult = loadWeather(
                latitude = geoData.latitude,
                longitude = geoData.longitude,
                useFahrenheit = useFahrenheit
            )

            val weatherData = when (weatherResult) {
                is WeatherLoadResult.Success -> weatherResult.data
                is WeatherLoadResult.Error -> {
                    notifyError(weatherResult.type)
                    return@execute
                }
            }

            val unitSymbol = if (useFahrenheit) "F" else "C"
            val temp = weatherData.temperature
            val weatherCode = weatherData.weatherCode
            val wind = weatherData.windSpeed
            val tempString = if (temp.isNaN()) "—°$unitSymbol" else String.format(Locale.US, "%.0f°%s", temp, unitSymbol)
            val windString = if (wind.isNaN()) "— m/s" else String.format(Locale.US, "%.1f m/s", wind)
            val condition = mapWeatherCode(weatherCode)

            runOnUiThread {
                locationText.text = getString(R.string.location_label, geoData.displayName)
                temperatureText.text = tempString
                conditionText.text = condition
                windText.text = windString
                statusText.text = getString(R.string.status_updated)
                applyWeatherVisuals(weatherCode)
                WeatherWidgetProvider.refreshAll(this)
            }
        }
    }

    private fun geocodeCity(city: String): GeoLookupResult {
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
                return GeoLookupResult.Error(AppErrorType.REQUEST_FAILED)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val results = json.optJSONArray("results")
                ?: return GeoLookupResult.Error(AppErrorType.CITY_NOT_FOUND)
            if (results.length() == 0) return GeoLookupResult.Error(AppErrorType.CITY_NOT_FOUND)

            val first = results.getJSONObject(0)
            val name = first.optString("name", city)
            val country = first.optString("country", "")
            val displayName = if (country.isBlank()) name else "$name, $country"
            val latitude = first.optDouble("latitude", Double.NaN)
            val longitude = first.optDouble("longitude", Double.NaN)
            if (latitude.isNaN() || longitude.isNaN()) {
                GeoLookupResult.Error(AppErrorType.REQUEST_FAILED)
            } else {
                GeoLookupResult.Success(
                    GeoResult(displayName = displayName, latitude = latitude, longitude = longitude)
                )
            }
        } catch (_: java.net.UnknownHostException) {
            GeoLookupResult.Error(AppErrorType.NO_INTERNET)
        } catch (_: SocketTimeoutException) {
            GeoLookupResult.Error(AppErrorType.REQUEST_TIMEOUT)
        } catch (_: Exception) {
            GeoLookupResult.Error(AppErrorType.REQUEST_FAILED)
        } finally {
            connection?.disconnect()
        }
    }

    private fun loadWeather(latitude: Double, longitude: Double, useFahrenheit: Boolean): WeatherLoadResult {
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
                return WeatherLoadResult.Error(AppErrorType.REQUEST_FAILED)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val temp = current.optDouble("temperature_2m", Double.NaN)
            val weatherCode = current.optInt("weather_code", -1)
            val wind = current.optDouble("wind_speed_10m", Double.NaN)

            WeatherLoadResult.Success(
                WeatherResult(
                    temperature = temp,
                    weatherCode = weatherCode,
                    windSpeed = wind
                )
            )
        } catch (_: java.net.UnknownHostException) {
            WeatherLoadResult.Error(AppErrorType.NO_INTERNET)
        } catch (_: SocketTimeoutException) {
            WeatherLoadResult.Error(AppErrorType.REQUEST_TIMEOUT)
        } catch (_: Exception) {
            WeatherLoadResult.Error(AppErrorType.REQUEST_FAILED)
        } finally {
            connection?.disconnect()
        }
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun notifyError(type: AppErrorType) {
        runOnUiThread {
            statusText.text = getString(type.statusRes)
            Snackbar.make(rootLayout, getString(type.messageRes), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun getSavedCity(): String = WeatherPrefs.getCity(this)

    private fun getSavedUnit(): String = WeatherPrefs.getUnit(this)

    private fun formatHourMinute(hour: Int, minute: Int): String {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
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

    private sealed interface GeoLookupResult {
        data class Success(val data: GeoResult) : GeoLookupResult
        data class Error(val type: AppErrorType) : GeoLookupResult
    }

    private sealed interface WeatherLoadResult {
        data class Success(val data: WeatherResult) : WeatherLoadResult
        data class Error(val type: AppErrorType) : WeatherLoadResult
    }

    private enum class AppErrorType(@StringRes val statusRes: Int, @StringRes val messageRes: Int) {
        NO_INTERNET(
            statusRes = R.string.status_no_internet,
            messageRes = R.string.error_no_internet
        ),
        REQUEST_TIMEOUT(
            statusRes = R.string.status_request_timeout,
            messageRes = R.string.error_request_timeout
        ),
        REQUEST_FAILED(
            statusRes = R.string.status_failed_weather,
            messageRes = R.string.error_request_failed
        ),
        CITY_NOT_FOUND(
            statusRes = R.string.status_city_not_found,
            messageRes = R.string.error_city_not_found
        ),
        NOTIFICATION_PERMISSION_DENIED(
            statusRes = R.string.status_notifications_disabled,
            messageRes = R.string.error_notification_permission
        )
    }
}
