package no.bylinnea.spire.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Environment
import android.widget.Switch
import android.widget.Toast
import no.bylinnea.spire.util.ApiKeyManager
import no.bylinnea.spire.util.CropHelper
import no.bylinnea.spire.service.PlantCareService
import no.bylinnea.spire.R
import no.bylinnea.spire.data.Plant

/** Form for adding a new plant. Handles photo capture, cropping, date picking, and optional AI identification. */
class AddPlantActivity : BasePlantFormActivity() {

    private var cameraImageUri: Uri? = null
    private var selectedPhotoUri: Uri? = null
    private var selectedDateAcquired: Long? = null
    private var petSafeValue: Boolean? = null
    private var wateringTip: String? = null
    private var fertilizingTip: String? = null
    private var repottingTip: String? = null
    private var mistingTip: String? = null
    private var rotatingTip: String? = null
    private var cleaningTip: String? = null
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override fun getSpeciesInputId()   = R.id.inputSpecies
    override fun getLightPrefInputId() = R.id.inputLightPreference
    override fun getNotesInputId()     = R.id.inputNotes
    override fun getPetSafeValue()     = petSafeValue
    override fun setPetSafeValue(value: Boolean?) { petSafeValue = value }
    override fun getWateringTip()      = wateringTip
    override fun setWateringTip(value: String?) { wateringTip = value }
    override fun getFertilizingTip()   = fertilizingTip
    override fun setFertilizingTip(value: String?) { fertilizingTip = value }
    override fun getRepottingTip()     = repottingTip
    override fun setRepottingTip(value: String?) { repottingTip = value }
    override fun getMistingTip()       = mistingTip
    override fun setMistingTip(value: String?) { mistingTip = value }
    override fun getRotatingTip()      = rotatingTip
    override fun setRotatingTip(value: String?) { rotatingTip = value }
    override fun getCleaningTip() = cleaningTip
    override fun setCleaningTip(value: String?) { cleaningTip = value }
    override fun updatePetSafePills() {
        val petSafeUnknown = findViewById<TextView>(R.id.petSafeUnknown)
        val petSafeSafe    = findViewById<TextView>(R.id.petSafeSafe)
        val petSafeToxic   = findViewById<TextView>(R.id.petSafeToxic)
        val pills = listOf(petSafeUnknown to null, petSafeSafe to true, petSafeToxic to false)
        pills.forEach { (pill, value) ->
            if (value == petSafeValue) {
                pill.background = ContextCompat.getDrawable(this, R.drawable.pill_selected)
                pill.setTextColor(0xFFFFFFFF.toInt())
            } else {
                pill.background = ContextCompat.getDrawable(this, R.drawable.pill_unselected)
                pill.setTextColor(ContextCompat.getColor(this, R.color.green_muted))
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) launchCrop(cameraImageUri!!)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = createImageFile()
            cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            cameraLauncher.launch(cameraImageUri)
        } else {
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            launchCrop(uri)
        }
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val croppedUri = CropHelper.getResultUri(result.data!!)
            if (croppedUri != null) {
                selectedPhotoUri = croppedUri
                showPhotoPreview(croppedUri)
                if (ApiKeyManager.isAiEnabled(this)) {
                    findViewById<TextView>(R.id.btnAskAi).visibility = View.VISIBLE
                }
            }
        }
    }

    private fun launchCrop(uri: Uri) {
        cropLauncher.launch(CropHelper.buildCropIntent(this, uri))
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_plant)

        val petSafeUnknown = findViewById<TextView>(R.id.petSafeUnknown)
        val petSafeSafe    = findViewById<TextView>(R.id.petSafeSafe)
        val petSafeToxic   = findViewById<TextView>(R.id.petSafeToxic)
        petSafeUnknown.setOnClickListener { petSafeValue = null;  updatePetSafePills() }
        petSafeSafe.setOnClickListener    { petSafeValue = true;  updatePetSafePills() }
        petSafeToxic.setOnClickListener   { petSafeValue = false; updatePetSafePills() }
        updatePetSafePills()

        val locationView = findViewById<TextView>(R.id.inputLocation)
        locationView.setOnClickListener { showLocationPicker(locationView) }

        if (!ApiKeyManager.isAiEnabled(this)) {
            findViewById<View>(R.id.identificationCard).visibility = View.GONE
        }

        findViewById<TextView>(R.id.buttonCamera).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                val file = createImageFile()
                cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                cameraLauncher.launch(cameraImageUri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        findViewById<TextView>(R.id.buttonGallery).setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        findViewById<TextView>(R.id.btnDateAcquired).setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateAcquired = cal.timeInMillis
                findViewById<TextView>(R.id.btnDateAcquired).apply {
                    text = dateFormat.format(Date(selectedDateAcquired!!))
                    setTextColor(ContextCompat.getColor(this@AddPlantActivity, R.color.text_primary))
                }
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Only show the AI button once a photo has been selected
        val aiBtn = findViewById<TextView>(R.id.btnAskAi)
        aiBtn.visibility = if (ApiKeyManager.isAiEnabled(this) && selectedPhotoUri != null)
            View.VISIBLE else View.GONE
        aiBtn.setOnClickListener {
            val uri = selectedPhotoUri ?: return@setOnClickListener
            askAiForCare(uri)
        }

        setupAiByNameButton(findViewById(R.id.btnAskAiByName))

        findViewById<TextView>(R.id.buttonSave).setOnClickListener {
            val name     = textOf(R.id.inputPlantName)
            val interval = intOf(R.id.inputWateringInterval)
            if (name.isEmpty())    { findEdit(R.id.inputPlantName).error = "Please enter a plant name"; return@setOnClickListener }
            if (interval == null)  { findEdit(R.id.inputWateringInterval).error = "Enter a valid number"; return@setOnClickListener }

            val newPlant = Plant(
                name = name,
                wateringIntervalDays = interval,
                photoUri = selectedPhotoUri?.toString(),
                species = textOrNull(R.id.inputSpecies),
                location = locationView.text.toString().trim().ifEmpty { null },
                lightPreference = textOrNull(R.id.inputLightPreference),
                dateAcquired = selectedDateAcquired,
                notes = textOrNull(R.id.inputNotes),
                fertilizerIntervalDays = intOrNull(R.id.inputFertilizerInterval),
                fertilizerType = textOrNull(R.id.inputFertilizerType),
                repottingIntervalDays = intOrNull(R.id.inputRepottingInterval),
                mistingIntervalDays = intOrNull(R.id.inputMistingInterval),
                rotatingIntervalDays = intOrNull(R.id.inputRotatingInterval),
                isPetSafe = petSafeValue,
                wateringTip = wateringTip,
                fertilizingTip = fertilizingTip,
                repottingTip = repottingTip,
                mistingTip = mistingTip,
                rotatingTip = rotatingTip,
                cleaningTip = cleaningTip,
                winterWateringIntervalDays = intOrNull(R.id.inputWinterWateringInterval),
                winterFertilizerIntervalDays = intOrNull(R.id.inputWinterFertilizerInterval),
                winterMistingIntervalDays = intOrNull(R.id.inputWinterMistingInterval),
                winterScheduleDisabled = findViewById<Switch>(R.id.switchWinterDisabled).isChecked,
                cleaningIntervalDays = intOrNull(R.id.inputCleaningInterval),
                temperaturePreference = textOrNull(R.id.inputTemperaturePreference)
            )
            setResult(RESULT_OK, Intent().putExtra("plant", newPlant))
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun askAiForCare(uri: Uri) {
        val aiBtn = findViewById<TextView>(R.id.btnAskAi)
        aiBtn.text = "identifying..."; aiBtn.isEnabled = false
        thread {
            val result = PlantCareService.identifyAndGetCare(this, uri)
            runOnUiThread {
                aiBtn.text = "ask AI for care info"; aiBtn.isEnabled = true
                if (result.error != null && result.identifiedSpecies == null) {
                    showStyledDialog("AI lookup failed", result.error, "OK") {}
                    return@runOnUiThread
                }
                showAiResultDialog(result)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showPhotoPreview(uri: Uri) {
        Glide.with(this).load(uri).circleCrop().into(findViewById(R.id.photoPreview))
        findViewById<TextView>(R.id.photoLabel).text = "photo selected ✓"
    }

    private fun createImageFile(): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile("PLANT_${ts}_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
    }
}