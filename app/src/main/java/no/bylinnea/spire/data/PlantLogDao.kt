package no.bylinnea.spire.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PlantLogDao {

    /** Returns all log entries for a plant, most recent first. */
    @Query("SELECT * FROM plant_log WHERE plantId = :plantId ORDER BY timestamp DESC")
    fun getLogsForPlant(plantId: Long): List<PlantLog>

    @Insert
    fun insertLog(log: PlantLog): Long

    @Update
    fun updateLog(log: PlantLog)

    @Delete
    fun deleteLog(log: PlantLog)
}