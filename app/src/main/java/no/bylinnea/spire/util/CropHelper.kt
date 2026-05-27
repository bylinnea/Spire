package no.bylinnea.spire.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yalantis.ucrop.UCrop
import java.io.File
import androidx.core.graphics.toColorInt

/**
 * Wraps uCrop to provide a consistent square/circle crop flow for plant photos.
 */
object CropHelper {

    fun buildCropIntent(
        context: Context,
        sourceUri: Uri
    ): Intent {
        val destFile = File(context.filesDir, "cropped_${System.currentTimeMillis()}.jpg")
        val destUri  = Uri.fromFile(destFile)

        val options = UCrop.Options().apply {
            setCircleDimmedLayer(true)
            setShowCropFrame(false)
            setShowCropGrid(false)

            setToolbarColor("#2A4A2A".toColorInt())
            setStatusBarColor("#1E3A1E".toColorInt())
            setToolbarWidgetColor("#F0EDE6".toColorInt())
            setActiveControlsWidgetColor("#4A7A3A".toColorInt())
            
            setCompressionQuality(90)
            setMaxBitmapSize(1024)
        }

        return UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(512, 512)
            .withOptions(options)
            .getIntent(context)
    }

    fun getResultUri(data: Intent): Uri? =
        UCrop.getOutput(data)

}
