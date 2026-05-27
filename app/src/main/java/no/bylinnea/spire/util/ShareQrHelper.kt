package no.bylinnea.spire.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import no.bylinnea.spire.R
import no.bylinnea.spire.data.Plant
import no.bylinnea.spire.data.PlantDatabase
import kotlin.concurrent.thread

/**
 * Displays the QR share dialog and generates the QR code bitmap for a plant.
 */
object ShareQrHelper {

    @SuppressLint("SetTextI18n")
    fun showShareDialog(context: Context, plant: Plant, db: PlantDatabase) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_share_options, null)
        view.findViewById<TextView>(R.id.shareTitle).text = "share  ·  ${plant.name}"

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .show()

        view.findViewById<TextView>(R.id.btnInfoOnly).setOnClickListener {
            dialog.dismiss()
            generateAndShow(context, plant, db, includeLogs = false)
        }
        view.findViewById<TextView>(R.id.btnWithLogs).setOnClickListener {
            dialog.dismiss()
            generateAndShow(context, plant, db, includeLogs = true)
        }
    }

    @SuppressLint("SetTextI18n", "InflateParams")
    private fun generateAndShow(
        context: Context,
        plant: Plant,
        db: PlantDatabase,
        includeLogs: Boolean
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_share_qr, null)
        view.findViewById<TextView>(R.id.qrPlantName).text = plant.name

        thread {
            val logs = if (includeLogs)
                db.plantLogDao().getLogsForPlant(plant.id).take(10)
            else
                emptyList()

            val json   = PlantShareHelper.toJson(plant, logs)
            val bitmap = PlantShareHelper.generateQrBitmap(json, 512)

            (context as? Activity)?.runOnUiThread {
                if (bitmap != null) {
                    view.findViewById<ImageView>(R.id.qrImage).setImageBitmap(bitmap)
                }
                if (includeLogs && logs.isNotEmpty()) {
                    view.findViewById<TextView>(R.id.qrSubtitle)?.text =
                        "Includes last ${logs.size} log entries"
                }
            }
        }
    }
}
