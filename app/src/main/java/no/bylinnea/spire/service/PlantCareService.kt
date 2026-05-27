package no.bylinnea.spire.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import no.bylinnea.spire.util.ApiKeyManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Handles plant identification and care recommendations using PlantNet and/or Claude.
 *
 * Identification strategy:
 * - Both keys: PlantNet identifies first, Claude provides care info. Falls back to
 *   Claude-only identification if PlantNet fails or returns low confidence.
 * - PlantNet only: identifies the species, no care info.
 * - Anthropic only: Claude identifies from photo and provides care info in one call.
 */
object PlantCareService {
    private const val PLANTNET_BASE    = "https://my-api.plantnet.org/v2/identify/all"
    private const val ANTHROPIC_URL    = "https://api.anthropic.com/v1/messages"
    private const val MAX_IMAGE_DIMENSION = 800
    private const val JPEG_QUALITY     = 80

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val plantNetClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class PlantCareResult(
        val identifiedSpecies: String? = null,
        val commonName: String? = null,
        val suggestedName: String? = null,
        val confidence: Int? = null,
        val wateringIntervalDays: Int? = null,
        val fertilizerIntervalDays: Int? = null,
        val fertilizerType: String? = null,
        val repottingIntervalDays: Int? = null,
        val mistingIntervalDays: Int? = null,
        val rotatingIntervalDays: Int? = null,
        val sunlight: String? = null,
        val commonIssues: String? = null,
        val extraTip: String? = null,
        val error: String? = null,
        val petSafe: Boolean? = null,
        val wateringTip: String? = null,
        val fertilizingTip: String? = null,
        val repottingTip: String? = null,
        val mistingTip: String? = null,
        val rotatingTip: String? = null,
        val cleaningTip: String? = null,
        val winterWateringIntervalDays: Int? = null,
        val winterFertilizerIntervalDays: Int? = null,
        val winterMistingIntervalDays: Int? = null,
        val cleaningIntervalDays: Int? = null,
        val temperaturePreference: String? = null
    )

    /** Identifies a plant from a photo and returns care recommendations. */
    fun identifyAndGetCare(context: Context, photoUri: Uri): PlantCareResult {
        val anthropicKey = ApiKeyManager.getAnthropicKey(context)
        val plantNetKey  = ApiKeyManager.getPlantNetKey(context)
        val nameStyle    = ApiKeyManager.getNameStyle(context)

        if (anthropicKey == null && plantNetKey == null)
            return PlantCareResult(error = "No API keys. Add them in Settings.")

        val tempFile = uriToTempFile(context, photoUri)
            ?: return PlantCareResult(error = "Could not read the photo. Please try again.")

        return try {
            when {
                plantNetKey != null && anthropicKey != null -> {
                    val species = try { identifyWithPlantNet(tempFile, plantNetKey) } catch (e: Exception) { null }
                    when {
                        species != null -> try {
                            getCareFromClaude(species.first, species.second, anthropicKey, nameStyle).copy(
                                identifiedSpecies = species.first,
                                commonName        = species.second,
                                confidence        = species.third
                            )
                        } catch (e: Exception) {
                            PlantCareResult(
                                identifiedSpecies = species.first,
                                commonName        = species.second,
                                confidence        = species.third,
                                error             = "Plant identified but care info unavailable. Try again."
                            )
                        }
                        else -> try {
                            identifyAndCareWithClaude(tempFile, anthropicKey, nameStyle)
                        } catch (e: Exception) {
                            PlantCareResult(error = "Identification failed. Check your connection and try again.")
                        }
                    }
                }
                plantNetKey != null -> {
                    val species = try { identifyWithPlantNet(tempFile, plantNetKey) } catch (e: Exception) { null }
                    species?.let {
                        PlantCareResult(
                            identifiedSpecies = it.first,
                            commonName        = it.second,
                            confidence        = it.third
                        )
                    } ?: PlantCareResult(error = "Could not identify plant. Try a clearer photo.")
                }
                else -> try {
                    identifyAndCareWithClaude(tempFile, anthropicKey!!, nameStyle)
                } catch (e: Exception) {
                    PlantCareResult(error = "AI request failed. Check your connection and try again.")
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    /** Fetches care info for a known species name without photo identification. */
    fun getCareFromSpeciesName(
        speciesName: String,
        anthropicKey: String,
        nameStyle: String? = null
    ): PlantCareResult = try {
        getCareFromClaude(speciesName, speciesName, anthropicKey, nameStyle)
    } catch (e: Exception) {
        PlantCareResult(error = "Could not get care info: ${e.message}")
    }

    private fun identifyWithPlantNet(imageFile: File, apiKey: String): Triple<String, String, Int>? {
        val compressed = compressImageFile(imageFile)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("images", "plant.jpg", compressed.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val request = Request.Builder()
            .url("$PLANTNET_BASE?api-key=$apiKey&lang=en")
            .post(body).build()
        val response = plantNetClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val json    = JSONObject(response.body?.string() ?: return null)
        val results = json.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val top     = results.getJSONObject(0)
        val score   = (top.optDouble("score", 0.0) * 100).toInt()
        if (score < 20) return null
        val species        = top.optJSONObject("species") ?: return null
        val scientificName = species.optString("scientificNameWithoutAuthor", "Unknown")
        val commonNames    = species.optJSONArray("commonNames")
        val commonName     = if (commonNames != null && commonNames.length() > 0)
            commonNames.getString(0) else scientificName
        return Triple(scientificName, commonName, score)
    }

    private fun identifyAndCareWithClaude(
        imageFile: File,
        apiKey: String,
        nameStyle: String? = null
    ): PlantCareResult {
        val base64Image = Base64.encodeToString(compressImageFile(imageFile), Base64.NO_WRAP)
        val prompt      = buildCarePrompt(includeIdentification = true, nameStyle = nameStyle)
        val requestJson = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 800)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "image")
                            put("source", JSONObject().apply {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                        put(JSONObject().apply { put("type", "text"); put("text", prompt) })
                    })
                })
            })
        }
        return callClaude(requestJson, apiKey, includesIdentification = true)
    }

    private fun getCareFromClaude(
        scientificName: String,
        commonName: String,
        apiKey: String,
        nameStyle: String? = null
    ): PlantCareResult {
        val prompt = "Give me care info for $scientificName ($commonName) as a houseplant.\n" +
                buildCarePrompt(includeIdentification = false, nameStyle = nameStyle)
        val requestJson = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 800)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
        }
        return callClaude(requestJson, apiKey, includesIdentification = false)
    }

    private fun buildCarePrompt(includeIdentification: Boolean, nameStyle: String? = null): String {
        val nameStyleField = if (nameStyle != null)
            "\n  \"suggestedName\": \"<a creative plant nickname inspired by: $nameStyle>\"," else ""

        val identFields = if (includeIdentification) """
            "scientificName": "<species or Unknown>",
            "commonName": "<common name>",
            "confidence": <0-100>,$nameStyleField
        """.trimIndent() else if (nameStyle != null) "\"suggestedName\": \"<creative nickname: $nameStyle>\"," else ""

        return """
            Respond ONLY with a JSON object, no other text, no markdown fences:
            {
              $identFields
              "wateringIntervalDays": <integer, days between watering>,
              "fertilizerIntervalDays": <integer, days between fertilizing, e.g. 30 for monthly>,
              "fertilizerType": "<short recommendation e.g. Balanced liquid NPK, Cactus mix>",
              "repottingIntervalDays": <integer, days between repotting, e.g. 730 for every 2 years. Use null if rarely needed>,
              "mistingIntervalDays": <integer if this plant benefits from misting, else null>,
              "rotatingIntervalDays": <integer, days between rotating for even light, e.g. 14>,
              "sunlight": "<short string e.g. Bright indirect light>",
              "commonIssues": "<one sentence>",
              "extraTip": "<one short tip>",
              "petSafe": <true if safe for cats and dogs, false if toxic, null if unknown>,
              "wateringTip": "<one short plant-specific watering tip or null>",
              "fertilizingTip": "<one short plant-specific fertilizing tip or null>",
              "repottingTip": "<one short plant-specific repotting tip or null>",
              "mistingTip": "<one short misting tip or null if not relevant>",
              "rotatingTip": "<one short rotating tip or null>",
              "winterWateringIntervalDays": <integer, winter watering interval, typically 1.5-2x normal>,
              "winterFertilizerIntervalDays": <integer, winter fertilizing interval, or null if should stop>,
              "winterMistingIntervalDays": <integer, winter misting interval considering dry heating, or null>,
              "cleaningIntervalDays": <integer, days between wiping leaves, e.g. 14, null if not needed>,
              "cleaningTip": "<one short leaf cleaning tip or null>",
              "temperaturePreference": "<preferred temperature range e.g. 18-24°C>"
            }
        """.trimIndent()
    }

    private fun callClaude(
        requestJson: JSONObject,
        apiKey: String,
        includesIdentification: Boolean
    ): PlantCareResult {
        val request = Request.Builder()
            .url(ANTHROPIC_URL)
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .build()

        val response = client.newCall(request).execute()
        if (response.code == 401) return PlantCareResult(error = "Invalid Anthropic API key.")
        if (!response.isSuccessful) return PlantCareResult(error = "AI lookup failed (${response.code}).")
        val body = response.body?.string() ?: return PlantCareResult(error = "Empty response.")

        return try {
            val text = JSONObject(body)
                .getJSONArray("content").getJSONObject(0).getString("text")
                .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val j = JSONObject(text)
            PlantCareResult(
                identifiedSpecies            = if (includesIdentification) j.optString("scientificName").ifBlank { null } else null,
                commonName                   = if (includesIdentification) j.optString("commonName").ifBlank { null } else null,
                suggestedName                = j.optString("suggestedName").ifBlank { null },
                confidence                   = if (includesIdentification) j.optInt("confidence", 0).takeIf { it > 0 } else null,
                wateringIntervalDays         = j.optInt("wateringIntervalDays").takeIf { it > 0 },
                fertilizerIntervalDays       = j.optInt("fertilizerIntervalDays").takeIf { it > 0 },
                fertilizerType               = j.optString("fertilizerType").ifBlank { null },
                repottingIntervalDays        = j.optInt("repottingIntervalDays").takeIf { it > 0 },
                mistingIntervalDays          = j.optInt("mistingIntervalDays").takeIf { it > 0 },
                rotatingIntervalDays         = j.optInt("rotatingIntervalDays").takeIf { it > 0 },
                sunlight                     = j.optString("sunlight").ifBlank { null },
                commonIssues                 = j.optString("commonIssues").ifBlank { null },
                extraTip                     = j.optString("extraTip").ifBlank { null },
                petSafe                      = if (j.isNull("petSafe")) null else j.optBoolean("petSafe"),
                wateringTip                  = j.optString("wateringTip").ifBlank { null },
                fertilizingTip               = j.optString("fertilizingTip").ifBlank { null },
                repottingTip                 = j.optString("repottingTip").ifBlank { null },
                mistingTip                   = j.optString("mistingTip").ifBlank { null },
                rotatingTip                  = j.optString("rotatingTip").ifBlank { null },
                winterWateringIntervalDays   = j.optInt("winterWateringIntervalDays").takeIf { it > 0 },
                winterFertilizerIntervalDays = j.optInt("winterFertilizerIntervalDays").takeIf { it > 0 },
                winterMistingIntervalDays    = j.optInt("winterMistingIntervalDays").takeIf { it > 0 },
                cleaningIntervalDays  = j.optInt("cleaningIntervalDays").takeIf { it > 0 },
                cleaningTip           = j.optString("cleaningTip").ifBlank { null },
                temperaturePreference = j.optString("temperaturePreference").ifBlank { null }
            )
        } catch (e: Exception) {
            PlantCareResult(error = "Could not parse AI response.")
        }
    }

    private fun compressImageFile(file: File): ByteArray {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val scale = maxOf(
            options.outWidth.toFloat()  / MAX_IMAGE_DIMENSION,
            options.outHeight.toFloat() / MAX_IMAGE_DIMENSION, 1f
        )
        val bitmap = BitmapFactory.decodeFile(file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = scale.toInt().coerceAtLeast(1) }
        ) ?: return file.readBytes()
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun uriToTempFile(context: Context, uri: Uri): File? = try {
        val temp = File.createTempFile("plant_id_", ".jpg", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output -> input.copyTo(output) }
        }
        temp
    } catch (e: Exception) { null }
}