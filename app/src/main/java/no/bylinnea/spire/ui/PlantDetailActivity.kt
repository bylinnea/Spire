package no.bylinnea.spire.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Gravity
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import no.bylinnea.spire.util.ApiKeyManager
import no.bylinnea.spire.util.CareTask
import no.bylinnea.spire.service.PlantDoctorService
import no.bylinnea.spire.R
import no.bylinnea.spire.util.ShareQrHelper
import no.bylinnea.spire.service.WidgetUpdater
import no.bylinnea.spire.util.activeWinterAwareTasks
import no.bylinnea.spire.data.Plant
import no.bylinnea.spire.data.PlantDatabase
import no.bylinnea.spire.data.PlantLog
import no.bylinnea.spire.data.PlantPhoto
import no.bylinnea.spire.util.markTaskDone
import no.bylinnea.spire.util.undoTask
import java.io.File
import java.util.Calendar

/**
 * Detail screen for a single plant. Shows care schedule, health log, growth photos,
 * light meter, and AI plant doctor.
 *
 * Supports swipe-right to go back, swipe-up/down to navigate between plants,
 * and tracks all edited plant IDs so MainActivity can reload only what changed.
 */
class PlantDetailActivity : BaseActivity() {

    private lateinit var plant: Plant
    private lateinit var db: PlantDatabase
    private lateinit var gestureDetector: GestureDetector
    private lateinit var plantIds: LongArray
    private lateinit var logAdapter: PlantLogAdapter
    private lateinit var growthPhotoAdapter: GrowthPhotoAdapter
    private var currentIndex     = 0
    private var doctorPhotoUri: Uri? = null
    private var doctorCameraUri: Uri? = null
    private var growthCameraUri: Uri? = null
    private var currentLogFilter = LogFilter.ALL
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var lightSensorListener: SensorEventListener? = null
    // Tracks all plants edited during this session (via care buttons or swipe navigation)
    // so MainActivity can reload only the affected plants when we return
    private val editedPlantIds = mutableSetOf<Long>()
    // Stores the last-done date before marking a task done, so undo restores the correct value.
    // Also passed to CareDetailActivity via the companion object for the same purpose.
    private val previousDatesBeforeMarkDone = mutableMapOf<CareTask.CareType, Long?>()

    enum class LogFilter(val label: String, val prefix: String?) {
        ALL       ("all",           null),
        NOTES     ("📝 notes",      null),
        DIAGNOSIS ("🩺 doctor",     "🩺"),
        WATER     ("💧 watered",    "💧"),
        FERTILIZED("🌱 fertilized", "🌱"),
        REPOTTED  ("🪴 repotted",   "🪴"),
        MISTED    ("💦 misted",     "💦"),
        ROTATED   ("🔄 rotated",    "🔄"),
        CLEANED   ("🍃 cleaned",    "🍃")
    }

    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val d = result.data ?: return@registerForActivityResult
            plant = d.getParcelableExtra("plant") ?: plant
            editedPlantIds.add(plant.id)
            thread { db.plantDao().updatePlant(plant) }
            bindPlant()
        }
    }

    private val careDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val plantId = result.data?.getLongExtra("plant_id", -1) ?: return@registerForActivityResult
            thread {
                val updated = db.plantDao().getAllPlants().firstOrNull { it.id == plantId }
                updated?.let {
                    runOnUiThread {
                        plant = it
                        editedPlantIds.add(plant.id)
                        bindPlant()
                        loadLogs()
                    }
                }
            }
        }
    }

    private val doctorCameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) doctorCameraUri?.let { setDoctorPhoto(it) }
    }

    private val doctorGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDoctorPhoto(it)
        }
    }

    private val growthCameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) growthCameraUri?.let { saveGrowthPhoto(it.toString()) }
    }

    private val growthGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            saveGrowthPhoto(it.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_detail)

        db           = PlantDatabase.getDatabase(this)
        plantIds     = intent.getLongArrayExtra("plant_ids") ?: longArrayOf()
        currentIndex = intent.getIntExtra("current_index", 0)
        plant = intent.getParcelableExtra("plant")!!

        bindPlant()
        setupGestures()
        setupLightMeter()
        setupGrowthPhotos()

        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { sendResultAndFinish() }
            })

        findViewById<TextView>(R.id.btnBack).setOnClickListener { sendResultAndFinish() }

        findViewById<TextView>(R.id.btnEdit).setOnClickListener {
            editLauncher.launch(
                Intent(this, EditPlantActivity::class.java).putExtra("plant", plant)
            )
        }

        findViewById<TextView>(R.id.btnMarkDead).setOnClickListener {
            showStyledDialog(
                title        = "mark as dead?",
                message      = "${plant.name} will be moved to the graveyard. You can restore them later.",
                positiveText = "mark as dead",
                negativeText = "cancel"
            ) {
                thread {
                    db.plantDao().updatePlant(plant.copy(diedDate = System.currentTimeMillis()))
                    runOnUiThread {
                        setResult(RESULT_OK, Intent().apply {
                            putExtra("action", "deleted"); putExtra("plant_id", plant.id)
                        })
                        finish()
                        overridePendingTransition(0, R.anim.slide_out_right)
                    }
                }
            }
        }

        findViewById<TextView>(R.id.btnDelete).setOnClickListener {
            showStyledDialog(
                title        = "delete forever?",
                message      = "This will permanently remove ${plant.name} and all their care history. This cannot be undone.",
                positiveText = "delete forever",
                negativeText = "cancel"
            ) {
                thread { db.plantDao().deletePlant(plant) }
                setResult(RESULT_OK, Intent().apply {
                    putExtra("action", "deleted"); putExtra("plant_id", plant.id)
                })
                finish()
                overridePendingTransition(0, R.anim.slide_out_right)
            }
        }

        findViewById<TextView>(R.id.btnShare).setOnClickListener {
            ShareQrHelper.showShareDialog(this, plant, db)
        }

        if (ApiKeyManager.isAiEnabled(this)) {
            findViewById<View>(R.id.doctorCard).visibility = View.VISIBLE
            findViewById<TextView>(R.id.btnDoctorCamera).setOnClickListener {
                val file = File.createTempFile("doctor_", ".jpg", cacheDir)
                doctorCameraUri = FileProvider.getUriForFile(
                    this, "${packageName}.fileprovider", file)
                doctorCameraLauncher.launch(doctorCameraUri)
            }
            findViewById<TextView>(R.id.btnDoctorGallery).setOnClickListener {
                doctorGalleryLauncher.launch("image/*")
            }
            findViewById<TextView>(R.id.btnDiagnose).setOnClickListener { runDiagnosis() }
        }

        setupLogFilters()

        logAdapter = PlantLogAdapter(mutableListOf())
        val logRv  = findViewById<RecyclerView>(R.id.logRecyclerView)
        logRv.layoutManager = LinearLayoutManager(this)
        logRv.adapter = logAdapter
        logRv.isNestedScrollingEnabled = false
        setupLogSwipe(logRv)

        loadLogs()
        findViewById<TextView>(R.id.btnAddLog).setOnClickListener {
            val input = findViewById<EditText>(R.id.logInput)
            val text  = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            val entry = PlantLog(plantId = plant.id, note = text)
            thread {
                db.plantLogDao().insertLog(entry)
                runOnUiThread {
                    input.text.clear()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(input.windowToken, 0)
                    loadLogs()
                }
            }
        }
    }

    private fun sendResultAndFinish() {
        if (editedPlantIds.isNotEmpty()) {
            setResult(RESULT_OK, Intent().apply {
                putExtra("action", "updated")
                putExtra("plant_id", plant.id)
                putExtra("all_edited_ids", editedPlantIds.toLongArray())
            })
        }
        finish()
        overridePendingTransition(0, R.anim.slide_out_right)
    }

    @SuppressLint("SetTextI18n")
    private fun bindPlant() {
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

        val photo = findViewById<ImageView>(R.id.detailPhoto)
        val emoji = findViewById<TextView>(R.id.detailEmoji)
        if (plant.photoUri != null) {
            photo.visibility = View.VISIBLE; emoji.visibility = View.GONE
            Glide.with(this).load(plant.photoUri?.toUri()).circleCrop().into(photo)
        } else {
            photo.visibility = View.GONE; emoji.visibility = View.VISIBLE
        }

        findViewById<TextView>(R.id.detailName).text = plant.name
        findViewById<TextView>(R.id.detailSchedule).text =
            "waters every ${plant.wateringIntervalDays} day(s)"

        if (plantIds.size > 1) {
            findViewById<TextView>(R.id.plantIndicator).apply {
                text = "${currentIndex + 1} / ${plantIds.size}"; visibility = View.VISIBLE
            }
        } else {
            findViewById<TextView>(R.id.plantIndicator).visibility = View.GONE
        }

        val container = findViewById<LinearLayout>(R.id.careTasksContainer)
        container.removeAllViews()

        for (task in plant.activeWinterAwareTasks(this)) {
            val row = layoutInflater.inflate(R.layout.item_care_task, container, false)
            row.findViewById<TextView>(R.id.careTaskEmoji).text  = task.type.emoji
            row.findViewById<TextView>(R.id.careTaskLabel).text  = task.type.label
            row.findViewById<TextView>(R.id.careTaskStatus).text = task.statusText

            val nextView  = row.findViewById<TextView>(R.id.careTaskNext)
            val daysUntil = task.daysUntilNext
            if (task.intervalDays != null && daysUntil != null) {
                nextView.visibility = View.VISIBLE
                nextView.text = task.nextDueText
                nextView.setTextColor(when {
                    daysUntil <= 0 -> ContextCompat.getColor(this, R.color.status_overdue_dot)
                    daysUntil <= 3 -> ContextCompat.getColor(this, R.color.amber)
                    else           -> ContextCompat.getColor(this, R.color.status_ok_dot)
                })
            } else if (task.daysSinceDone == null) {
                nextView.visibility = View.VISIBLE
                nextView.text = task.nextDueText
                nextView.setTextColor(ContextCompat.getColor(this, R.color.status_overdue_dot))
            } else {
                nextView.visibility = View.GONE
            }

            val btn = row.findViewById<TextView>(R.id.careTaskBtn)
            if (task.isDoneToday) {
                btn.text = "undo"
                btn.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
                btn.background = null
            } else {
                btn.text = "mark done"
                btn.setTextColor(ContextCompat.getColor(this, R.color.green_forest))
                btn.background = ContextCompat.getDrawable(this, R.drawable.btn_outline_green)
            }

            btn.setOnClickListener {
                val wasDoneToday = task.isDoneToday
                val previousDate = when (task.type) {
                    CareTask.CareType.WATER     -> plant.lastWateredDate
                    CareTask.CareType.FERTILIZE -> plant.lastFertilizedDate
                    CareTask.CareType.REPOT     -> plant.lastRepottedDate
                    CareTask.CareType.MIST      -> plant.lastMistedDate
                    CareTask.CareType.ROTATE    -> plant.lastRotatedDate
                    CareTask.CareType.CLEAN     -> plant.lastCleanedDate
                }
                if (!wasDoneToday) {
                    previousDatesBeforeMarkDone[task.type] = previousDate
                    CareDetailActivity.pendingPreviousDates[task.type] = previousDate
                }
                plant = if (wasDoneToday) plant.undoTask(task.type, previousDatesBeforeMarkDone[task.type])
                else plant.markTaskDone(task.type)
                val capturedPlantId = plant.id
                thread {
                    db.plantDao().updatePlant(plant)
                    if (!wasDoneToday && ApiKeyManager.isLogEnabled(this, task.type)) {
                        val autoLogNote = when (task.type) {
                            CareTask.CareType.WATER     -> "💧 Watered"
                            CareTask.CareType.FERTILIZE -> "🌱 Fertilized"
                            CareTask.CareType.REPOT     -> "🪴 Repotted"
                            CareTask.CareType.MIST      -> "💦 Misted"
                            CareTask.CareType.ROTATE    -> "🔄 Rotated"
                            CareTask.CareType.CLEAN     -> "🍃 Cleaned leaves"
                        }
                        db.plantLogDao().insertLog(
                            PlantLog(
                                plantId = capturedPlantId,
                                note = autoLogNote
                            )
                        )
                    }
                    runOnUiThread {
                        WidgetUpdater.update(this)
                        editedPlantIds.add(plant.id)
                        bindPlant()
                        loadLogs()
                        setupLightMeter()
                        setupGrowthPhotos()
                    }
                }
            }

            if (task.isOverdue && !task.isDoneToday) {
                row.setBackgroundColor(ContextCompat.getColor(this, R.color.status_overdue_bg))
            } else {
                row.setBackgroundColor(0x00000000)
            }
            row.setOnClickListener { openCareDetail(task.type) }
            container.addView(row)
        }

        bindOptionalRow(R.id.rowSpecies,      R.id.detailSpecies,      plant.species)
        bindOptionalRow(R.id.rowLocation,     R.id.detailLocation,     plant.location)
        bindOptionalRow(
            R.id.rowDateAcquired, R.id.detailDateAcquired,
            plant.dateAcquired?.let { dateFormat.format(Date(it)) })
        bindOptionalRow(R.id.rowNotes, R.id.detailNotes, plant.notes)

        findViewById<TextView>(R.id.badgePetSafe).visibility =
            if (plant.isPetSafe == true) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.badgeToxic).visibility =
            if (plant.isPetSafe == false) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.petSafeDisclaimer).visibility =
            if (plant.isPetSafe != null) View.VISIBLE else View.GONE
    }

    private fun openCareDetail(careType: CareTask.CareType) {
        // CareDetailActivity is not Parcelable-aware for Plant, so all required fields
        // are passed individually as intent extras
        careDetailLauncher.launch(Intent(this, CareDetailActivity::class.java).apply {
            putExtra("care_type",         careType.name)
            putExtra("plant_id",          plant.id)
            putExtra("plant_name",        plant.name)
            putExtra("watering_interval", plant.wateringIntervalDays)
            putExtra("fertilizer_type",   plant.fertilizerType)
            putExtra("plant_ids",         plantIds)
            putExtra("current_index",     currentIndex)
            putExtra("active_care_types", plant.activeWinterAwareTasks(this@PlantDetailActivity)
                .map { it.type.name }.toTypedArray())
            plant.lastWateredDate?.let        { putExtra("last_watered",              it) }
            plant.fertilizerIntervalDays?.let { putExtra("fertilizer_interval",       it) }
            plant.lastFertilizedDate?.let     { putExtra("last_fertilized",           it) }
            plant.repottingIntervalDays?.let  { putExtra("repotting_interval",        it) }
            plant.lastRepottedDate?.let       { putExtra("last_repotted",             it) }
            plant.mistingIntervalDays?.let    { putExtra("misting_interval",          it) }
            plant.lastMistedDate?.let         { putExtra("last_misted",               it) }
            plant.rotatingIntervalDays?.let   { putExtra("rotating_interval",         it) }
            plant.lastRotatedDate?.let        { putExtra("last_rotated",              it) }
            plant.cleaningIntervalDays?.let   { putExtra("cleaning_interval",         it) }
            plant.lastCleanedDate?.let        { putExtra("last_cleaned",              it) }
            plant.lastRepotSkippedDate?.let   { putExtra("last_repot_skipped",        it) }
            plant.wateringTip?.let            { putExtra("watering_tip",              it) }
            plant.fertilizingTip?.let         { putExtra("fertilizing_tip",           it) }
            plant.repottingTip?.let           { putExtra("repotting_tip",             it) }
            plant.mistingTip?.let             { putExtra("misting_tip",               it) }
            plant.rotatingTip?.let            { putExtra("rotating_tip",              it) }
            plant.cleaningTip?.let            { putExtra("cleaning_tip",              it) }
            plant.winterWateringIntervalDays?.let   { putExtra("winter_watering_interval",   it) }
            plant.winterFertilizerIntervalDays?.let { putExtra("winter_fertilizer_interval", it) }
            plant.winterMistingIntervalDays?.let    { putExtra("winter_misting_interval",    it) }
            plant.winterScheduleDisabled?.let       { putExtra("winter_disabled",            it) }
        })
        overridePendingTransition(R.anim.slide_in_right, 0)
    }

    private fun bindOptionalRow(rowId: Int, textId: Int, value: String?) {
        val row = findViewById<View>(rowId)
        if (value.isNullOrBlank()) row.visibility = View.GONE
        else { row.visibility = View.VISIBLE; findViewById<TextView>(textId).text = value }
    }

    private fun setDoctorPhoto(uri: Uri) {
        doctorPhotoUri = uri
        Glide.with(this).load(uri).circleCrop().into(findViewById(R.id.doctorPhotoPreview))
        findViewById<TextView>(R.id.btnDiagnose).visibility = View.VISIBLE
        findViewById<TextView>(R.id.doctorStatus).visibility = View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun runDiagnosis() {
        val uri         = doctorPhotoUri ?: return
        val statusView  = findViewById<TextView>(R.id.doctorStatus)
        val diagnoseBtn = findViewById<TextView>(R.id.btnDiagnose)

        diagnoseBtn.text = "analysing..."; diagnoseBtn.isEnabled = false
        statusView.visibility = View.VISIBLE
        statusView.text = "🔍 Looking at your plant..."
        statusView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        val capturedPlant = plant
        thread {
            val result = PlantDoctorService.diagnose(this, uri, capturedPlant)
            runOnUiThread {
                diagnoseBtn.text = "diagnose plant"; diagnoseBtn.isEnabled = true
                if (result.error != null) {
                    statusView.text = result.error
                    statusView.setTextColor(ContextCompat.getColor(this, R.color.status_overdue_dot))
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
                statusView.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                val capturedPlantId = capturedPlant.id
                thread {
                    db.plantLogDao().insertLog(
                        PlantLog(
                            plantId = capturedPlantId,
                            note = "🩺 $diagnosis"
                        )
                    )
                    runOnUiThread { loadLogs() }
                }
            }
        }
    }

    private fun resetDoctorState() {
        doctorPhotoUri  = null
        doctorCameraUri = null
        findViewById<ImageView?>(R.id.doctorPhotoPreview)?.setImageResource(R.drawable.ic_camera)
        findViewById<TextView>(R.id.btnDiagnose).visibility = View.GONE
        findViewById<TextView>(R.id.doctorStatus).apply { visibility = View.GONE; text = "" }
    }

    private fun setupLogFilters() {
        val trigger       = findViewById<TextView>(R.id.filterTrigger)
        val pillsExpanded = findViewById<View>(R.id.filterPillsExpanded)

        val filtersToShow = mutableListOf(LogFilter.ALL, LogFilter.NOTES)
        if (ApiKeyManager.isAiEnabled(this)) filtersToShow.add(LogFilter.DIAGNOSIS)

        val typeToFilter = mapOf(
            CareTask.CareType.WATER     to LogFilter.WATER,
            CareTask.CareType.FERTILIZE to LogFilter.FERTILIZED,
            CareTask.CareType.REPOT     to LogFilter.REPOTTED,
            CareTask.CareType.MIST      to LogFilter.MISTED,
            CareTask.CareType.ROTATE    to LogFilter.ROTATED,
            CareTask.CareType.CLEAN     to LogFilter.CLEANED
        )
        ApiKeyManager.enabledLogTypes(this).forEach { type ->
            typeToFilter[type]?.let { filtersToShow.add(it) }
        }

        val allPillIds = mapOf(
            LogFilter.ALL        to R.id.filterAll,
            LogFilter.NOTES      to R.id.filterNotes,
            LogFilter.DIAGNOSIS  to R.id.filterDiagnosis,
            LogFilter.WATER      to R.id.filterWater,
            LogFilter.FERTILIZED to R.id.filterFertilized,
            LogFilter.REPOTTED   to R.id.filterRepotted,
            LogFilter.MISTED     to R.id.filterMisted,
            LogFilter.ROTATED    to R.id.filterRotated,
            LogFilter.CLEANED    to R.id.filterCleaned
        )

        allPillIds.forEach { (filter, id) ->
            val pill = findViewById<TextView?>(id) ?: return@forEach
            pill.visibility = if (filter in filtersToShow) View.VISIBLE else View.GONE
            pill.setOnClickListener {
                currentLogFilter = filter
                updateFilterPills(allPillIds)
                pillsExpanded.visibility = View.GONE
                trigger.text = filter.label
                loadLogs()
            }
        }

        trigger.setOnClickListener {
            val expanding = pillsExpanded.visibility != View.VISIBLE
            pillsExpanded.visibility = if (expanding) View.VISIBLE else View.GONE
            trigger.text = if (expanding) "···" else currentLogFilter.label
        }

        updateFilterPills(allPillIds)
    }

    private fun updateFilterPills(pills: Map<LogFilter, Int>) {
        pills.forEach { (filter, id) ->
            val pill = findViewById<TextView?>(id) ?: return@forEach
            if (filter == currentLogFilter) {
                pill.background = ContextCompat.getDrawable(this, R.drawable.pill_selected)
                pill.setTextColor(0xFFFFFFFF.toInt())
            } else {
                pill.background = ContextCompat.getDrawable(this, R.drawable.pill_unselected)
                pill.setTextColor(ContextCompat.getColor(this, R.color.green_muted))
            }
        }
    }

    private fun loadLogs() {
        val plantId = plant.id
        val filter  = currentLogFilter
        thread {
            val all = db.plantLogDao().getLogsForPlant(plantId)
            val filtered = when (filter) {
                LogFilter.ALL   -> all
                // Notes filter excludes all auto-logged care events, showing only manual entries
                LogFilter.NOTES -> all.filter { entry ->
                    !entry.note.startsWith("🩺") && !entry.note.startsWith("💧") &&
                            !entry.note.startsWith("🌱") && !entry.note.startsWith("🪴") &&
                            !entry.note.startsWith("🌫") && !entry.note.startsWith("🔄")
                }
                else -> filter.prefix?.let { prefix -> all.filter { it.note.startsWith(prefix) } } ?: all
            }
            runOnUiThread {
                logAdapter.setEntries(filtered)
                findViewById<TextView>(R.id.logEmpty).visibility =
                    if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupLogSwipe(recyclerView: RecyclerView) {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                t: RecyclerView.ViewHolder) = false

            @SuppressLint("SetTextI18n")
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val entry    = logAdapter.getEntryAt(position)
                if (direction == ItemTouchHelper.LEFT) {
                    logAdapter.removeAt(position)
                    thread { db.plantLogDao().deleteLog(entry) }
                    updateLogEmptyState()
                } else {
                    logAdapter.notifyItemChanged(position)
                    val ctx        = this@PlantDetailActivity
                    val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                    var editedTimestamp = entry.timestamp

                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(48, 24, 48, 8)
                    }
                    val editInput = EditText(ctx).apply {
                        setText(entry.note); selectAll(); hint = "Note"
                        setHintTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
                    }
                    container.addView(editInput)

                    val dateLabelHeader = TextView(ctx).apply {
                        text = "date"; textSize = 10f
                        setTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
                        setPadding(0, 24, 0, 4); isAllCaps = true; letterSpacing = 0.12f
                    }
                    container.addView(dateLabelHeader)

                    val dateBtn = TextView(ctx).apply {
                        text = dateFormat.format(Date(editedTimestamp)); textSize = 15f
                        setTextColor(ContextCompat.getColor(ctx, R.color.green_forest))
                        setPadding(0, 8, 0, 8)
                    }
                    container.addView(dateBtn)

                    dateBtn.setOnClickListener {
                        val cal = Calendar.getInstance().apply { timeInMillis = editedTimestamp }
                        DatePickerDialog(ctx, { _, y, m, d ->
                            cal.set(y, m, d); editedTimestamp = cal.timeInMillis
                            dateBtn.text = dateFormat.format(Date(editedTimestamp))
                        }, cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)).show()
                    }

                    AlertDialog.Builder(ctx).setTitle("Edit entry").setView(container)
                        .setPositiveButton("Save") { _, _ ->
                            val newText = editInput.text.toString().trim()
                            if (newText.isNotEmpty()) {
                                val updated = entry.copy(note = newText, timestamp = editedTimestamp)
                                logAdapter.updateEntry(position, updated)
                                thread { db.plantLogDao().updateLog(updated) }
                            }
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                     dX: Float, dY: Float, state: Int, active: Boolean) {
                val item  = vh.itemView
                val paint = Paint()
                val alpha = (abs(dX) / (item.width * 0.35f)).coerceIn(0f, 1f)
                if (dX < 0) {
                    paint.color = "#C0705A".toColorInt(); paint.alpha = (alpha * 255).toInt()
                    c.drawRect(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat(), paint)
                    val tp = Paint().apply { color = Color.WHITE; this.alpha = (alpha * 255).toInt(); textSize = 32f; typeface = Typeface.create("sans-serif", Typeface.NORMAL); isAntiAlias = true }
                    c.drawText("delete", item.right - tp.measureText("delete") - 28f, item.top + item.height / 2f + tp.textSize / 3f, tp)
                } else if (dX > 0) {
                    paint.color = "#3D6B3D".toColorInt(); paint.alpha = (alpha * 255).toInt()
                    c.drawRect(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat(), paint)
                    val tp = Paint().apply { color = Color.WHITE; this.alpha = (alpha * 255).toInt(); textSize = 32f; typeface = Typeface.create("sans-serif", Typeface.NORMAL); isAntiAlias = true }
                    c.drawText("edit", item.left + 28f, item.top + item.height / 2f + tp.textSize / 3f, tp)
                }
                super.onChildDraw(c, rv, vh, dX, dY, state, active)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    private fun updateLogEmptyState() {
        findViewById<TextView>(R.id.logEmpty).visibility =
            if (logAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun setupGestures() {
        val scrollView      = findViewById<ScrollView>(R.id.detailScrollView)
        val indicatorTop    = findViewById<View>(R.id.edgeIndicatorTop)
        val indicatorBottom = findViewById<View>(R.id.edgeIndicatorBottom)

        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onScroll(e1: MotionEvent?, e2: MotionEvent,
                                      distanceX: Float, distanceY: Float): Boolean {
                    val dy      = e2.y - (e1?.y ?: 0f)
                    val isAtTop    = !scrollView.canScrollVertically(-1)
                    val isAtBottom = !scrollView.canScrollVertically(1)
                    if (isAtTop && dy > 0)         indicatorTop.alpha    = (dy / 300f).coerceIn(0f, 1f)
                    else if (isAtBottom && dy < 0) indicatorBottom.alpha = (abs(dy) / 300f).coerceIn(0f, 1f)
                    else { indicatorTop.alpha = 0f; indicatorBottom.alpha = 0f }
                    return false
                }

                override fun onFling(e1: MotionEvent?, e2: MotionEvent,
                                     velocityX: Float, velocityY: Float): Boolean {
                    indicatorTop.alpha = 0f; indicatorBottom.alpha = 0f
                    val dx = e2.x - (e1?.x ?: 0f)
                    val dy = e2.y - (e1?.y ?: 0f)
                    val isAtTop    = !scrollView.canScrollVertically(-1)
                    val isAtBottom = !scrollView.canScrollVertically(1)
                    return when {
                        dx > 120f && abs(dx) > abs(dy) && abs(velocityX) > 200f ->
                        { sendResultAndFinish(); true }
                        dy < -120f && abs(dy) > abs(dx) && abs(velocityY) > 200f && isAtBottom ->
                        { navigateToPlant(currentIndex + 1, true); true }
                        dy > 120f && abs(dy) > abs(dx) && abs(velocityY) > 200f && isAtTop ->
                        { navigateToPlant(currentIndex - 1, false); true }
                        else -> false
                    }
                }
            })
    }

    // Passes touch events to the gesture detector, but lets the log RecyclerView
    // handle its own swipe actions without interference
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            findViewById<View>(R.id.edgeIndicatorTop).animate().alpha(0f).setDuration(200).start()
            findViewById<View>(R.id.edgeIndicatorBottom).animate().alpha(0f).setDuration(200).start()
        }
        val logRv = findViewById<RecyclerView?>(R.id.logRecyclerView)
        if (logRv != null && event.action == MotionEvent.ACTION_DOWN) {
            val loc = IntArray(2)
            logRv.getLocationOnScreen(loc)
            val inRv = event.rawX >= loc[0] && event.rawX <= loc[0] + logRv.width &&
                    event.rawY >= loc[1] && event.rawY <= loc[1] + logRv.height
            if (inRv) return super.dispatchTouchEvent(event)
        }
        gestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    private fun navigateToPlant(index: Int, forward: Boolean) {
        if (plantIds.isEmpty()) return
        val wrapped = ((index % plantIds.size) + plantIds.size) % plantIds.size
        thread {
            val next = db.plantDao().getAllPlants().firstOrNull { it.id == plantIds[wrapped] }
            next?.let {
                runOnUiThread {
                    plant = it; currentIndex = wrapped; bindPlant(); loadLogs()
                    resetDoctorState()
                    val scrollView = findViewById<ScrollView>(R.id.detailScrollView)
                    scrollView.scrollTo(0, 0)
                    val anim = if (forward) R.anim.slide_in_bottom else R.anim.slide_in_top
                    scrollView.startAnimation(
                        AnimationUtils.loadAnimation(this, anim))
                }
            }
        }
    }

    private fun setupLightMeter() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor   = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            findViewById<View>(R.id.lightMeterCard).visibility = View.GONE
            return
        }

        val pref = plant.lightPreference
        if (!pref.isNullOrBlank()) {
            findViewById<View>(R.id.rowLightPreference).visibility = View.VISIBLE
            findViewById<TextView>(R.id.detailLightPreference).text = pref
        } else {
            findViewById<View>(R.id.rowLightPreference).visibility = View.GONE
        }

        val temp = plant.temperaturePreference
        if (!temp.isNullOrBlank()) {
            findViewById<View>(R.id.rowTemperaturePreference).visibility = View.VISIBLE
            findViewById<TextView>(R.id.detailTemperaturePreference).text = temp
        } else {
            findViewById<View>(R.id.rowTemperaturePreference).visibility = View.GONE
        }

        val btnMeasure = findViewById<TextView>(R.id.btnMeasureLight)
        val btnStop    = findViewById<TextView>(R.id.btnStopLight)
        val container  = findViewById<View>(R.id.lightReadingContainer)
        val luxView    = findViewById<TextView>(R.id.lightLuxValue)
        val labelView  = findViewById<TextView>(R.id.lightLabel)
        val fcView     = findViewById<TextView>(R.id.lightFcValue)
        val matchView  = findViewById<TextView>(R.id.lightMatchIndicator)

        btnMeasure.setOnClickListener {
            container.visibility  = View.VISIBLE
            btnMeasure.visibility = View.GONE
            lightSensorListener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                @SuppressLint("SetTextI18n")
                override fun onSensorChanged(event: SensorEvent) {
                    val lux = event.values[0]
                    luxView.text   = "${lux.toInt()} lux"
                    fcView.text    = "(${(lux / 10.764f).toInt()} fc)"
                    labelView.text = luxToLabel(lux)
                    val p = plant.lightPreference
                    if (!p.isNullOrBlank()) {
                        matchView.visibility = View.VISIBLE
                        if (lightLabelMatches(lux, p)) {
                            matchView.text = "✅ Matches this plant's preference"
                            matchView.setTextColor(ContextCompat.getColor(
                                this@PlantDetailActivity, R.color.status_ok_dot))
                        } else {
                            matchView.text = "⚠️ This plant prefers $p"
                            matchView.setTextColor(ContextCompat.getColor(
                                this@PlantDetailActivity, R.color.amber))
                        }
                    } else matchView.visibility = View.GONE
                }
            }
            sensorManager?.registerListener(
                lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        btnStop.setOnClickListener { stopLightMeter() }
    }

    private fun stopLightMeter() {
        lightSensorListener?.let { sensorManager?.unregisterListener(it) }
        lightSensorListener = null
        findViewById<View>(R.id.lightReadingContainer).visibility = View.GONE
        findViewById<TextView>(R.id.btnMeasureLight).visibility   = View.VISIBLE
    }

    private fun luxToLabel(lux: Float) = when {
        lux < 500   -> "Low light"
        lux < 2500  -> "Medium light"
        lux < 10000 -> "Bright indirect light"
        else        -> "Direct sunlight"
    }

    private fun lightLabelMatches(lux: Float, preference: String): Boolean {
        val p = preference.lowercase()
        return when {
            lux < 500   -> p.contains("low")
            lux < 2500  -> p.contains("medium") || p.contains("moderate")
            lux < 10000 -> p.contains("bright") || p.contains("indirect")
            else        -> p.contains("direct") || p.contains("full sun")
        }
    }

    override fun onPause() {
        super.onPause()
        stopLightMeter()
    }

    private fun setupGrowthPhotos() {
        growthPhotoAdapter = GrowthPhotoAdapter(
            mutableListOf(),
            onPhotoTapped = { photo -> showGrowthPhotoFullscreen(photo) },
            onPhotoLongPressed = { photo ->
                showStyledDialog(
                    title        = "delete photo",
                    message      = "Remove this growth photo? This cannot be undone.",
                    positiveText = "delete",
                    negativeText = "cancel"
                ) {
                    thread {
                        db.plantPhotoDao().deletePhoto(photo)
                        runOnUiThread {
                            growthPhotoAdapter.removePhoto(photo)
                            updateGrowthPhotosEmpty()
                        }
                    }
                }
            }
        )

        val recycler = findViewById<RecyclerView>(R.id.growthPhotosRecycler)
        recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recycler.adapter = growthPhotoAdapter

        loadGrowthPhotos()

        findViewById<TextView>(R.id.btnAddGrowthPhoto).setOnClickListener {
            showTwoOptionDialog(
                title       = "add growth photo",
                message     = "Choose how to add a photo to track your plant's growth.",
                option1Text = "📷 camera",
                option2Text = "🖼️ gallery",
                onOption1   = {
                    val file = File.createTempFile("growth_", ".jpg", filesDir)
                    growthCameraUri = FileProvider.getUriForFile(
                        this, "${packageName}.fileprovider", file)
                    growthCameraLauncher.launch(growthCameraUri)
                },
                onOption2   = { growthGalleryLauncher.launch("image/*") }
            )
        }
    }

    private fun saveGrowthPhoto(uri: String) {
        thread {
            val photo = PlantPhoto(plantId = plant.id, photoUri = uri)
            val id    = db.plantPhotoDao().insertPhoto(photo)
            val saved = photo.copy(id = id)
            runOnUiThread {
                growthPhotoAdapter.addPhoto(saved)
                findViewById<RecyclerView>(R.id.growthPhotosRecycler).visibility = View.VISIBLE
                findViewById<TextView>(R.id.growthPhotosEmpty).visibility = View.GONE
            }
        }
    }

    private fun loadGrowthPhotos() {
        thread {
            val photos = db.plantPhotoDao().getPhotosForPlant(plant.id)
            runOnUiThread {
                growthPhotoAdapter.setPhotos(photos)
                updateGrowthPhotosEmpty()
            }
        }
    }

    private fun updateGrowthPhotosEmpty() {
        val hasPhotos = growthPhotoAdapter.itemCount > 0
        findViewById<RecyclerView>(R.id.growthPhotosRecycler).visibility =
            if (hasPhotos) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.growthPhotosEmpty).visibility =
            if (hasPhotos) View.GONE else View.VISIBLE
    }

    private fun showGrowthPhotoFullscreen(photo: PlantPhoto) {
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(FrameLayout(this).apply {
            val imageView = ImageView(this@PlantDetailActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(0xFF000000.toInt())
            }
            Glide.with(this@PlantDetailActivity).load(photo.photoUri).into(imageView)
            addView(imageView)
            addView(TextView(this@PlantDetailActivity).apply {
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