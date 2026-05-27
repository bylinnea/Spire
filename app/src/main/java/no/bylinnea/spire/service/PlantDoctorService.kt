package no.bylinnea.spire.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import no.bylinnea.spire.util.ApiKeyManager
import no.bylinnea.spire.data.Plant
import okhttp3.MediaType.Companion.toMediaType
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
 * Sends a plant photo to Claude along with the plant's care context and returns
 * a short plain-text diagnosis. Requires an Anthropic API key.
 */
object PlantDoctorService {

    private const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
    private const val MAX_IMAGE_DIMENSION = 800
    private const val JPEG_QUALITY = 80

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class DiagnosisResult(
        val diagnosis: String? = null,
        val error: String? = null
    )

    fun diagnose(context: Context, photoUri: Uri, plant: Plant): DiagnosisResult {
        val apiKey = ApiKeyManager.getAnthropicKey(context)
            ?: return DiagnosisResult(error = "No Anthropic API key. Add it in Settings.")

        val tempFile = uriToTempFile(context, photoUri)
            ?: return DiagnosisResult(error = "Could not read the photo.")

        return try {
            val base64Image = Base64.encodeToString(
                compressImage(tempFile), Base64.NO_WRAP
            )

            // Build a short care context string to help Claude give more relevant advice
            val contextStr = buildString {
                append("Plant: ${plant.name}")
                plant.species?.let { append(", species: $it") }
                append(". Waters every ${plant.wateringIntervalDays} days")
                plant.lastWateredDate?.let {
                    val days = TimeUnit.MILLISECONDS
                        .toDays(System.currentTimeMillis() - it)
                    append(", last watered $days days ago")
                }
                plant.location?.let { append(". Location: $it") }
            }

            val prompt = """
                $contextStr.
                Look at this plant photo. In 2-3 sentences: what looks wrong (if anything), 
                why it might be happening, and one concrete action to take.
                If the plant looks healthy, just say so briefly.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("model", "claude-haiku-4-5-20251001")
                put("max_tokens", 200)
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
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(ANTHROPIC_URL)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .build()

            val response = client.newCall(request).execute()

            when {
                response.code == 401 -> DiagnosisResult(error = "Invalid API key.")
                !response.isSuccessful -> DiagnosisResult(error = "Diagnosis failed (${response.code}).")
                else -> {
                    val text = JSONObject(response.body?.string() ?: "")
                        .getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()
                    DiagnosisResult(diagnosis = text)
                }
            }
        } catch (e: Exception) {
            DiagnosisResult(error = "Something went wrong: ${e.message}")
        } finally {
            tempFile.delete()
        }
    }

    private fun compressImage(file: File): ByteArray {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val scale = maxOf(opts.outWidth.toFloat() / MAX_IMAGE_DIMENSION,
            opts.outHeight.toFloat() / MAX_IMAGE_DIMENSION, 1f)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = scale.toInt().coerceAtLeast(1) })
            ?: return file.readBytes()
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun uriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val temp = File.createTempFile("diagnosis_", ".jpg", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { it2 -> input.copyTo(it2) }
            }
            temp
        } catch (e: Exception) { null }
    }
}
