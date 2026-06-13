package no.bylinnea.spire.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import java.io.File
import kotlin.concurrent.thread
import no.bylinnea.spire.R
import no.bylinnea.spire.data.Plant
import no.bylinnea.spire.data.PlantDatabase
import no.bylinnea.spire.data.PlantLog
import no.bylinnea.spire.service.PlantDoctorService
import no.bylinnea.spire.util.ApiKeyManager

/**
 * The AI "plant doctor" card on the plant detail screen: lets the user attach a
 * photo (camera or gallery), runs it through [PlantDoctorService], shows the
 * diagnosis, and logs it.
 *
 * The host activity owns the activity-result launchers (they must be registered
 * on the activity) and forwards their results via [onCameraCaptured] /
 * [onGalleryPicked]. [launchCamera] / [launchGallery] trigger those launchers,
 * and [onLogChanged] is called after a diagnosis is saved so the host can
 * refresh its log list.
 */
class PlantDoctorSection(
    private val activity: Activity,
    private val db: PlantDatabase,
    private val plant: () -> Plant,
    private val launchCamera: (Uri) -> Unit,
    private val launchGallery: () -> Unit,
    private val onLogChanged: () -> Unit
) {
    private var photoUri: Uri? = null
    private var cameraUri: Uri? = null

    private fun <T : View?> find(id: Int): T = activity.findViewById(id)

    fun setup() {
        if (!ApiKeyManager.isAiEnabled(activity)) return
        find<View>(R.id.doctorCard).visibility = View.VISIBLE
        find<TextView>(R.id.btnDoctorCamera).setOnClickListener {
            val file = File.createTempFile("doctor_", ".jpg", activity.cacheDir)
            val uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", file)
            cameraUri = uri
            launchCamera(uri)
        }
        find<TextView>(R.id.btnDoctorGallery).setOnClickListener { launchGallery() }
        find<TextView>(R.id.btnDiagnose).setOnClickListener { runDiagnosis() }
    }

    fun onCameraCaptured() { cameraUri?.let { setPhoto(it) } }

    fun onGalleryPicked(uri: Uri) { setPhoto(uri) }

    private fun setPhoto(uri: Uri) {
        photoUri = uri
        Glide.with(activity).load(uri).circleCrop().into(find<ImageView>(R.id.doctorPhotoPreview))
        find<TextView>(R.id.btnDiagnose).visibility = View.VISIBLE
        find<TextView>(R.id.doctorStatus).visibility = View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun runDiagnosis() {
        val uri         = photoUri ?: return
        val statusView  = find<TextView>(R.id.doctorStatus)
        val diagnoseBtn = find<TextView>(R.id.btnDiagnose)

        diagnoseBtn.text = "analysing..."; diagnoseBtn.isEnabled = false
        statusView.visibility = View.VISIBLE
        statusView.text = "🔍 Looking at your plant..."
        statusView.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))

        val capturedPlant = plant()
        thread {
            val result = PlantDoctorService.diagnose(activity, uri, capturedPlant)
            activity.runOnUiThread {
                diagnoseBtn.text = "diagnose plant"; diagnoseBtn.isEnabled = true
                if (result.error != null) {
                    statusView.text = result.error
                    statusView.setTextColor(ContextCompat.getColor(activity, R.color.status_overdue_dot))
                    return@runOnUiThread
                }
                // Strip any markdown formatting Claude may return before displaying
                val diagnosis = (result.diagnosis ?: "No diagnosis available.")
                    .lines().joinToString("\n") { line ->
                        line.replace(Regex("^#+\\s*"), "")
                            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                            .replace(Regex("\\*(.*?)\\*"), "$1")
                    }.trim()
                statusView.text = diagnosis
                statusView.setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
                val capturedPlantId = capturedPlant.id
                thread {
                    db.plantLogDao().insertLog(
                        PlantLog(plantId = capturedPlantId, note = "🩺 $diagnosis")
                    )
                    activity.runOnUiThread { onLogChanged() }
                }
            }
        }
    }

    fun reset() {
        photoUri  = null
        cameraUri = null
        find<ImageView?>(R.id.doctorPhotoPreview)?.setImageResource(R.drawable.ic_camera)
        find<TextView>(R.id.btnDiagnose).visibility = View.GONE
        find<TextView>(R.id.doctorStatus).apply { visibility = View.GONE; text = "" }
    }
}