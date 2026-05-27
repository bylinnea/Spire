package no.bylinnea.spire.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Core data model for a plant. All date fields are Unix timestamps in milliseconds.
 * Null interval fields mean that care type is not tracked for this plant.
 */
@Parcelize
@Entity(tableName = "plants")
data class Plant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val wateringIntervalDays: Int,
    val lastWateredDate: Long? = null,
    val photoUri: String? = null,
    val species: String? = null,
    val location: String? = null,
    val dateAcquired: Long? = null,
    val notes: String? = null,

    val fertilizerIntervalDays: Int? = null,
    val lastFertilizedDate: Long? = null,
    val fertilizerType: String? = null,

    val repottingIntervalDays: Int? = null,
    val lastRepottedDate: Long? = null,
    // Tracks when a repot was intentionally skipped, to reset the overdue timer
    val lastRepotSkippedDate: Long? = null,

    val mistingIntervalDays: Int? = null,
    val lastMistedDate: Long? = null,

    val rotatingIntervalDays: Int? = null,
    val lastRotatedDate: Long? = null,

    val cleaningIntervalDays: Int? = null,
    val lastCleanedDate: Long? = null,

    val temperaturePreference: String? = null,
    val lightPreference: String? = null,

    // When set, these override the standard intervals during winter/seasonal mode
    val winterWateringIntervalDays: Int? = null,
    val winterFertilizerIntervalDays: Int? = null,
    val winterMistingIntervalDays: Int? = null,
    // If true, this plant ignores the global winter mode toggle
    val winterScheduleDisabled: Boolean? = null,

    // Null means unknown, true = safe, false = toxic
    val isPetSafe: Boolean? = null,

    // AI-generated care tips, fetched once and cached per plant
    val wateringTip: String? = null,
    val fertilizingTip: String? = null,
    val repottingTip: String? = null,
    val mistingTip: String? = null,
    val rotatingTip: String? = null,
    val cleaningTip: String? = null,

    // Set when a plant is moved to the graveyard. Null means the plant is alive.
    val diedDate: Long? = null,
) : Parcelable