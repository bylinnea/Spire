package no.bylinnea.spire.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PlantDao {

    /** Returns all living plants, ordered by name. */
    @Query("SELECT * FROM plants WHERE diedDate IS NULL ORDER BY name ASC")
    fun getAllPlants(): List<Plant>

    /** Returns all dead plants, most recently deceased first. */
    @Query("SELECT * FROM plants WHERE diedDate IS NOT NULL ORDER BY diedDate DESC")
    fun getDeadPlants(): List<Plant>

    /** Inserts a new plant and returns its generated id. */
    @Insert
    fun insertPlant(plant: Plant): Long

    /** Permanently deletes a plant and all associated data. */
    @Delete
    fun deletePlant(plant: Plant)

    @Update
    fun updatePlant(plant: Plant)

    @Query("SELECT * FROM plants WHERE id = :id LIMIT 1")
    fun getPlantById(id: Long): Plant?
}