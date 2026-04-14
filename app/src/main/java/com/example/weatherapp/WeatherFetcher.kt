package com.example.weatherapp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object WeatherFetcher {

    data class WeatherSnapshot(
        val displayName: String,
        val temperature: Double,
        val weatherCode: Int,
        val condition: String,
        val windSpeed: Double,
        val unitSymbol: String
    )

    enum class ErrorType {
        NO_INTERNET,
        TIMEOUT,
        REQUEST_FAILED,
        CITY_NOT_FOUND
    }

    sealed interface Result {
        data class Success(val data: WeatherSnapshot) : Result
        data class Error(val type: ErrorType) : Result
    }

    fun fetch(city: String, useFahrenheit: Boolean): Result {
        val geo = geocodeCity(city)
        val geoData = when (geo) {
            is GeoLookupResult.Success -> geo.data
            is GeoLookupResult.Error -> return Result.Error(geo.type)
        }

        val weather = loadWeather(geoData.latitude, geoData.longitude, useFahrenheit)
        val weatherData = when (weather) {
            is WeatherLoadResult.Success -> weather.data
            is WeatherLoadResult.Error -> return Result.Error(weather.type)
        }

        val unitSymbol = if (useFahrenheit) "F" else "C"
        return Result.Success(
            WeatherSnapshot(
                displayName = geoData.displayName,
                temperature = weatherData.temperature,
                weatherCode = weatherData.weatherCode,
                condition = mapWeatherCode(weatherData.weatherCode),
                windSpeed = weatherData.windSpeed,
                unitSymbol = unitSymbol
            )
        )
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
                return GeoLookupResult.Error(ErrorType.REQUEST_FAILED)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return GeoLookupResult.Error(ErrorType.CITY_NOT_FOUND)
            if (results.length() == 0) return GeoLookupResult.Error(ErrorType.CITY_NOT_FOUND)

            val first = results.getJSONObject(0)
            val name = first.optString("name", city)
            val country = first.optString("country", "")
            val displayName = if (country.isBlank()) name else "$name, $country"
            val latitude = first.optDouble("latitude", Double.NaN)
            val longitude = first.optDouble("longitude", Double.NaN)
            if (latitude.isNaN() || longitude.isNaN()) {
                GeoLookupResult.Error(ErrorType.REQUEST_FAILED)
            } else {
                GeoLookupResult.Success(
                    GeoResult(
                        displayName = displayName,
                        latitude = latitude,
                        longitude = longitude
                    )
                )
            }
        } catch (_: java.net.UnknownHostException) {
            GeoLookupResult.Error(ErrorType.NO_INTERNET)
        } catch (_: SocketTimeoutException) {
            GeoLookupResult.Error(ErrorType.TIMEOUT)
        } catch (_: Exception) {
            GeoLookupResult.Error(ErrorType.REQUEST_FAILED)
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
                return WeatherLoadResult.Error(ErrorType.REQUEST_FAILED)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val current = json.getJSONObject("current")

            WeatherLoadResult.Success(
                WeatherResult(
                    temperature = current.optDouble("temperature_2m", Double.NaN),
                    weatherCode = current.optInt("weather_code", -1),
                    windSpeed = current.optDouble("wind_speed_10m", Double.NaN)
                )
            )
        } catch (_: java.net.UnknownHostException) {
            WeatherLoadResult.Error(ErrorType.NO_INTERNET)
        } catch (_: SocketTimeoutException) {
            WeatherLoadResult.Error(ErrorType.TIMEOUT)
        } catch (_: Exception) {
            WeatherLoadResult.Error(ErrorType.REQUEST_FAILED)
        } finally {
            connection?.disconnect()
        }
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
        data class Error(val type: ErrorType) : GeoLookupResult
    }

    private sealed interface WeatherLoadResult {
        data class Success(val data: WeatherResult) : WeatherLoadResult
        data class Error(val type: ErrorType) : WeatherLoadResult
    }
}
