package no.bylinnea.spire.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import no.bylinnea.spire.util.ApiKeyManager
import no.bylinnea.spire.util.CareTask
import no.bylinnea.spire.R
import no.bylinnea.spire.data.PlantDatabase
import no.bylinnea.spire.util.BackupHelper
import kotlin.concurrent.thread

/**
 * Settings screen. Covers AI keys, health log toggles, notifications, rooms, backup/restore, and danger zone.
 */
class SettingsActivity : BaseActivity() {

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        thread {
            try {
                val json = contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
                    ?: return@thread
                val db     = PlantDatabase.getDatabase(this)
                val result = BackupHelper.importFromJson(json, db)
                runOnUiThread {
                    val msg = when {
                        result.imported == 0 && result.skipped == 0 ->
                            "No plants found in the file."
                        result.imported == 0 ->
                            "Nothing imported - all ${result.skipped} plant(s) already exist."
                        result.skipped == 0 ->
                            "Imported ${result.imported} plant(s) successfully."
                        else ->
                            "Imported ${result.imported} plant(s). Skipped ${result.skipped} (already exist)."
                    }
                    showStyledDialog("import complete", msg, "ok") {}
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showStyledDialog("import failed",
                        "Could not read the file. Make sure it's a Plant Mom backup.",
                        "ok") {}
                }
            }
        }
    }

    @SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val anthropicInput    = findViewById<EditText>(R.id.inputApiKey)
        val plantnetInput     = findViewById<EditText>(R.id.inputPlantNetKey)
        val btnSave           = findViewById<TextView>(R.id.btnSaveKey)
        val btnClearAnt       = findViewById<TextView>(R.id.btnClearKey)
        val btnClearPN        = findViewById<TextView>(R.id.btnClearPlantNet)
        val btnBack           = findViewById<TextView>(R.id.btnBack)
        val anthropicStatus   = findViewById<TextView>(R.id.keyStatus)
        val plantnetStatus    = findViewById<TextView>(R.id.plantnetStatus)
        val aiSwitch          = findViewById<Switch>(R.id.switchAiEnabled)
        val singleNotifSwitch = findViewById<Switch>(R.id.switchSingleNotif)
        val aiSection         = findViewById<View>(R.id.aiKeysSection)
        val inputNameStyle = findViewById<EditText>(R.id.inputNameStyle)
        val btnToggleKeys     = findViewById<TextView>(R.id.btnToggleAiKeys)
        val btnAiKeysInfo     = findViewById<TextView>(R.id.btnAiKeysInfo)
        val aiKeysInfoSection = findViewById<View>(R.id.aiKeysInfoSection)
        val switchWinter      = findViewById<Switch>(R.id.switchWinterMode)
        val btnWinterInfo     = findViewById<TextView>(R.id.btnWinterInfo)
        val winterInfoText    = findViewById<TextView>(R.id.winterInfoText)

        updateAnthropicStatus(anthropicStatus, btnClearAnt)
        updatePlantNetStatus(plantnetStatus, btnClearPN)

        val aiEnabled = ApiKeyManager.isAiEnabled(this)
        aiSwitch.isChecked       = aiEnabled
        aiSection.visibility     = View.GONE
        btnToggleKeys.visibility = if (aiEnabled) View.VISIBLE else View.GONE
        btnAiKeysInfo.visibility = if (aiEnabled) View.VISIBLE else View.GONE

        aiSwitch.setOnCheckedChangeListener { _, isChecked ->
            ApiKeyManager.setAiEnabled(this, isChecked)
            btnToggleKeys.visibility  = if (isChecked) View.VISIBLE else View.GONE
            btnAiKeysInfo.visibility  = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                aiSection.visibility         = View.GONE
                aiKeysInfoSection.visibility = View.GONE
                btnToggleKeys.text           = "manage api keys ↓"
                btnAiKeysInfo.text           = "what are these keys? ↓"
            }
        }

        btnToggleKeys.setOnClickListener {
            val expanding = aiSection.visibility != View.VISIBLE
            aiSection.visibility = if (expanding) View.VISIBLE else View.GONE
            btnToggleKeys.text   = if (expanding) "hide api keys ↑" else "manage api keys ↓"
        }

        btnAiKeysInfo.setOnClickListener {
            val expanding = aiKeysInfoSection.visibility != View.VISIBLE
            aiKeysInfoSection.visibility = if (expanding) View.VISIBLE else View.GONE
            btnAiKeysInfo.text = if (expanding) "what are these keys? ↑" else "what are these keys? ↓"
        }

        btnSave.setOnClickListener {
            val ant    = anthropicInput.text.toString().trim()
            val pn     = plantnetInput.text.toString().trim()
            var saved  = false
            if (ant.isNotEmpty()) {
                if (!ant.startsWith("sk-ant-")) {
                    anthropicInput.error = "Anthropic keys start with sk-ant-"
                    return@setOnClickListener
                }
                ApiKeyManager.saveAnthropicKey(this, ant)
                anthropicInput.text.clear()
                saved = true
            }
            if (pn.isNotEmpty()) {
                ApiKeyManager.savePlantNetKey(this, pn)
                plantnetInput.text.clear()
                saved = true
            }
            if (saved) {
                updateAnthropicStatus(anthropicStatus, btnClearAnt)
                updatePlantNetStatus(plantnetStatus, btnClearPN)
            }
        }

        btnClearAnt.setOnClickListener {
            ApiKeyManager.clearAnthropicKey(this)
            updateAnthropicStatus(anthropicStatus, btnClearAnt)
        }
        btnClearPN.setOnClickListener {
            ApiKeyManager.clearPlantNetKey(this)
            updatePlantNetStatus(plantnetStatus, btnClearPN)
        }
        inputNameStyle.setText(ApiKeyManager.getNameStyle(this) ?: "")
        inputNameStyle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {
                ApiKeyManager.setNameStyle(this@SettingsActivity, s?.toString()?.trim())
            }
        })

        mapOf(
            CareTask.CareType.WATER     to R.id.switchLogWater,
            CareTask.CareType.FERTILIZE to R.id.switchLogFertilize,
            CareTask.CareType.REPOT     to R.id.switchLogRepot,
            CareTask.CareType.MIST      to R.id.switchLogMist,
            CareTask.CareType.ROTATE    to R.id.switchLogRotate,
            CareTask.CareType.CLEAN     to R.id.switchLogClean
        ).forEach { (type, switchId) ->
            val sw = findViewById<Switch>(switchId)
            sw.isChecked = ApiKeyManager.isLogEnabled(this, type)
            sw.setOnCheckedChangeListener { _, isChecked ->
                ApiKeyManager.setLogEnabled(this, type, isChecked)
            }
        }

        singleNotifSwitch.isChecked = ApiKeyManager.isSingleNotification(this)
        singleNotifSwitch.setOnCheckedChangeListener { _, isChecked ->
            ApiKeyManager.setSingleNotification(this, isChecked)
        }

        switchWinter.isChecked = ApiKeyManager.isWinterModeEnabled(this)
        switchWinter.setOnCheckedChangeListener { _, isChecked ->
            ApiKeyManager.setWinterModeEnabled(this, isChecked)
        }

        btnWinterInfo.setOnClickListener {
            val expanding = winterInfoText.visibility != View.VISIBLE
            winterInfoText.visibility = if (expanding) View.VISIBLE else View.GONE
            btnWinterInfo.text = if (expanding) "when should I use this? ↑" else "when should I use this? ↓"
        }

        findViewById<TextView>(R.id.btnManageRooms).setOnClickListener {
            thread {
                val rooms = PlantDatabase.getDatabase(this).plantDao().getAllPlants()
                    .mapNotNull { it.location?.trim() }
                    .filter { it.isNotBlank() }
                    .distinct().sorted()
                runOnUiThread {
                    if (rooms.isEmpty()) {
                        Toast.makeText(this, "No rooms defined yet", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    val view = layoutInflater.inflate(R.layout.dialog_room_action, null)
                    view.findViewById<TextView>(R.id.dialogTitle).text = "manage rooms"
                    view.findViewById<TextView>(R.id.dialogMessage).text = "Tap a room to delete it from all plants."
                    val btnPos = view.findViewById<TextView>(R.id.dialogBtnPositive)
                    val btnNeg = view.findViewById<TextView>(R.id.dialogBtnNegative)
                    btnNeg.text = "close"
                    btnPos.visibility = View.GONE
                    val container = view.findViewById<TextView>(R.id.dialogTitle).parent as ViewGroup
                    val dialog = AlertDialog.Builder(this).setView(view).show()
                    btnNeg.setOnClickListener { dialog.dismiss() }
                    rooms.forEach { room ->
                        val roomBtn = TextView(this).apply {
                            text = "🗑  $room"
                            textSize = 14f
                            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.status_overdue_dot))
                            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                            gravity = Gravity.CENTER
                            setPadding(0, 36, 0, 36)
                            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_outline_terracotta)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = 24 }
                        }
                        roomBtn.setOnClickListener {
                            dialog.dismiss()
                            showStyledDialog(
                                title        = "delete room",
                                message      = "Remove \"$room\" from all plants? The plants themselves won't be deleted.",
                                positiveText = "delete room"
                            ) {
                                thread {
                                    val db = PlantDatabase.getDatabase(this)
                                    val plants = db.plantDao().getAllPlants()
                                        .filter { it.location?.trim() == room }
                                    plants.forEach { db.plantDao().updatePlant(it.copy(location = null)) }
                                    runOnUiThread {
                                        Toast.makeText(this,
                                            "\"$room\" removed from ${plants.size} plant(s)",
                                            Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        container.addView(roomBtn, container.indexOfChild(btnNeg))
                    }
                }
            }
        }

        findViewById<TextView>(R.id.btnDeleteAllData).setOnClickListener {
            // Two confirmation dialogs for irreversible action
            showStyledDialog(
                title        = "re-run setup",
                message      = "This will restart the welcome flow. Your plants and data won't be affected.",
                positiveText = "let's go"
            ) {
                ApiKeyManager.setOnboardingComplete(this, false)
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
            }
        }

        findViewById<TextView>(R.id.btnDeleteAllData).setOnClickListener {
            showStyledDialog(
                title        = "delete all data",
                message      = "This will permanently delete all your plants, care logs and settings. This cannot be undone.",
                positiveText = "delete everything"
            ) {
                showStyledDialog(
                    title        = "are you sure?",
                    message      = "There is no way to recover your data after this.",
                    positiveText = "yes, delete everything"
                ) {
                    thread {
                        val db = PlantDatabase.getDatabase(this)
                        db.plantDao().getAllPlants().forEach { db.plantDao().deletePlant(it) }
                        ApiKeyManager.setOnboardingComplete(this, false)
                        runOnUiThread {
                            Toast.makeText(this, "All data deleted", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, OnboardingActivity::class.java))
                            finishAffinity()
                        }
                    }
                }
            }
        }

        findViewById<TextView>(R.id.btnExportData).setOnClickListener {
            thread {
                try {
                    val db   = PlantDatabase.getDatabase(this)
                    val json = BackupHelper.exportToJson(db)
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(java.util.Date())
                    val file = java.io.File(cacheDir, "spire_backup_$date.json")
                    file.writeText(json)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this, "${packageName}.fileprovider", file
                    )
                    runOnUiThread {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Spire backup $date")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, "Save backup"))
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this,
                            "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        findViewById<TextView>(R.id.btnImportData).setOnClickListener {
            importFileLauncher.launch("*/*")
        }

        btnBack.setOnClickListener { finish() }
    }

    @SuppressLint("SetTextI18n")
    private fun updateAnthropicStatus(statusText: TextView, btnClear: TextView) {
        if (ApiKeyManager.hasAnthropicKey(this)) {
            statusText.text = "Anthropic key saved ✓"
            statusText.setTextColor(getColor(R.color.status_ok_dot))
            btnClear.visibility = View.VISIBLE
        } else {
            statusText.text = "No key saved"
            statusText.setTextColor(getColor(R.color.text_hint))
            btnClear.visibility = View.GONE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updatePlantNetStatus(statusText: TextView, btnClear: TextView) {
        if (ApiKeyManager.hasPlantNetKey(this)) {
            statusText.text = "PlantNet key saved ✓"
            statusText.setTextColor(getColor(R.color.status_ok_dot))
            btnClear.visibility = View.VISIBLE
        } else {
            statusText.text = "No key saved"
            statusText.setTextColor(getColor(R.color.text_hint))
            btnClear.visibility = View.GONE
        }
    }
}