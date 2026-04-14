package com.example.weatherapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object WeatherNotificationScheduler {

    fun scheduleDaily(context: Context) {
        if (!WeatherPrefs.isNotificationEnabled(context)) {
            cancelDaily(context)
            return
        }

        val hour = WeatherPrefs.getNotificationHour(context)
        val minute = WeatherPrefs.getNotificationMinute(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = notificationPendingIntent(context)

        val firstTrigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            firstTrigger.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancelDaily(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(notificationPendingIntent(context))
    }

    private fun notificationPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyWeatherAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY_WEATHER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val REQUEST_CODE_DAILY_WEATHER = 6001
}
