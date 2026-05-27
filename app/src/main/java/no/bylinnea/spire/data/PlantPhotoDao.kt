package no.bylinnea.spire.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PlantPhotoDao {

    /** Returns all growth photos for a plant, oldest first. */
    @Query("SELECT * FROM plant_photo WHERE plantId = :plantId ORDER BY timestamp ASC")
    fun getPhotosForPlant(plantId: Long): List<PlantPhoto>

    @Insert
    fun insertPhoto(photo: PlantPhoto): Long

    @Delete
    fun deletePhoto(photo: PlantPhoto)

    @Update
    fun updatePhoto(photo: PlantPhoto)
}