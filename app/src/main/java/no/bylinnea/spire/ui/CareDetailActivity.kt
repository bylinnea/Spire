package no.bylinnea.spire.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GestureDetectorCompat
import no.bylinnea.spire.util.ApiKeyManager
import no.bylinnea.spire.util.CareTask
import no.bylinnea.spire.util.HapticHelper
import no.bylinnea.spire.R
import no.bylinnea.spire.data.PlantDatabase
import no.bylinnea.spire.data.PlantLog
import no.bylinnea.spire.util.markTaskDone
import no.bylinnea.spire.util.undoTask
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Shows schedule, history, and tools for a single care type (water, fertilize, repot, etc.).
 * Supports swipe-right to go back, swipe-up/down to cycle through care types,
 * and swipe between plants via plant_ids array passed from PlantDetailActivity.
 */
class CareDetailActivity : AppCompatActivity() {

    private lateinit var db: PlantDatabase
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var plantIds: LongArray
    private lateinit var careType: CareTask.CareType
    private var currentIndex    = 0
    private var currentPlantId  = -1L
    private var currentPlantName = ""
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private var previousDateBeforeMarkDone: Long? = null
    private var lastWatered:      Long? = null
    private var lastFertilized:   Long? = null
    private var lastRepotted:     Long? = null
    private var lastMisted:       Long? = null
    private var lastRotated:      Long? = null
    private var lastCleaned:      Long? = null
    private var lastRepotSkipped: Long? = null

    companion object {
        // Used by PlantDetailActivity to pass the pre-mark-done date when navigating here
        // directly after tapping a care button, so undo works correctly.
        val pendingPreviousDates = mutableMapOf<CareTask.CareType, Long?>()
    }

    private val fallbackTips = mapOf(
        CareTask.CareType.WATER     to "Water until it drains from the bottom, then empty the saucer after 30 minutes to prevent root rot.",
        CareTask.CareType.FERTILIZE to "Apply at half the recommended strength during the growing season to avoid burning the roots.",
        CareTask.CareType.REPOT     to "Best done in spring when the plant is actively growing. Choose a pot only 1–2 inches larger than the current one.",
        CareTask.CareType.MIST      to "Mist in the morning so the leaves have time to dry before evening, reducing the risk of fungal issues.",
        CareTask.CareType.ROTATE    to "Rotate a quarter turn each time to ensure all sides get even light and the plant grows straight.",
        CareTask.CareType.CLEAN     to "Wipe leaves gently with a damp cloth to remove dust. This helps the plant absorb more light and keeps pests away."
    )

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_care_detail)

        db = PlantDatabase.getDatabase(this)

        val careTypeName = intent.getStringExtra("care_type") ?: run { finish(); return }
        val plantName    = intent.getStringExtra("plant_name") ?: ""
        val plantId      = intent.getLongExtra("plant_id", -1)
        careType         = CareTask.CareType.valueOf(careTypeName)
        currentPlantId   = plantId
        currentPlantName = plantName
        plantIds         = intent.getLongArrayExtra("plant_ids") ?: longArrayOf(plantId)
        currentIndex     = intent.getIntExtra("current_index", 0)

        lastWatered      = intent.getLongExtra("last_watered",       0L).takeIf { it > 0 }
        lastFertilized   = intent.getLongExtra("last_fertilized",    0L).takeIf { it > 0 }
        lastRepotted     = intent.getLongExtra("last_repotted",      0L).takeIf { it > 0 }
        lastMisted       = intent.getLongExtra("last_misted",        0L).takeIf { it > 0 }
        lastRotated      = intent.getLongExtra("last_rotated",       0L).takeIf { it > 0 }
        lastCleaned      = intent.getLongExtra("last_cleaned",       0L).takeIf { it > 0 }
        lastRepotSkipped = intent.getLongExtra("last_repot_skipped", 0L).takeIf { it > 0 }

        // If PlantDetailActivity already marked this task done and stored the previous date,
        // use that. Otherwise derive it from the last done date, unless it was done today.
        previousDateBeforeMarkDone = pendingPreviousDates[careType] ?: when (careType) {
            CareTask.CareType.WATER     -> if (isDoneTodayCheck(lastWatered))    null else lastWatered
            CareTask.CareType.FERTILIZE -> if (isDoneTodayCheck(lastFertilized)) null else lastFertilized
            CareTask.CareType.REPOT     -> if (isDoneTodayCheck(lastRepotted))   null else lastRepotted
            CareTask.CareType.MIST      -> if (isDoneTodayCheck(lastMisted))     null else lastMisted
            CareTask.CareType.ROTATE    -> if (isDoneTodayCheck(lastRotated))    null else lastRotated
            CareTask.CareType.CLEAN     -> if (isDoneTodayCheck(lastCleaned))    null else lastCleaned
        }
        pendingPreviousDates.remove(careType)

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish(); overridePendingTransition(0, R.anim.slide_out_right)
        }
        findViewById<TextView>(R.id.careDetailPlantName).text = plantName
        findViewById<TextView>(R.id.careDetailTitle).text =
            "${careType.emoji} ${careType.label.replaceFirstChar { it.uppercase() }}"

        bindSchedule(careType)
        bindTip(careType)

        val logRecycler = findViewById<RecyclerView>(R.id.careDetailLogRecycler)
        val logAdapter  = PlantLogAdapter(mutableListOf())
        logRecycler.layoutManager = LinearLayoutManager(this)
        logRecycler.adapter = logAdapter
        logRecycler.isNestedScrollingEnabled = false

        thread {
            // Filter logs to only those belonging to this care type, identified by emoji prefix
            val logs = db.plantLogDao().getLogsForPlant(plantId)
                .filter { it.note.startsWith(careType.prefix()) }
            runOnUiThread {
                logAdapter.setEntries(logs)
                findViewById<TextView>(R.id.careDetailLogEmpty).visibility =
                    if (logs.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        setupGestures()
        setupFertilizerCalculator()
        setupRepottingCalculator()
        setupMarkDone()

        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish(); overridePendingTransition(0, R.anim.slide_out_right)
                }
            })
    }

    private fun CareTask.CareType.prefix() = when (this) {
        CareTask.CareType.WATER     -> "💧"
        CareTask.CareType.FERTILIZE -> "🌱"
        CareTask.CareType.REPOT     -> "🪴"
        CareTask.CareType.MIST      -> "🌫"
        CareTask.CareType.ROTATE    -> "🔄"
        CareTask.CareType.CLEAN     -> "🍃"
    }

    private fun isDoneTodayCheck(date: Long?): Boolean {
        date ?: return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = date }
        val cal2 = Calendar.getInstance()
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun bindTip(careType: CareTask.CareType) {
        val aiTip = when (careType) {
            CareTask.CareType.WATER     -> intent.getStringExtra("watering_tip")
            CareTask.CareType.FERTILIZE -> intent.getStringExtra("fertilizing_tip")
            CareTask.CareType.REPOT     -> intent.getStringExtra("repotting_tip")
            CareTask.CareType.MIST      -> intent.getStringExtra("misting_tip")
            CareTask.CareType.ROTATE    -> intent.getStringExtra("rotating_tip")
            CareTask.CareType.CLEAN     -> intent.getStringExtra("cleaning_tip")
        }
        val tip = aiTip ?: fallbackTips[careType]
        if (tip != null) {
            findViewById<TextView>(R.id.tipLabel).text = if (aiTip != null) "tip" else "general tip"
            findViewById<TextView>(R.id.careDetailTip).text = tip
            findViewById<View>(R.id.tipCard).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.tipCard).visibility = View.GONE
        }
    }

    private fun setupGestures() {
        gestureDetector = GestureDetectorCompat(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onScroll(e1: MotionEvent?, e2: MotionEvent,
                                      distanceX: Float, distanceY: Float): Boolean {
                    val scrollView = findViewById<ScrollView>(R.id.careDetailScrollView)
                    val isAtTop    = !scrollView.canScrollVertically(-1)
                    val isAtBottom = !scrollView.canScrollVertically(1)
                    val dy = e2.y - (e1?.y ?: 0f)
                    val top    = findViewById<View?>(R.id.edgeIndicatorTop)
                    val bottom = findViewById<View?>(R.id.edgeIndicatorBottom)
                    if (isAtTop && dy > 0)    top?.alpha    = (dy / 300f).coerceIn(0f, 0.8f)
                    else                       top?.animate()?.alpha(0f)?.setDuration(150)?.start()
                    if (isAtBottom && dy < 0) bottom?.alpha = (-dy / 300f).coerceIn(0f, 0.8f)
                    else                       bottom?.animate()?.alpha(0f)?.setDuration(150)?.start()
                    return false
                }

                override fun onFling(e1: MotionEvent?, e2: MotionEvent,
                                     velocityX: Float, velocityY: Float): Boolean {
                    val dx = e2.x - (e1?.x ?: 0f)
                    val dy = e2.y - (e1?.y ?: 0f)
                    val scrollView = findViewById<ScrollView>(R.id.careDetailScrollView)
                    val isAtTop    = !scrollView.canScrollVertically(-1)
                    val isAtBottom = !scrollView.canScrollVertically(1)
                    findViewById<View?>(R.id.edgeIndicatorTop)?.animate()?.alpha(0f)?.setDuration(200)?.start()
                    findViewById<View?>(R.id.edgeIndicatorBottom)?.animate()?.alpha(0f)?.setDuration(200)?.start()
                    return when {
                        dx > 180f && abs(dx) > abs(dy) && abs(velocityX) > 400f ->
                        { finish(); overridePendingTransition(0, R.anim.slide_out_right); true }
                        dy < -200f && abs(dy) > abs(dx) && abs(velocityY) > 600f && isAtBottom ->
                        { navigateToCareType(1, true); true }
                        dy > 200f && abs(dy) > abs(dx) && abs(velocityY) > 600f && isAtTop ->
                        { navigateToCareType(-1, false); true }
                        else -> false
                    }
                }
            })
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    @SuppressLint("SetTextI18n")
    private fun navigateToCareType(direction: Int, forward: Boolean) {
        val activeTasks = intent.getStringArrayExtra("active_care_types") ?: return
        val currentPos  = activeTasks.indexOf(careType.name)
        val nextPos     = ((currentPos + direction + activeTasks.size) % activeTasks.size)
        val nextType    = CareTask.CareType.valueOf(activeTasks[nextPos])
        if (nextType == careType) return

        careType = nextType

        val scrollView = findViewById<ScrollView>(R.id.careDetailScrollView)
        scrollView.scrollTo(0, 0)
        scrollView.startAnimation(
            AnimationUtils.loadAnimation(
            this, if (forward) R.anim.slide_in_bottom else R.anim.slide_in_top))

        scrollView.postDelayed({
            findViewById<TextView>(R.id.careDetailTitle).text =
                "${careType.emoji} ${careType.label.replaceFirstChar { it.uppercase() }}"
            bindSchedule(careType)
            bindTip(careType)
            reloadLogs()
            setupFertilizerCalculator()
            setupRepottingCalculator()
            setupMarkDone()
        }, 120)
    }

    private fun bindSchedule(careType: CareTask.CareType) {
        val intervalDays = when (careType) {
            CareTask.CareType.WATER     -> intent.getIntExtra("watering_interval", 0)
            CareTask.CareType.FERTILIZE -> intent.getIntExtra("fertilizer_interval", 0).takeIf { it > 0 }
            CareTask.CareType.REPOT     -> intent.getIntExtra("repotting_interval", 0).takeIf { it > 0 }
            CareTask.CareType.MIST      -> intent.getIntExtra("misting_interval", 0).takeIf { it > 0 }
            CareTask.CareType.ROTATE    -> intent.getIntExtra("rotating_interval", 0).takeIf { it > 0 }
            CareTask.CareType.CLEAN     -> intent.getIntExtra("cleaning_interval", 0).takeIf { it > 0 }
        }
        val lastDoneMs = when (careType) {
            CareTask.CareType.WATER     -> lastWatered
            CareTask.CareType.FERTILIZE -> lastFertilized
            CareTask.CareType.REPOT     -> listOfNotNull(lastRepotted, lastRepotSkipped).maxOrNull()
            CareTask.CareType.MIST      -> lastMisted
            CareTask.CareType.ROTATE    -> lastRotated
            CareTask.CareType.CLEAN     -> lastCleaned
        }
        bindScheduleFromValues(intervalDays, lastDoneMs)
    }

    @SuppressLint("SetTextI18n")
    private fun bindScheduleFromValues(intervalDays: Any?, lastDoneMs: Long?) {
        val intervalInt  = if (intervalDays is Int) intervalDays else 0
        val intervalView = findViewById<TextView>(R.id.careDetailInterval)
        intervalView.text = if (intervalInt > 0) "$intervalInt days" else "not set"

        val winterActive = ApiKeyManager.isWinterModeEnabled(this) &&
                !intent.getBooleanExtra("winter_disabled", false)
        if (winterActive) {
            val winterInterval = when (careType) {
                CareTask.CareType.WATER     -> intent.getIntExtra("winter_watering_interval",   0).takeIf { it > 0 }
                CareTask.CareType.FERTILIZE -> intent.getIntExtra("winter_fertilizer_interval", 0).takeIf { it > 0 }
                CareTask.CareType.MIST      -> intent.getIntExtra("winter_misting_interval",    0).takeIf { it > 0 }
                else -> null
            }
            if (winterInterval != null) intervalView.text = "$winterInterval days ❄️"
        }

        val lastDoneView = findViewById<TextView>(R.id.careDetailLastDone)
        if (lastDoneMs != null) {
            val lastCal = Calendar.getInstance().apply { timeInMillis = lastDoneMs }
            val nowCal  = Calendar.getInstance()
            val isToday = lastCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                    lastCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
            val isYesterday = run {
                nowCal.add(Calendar.DAY_OF_YEAR, -1)
                val y = lastCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                        lastCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
                nowCal.add(Calendar.DAY_OF_YEAR, 1)
                y
            }
            val lastMidnight = Calendar.getInstance().apply {
                timeInMillis = lastDoneMs
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
            }
            val todayMidnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
            }
            val daysAgo = TimeUnit.MILLISECONDS
                .toDays(todayMidnight.timeInMillis - lastMidnight.timeInMillis)
            lastDoneView.text = when {
                isToday     -> "today"
                isYesterday -> "yesterday"
                else        -> "$daysAgo days ago  ·  ${dateFormat.format(Date(lastDoneMs))}"
            }
            findViewById<View>(R.id.rowLastDone).visibility = View.VISIBLE
        } else {
            lastDoneView.text = "never"
        }

        val effectiveInterval = if (winterActive) {
            when (careType) {
                CareTask.CareType.WATER     -> intent.getIntExtra("winter_watering_interval",   0).takeIf { it > 0 }
                CareTask.CareType.FERTILIZE -> intent.getIntExtra("winter_fertilizer_interval", 0).takeIf { it > 0 }
                CareTask.CareType.MIST      -> intent.getIntExtra("winter_misting_interval",    0).takeIf { it > 0 }
                else -> null
            } ?: intervalInt
        } else intervalInt

        val nextDueView = findViewById<TextView>(R.id.careDetailNextDue)
        if (effectiveInterval > 0 && lastDoneMs != null) {
            val nextMs   = lastDoneMs + effectiveInterval * 24 * 60 * 60 * 1000L
            val daysLeft = TimeUnit.MILLISECONDS
                .toDays(nextMs - System.currentTimeMillis())
            nextDueView.text = when {
                daysLeft < 0   -> "${-daysLeft} day(s) overdue"
                daysLeft == 0L -> "today"
                daysLeft == 1L -> "tomorrow"
                else           -> "in $daysLeft days  ·  ${dateFormat.format(Date(nextMs))}"
            }
            nextDueView.setTextColor(when {
                daysLeft <= 0 -> ContextCompat.getColor(this, R.color.status_overdue_dot)
                daysLeft <= 3 -> ContextCompat.getColor(this, R.color.amber)
                else          -> ContextCompat.getColor(this, R.color.status_ok_dot)
            })
            findViewById<View>(R.id.rowNextDue).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.rowNextDue).visibility = View.GONE
        }

        val fertRow = findViewById<View>(R.id.rowFertilizerType)
        if (careType == CareTask.CareType.FERTILIZE) {
            val fertType = intent.getStringExtra("fertilizer_type")
            if (!fertType.isNullOrBlank()) {
                findViewById<TextView>(R.id.careDetailFertilizerType).text = fertType
                fertRow.visibility = View.VISIBLE
            } else fertRow.visibility = View.GONE
        } else fertRow.visibility = View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun updateMarkDoneBtn(btn: TextView, isDoneToday: Boolean) {
        if (isDoneToday) {
            btn.text = "undo"
            btn.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            btn.background = null
        } else {
            btn.text = "mark done"
            btn.setTextColor(ContextCompat.getColor(this, R.color.green_forest))
            btn.background = ContextCompat.getDrawable(this, R.drawable.btn_outline_green)
        }
    }

    private fun setupMarkDone() {
        val btn = findViewById<TextView>(R.id.btnMarkDone)

        fun isDoneToday(): Boolean {
            val lastDone = when (careType) {
                CareTask.CareType.WATER     -> lastWatered
                CareTask.CareType.FERTILIZE -> lastFertilized
                CareTask.CareType.REPOT     -> lastRepotted
                CareTask.CareType.MIST      -> lastMisted
                CareTask.CareType.ROTATE    -> lastRotated
                CareTask.CareType.CLEAN     -> lastCleaned
            } ?: return false
            return isDoneTodayCheck(lastDone)
        }

        updateMarkDoneBtn(btn, isDoneToday())

        btn.setOnClickListener {
            HapticHelper.tap(this)
            val wasDoneToday = isDoneToday()
            val now = System.currentTimeMillis()

            if (!wasDoneToday) {
                previousDateBeforeMarkDone = when (careType) {
                    CareTask.CareType.WATER     -> lastWatered
                    CareTask.CareType.FERTILIZE -> lastFertilized
                    CareTask.CareType.REPOT     -> lastRepotted
                    CareTask.CareType.MIST      -> lastMisted
                    CareTask.CareType.ROTATE    -> lastRotated
                    CareTask.CareType.CLEAN     -> lastCleaned
                }
                when (careType) {
                    CareTask.CareType.WATER     -> lastWatered    = now
                    CareTask.CareType.FERTILIZE -> lastFertilized = now
                    CareTask.CareType.REPOT     -> lastRepotted   = now
                    CareTask.CareType.MIST      -> lastMisted     = now
                    CareTask.CareType.ROTATE    -> lastRotated    = now
                    CareTask.CareType.CLEAN     -> lastCleaned    = now
                }
            } else {
                when (careType) {
                    CareTask.CareType.WATER     -> lastWatered    = previousDateBeforeMarkDone
                    CareTask.CareType.FERTILIZE -> lastFertilized = previousDateBeforeMarkDone
                    CareTask.CareType.REPOT     -> lastRepotted   = previousDateBeforeMarkDone
                    CareTask.CareType.MIST      -> lastMisted     = previousDateBeforeMarkDone
                    CareTask.CareType.ROTATE    -> lastRotated    = previousDateBeforeMarkDone
                    CareTask.CareType.CLEAN     -> lastCleaned    = previousDateBeforeMarkDone
                }
            }

            thread {
                val plant   = db.plantDao().getPlantById(currentPlantId) ?: return@thread
                val updated = if (wasDoneToday) plant.undoTask(careType, previousDateBeforeMarkDone)
                else plant.markTaskDone(careType)
                db.plantDao().updatePlant(updated)

                if (!wasDoneToday && ApiKeyManager.isLogEnabled(this, careType)) {
                    val note = when (careType) {
                        CareTask.CareType.WATER     -> "💧 Watered"
                        CareTask.CareType.FERTILIZE -> "🌱 Fertilized"
                        CareTask.CareType.REPOT     -> "🪴 Repotted"
                        CareTask.CareType.MIST      -> "💦 Misted"
                        CareTask.CareType.ROTATE    -> "🔄 Rotated"
                        CareTask.CareType.CLEAN     -> "🍃 Cleaned leaves"
                    }
                    db.plantLogDao().insertLog(PlantLog(plantId = currentPlantId, note = note))
                }

                runOnUiThread {
                    bindSchedule(careType)
                    reloadLogs()
                    setupMarkDone()
                    setResult(RESULT_OK, Intent().apply {
                        putExtra("action", "updated")
                        putExtra("plant_id", currentPlantId)
                    })
                }
            }
        }
    }

    private fun reloadLogs() {
        val prefix = careType.prefix()
        thread {
            val logs = db.plantLogDao().getLogsForPlant(currentPlantId)
                .filter { it.note.startsWith(prefix) }
            runOnUiThread {
                (findViewById<RecyclerView>(R.id.careDetailLogRecycler).adapter as PlantLogAdapter)
                    .setEntries(logs)
                findViewById<TextView>(R.id.careDetailLogEmpty).visibility =
                    if (logs.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun setupFertilizerCalculator() {
        val trigger = findViewById<TextView>(R.id.fertCalcTrigger)
        val inputs  = findViewById<View>(R.id.fertCalcInputs)
        val result  = findViewById<TextView>(R.id.fertCalcResult)

        if (careType != CareTask.CareType.FERTILIZE) {
            trigger.visibility = View.GONE
            inputs.visibility  = View.GONE
            return
        }
        trigger.visibility = View.VISIBLE

        trigger.setOnClickListener {
            val expanding = inputs.visibility != View.VISIBLE
            inputs.visibility = if (expanding) View.VISIBLE else View.GONE
            trigger.text = if (expanding) "fertilizer calculator ↑" else "fertilizer calculator ↓"
        }

        findViewById<TextView>(R.id.btnCalculate).setOnClickListener {
            val bottleDose  = findViewById<EditText>(R.id.inputBottleDose).text.toString().toDoubleOrNull()
            val waterAmount = findViewById<EditText>(R.id.inputWaterAmount).text.toString().toDoubleOrNull()
            // Scales the bottle's recommended dose (per litre) to the actual water amount entered

            if (bottleDose == null || waterAmount == null) {
                result.visibility = View.VISIBLE
                result.text = "Please enter the bottle dose and water amount."
                result.setTextColor(ContextCompat.getColor(this, R.color.status_overdue_dot))
                return@setOnClickListener
            }

            val npk     = findViewById<EditText>(R.id.inputNPK).text.toString().trim()
            val npkNote = if (npk.isNotBlank()) " ($npk)" else ""
            result.visibility = View.VISIBLE
            result.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            result.text = buildString {
                appendLine("For ${waterAmount}L of water$npkNote:")
                appendLine()
                appendLine("Full strength:   ${String.format("%.1f", bottleDose * waterAmount)} ml")
                appendLine("Half strength:   ${String.format("%.1f", bottleDose * waterAmount / 2.0)} ml")
                appendLine()
                append("Mix into your watering can and pour onto the plant as normal. Half strength is safer for most houseplants.")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupRepottingCalculator() {
        val trigger   = findViewById<TextView>(R.id.repotCalcTrigger)
        val inputs    = findViewById<View>(R.id.repotCalcInputs)
        val result    = findViewById<TextView>(R.id.repotCalcResult)
        val unitLabel = findViewById<TextView>(R.id.repotCalcUnitLabel)
        val skipBtn   = findViewById<TextView>(R.id.btnSkipRepot)

        if (careType != CareTask.CareType.REPOT) {
            trigger.visibility = View.GONE
            inputs.visibility  = View.GONE
            skipBtn.visibility = View.GONE
            return
        }

        unitLabel.text     = "cm"
        trigger.visibility = View.VISIBLE
        skipBtn.visibility = View.VISIBLE

        trigger.setOnClickListener {
            val expanding = inputs.visibility != View.VISIBLE
            inputs.visibility = if (expanding) View.VISIBLE else View.GONE
            trigger.text = if (expanding) "pot size calculator ↑" else "pot size calculator ↓"
        }

        findViewById<EditText>(R.id.inputCurrentPotSize)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                @SuppressLint("DefaultLocale")
                override fun afterTextChanged(s: Editable?) {
                    val current = s.toString().toDoubleOrNull()
                    if (current != null && current > 0) {
                        result.visibility = View.VISIBLE
                        result.text = buildString {
                            appendLine("Suggested next pot: ${String.format("%.0f", current + 5)} cm")
                            appendLine()
                            append("Only go up one size at a time - too large a pot can cause overwatering and root rot.")
                        }
                    } else result.visibility = View.GONE
                }
            })

        skipBtn.setOnClickListener {
            val view = layoutInflater.inflate(R.layout.dialog_share_options, null)
            view.findViewById<TextView>(R.id.shareTitle).text = "skip repotting"
            view.findViewById<TextView>(R.id.btnInfoOnly).apply {
                text = "skip - remind me again in ${intent.getIntExtra("repotting_interval", 0)} days"
                setTextColor(ContextCompat.getColor(this@CareDetailActivity, R.color.status_overdue_dot))
                background = ContextCompat.getDrawable(this@CareDetailActivity, R.drawable.btn_outline_terracotta)
            }
            view.findViewById<TextView>(R.id.btnWithLogs).apply {
                text = "cancel"
                setTextColor(ContextCompat.getColor(this@CareDetailActivity, R.color.green_forest))
                background = ContextCompat.getDrawable(this@CareDetailActivity, R.drawable.btn_outline_green)
            }

            val dialog = AlertDialog.Builder(this).setView(view).show()

            view.findViewById<TextView>(R.id.btnInfoOnly).setOnClickListener {
                dialog.dismiss()
                thread {
                    val now   = System.currentTimeMillis()
                    val plant = db.plantDao().getPlantById(currentPlantId) ?: return@thread
                    db.plantDao().updatePlant(plant.copy(lastRepotSkippedDate = now))
                    db.plantLogDao().insertLog(
                        PlantLog(
                            plantId = currentPlantId,
                            note = "🪴 Repotting skipped"
                        )
                    )
                    runOnUiThread { bindSchedule(careType); reloadLogs() }
                }
            }
            view.findViewById<TextView>(R.id.btnWithLogs).setOnClickListener { dialog.dismiss() }
        }
    }
}