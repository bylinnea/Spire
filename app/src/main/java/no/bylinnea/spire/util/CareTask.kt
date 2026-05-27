package no.bylinnea.spire.util

import android.content.Context
import no.bylinnea.spire.data.Plant
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Represents a single care task (water, fertilize, repot, etc.) for a plant,
 * with computed properties for overdue status, days until next due, and display text.
 */
data class CareTask(
    val type: CareType,
    val intervalDays: Int?,
    val lastDoneDate: Long?,
    // Optional override for the next due timestamp, used by the repotting grace period
    // to delay the first repot reminder for newly acquired plants
    val nextDueOverrideMs: Long? = null
) {
    enum class CareType(val emoji: String, val label: String, val doneLabel: String) {
        WATER    ("💧", "water",     "watered"),
        FERTILIZE("🌱", "fertilize", "fertilized"),
        REPOT    ("🪴", "repot",     "repotted"),
        MIST     ("💦", "mist",      "misted"),
        ROTATE   ("🔄", "rotate",    "rotated"),
        CLEAN    ("🍃", "clean leaves", "cleaned")
    }

    // Normalises both dates to midnight so "done yesterday" is always 1 day ago,
    // regardless of what time of day the task was marked done
    val daysSinceDone: Long? get() = lastDoneDate?.let {
        val lastCal = Calendar.getInstance().apply { timeInMillis = it }
        val nowCal  = Calendar.getInstance()
        lastCal.set(Calendar.HOUR_OF_DAY, 0)
        lastCal.set(Calendar.MINUTE, 0)
        lastCal.set(Calendar.SECOND, 0)
        lastCal.set(Calendar.MILLISECOND, 0)
        nowCal.set(Calendar.HOUR_OF_DAY, 0)
        nowCal.set(Calendar.MINUTE, 0)
        nowCal.set(Calendar.SECOND, 0)
        nowCal.set(Calendar.MILLISECOND, 0)
        TimeUnit.MILLISECONDS.toDays(nowCal.timeInMillis - lastCal.timeInMillis)
    }

    val isDoneToday: Boolean get() {
        val last = lastDoneDate ?: return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = last }
        val cal2 = Calendar.getInstance()
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    val isOverdue: Boolean get() {
        nextDueOverrideMs?.let { return System.currentTimeMillis() >= it }
        return intervalDays != null &&
                (daysSinceDone == null || daysSinceDone!! >= intervalDays)
    }

    val isDueToday: Boolean get() = isOverdue && !isDoneToday

    val daysUntilNext: Int? get() {
        nextDueOverrideMs?.let {
            return TimeUnit.MILLISECONDS
                .toDays(it - System.currentTimeMillis()).toInt()
        }
        val interval = intervalDays ?: return null
        val since    = daysSinceDone ?: return null
        return (interval - since).toInt()
    }

    val nextDueText: String get() {
        nextDueOverrideMs?.let {
            val days = daysUntilNext ?: return ""
            return when {
                days <= 0  -> "overdue by ${-days} day(s)"
                days == 1  -> "due tomorrow"
                else       -> "next in $days days"
            }
        }
        intervalDays ?: return ""
        val days = daysUntilNext
        return when {
            daysSinceDone == null -> "never done - due now"
            days == null          -> ""
            days <= 0             -> "overdue by ${-days} day(s)"
            days == 1             -> "due tomorrow"
            else                  -> "next in $days days"
        }
    }

    val statusText: String get() = when {
        intervalDays == null  -> "not set up"
        isDoneToday           -> "${type.doneLabel} today ✓"
        nextDueOverrideMs != null -> {
            val days = daysUntilNext ?: 0
            when {
                days <= 0  -> "not yet repotted - due now"
                days == 1  -> "not yet repotted - due tomorrow"
                else       -> "not yet repotted - due in $days days"
            }
        }
        daysSinceDone == null -> "never ${type.doneLabel}"
        daysSinceDone == 1L   -> "${type.doneLabel} yesterday"
        else                  -> "${type.doneLabel} $daysSinceDone days ago"
    }
}

fun Plant.careTasks(): List<CareTask> = listOf(
    CareTask(CareTask.CareType.WATER,     wateringIntervalDays,    lastWateredDate),
    CareTask(CareTask.CareType.FERTILIZE, fertilizerIntervalDays,  lastFertilizedDate),
    run {
        val base = listOfNotNull(lastRepottedDate, lastRepotSkippedDate).maxOrNull()
        val sixtyDaysAgo = System.currentTimeMillis() - 60 * 24 * 60 * 60 * 1000L
        // If the plant was acquired recently and has never been repotted,
        // give a 14-day grace period before showing it as due
        val graceOverride = if (base == null && dateAcquired != null && dateAcquired > sixtyDaysAgo)
            dateAcquired + 14 * 24 * 60 * 60 * 1000L else null
        CareTask(CareTask.CareType.REPOT, repottingIntervalDays, base, graceOverride)
    },
    CareTask(CareTask.CareType.MIST,      mistingIntervalDays,     lastMistedDate),
    CareTask(CareTask.CareType.ROTATE,    rotatingIntervalDays,    lastRotatedDate),
    CareTask(CareTask.CareType.CLEAN, cleaningIntervalDays, lastCleanedDate)
)

fun Plant.activeTasks(): List<CareTask> = careTasks().filter { it.intervalDays != null }

fun Plant.winterAwareCareTasks(context: Context): List<CareTask> {
    val winterActive = ApiKeyManager.isWinterModeEnabled(context)
            && winterScheduleDisabled != true
    return listOf(
        CareTask(CareTask.CareType.WATER,
            if (winterActive && winterWateringIntervalDays != null) winterWateringIntervalDays
            else wateringIntervalDays,
            lastWateredDate),
        CareTask(CareTask.CareType.FERTILIZE,
            if (winterActive && winterFertilizerIntervalDays != null) winterFertilizerIntervalDays
            else fertilizerIntervalDays,
            lastFertilizedDate),
        run {
            val base = listOfNotNull(lastRepottedDate, lastRepotSkippedDate).maxOrNull()
            val sixtyDaysAgo = System.currentTimeMillis() - 60 * 24 * 60 * 60 * 1000L
            val graceOverride = if (base == null && dateAcquired != null && dateAcquired > sixtyDaysAgo)
                dateAcquired + 14 * 24 * 60 * 60 * 1000L else null
            CareTask(CareTask.CareType.REPOT, repottingIntervalDays, base, graceOverride)
        },
        CareTask(CareTask.CareType.MIST,
            if (winterActive && winterMistingIntervalDays != null) winterMistingIntervalDays
            else mistingIntervalDays,
            lastMistedDate),
        CareTask(CareTask.CareType.ROTATE,    rotatingIntervalDays,   lastRotatedDate),
                CareTask(CareTask.CareType.CLEAN, cleaningIntervalDays, lastCleanedDate)
    )
}

fun Plant.activeWinterAwareTasks(context: Context): List<CareTask> =
    winterAwareCareTasks(context).filter { it.intervalDays != null }

fun Plant.tasksDueToday(): List<CareTask> = activeTasks().filter { it.isDueToday }

fun Plant.markTaskDone(type: CareTask.CareType): Plant {
    val now = System.currentTimeMillis()
    return when (type) {
        CareTask.CareType.WATER     -> copy(lastWateredDate    = now)
        CareTask.CareType.FERTILIZE -> copy(lastFertilizedDate = now)
        CareTask.CareType.REPOT     -> copy(lastRepottedDate   = now)
        CareTask.CareType.MIST      -> copy(lastMistedDate     = now)
        CareTask.CareType.ROTATE    -> copy(lastRotatedDate    = now)
        CareTask.CareType.CLEAN     -> copy(lastCleanedDate = now)
    }
}

fun Plant.undoTask(type: CareTask.CareType, previousDate: Long? = null): Plant = when (type) {
    CareTask.CareType.WATER     -> copy(lastWateredDate    = previousDate)
    CareTask.CareType.FERTILIZE -> copy(lastFertilizedDate = previousDate)
    CareTask.CareType.REPOT     -> copy(lastRepottedDate   = previousDate)
    CareTask.CareType.MIST      -> copy(lastMistedDate     = previousDate)
    CareTask.CareType.ROTATE    -> copy(lastRotatedDate    = previousDate)
    CareTask.CareType.CLEAN     -> copy(lastCleanedDate    = previousDate)
}
