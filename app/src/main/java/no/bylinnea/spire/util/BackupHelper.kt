package no.bylinnea.spire.util

import no.bylinnea.spire.data.Plant
import no.bylinnea.spire.data.PlantDatabase
import no.bylinnea.spire.data.PlantLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles JSON export and import of all plant data and health logs.
 * Photos are not included in backups since they are device-specific file URIs.
 */
object BackupHelper {

    private const val BACKUP_VERSION = 1

    data class ImportResult(val imported: Int, val skipped: Int)

    fun exportToJson(db: PlantDatabase): String {
        val allPlants = db.plantDao().getAllPlants() + db.plantDao().getDeadPlants()
        val plantsArray = JSONArray()

        allPlants.forEach { plant ->
            val logs = db.plantLogDao().getLogsForPlant(plant.id)
            plantsArray.put(plantToJson(plant, logs))
        }

        return JSONObject().apply {
            put("version",  BACKUP_VERSION)
            put("exported", System.currentTimeMillis())
            put("plants",   plantsArray)
        }.toString(2)
    }


    fun importFromJson(json: String, db: PlantDatabase): ImportResult {
        val obj         = JSONObject(json)
        val plantsArray = obj.getJSONArray("plants")

        // Duplicate detection by name - plants with the same name (case-insensitive) are skipped
        val existingNames = (db.plantDao().getAllPlants() + db.plantDao().getDeadPlants())
            .map { it.name.lowercase().trim() }
            .toSet()

        var imported = 0
        var skipped  = 0

        for (i in 0 until plantsArray.length()) {
            val plantObj = plantsArray.getJSONObject(i)
            val name     = plantObj.optString("name").trim()

            if (name.isBlank() || name.lowercase() in existingNames) {
                skipped++
                continue
            }

            val id = db.plantDao().insertPlant(plantFromJson(plantObj))

            plantObj.optJSONArray("logs")?.let { logsArray ->
                for (j in 0 until logsArray.length()) {
                    val logObj = logsArray.getJSONObject(j)
                    db.plantLogDao().insertLog(
                        PlantLog(
                            plantId   = id,
                            note      = logObj.optString("note"),
                            timestamp = logObj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            imported++
        }

        return ImportResult(imported, skipped)
    }

    private fun plantToJson(plant: Plant, logs: List<PlantLog>) = JSONObject().apply {
        put("name",                 plant.name)
        put("wateringIntervalDays", plant.wateringIntervalDays)
        plant.lastWateredDate?.let        { put("lastWateredDate",            it) }
        plant.photoUri?.let               { put("photoUri",                   it) }
        plant.species?.let                { put("species",                    it) }
        plant.location?.let               { put("location",                   it) }
        plant.dateAcquired?.let           { put("dateAcquired",               it) }
        plant.notes?.let                  { put("notes",                      it) }
        plant.fertilizerIntervalDays?.let { put("fertilizerIntervalDays",     it) }
        plant.lastFertilizedDate?.let     { put("lastFertilizedDate",         it) }
        plant.fertilizerType?.let         { put("fertilizerType",             it) }
        plant.repottingIntervalDays?.let  { put("repottingIntervalDays",      it) }
        plant.lastRepottedDate?.let       { put("lastRepottedDate",           it) }
        plant.lastRepotSkippedDate?.let   { put("lastRepotSkippedDate",       it) }
        plant.mistingIntervalDays?.let    { put("mistingIntervalDays",        it) }
        plant.lastMistedDate?.let         { put("lastMistedDate",             it) }
        plant.rotatingIntervalDays?.let   { put("rotatingIntervalDays",       it) }
        plant.lastRotatedDate?.let        { put("lastRotatedDate",            it) }
        plant.cleaningIntervalDays?.let   { put("cleaningIntervalDays",       it) }
        plant.lastCleanedDate?.let        { put("lastCleanedDate",            it) }
        plant.temperaturePreference?.let  { put("temperaturePreference",      it) }
        plant.lightPreference?.let        { put("lightPreference",            it) }
        plant.winterWateringIntervalDays?.let   { put("winterWateringIntervalDays",   it) }
        plant.winterFertilizerIntervalDays?.let { put("winterFertilizerIntervalDays", it) }
        plant.winterMistingIntervalDays?.let    { put("winterMistingIntervalDays",    it) }
        plant.winterScheduleDisabled?.let { put("winterScheduleDisabled",     it) }
        plant.isPetSafe?.let              { put("isPetSafe",                  it) }
        plant.wateringTip?.let            { put("wateringTip",                it) }
        plant.fertilizingTip?.let         { put("fertilizingTip",             it) }
        plant.repottingTip?.let           { put("repottingTip",               it) }
        plant.mistingTip?.let             { put("mistingTip",                 it) }
        plant.rotatingTip?.let            { put("rotatingTip",                it) }
        plant.cleaningTip?.let            { put("cleaningTip",                it) }
        plant.diedDate?.let               { put("diedDate",                   it) }

        if (logs.isNotEmpty()) {
            put("logs", JSONArray().also { arr ->
                logs.forEach { log ->
                    arr.put(JSONObject().apply {
                        put("note",      log.note)
                        put("timestamp", log.timestamp)
                    })
                }
            })
        }
    }

    private fun plantFromJson(j: JSONObject) = Plant(
        name                         = j.optString("name"),
        wateringIntervalDays         = j.optInt("wateringIntervalDays", 7),
        lastWateredDate              = j.optLong("lastWateredDate",            0).takeIf { it > 0 },
        photoUri                     = j.optString("photoUri").ifBlank { null },
        species                      = j.optString("species").ifBlank { null },
        location                     = j.optString("location").ifBlank { null },
        dateAcquired                 = j.optLong("dateAcquired",               0).takeIf { it > 0 },
        notes                        = j.optString("notes").ifBlank { null },
        fertilizerIntervalDays       = j.optInt("fertilizerIntervalDays",      0).takeIf { it > 0 },
        lastFertilizedDate           = j.optLong("lastFertilizedDate",         0).takeIf { it > 0 },
        fertilizerType               = j.optString("fertilizerType").ifBlank { null },
        repottingIntervalDays        = j.optInt("repottingIntervalDays",       0).takeIf { it > 0 },
        lastRepottedDate             = j.optLong("lastRepottedDate",           0).takeIf { it > 0 },
        lastRepotSkippedDate         = j.optLong("lastRepotSkippedDate",       0).takeIf { it > 0 },
        mistingIntervalDays          = j.optInt("mistingIntervalDays",         0).takeIf { it > 0 },
        lastMistedDate               = j.optLong("lastMistedDate",             0).takeIf { it > 0 },
        rotatingIntervalDays         = j.optInt("rotatingIntervalDays",        0).takeIf { it > 0 },
        lastRotatedDate              = j.optLong("lastRotatedDate",            0).takeIf { it > 0 },
        cleaningIntervalDays         = j.optInt("cleaningIntervalDays",        0).takeIf { it > 0 },
        lastCleanedDate              = j.optLong("lastCleanedDate",            0).takeIf { it > 0 },
        temperaturePreference        = j.optString("temperaturePreference").ifBlank { null },
        lightPreference              = j.optString("lightPreference").ifBlank { null },
        winterWateringIntervalDays   = j.optInt("winterWateringIntervalDays",  0).takeIf { it > 0 },
        winterFertilizerIntervalDays = j.optInt("winterFertilizerIntervalDays",0).takeIf { it > 0 },
        winterMistingIntervalDays    = j.optInt("winterMistingIntervalDays",   0).takeIf { it > 0 },
        // Boolean fields need explicit null check since optBoolean returns false for missing keys
        winterScheduleDisabled = if (j.has("winterScheduleDisabled")) j.optBoolean("winterScheduleDisabled") else null,
        isPetSafe              = if (j.has("isPetSafe")) j.optBoolean("isPetSafe") else null,
        wateringTip                  = j.optString("wateringTip").ifBlank { null },
        fertilizingTip               = j.optString("fertilizingTip").ifBlank { null },
        repottingTip                 = j.optString("repottingTip").ifBlank { null },
        mistingTip                   = j.optString("mistingTip").ifBlank { null },
        rotatingTip                  = j.optString("rotatingTip").ifBlank { null },
        cleaningTip                  = j.optString("cleaningTip").ifBlank { null },
        diedDate                     = j.optLong("diedDate",                   0).takeIf { it > 0 }
    )
}
