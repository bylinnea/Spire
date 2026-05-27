package no.bylinnea.spire.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import no.bylinnea.spire.R
import no.bylinnea.spire.data.PlantDatabase
import kotlin.concurrent.thread

/**
 * Base class for all activities in Spire.
 * Provides shared dialogs and the plant sitter guide generator.
 */
open class BaseActivity : AppCompatActivity() {

        protected fun showStyledDialog(
                title: String,
                message: String,
                positiveText: String,
                negativeText: String = "cancel",
                onPositive: () -> Unit
        ) {
                val view = layoutInflater.inflate(R.layout.dialog_room_action, null)
                view.findViewById<TextView>(R.id.dialogTitle).text = title
                view.findViewById<TextView>(R.id.dialogMessage).text = message
                val btnPos = view.findViewById<TextView>(R.id.dialogBtnPositive)
                val btnNeg = view.findViewById<TextView>(R.id.dialogBtnNegative)
                btnPos.text = positiveText
                btnNeg.text = negativeText
                val dialog = AlertDialog.Builder(this).setView(view).show()
                btnPos.setOnClickListener { dialog.dismiss(); onPositive() }
                btnNeg.setOnClickListener { dialog.dismiss() }
        }

        protected fun showTwoOptionDialog(
                title: String,
                message: String,
                option1Text: String,
                option2Text: String,
                onOption1: () -> Unit,
                onOption2: () -> Unit
        ) {
                val view = layoutInflater.inflate(R.layout.dialog_room_action, null)
                view.findViewById<TextView>(R.id.dialogTitle).text = title
                view.findViewById<TextView>(R.id.dialogMessage).text = message
                val btnPos = view.findViewById<TextView>(R.id.dialogBtnPositive)
                val btnNeg = view.findViewById<TextView>(R.id.dialogBtnNegative)
                btnPos.text = option1Text
                btnNeg.text = option2Text
                btnNeg.setTextColor(ContextCompat.getColor(this, R.color.green_forest))
                btnNeg.background = ContextCompat.getDrawable(this, R.drawable.btn_outline_green)
                val dialog = AlertDialog.Builder(this).setView(view).show()
                btnPos.setOnClickListener { dialog.dismiss(); onOption1() }
                btnNeg.setOnClickListener { dialog.dismiss(); onOption2() }
        }

        @SuppressLint("SetTextI18n")
        protected fun showPlantSitterDialog(db: PlantDatabase) {
                val view = layoutInflater.inflate(R.layout.dialog_room_action, null)
                view.findViewById<TextView>(R.id.dialogTitle).text = "plant sitter"
                view.findViewById<TextView>(R.id.dialogMessage).text = "How many days will you be away?"

                val input = EditText(this).apply {
                        hint = "e.g. 14"
                        inputType = InputType.TYPE_CLASS_NUMBER
                        setPadding(48, 16, 48, 16)
                }
                (view.findViewById<TextView>(R.id.dialogTitle).parent as ViewGroup)
                        .addView(input, 2)

                val btnPos = view.findViewById<TextView>(R.id.dialogBtnPositive)
                val btnNeg = view.findViewById<TextView>(R.id.dialogBtnNegative)
                btnPos.text = "generate guide"
                btnNeg.text = "cancel"

                val dialog = AlertDialog.Builder(this).setView(view).show()

                btnPos.setOnClickListener {
                        val days = input.text.toString().toIntOrNull()
                        if (days == null || days <= 0) {
                                input.error = "Please enter a valid number of days"
                                return@setOnClickListener
                        }
                        dialog.dismiss()
                        generatePlantSitterGuide(db, days)
                }
                btnNeg.setOnClickListener { dialog.dismiss() }
        }

        private fun generatePlantSitterGuide(db: PlantDatabase, days: Int) {
                thread {
                        val plants = db.plantDao().getAllPlants()
                        val now    = System.currentTimeMillis()
                        val dayMs  = 24 * 60 * 60 * 1000L

                        val text = buildString {
                                appendLine("🌿 Plant Sitter Guide - $days day${if (days == 1) "" else "s"}")
                                appendLine("=".repeat(40))
                                appendLine()

                                plants.forEach { plant ->
                                        val tasks = mutableListOf<String>()

                                        // Calculate which days during the trip each care task falls due,
                                        // starting from when it was last done
                                        val lastWatered = plant.lastWateredDate ?: 0L
                                        val daysSince   = ((now - lastWatered) / dayMs).toInt()
                                        val waterDays   = mutableListOf<Int>()
                                        var next        = plant.wateringIntervalDays - daysSince
                                        while (next <= days) {
                                                if (next > 0) waterDays.add(next)
                                                next += plant.wateringIntervalDays
                                        }
                                        if (waterDays.isNotEmpty())
                                                tasks.add("💧 Water on day${if (waterDays.size > 1) "s" else ""}: ${waterDays.joinToString(", ")}")
                                        else
                                                tasks.add("💧 No watering needed this trip!")

                                        listOf(
                                                Triple(plant.fertilizerIntervalDays, plant.lastFertilizedDate, "🌱 Fertilize"),
                                                Triple(plant.mistingIntervalDays,    plant.lastMistedDate,     "💦 Mist"),
                                                Triple(plant.cleaningIntervalDays,   plant.lastCleanedDate,    "🍃 Clean leaves"),
                                                Triple(plant.rotatingIntervalDays,   plant.lastRotatedDate,    "🔄 Rotate")
                                        ).forEach { (interval, lastDone, label) ->
                                                if (interval != null) {
                                                        val sinceDone = ((now - (lastDone ?: 0L)) / dayMs).toInt()
                                                        val dueDays   = mutableListOf<Int>()
                                                        var nextDay   = interval - sinceDone
                                                        while (nextDay <= days) {
                                                                if (nextDay > 0) dueDays.add(nextDay)
                                                                nextDay += interval
                                                        }
                                                        if (dueDays.isNotEmpty())
                                                                tasks.add("$label on day${if (dueDays.size > 1) "s" else ""}: ${dueDays.joinToString(", ")}")
                                                }
                                        }

                                        appendLine("🪴 ${plant.name}")
                                        if (!plant.location.isNullOrBlank()) appendLine("   📍 ${plant.location}")
                                        tasks.forEach { appendLine("   $it") }
                                        appendLine()
                                }

                                appendLine("Thank you for looking after my plants! 🌱")
                        }

                        runOnUiThread {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Plant Sitter Guide")
                                        putExtra(Intent.EXTRA_TEXT, text)
                                }
                                startActivity(Intent.createChooser(intent, "Share plant sitter guide"))
                        }
                }
        }
}