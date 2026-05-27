package no.bylinnea.spire.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A single entry in a plant's growth photo timeline. Deleted automatically when the plant is deleted. */
@Entity(
    tableName = "plant_photo",
    foreignKeys = [ForeignKey(
        entity = Plant::class,
        parentColumns = ["id"],
        childColumns = ["plantId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("plantId")]
)
data class PlantPhoto(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val plantId: Long,
    val photoUri: String,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null
)