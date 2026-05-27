package no.bylinnea.spire.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A single timestamped health log entry for a plant. Deleted automatically when the plant is deleted. */
@Entity(
    tableName = "plant_log",
    foreignKeys = [ForeignKey(
        entity        = Plant::class,
        parentColumns = ["id"],
        childColumns  = ["plantId"],
        onDelete      = ForeignKey.CASCADE
    )],
    indices = [Index("plantId")]
)
data class PlantLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val plantId: Long,
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)