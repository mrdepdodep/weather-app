package com.example.weatherapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Locale
import java.util.concurrent.Executors

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private val ioExecutor = Executors.newSingleThreadExecutor()

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WeatherWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            if (widgetIds.isNotEmpty()) {
                updateWidgets(context, manager, widgetIds)
            }
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val city = WeatherPrefs.getCity(context)
            val useFahrenheit = WeatherPrefs.getUnit(context) == WeatherPrefs.UNIT_FAHRENHEIT

            if (city.isBlank()) {
                val views = baseViews(context).apply {
                    setTextViewText(R.id.widgetCityText, context.getString(R.string.widget_city_missing))
                    setTextViewText(R.id.widgetTempText, "—")
                    setTextViewText(R.id.widgetConditionText, context.getString(R.string.widget_open_app_to_configure))
                }
                manager.updateAppWidget(appWidgetIds, views)
                return
            }

            val loadingViews = baseViews(context).apply {
                setTextViewText(R.id.widgetCityText, city)
                setTextViewText(R.id.widgetTempText, "…")
                setTextViewText(R.id.widgetConditionText, context.getString(R.string.status_loading))
            }
            manager.updateAppWidget(appWidgetIds, loadingViews)

            ioExecutor.execute {
                val result = WeatherFetcher.fetch(city, useFahrenheit)
                val views = baseViews(context)

                when (result) {
                    is WeatherFetcher.Result.Success -> {
                        val snapshot = result.data
                        val tempText = if (snapshot.temperature.isNaN()) {
                            "—°${snapshot.unitSymbol}"
                        } else {
                            String.format(Locale.US, "%.0f°%s", snapshot.temperature, snapshot.unitSymbol)
                        }
                        views.setTextViewText(R.id.widgetCityText, snapshot.displayName)
                        views.setTextViewText(R.id.widgetTempText, tempText)
                        views.setTextViewText(R.id.widgetConditionText, snapshot.condition)
                    }

                    is WeatherFetcher.Result.Error -> {
                        views.setTextViewText(R.id.widgetCityText, city)
                        views.setTextViewText(R.id.widgetTempText, "—")
                        views.setTextViewText(
                            R.id.widgetConditionText,
                            context.getString(errorToString(result.type))
                        )
                    }
                }

                manager.updateAppWidget(appWidgetIds, views)
            }
        }

        private fun baseViews(context: Context): RemoteViews {
            val openIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                8001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return RemoteViews(context.packageName, R.layout.widget_weather).apply {
                setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)
            }
        }

        private fun errorToString(type: WeatherFetcher.ErrorType): Int {
            return when (type) {
                WeatherFetcher.ErrorType.NO_INTERNET -> R.string.error_no_internet
                WeatherFetcher.ErrorType.TIMEOUT -> R.string.error_request_timeout
                WeatherFetcher.ErrorType.REQUEST_FAILED -> R.string.error_request_failed
                WeatherFetcher.ErrorType.CITY_NOT_FOUND -> R.string.error_city_not_found
            }
        }
    }
}
