package no.bylinnea.spire.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import no.bylinnea.spire.util.ApiKeyManager
import no.bylinnea.spire.service.PlantCareService
import no.bylinnea.spire.R
import no.bylinnea.spire.data.PlantDatabase
import kotlin.concurrent.thread

/**
 * Shared base for AddPlantActivity and EditPlantActivity.
 * Handles AI care lookup, the room picker, and form field helpers.
 */
abstract class BasePlantFormActivity : BaseActivity() {

    protected abstract fun getSpeciesInputId(): Int
    protected abstract fun getLightPrefInputId(): Int
    protected abstract fun getNotesInputId(): Int
    protected abstract fun getPetSafeValue(): Boolean?
    protected abstract fun setPetSafeValue(value: Boolean?)
    protected abstract fun updatePetSafePills()
    protected abstract fun getWateringTip(): String?
    protected abstract fun setWateringTip(value: String?)
    protected abstract fun getFertilizingTip(): String?
    protected abstract fun setFertilizingTip(value: String?)
    protected abstract fun getRepottingTip(): String?
    protected abstract fun setRepottingTip(value: String?)
    protected abstract fun getMistingTip(): String?
    protected abstract fun setMistingTip(value: String?)
    protected abstract fun getRotatingTip(): String?
    protected abstract fun setRotatingTip(value: String?)
    protected abstract fun setCleaningTip(value: String?)
    protected abstract fun getCleaningTip(): String?


    @SuppressLint("SetTextI18n")
    protected fun setupAiByNameButton(btn: TextView) {
        if (!ApiKeyManager.isAiEnabled(this)) {
            btn.visibility = View.GONE
            return
        }

        // Only show the button when a species name has been entered
        val speciesField = findEdit(getSpeciesInputId())
        btn.visibility = if (speciesField.text.isNullOrBlank()) View.GONE else View.VISIBLE

        speciesField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btn.visibility = if (s.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        })

        btn.setOnClickListener {
            val species = speciesField.text.toString().trim()
            if (species.isBlank()) return@setOnClickListener
            btn.text = "identifying..."; btn.isEnabled = false
            thread {
                val anthropicKey = ApiKeyManager.getAnthropicKey(this)
                if (anthropicKey == null) {
                    runOnUiThread {
                        btn.text = "✨ get care from species name"
                        btn.isEnabled = true
                        Toast.makeText(this, "No Anthropic API key set", Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }
                val nameStyle = ApiKeyManager.getNameStyle(this)
                val result    = PlantCareService.getCareFromSpeciesName(species, anthropicKey, nameStyle)
                runOnUiThread {
                    btn.text = "✨ get care from species name"
                    btn.isEnabled = true
                    if (result.error != null) {
                        showStyledDialog("AI lookup failed", result.error, "OK") {}
                        return@runOnUiThread
                    }
                    showAiResultDialog(result)
                }
            }
        }
    }

    protected fun showAiResultDialog(result: PlantCareService.PlantCareResult) {
        val identified = listOfNotNull(
            result.suggestedName?.let { "Suggested name: $it" },
            result.commonName?.let    { "Plant: $it" },
            result.identifiedSpecies?.let { "Species: $it" },
            result.confidence?.let    { "Confidence: $it%" }
        )
        val care = listOfNotNull(
            result.wateringIntervalDays?.let   { "💧 Water every $it days" },
            result.fertilizerIntervalDays?.let { "🌱 Fertilize every $it days" },
            result.fertilizerType?.let         { "   Type: $it" },
            result.repottingIntervalDays?.let  { "🪴 Repot every $it days" },
            result.mistingIntervalDays?.let    { "🌫️ Mist every $it days" },
            result.rotatingIntervalDays?.let   { "🔄 Rotate every $it days" },
            result.cleaningIntervalDays?.let { "🍃 Clean leaves every $it days" },
            result.sunlight?.let               { "☀️ $it" },
            result.petSafe?.let                { if (it) "🐾 Pet safe" else "⚠️ Toxic to pets" }
        )
        val message = buildString {
            if (identified.isNotEmpty()) { append(identified.joinToString("\n")); append("\n\n") }
            append("Care recommendations:\n")
            append(care.joinToString("\n").ifEmpty { "No care data returned." })
            append("\n\nApply these values to the form?")
        }
        showStyledDialog(
            title        = "AI care recommendations",
            message      = message,
            positiveText = "apply",
            negativeText = "cancel"
        ) { applyAiResult(result) }
    }

    protected fun applyAiResult(result: PlantCareService.PlantCareResult) {
        if (!ApiKeyManager.isAiDisclaimerShown(this)) {
            showStyledDialog(
                title        = "a note on AI suggestions",
                message      = "AI care recommendations are a helpful starting point, not professional advice.\n\nPet safety information in particular may not be accurate — always verify with a vet before introducing plants to pets.",
                positiveText = "got it",
                negativeText = ""
            ) {
                ApiKeyManager.setAiDisclaimerShown(this)
                doApplyAiResult(result)
            }
            return
        }
        doApplyAiResult(result)
    }

    private fun doApplyAiResult(result: PlantCareService.PlantCareResult) {
        // Use suggested name first, fall back to common name, then scientific name
        val displayName = result.suggestedName ?: result.commonName ?: result.identifiedSpecies
        displayName?.let                     { prefillIfEmpty(R.id.inputPlantName,              it) }
        result.identifiedSpecies?.let        { prefillIfEmpty(R.id.inputSpecies,                it) }
        result.wateringIntervalDays?.let     { prefillIfEmpty(R.id.inputWateringInterval,       it.toString()) }
        result.fertilizerIntervalDays?.let   { prefillIfEmpty(R.id.inputFertilizerInterval,     it.toString()) }
        result.fertilizerType?.let           { prefillIfEmpty(R.id.inputFertilizerType,         it) }
        result.repottingIntervalDays?.let    { prefillIfEmpty(R.id.inputRepottingInterval,      it.toString()) }
        result.mistingIntervalDays?.let      { prefillIfEmpty(R.id.inputMistingInterval,        it.toString()) }
        result.rotatingIntervalDays?.let     { prefillIfEmpty(R.id.inputRotatingInterval,       it.toString()) }
        result.sunlight?.let                 { prefillIfEmpty(R.id.inputLightPreference,        it) }
        result.winterWateringIntervalDays?.let   { prefillIfEmpty(R.id.inputWinterWateringInterval,   it.toString()) }
        result.winterFertilizerIntervalDays?.let { prefillIfEmpty(R.id.inputWinterFertilizerInterval, it.toString()) }
        result.winterMistingIntervalDays?.let    { prefillIfEmpty(R.id.inputWinterMistingInterval,    it.toString()) }
        result.cleaningIntervalDays?.let  { prefillIfEmpty(R.id.inputCleaningInterval, it.toString()) }
        result.temperaturePreference?.let { prefillIfEmpty(R.id.inputTemperaturePreference, it) }
        val careBits = listOfNotNull(
            result.commonIssues?.let { "⚠️ $it" },
            result.extraTip?.let     { "💡 $it" }
        )
        if (careBits.isNotEmpty()) prefillIfEmpty(R.id.inputNotes, careBits.joinToString("\n"))
        result.petSafe?.let { setPetSafeValue(it); updatePetSafePills() }
        setWateringTip(result.wateringTip)
        setFertilizingTip(result.fertilizingTip)
        setRepottingTip(result.repottingTip)
        setMistingTip(result.mistingTip)
        setRotatingTip(result.rotatingTip)
        setCleaningTip(result.cleaningTip)
    }
    // Only fills a field if it is currently empty, preserving any value the user typed manually
    protected fun prefillIfEmpty(id: Int, value: String) {
        val v = findEdit(id); if (v.text.isNullOrBlank()) v.setText(value)
    }
    @SuppressLint("SetTextI18n")
    protected fun showLocationPicker(locationView: TextView) {
        thread {
            val db = PlantDatabase.getDatabase(this)
            val rooms = db.plantDao().getAllPlants()
                .mapNotNull { it.location?.trim() }
                .filter { it.isNotBlank() }
                .distinct().sorted()
            runOnUiThread {
                val view = layoutInflater.inflate(R.layout.dialog_room_action, null)
                view.findViewById<TextView>(R.id.dialogTitle).text = "select room"
                view.findViewById<TextView>(R.id.dialogMessage).visibility = View.GONE
                val btnPos = view.findViewById<TextView>(R.id.dialogBtnPositive)
                val btnNeg = view.findViewById<TextView>(R.id.dialogBtnNegative)
                btnPos.text = "＋ add new room"
                btnNeg.text = "cancel"
                val container = view.findViewById<TextView>(R.id.dialogTitle).parent as ViewGroup
                val dialog = AlertDialog.Builder(this).setView(view).show()
                btnNeg.setOnClickListener { dialog.dismiss() }

                rooms.forEach { room ->
                    val roomBtn = TextView(this).apply {
                        text = room
                        textSize = 14f
                        setTextColor(ContextCompat.getColor(this@BasePlantFormActivity, R.color.green_forest))
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        gravity = Gravity.CENTER
                        setPadding(0, 36, 0, 36)
                        background = ContextCompat.getDrawable(this@BasePlantFormActivity, R.drawable.btn_outline_green)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 24 }
                    }
                    roomBtn.setOnClickListener {
                        locationView.text = room
                        locationView.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                        dialog.dismiss()
                    }
                    container.addView(roomBtn, container.indexOfChild(btnPos))
                }

                btnPos.setOnClickListener {
                    dialog.dismiss()
                    val input = EditText(this).apply {
                        hint = "e.g. Living room"; setPadding(48, 32, 48, 16)
                    }
                    val addView = layoutInflater.inflate(R.layout.dialog_room_action, null)
                    addView.findViewById<TextView>(R.id.dialogTitle).text = "new room"
                    addView.findViewById<TextView>(R.id.dialogMessage).visibility = View.GONE
                    (addView.findViewById<TextView>(R.id.dialogTitle).parent as ViewGroup).addView(input, 1)
                    val addDialog = AlertDialog.Builder(this).setView(addView).show()
                    addView.findViewById<TextView>(R.id.dialogBtnPositive).apply {
                        text = "add room"
                        setOnClickListener {
                            val newRoom = input.text.toString().trim()
                            if (newRoom.isNotBlank()) {
                                locationView.text = newRoom
                                locationView.setTextColor(ContextCompat.getColor(this@BasePlantFormActivity, R.color.text_primary))
                            }
                            addDialog.dismiss()
                        }
                    }
                    addView.findViewById<TextView>(R.id.dialogBtnNegative).apply {
                        text = "cancel"
                        setOnClickListener { addDialog.dismiss() }
                    }
                }
            }
        }
    }
    protected fun findEdit(id: Int) = findViewById<EditText>(id)
    protected fun textOf(id: Int)   = findEdit(id).text.toString().trim()
    protected fun textOrNull(id: Int) = textOf(id).ifEmpty { null }
    protected fun intOf(id: Int)    = textOf(id).toIntOrNull()?.takeIf { it > 0 }
    protected fun intOrNull(id: Int) = textOf(id).toIntOrNull()?.takeIf { it > 0 }
}