package no.bylinnea.spire.ui

import android.app.Activity
import android.app.Dialog
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import no.bylinnea.spire.R
import no.bylinnea.spire.data.PlantDatabase
import no.bylinnea.spire.data.PlantPhoto

/**
 * The growth-photos strip on the plant detail screen: a horizontal gallery of
 * timestamped photos with add (camera/gallery), tap-to-enlarge, and
 * long-press-to-delete.
 *
 * The host activity owns the activity-result launchers and the styled dialogs
 * (which live on BaseActivity). It injects them here: [launchCamera] /
 * [launchGallery] trigger the launchers, [confirmDelete] / [chooseSource] show
 * the dialogs, and the host forwards launcher results via [onCameraCaptured] /
 * [onGalleryPicked].
 */
class GrowthPhotoSection(
    private val activity: Activity,
    private val db: PlantDatabase,
    private val plantId: () -> Long,
    private val launchCamera: (Uri) -> Unit,
    private val launchGallery: () -> Unit,
    private val confirmDelete: (onConfirm: () -> Unit) -> Unit,
    private val chooseSource: (onCamera: () -> Unit, onGallery: () -> Unit) -> Unit
) {
    private lateinit var adapter: GrowthPhotoAdapter
    private var cameraUri: Uri? = null

    private fun <T : View> find(id: Int): T = activity.findViewById(id)

    fun setup() {
        adapter = GrowthPhotoAdapter(
            mutableListOf(),
            onPhotoTapped = { photo -> showFullscreen(photo) },
            onPhotoLongPressed = { photo ->
                confirmDelete {
                    thread {
                        db.plantPhotoDao().deletePhoto(photo)
                        activity.runOnUiThread {
                            adapter.removePhoto(photo)
                            updateEmpty()
                        }
                    }
                }
            }
        )

        val recycler = find<RecyclerView>(R.id.growthPhotosRecycler)
        recycler.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        recycler.adapter = adapter

        loadPhotos()

        find<TextView>(R.id.btnAddGrowthPhoto).setOnClickListener {
            chooseSource(
                {
                    val file = File.createTempFile("growth_", ".jpg", activity.filesDir)
                    val uri = FileProvider.getUriForFile(
                        activity, "${activity.packageName}.fileprovider", file)
                    cameraUri = uri
                    launchCamera(uri)
                },
                { launchGallery() }
            )
        }
    }

    fun onCameraCaptured() { cameraUri?.let { save(it.toString()) } }

    fun onGalleryPicked(uri: String) { save(uri) }

    private fun save(uri: String) {
        thread {
            val photo = PlantPhoto(plantId = plantId(), photoUri = uri)
            val id    = db.plantPhotoDao().insertPhoto(photo)
            val saved = photo.copy(id = id)
            activity.runOnUiThread {
                adapter.addPhoto(saved)
                find<RecyclerView>(R.id.growthPhotosRecycler).visibility = View.VISIBLE
                find<TextView>(R.id.growthPhotosEmpty).visibility = View.GONE
            }
        }
    }

    private fun loadPhotos() {
        thread {
            val photos = db.plantPhotoDao().getPhotosForPlant(plantId())
            activity.runOnUiThread {
                adapter.setPhotos(photos)
                updateEmpty()
            }
        }
    }

    private fun updateEmpty() {
        val hasPhotos = adapter.itemCount > 0
        find<RecyclerView>(R.id.growthPhotosRecycler).visibility =
            if (hasPhotos) View.VISIBLE else View.GONE
        find<TextView>(R.id.growthPhotosEmpty).visibility =
            if (hasPhotos) View.GONE else View.VISIBLE
    }

    private fun showFullscreen(photo: PlantPhoto) {
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(FrameLayout(activity).apply {
            val imageView = ImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(0xFF000000.toInt())
            }
            Glide.with(activity).load(photo.photoUri).into(imageView)
            addView(imageView)
            addView(TextView(activity).apply {
                text = dateFormat.format(Date(photo.timestamp))
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(48, 48, 48, 48)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
            })
            setOnClickListener { dialog.dismiss() }
        })
        dialog.show()
    }
}
