package no.bylinnea.spire.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules a daily background check for overdue plant care tasks. */
object WateringScheduler {

    private const val WORK_NAME = "watering_check"

    fun schedule(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<WateringWorker>(
            1, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}