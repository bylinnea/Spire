package no.bylinnea.spire.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.graphics.toColorInt
import no.bylinnea.spire.data.Plant
import no.bylinnea.spire.data.PlantLog
import java.util.concurrent.TimeUnit

/**
 * Handles plant sharing via QR code. Serializes a plant and its recent logs
 * to a compact JSON string, generates a QR bitmap, and deserializes on the receiving end.
 */
object PlantShareHelper {

    // QR codes have a size limit, so field names are kept short and only the
    // most recent log entries are included
    private const val MAX_LOG_ENTRIES = 10

    // Strips non-ASCII characters to ensure the JSON encodes cleanly into a QR code
    private fun String.safeForQr(): String =
        this.replace(Regex("[^\\x00-\\x7F]"), "").trim()

    fun toJson(plant: Plant, logs: List<PlantLog> = emptyList()): String {
        return JSONObject().apply {
            put("v",  1)
            put("n",  plant.name.safeForQr())
            plant.species?.let        { put("sp",  it.safeForQr()) }
            plant.location?.let       { put("loc", it.safeForQr()) }
            plant.notes?.let          { put("nt",  it.safeForQr()) }
            plant.fertilizerType?.let { put("ft",  it.safeForQr()) }
            plant.dateAcquired?.let   { put("da",  it) }
            put("wi", plant.wateringIntervalDays)
            plant.lastWateredDate?.let        { put("lw",  it) }
            plant.fertilizerIntervalDays?.let { put("fi",  it) }
            plant.lastFertilizedDate?.let     { put("lf",  it) }
            plant.repottingIntervalDays?.let  { put("ri",  it) }
            plant.lastRepottedDate?.let       { put("lr",  it) }
            plant.mistingIntervalDays?.let    { put("mi",  it) }
            plant.lastMistedDate?.let         { put("lm",  it) }
            plant.rotatingIntervalDays?.let   { put("rti", it) }
            plant.lastRotatedDate?.let        { put("lrt", it) }
            if (logs.isNotEmpty()) {
                val arr = JSONArray()
                logs.take(MAX_LOG_ENTRIES).forEach { log ->
                    arr.put(JSONObject().apply {
                        put("note", log.note.safeForQr())
                        put("ts",   log.timestamp)
                    })
                }
                put("logs", arr)
            }
        }.toString()
    }

    fun fromJson(json: String): Pair<Plant, List<PlantLog>>? {
        return try {
            val j    = JSONObject(json)
            val name = j.optString("n").takeIf { it.isNotBlank() } ?: return null
            val wi   = j.optInt("wi", 0).takeIf { it > 0 } ?: return null
            val plant = Plant(
                name = name,
                wateringIntervalDays = wi,
                species = j.optString("sp").ifBlank { null },
                location = j.optString("loc").ifBlank { null },
                notes = j.optString("nt").ifBlank { null },
                fertilizerType = j.optString("ft").ifBlank { null },
                dateAcquired = j.optLong("da", 0L).takeIf { it > 0 },
                lastWateredDate = j.optLong("lw", 0L).takeIf { it > 0 },
                fertilizerIntervalDays = j.optInt("fi", 0).takeIf { it > 0 },
                lastFertilizedDate = j.optLong("lf", 0L).takeIf { it > 0 },
                repottingIntervalDays = j.optInt("ri", 0).takeIf { it > 0 },
                lastRepottedDate = j.optLong("lr", 0L).takeIf { it > 0 },
                mistingIntervalDays = j.optInt("mi", 0).takeIf { it > 0 },
                lastMistedDate = j.optLong("lm", 0L).takeIf { it > 0 },
                rotatingIntervalDays = j.optInt("rti", 0).takeIf { it > 0 },
                lastRotatedDate = j.optLong("lrt", 0L).takeIf { it > 0 }
            )
            val logs = mutableListOf<PlantLog>()
            j.optJSONArray("logs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    logs.add(
                        PlantLog(
                            plantId = 0,
                            note = e.optString("note"),
                            timestamp = e.optLong("ts", System.currentTimeMillis())
                        )
                    )
                }
            }
            Pair(plant, logs)
        } catch (e: Exception) { null }
    }

    fun generateQrBitmap(json: String, size: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN        to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bits = QRCodeWriter().encode(json, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp  = createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp[x, y] = if (bits[x, y]) "#2A4A2A".toColorInt()
                    else "#F0EDE6".toColorInt()
                }
            }
            bmp
        } catch (e: Exception) { null }
    }

    fun summarise(plant: Plant, logCount: Int = 0): String = buildString {
        appendLine(plant.name)
        plant.species?.let { appendLine("Species: $it") }
        appendLine("Water every ${plant.wateringIntervalDays} days")
        plant.fertilizerIntervalDays?.let { appendLine("Fertilize every $it days") }
        plant.repottingIntervalDays?.let  { appendLine("Repot every $it days") }
        plant.mistingIntervalDays?.let    { appendLine("Mist every $it days") }
        plant.rotatingIntervalDays?.let   { appendLine("Rotate every $it days") }
        plant.lastWateredDate?.let {
            val days = TimeUnit.MILLISECONDS
                .toDays(System.currentTimeMillis() - it)
            appendLine("Last watered $days day(s) ago")
        }
        if (logCount > 0) appendLine("Includes $logCount log entr${if (logCount == 1) "y" else "ies"}")
    }.trim()
}
