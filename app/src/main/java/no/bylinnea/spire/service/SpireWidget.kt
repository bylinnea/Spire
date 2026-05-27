package no.bylinnea.spire.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import no.bylinnea.spire.util.CareTask
import no.bylinnea.spire.R
import no.bylinnea.spire.util.activeWinterAwareTasks
import no.bylinnea.spire.data.PlantDatabase
import no.bylinnea.spire.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home screen widget showing plants with overdue care tasks.
 * Displays up to 4 plants, with an overflow count if more are due.
 * Tapping the widget opens MainActivity.
 */
class SpireWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_spire)

            val today = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.widgetDate, today)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetTitle, pendingIntent)

            try {
                val db = PlantDatabase.getWidgetDatabase(context)
                val plants = db.plantDao().getAllPlants()

                data class DueItem(val plantName: String, val tasks: List<CareTask>)

                val dueItems = plants.mapNotNull { plant ->
                    val due = plant.activeWinterAwareTasks(context).filter { it.isOverdue && !it.isDoneToday }
                    if (due.isNotEmpty()) DueItem(plant.name, due) else null
                }.sortedByDescending { it.tasks.size }

                val rowIds = listOf(
                    R.id.widgetRow1, R.id.widgetRow2,
                    R.id.widgetRow3, R.id.widgetRow4
                )

                if (dueItems.isEmpty()) {
                    views.setViewVisibility(R.id.widgetAllGood, View.VISIBLE)
                    rowIds.forEach { views.setViewVisibility(it, View.GONE) }
                    views.setViewVisibility(R.id.widgetMore, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widgetAllGood, View.GONE)

                    val maxRows = 4
                    dueItems.take(maxRows).forEachIndexed { i, item ->
                        val emoji = item.tasks.joinToString(" ") { it.type.emoji }
                        val rowText = "$emoji  ${item.plantName}"
                        views.setTextViewText(rowIds[i], rowText)
                        views.setViewVisibility(rowIds[i], View.VISIBLE)
                    }

                    for (i in dueItems.size until maxRows) {
                        views.setViewVisibility(rowIds[i], View.GONE)
                    }

                    val overflow = dueItems.size - maxRows
                    if (overflow > 0) {
                        views.setTextViewText(R.id.widgetMore, "+$overflow more")
                        views.setViewVisibility(R.id.widgetMore, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widgetMore, View.GONE)
                    }
                }
            } catch (e: Exception) {
                // If the DB read fails, show a fallback tap-to-open message
                views.setTextViewText(R.id.widgetAllGood, "Tap to open Spire")
                views.setViewVisibility(R.id.widgetAllGood, View.VISIBLE)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
