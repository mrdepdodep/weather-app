package com.example.weatherapp

import android.content.Context

object WeatherPrefs {
    const val PREFS_NAME = "weather_prefs"
    private const val KEY_CITY = "city"
    private const val KEY_UNIT = "unit"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_NOTIFICATION_HOUR = "notification_hour"
    private const val KEY_NOTIFICATION_MINUTE = "notification_minute"

    const val UNIT_CELSIUS = "celsius"
    const val UNIT_FAHRENHEIT = "fahrenheit"

    private const val DEFAULT_NOTIFICATION_HOUR = 8
    private const val DEFAULT_NOTIFICATION_MINUTE = 0

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCity(context: Context): String = prefs(context).getString(KEY_CITY, "").orEmpty()

    fun setCity(context: Context, city: String) {
        prefs(context).edit().putString(KEY_CITY, city).apply()
    }

    fun getUnit(context: Context): String =
        prefs(context).getString(KEY_UNIT, UNIT_CELSIUS).orEmpty()

    fun setUnit(context: Context, unit: String) {
        prefs(context).edit().putString(KEY_UNIT, unit).apply()
    }

    fun isNotificationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATIONS_ENABLED, false)

    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getNotificationHour(context: Context): Int =
        prefs(context).getInt(KEY_NOTIFICATION_HOUR, DEFAULT_NOTIFICATION_HOUR)

    fun getNotificationMinute(context: Context): Int =
        prefs(context).getInt(KEY_NOTIFICATION_MINUTE, DEFAULT_NOTIFICATION_MINUTE)

    fun setNotificationTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt(KEY_NOTIFICATION_HOUR, hour)
            .putInt(KEY_NOTIFICATION_MINUTE, minute)
            .apply()
    }
}
