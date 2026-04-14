package com.example.weatherapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                if (WeatherPrefs.isNotificationEnabled(context)) {
                    WeatherNotificationScheduler.scheduleDaily(context)
                } else {
                    WeatherNotificationScheduler.cancelDaily(context)
                }
            }
        }
    }
}
