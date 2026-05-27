package no.bylinnea.spire.util

import no.bylinnea.spire.data.Plant

/** Sort options for the main plant list. */
enum class SortOption(val label: String) {
    NAME("name"),
    NEXT_DUE("next due"),
    MOST_OVERDUE("most overdue"),
    BY_ROOM("by room")
}

fun List<Plant>.sorted(by: SortOption): List<Plant> = when (by) {

    SortOption.NAME ->
        sortedBy { it.name.lowercase() }

    SortOption.NEXT_DUE ->
        sortedBy { plant ->
            plant.activeTasks()
                .mapNotNull { it.daysUntilNext }
                .minOrNull() ?: Int.MAX_VALUE
        }

    SortOption.MOST_OVERDUE ->
        sortedByDescending { plant ->
            // Score = days since last done minus interval, so higher = more overdue.
            // Plants never done get a large synthetic value (interval * 10).
            plant.activeTasks()
                .filter { it.isOverdue }
                .mapNotNull { task ->
                    val interval = task.intervalDays ?: return@mapNotNull null
                    val since    = task.daysSinceDone ?: (interval * 10).toLong()
                    since - interval
                }
                .maxOrNull() ?: -999L
        }

    SortOption.BY_ROOM ->
        // Plants with no room set sort to the bottom
        sortedWith(compareBy(
            { it.location?.lowercase() ?: "zzz" },
            { it.name.lowercase() }
        ))
}