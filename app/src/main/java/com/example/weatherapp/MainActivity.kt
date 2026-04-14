package com.example.weatherapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var statusText: TextView
    private lateinit var locationText: TextView
    private lateinit var temperatureText: TextView
    private lateinit var conditionText: TextView
    private lateinit var windText: TextView
    private lateinit var weatherIconText: TextView
    private lateinit var refreshButton: Button
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted && hasLocationPermission()) {
                refreshLocationAndWeather()
            } else {
                statusText.text = getString(R.string.status_no_permission)
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

        locationClient = LocationServices.getFusedLocationProviderClient(this)

        refreshButton.setOnClickListener {
            checkPermissionAndLoad()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndLoad()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        ioExecutor.shutdown()
    }

    private fun checkPermissionAndLoad() {
        when {
            hasLocationPermission() -> refreshLocationAndWeather()
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                statusText.text = getString(R.string.status_no_permission)
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            else -> requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun refreshLocationAndWeather() {
        if (!isLocationEnabled()) {
            statusText.text = getString(R.string.status_location_disabled)
            return
        }

        statusText.text = getString(R.string.status_loading)

        val cancellationToken = CancellationTokenSource()
        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    renderLocation(location)
                    loadWeather(location.latitude, location.longitude)
                } else {
                    locationClient.lastLocation
                        .addOnSuccessListener { last ->
                            if (last != null) {
                                renderLocation(last)
                                loadWeather(last.latitude, last.longitude)
                            } else {
                                requestSingleLocationUpdate()
                            }
                        }
                        .addOnFailureListener {
                            requestSingleLocationUpdate()
                        }
                }
            }
            .addOnFailureListener {
                requestSingleLocationUpdate()
            }
    }

    private fun requestSingleLocationUpdate() {
        statusText.text = getString(R.string.status_fetching_fix)

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1000L)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdates(1)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location != null) {
                    locationClient.removeLocationUpdates(this)
                    mainHandler.removeCallbacksAndMessages(null)
                    renderLocation(location)
                    loadWeather(location.latitude, location.longitude)
                }
            }
        }

        mainHandler.postDelayed({
            locationClient.removeLocationUpdates(callback)
            if (statusText.text == getString(R.string.status_fetching_fix)) {
                statusText.text = getString(R.string.status_failed_location)
            }
        }, 12000L)

        try {
            locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            mainHandler.removeCallbacksAndMessages(null)
            statusText.text = getString(R.string.status_no_permission)
        }
    }

    private fun isLocationEnabled(): Boolean {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun renderLocation(location: Location) {
        locationText.text = String.format(
            Locale.US,
            "📍 %.4f, %.4f",
            location.latitude,
            location.longitude
        )
    }

    private fun loadWeather(latitude: Double, longitude: Double) {
        ioExecutor.execute {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$latitude&longitude=$longitude" +
                        "&current=temperature_2m,weather_code,wind_speed_10m" +
                        "&timezone=auto"
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    runOnUiThread { statusText.text = getString(R.string.status_failed_weather) }
                    connection.disconnect()
                    return@execute
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(body)
                val current = json.getJSONObject("current")
                val temp = current.optDouble("temperature_2m", Double.NaN)
                val weatherCode = current.optInt("weather_code", -1)
                val wind = current.optDouble("wind_speed_10m", Double.NaN)

                val tempString = if (temp.isNaN()) "—°" else String.format(Locale.US, "%.0f°", temp)
                val windString = if (wind.isNaN()) "— m/s" else String.format(Locale.US, "%.1f m/s", wind)
                val condition = mapWeatherCode(weatherCode)

                runOnUiThread {
                    temperatureText.text = tempString
                    conditionText.text = condition
                    windText.text = windString
                    statusText.text = getString(R.string.status_updated)
                    applyWeatherVisuals(weatherCode)
                }
            } catch (_: Exception) {
                runOnUiThread { statusText.text = getString(R.string.status_failed_weather) }
            }
        }
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
}
