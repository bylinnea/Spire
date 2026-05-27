package no.bylinnea.spire.service

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import no.bylinnea.spire.util.CareTask
import no.bylinnea.spire.util.activeWinterAwareTasks
import no.bylinnea.spire.data.Plant
import no.bylinnea.spire.data.PlantDatabase
import no.bylinnea.spire.ui.MainActivity

/**
 * WorkManager worker that runs daily and fires care reminder notifications
 * for any plants with overdue tasks.
 *
 * Each overdue plant gets its own notification (IDs starting at 2000).
 * When multiple plants are due, a grouped summary notification is also shown.
 */
class WateringWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        const val CHANNEL_ID   = "watering_reminders"
        const val CHANNEL_NAME = "Plant Care Reminders"
        const val SUMMARY_ID   = 1000  // Summary notification ID, below per-plant range (2000+)
    }

    override fun doWork(): Result {
        val db     = PlantDatabase.getDatabase(context)
        val plants = db.plantDao().getAllPlants()

        // Find plants with at least one overdue task
        val overdueByPlant = plants.mapNotNull { plant ->
            val overdueTasks = plant.activeWinterAwareTasks(context).filter { it.isOverdue && !it.isDoneToday }
            if (overdueTasks.isNotEmpty()) Pair(plant, overdueTasks) else null
        }

        if (overdueByPlant.isEmpty()) {
            WidgetUpdater.update(context)
            return Result.success()
        }

        createNotificationChannel()
        val manager = context.getSystemService(NotificationManager::class.java)

        overdueByPlant.forEachIndexed { index, (plant, tasks) ->
            val notification = buildPlantNotification(plant, tasks, index)
            manager.notify(2000 + index, notification)
        }

        if (overdueByPlant.size > 1) {
            val summary = buildSummaryNotification(overdueByPlant.map { it.first })
            manager.notify(SUMMARY_ID, summary)
        }

        WidgetUpdater.update(context)
        return Result.success()
    }

    private fun buildPlantNotification(
        plant: Plant,
        overdueTasks: List<CareTask>,
        index: Int
    ): Notification {

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_plant_id", plant.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2000 + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val taskList = overdueTasks.joinToString(" · ") {
            "${it.type.emoji} ${it.type.label}"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle("${plant.name} needs attention")
            .setContentText(taskList)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Time to: $taskList"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("plant_care")
            .build()
    }

    private fun buildSummaryNotification(plants: List<Plant>): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, SUMMARY_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val names = plants.joinToString(", ") { it.name }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle("${plants.size} plants need attention 🌿")
            .setContentText(names)
            .setStyle(NotificationCompat.InboxStyle().also {
                plants.forEach { plant ->
                    val tasks = plant.activeWinterAwareTasks(context)
                        .filter { it.isOverdue && !it.isDoneToday }
                        .joinToString(" · ") { t -> "${t.type.emoji} ${t.type.label}" }
                    it.addLine("${plant.name}  -  $tasks")
                }
                it.setSummaryText("${plants.size} plants")
            })
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("plant_care")
            .setGroupSummary(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Reminds you to care for your plants" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
