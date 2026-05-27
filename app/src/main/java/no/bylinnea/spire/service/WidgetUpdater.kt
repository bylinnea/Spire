package no.bylinnea.spire.service

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Triggers a refresh of all active Spire home screen widgets. */
object WidgetUpdater {
    fun update(context: Context) {
        val manager   = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, SpireWidget::class.java)
        val ids       = manager.getAppWidgetIds(component)
        ids.forEach { SpireWidget.updateWidget(context, manager, it) }
    }
}