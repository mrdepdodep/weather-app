package com.example.weatherapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import java.util.concurrent.Executors

class DailyWeatherAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!WeatherPrefs.isNotificationEnabled(context)) return

        val city = WeatherPrefs.getCity(context)
        if (city.isBlank()) return

        val pendingResult = goAsync()
        ioExecutor.execute {
            try {
                val useFahrenheit = WeatherPrefs.getUnit(context) == WeatherPrefs.UNIT_FAHRENHEIT
                val result = WeatherFetcher.fetch(city, useFahrenheit)
                if (result is WeatherFetcher.Result.Success) {
                    showNotification(context, result.data)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, snapshot: WeatherFetcher.WeatherSnapshot) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val tempText = if (snapshot.temperature.isNaN()) {
            "—°${snapshot.unitSymbol}"
        } else {
            String.format(Locale.US, "%.0f°%s", snapshot.temperature, snapshot.unitSymbol)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            7001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(context.getString(R.string.notification_title, snapshot.displayName))
            .setContentText(context.getString(R.string.notification_text, tempText, snapshot.condition))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DAILY, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private val ioExecutor = Executors.newSingleThreadExecutor()
        private const val CHANNEL_ID = "daily_weather_channel"
        private const val NOTIFICATION_ID_DAILY = 4101
    }
}
