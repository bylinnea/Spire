package no.bylinnea.spire.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import androidx.core.graphics.toColorInt
import no.bylinnea.spire.util.ApiKeyManager
import no.bylinnea.spire.util.CareTask
import no.bylinnea.spire.util.HapticHelper
import no.bylinnea.spire.util.PlantShareHelper
import no.bylinnea.spire.R
import no.bylinnea.spire.util.SortOption
import no.bylinnea.spire.service.WateringScheduler
import no.bylinnea.spire.service.WidgetUpdater
import no.bylinnea.spire.data.Plant
import no.bylinnea.spire.data.PlantDatabase
import no.bylinnea.spire.data.PlantLog
import no.bylinnea.spire.util.sorted
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Main plant list screen. Handles sorting, swipe actions, FAB navigation,
 * and watering. Refreshes the list on every resume so changes from other
 * screens (graveyard, detail, settings) are always reflected.
 */
class MainActivity : BaseActivity() {

    private lateinit var adapter: PlantAdapter
    private lateinit var emptyState: View
    private lateinit var db: PlantDatabase
    private val allPlants = mutableListOf<Plant>()
    private var currentSort = SortOption.NAME
    private var isFirstResume = true

    private fun applySort(sort: SortOption) {
        currentSort = sort
        adapter.setPlants(allPlants.sorted(sort), groupByRoom = sort == SortOption.BY_ROOM)
        updateEmptyState()
        val pills = mapOf(
            SortOption.NAME         to findViewById<TextView>(R.id.pillName),
            SortOption.NEXT_DUE     to findViewById<TextView>(R.id.pillNextDue),
            SortOption.MOST_OVERDUE to findViewById<TextView>(R.id.pillOverdue),
            SortOption.BY_ROOM      to findViewById<TextView>(R.id.pillByRoom)
        )
        pills.forEach { (option, pill) ->
            if (option == sort) {
                pill.background = ContextCompat.getDrawable(this, R.drawable.pill_selected)
                pill.setTextColor(0xFFFFFFFF.toInt())
            } else {
                pill.background = ContextCompat.getDrawable(this, R.drawable.pill_unselected)
                pill.setTextColor(ContextCompat.getColor(this, R.color.green_muted))
            }
        }
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val raw = result.contents ?: return@registerForActivityResult
        val parsed = PlantShareHelper.fromJson(raw)
        if (parsed == null) {
            Toast.makeText(this, "Not a valid Plant Mom QR code", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val (plant, logs) = parsed
        AlertDialog.Builder(this)
            .setTitle("Import plant?")
            .setMessage(PlantShareHelper.summarise(plant, logs.size))
            .setPositiveButton("Add to my plants") { _, _ ->
                thread {
                    val id    = db.plantDao().insertPlant(plant)
                    val saved = plant.copy(id = id)
                    logs.forEach { log ->
                        db.plantLogDao().insertLog(log.copy(plantId = id))
                    }
                    db.plantLogDao().insertLog(
                        // Record that this plant was imported via QR rather than added manually
                        PlantLog(plantId = id, note = "📱 Imported into Plant Mom")
                    )
                    runOnUiThread {
                        allPlants.add(saved)
                        applySort(currentSort)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val addPlantLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val newPlant = result.data?.getParcelableExtra<Plant>("plant")
                ?: return@registerForActivityResult
            thread {
                val id    = db.plantDao().insertPlant(newPlant)
                val saved = newPlant.copy(id = id)
                runOnUiThread {
                    allPlants.add(saved)
                    applySort(currentSort)
                }
            }
        }
    }

    private var editingPosition = -1
    private val editPlantLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && editingPosition >= 0) {
            val updated = result.data?.getParcelableExtra<Plant>("plant")
                ?: return@registerForActivityResult
            thread {
                db.plantDao().updatePlant(updated)
                runOnUiThread {
                    val i = allPlants.indexOfFirst { it.id == updated.id }
                    if (i >= 0) allPlants[i] = updated
                    applySort(currentSort)
                    editingPosition = -1
                }
            }
        }
    }

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val action = result.data?.getStringExtra("action") ?: return@registerForActivityResult
            val plantId = result.data?.getLongExtra("plant_id", -1) ?: return@registerForActivityResult
            // Detail screen may have swiped through multiple plants, so we reload all edited ids
            val allEditedIds = result.data?.getLongArrayExtra("all_edited_ids")
                ?: longArrayOf(plantId)
            when (action) {
                "deleted" -> {
                    allPlants.removeAll { it.id == plantId }
                    applySort(currentSort)
                }
                "updated" -> {
                    thread {
                        val updatedPlants = db.plantDao().getAllPlants()
                            .filter { it.id in allEditedIds }
                        runOnUiThread {
                            updatedPlants.forEach { updated ->
                                val i = allPlants.indexOfFirst { it.id == updated.id }
                                if (i >= 0) allPlants[i] = updated else allPlants.add(updated)
                            }
                            applySort(currentSort)
                        }
                    }
                }
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) WateringScheduler.schedule(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emptyState = findViewById(R.id.emptyState)
        db         = PlantDatabase.getDatabase(this)

        adapter = PlantAdapter(
            onWaterClicked     = { plant, position -> onWaterPlant(plant, position) },
            onPlantTapped      = { plant, position -> openDetail(plant, position) },
            onWaterRoomClicked = { roomName -> waterAllInRoom(roomName) }
        )

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        thread {
            val saved = db.plantDao().getAllPlants()
            runOnUiThread {
                allPlants.addAll(saved)
                applySort(currentSort)

                val openPlantId = intent.getLongExtra("open_plant_id", -1L)
                if (openPlantId != -1L) {
                    val allPlantsList = adapter.getAllPlants()
                    val position = allPlantsList.indexOfFirst { it.id == openPlantId }
                    if (position >= 0) openDetail(allPlantsList[position], position)
                }
            }
        }

        val pillTrigger  = findViewById<TextView>(R.id.pillTrigger)
        val pillsExpanded = findViewById<View>(R.id.pillsExpanded)

        pillTrigger.setOnClickListener {
            val expanding = pillsExpanded.visibility != View.VISIBLE
            pillsExpanded.visibility = if (expanding) View.VISIBLE else View.GONE
            pillTrigger.text = if (expanding) "···" else currentSort.label
        }

        findViewById<TextView>(R.id.pillName).setOnClickListener {
            applySort(SortOption.NAME)
            pillsExpanded.visibility = View.GONE
            pillTrigger.text = SortOption.NAME.label
        }
        findViewById<TextView>(R.id.pillNextDue).setOnClickListener {
            applySort(SortOption.NEXT_DUE)
            pillsExpanded.visibility = View.GONE
            pillTrigger.text = SortOption.NEXT_DUE.label
        }
        findViewById<TextView>(R.id.pillOverdue).setOnClickListener {
            applySort(SortOption.MOST_OVERDUE)
            pillsExpanded.visibility = View.GONE
            pillTrigger.text = SortOption.MOST_OVERDUE.label
        }
        findViewById<TextView>(R.id.pillByRoom).setOnClickListener {
            applySort(SortOption.BY_ROOM)
            pillsExpanded.visibility = View.GONE
            pillTrigger.text = SortOption.BY_ROOM.label
        }
        setupSwipeActions(recyclerView)

        val fabScanRow   = findViewById<View>(R.id.fabScanRow)
        val fabAddRow    = findViewById<View>(R.id.fabAddRow)
        var fabExpanded = false


        fun collapseFab() {
            fabScanRow.visibility   = View.GONE
            fabAddRow.visibility    = View.GONE
            fabExpanded = false
            findViewById<FloatingActionButton>(R.id.fab)
                .setImageResource(android.R.drawable.ic_input_add)
        }

        findViewById<FloatingActionButton>(R.id.fab)
            .setOnClickListener {
                fabExpanded = !fabExpanded
                fabScanRow.visibility   = if (fabExpanded) View.VISIBLE else View.GONE
                fabAddRow.visibility    = if (fabExpanded) View.VISIBLE else View.GONE
            }

        findViewById<FloatingActionButton>(R.id.fabAdd)
            .setOnClickListener {
                collapseFab()
                addPlantLauncher.launch(Intent(this, AddPlantActivity::class.java))
            }


        findViewById<FloatingActionButton>(R.id.fabScan)
            .setOnClickListener {
                collapseFab()
                scanLauncher.launch(ScanOptions().apply {
                    setPrompt("Scan a Plant Mom QR code")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                })
            }
        findViewById<TextView>(R.id.btnGraveyard).setOnClickListener {
            startActivity(Intent(this, GraveyardActivity::class.java))
        }

        findViewById<TextView>(R.id.btnPlantSitter).setOnClickListener {
            showPlantSitterDialog(db)
        }

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) { isFirstResume = false; return }
        thread {
            val plants = db.plantDao().getAllPlants()
            runOnUiThread {
                allPlants.clear()
                allPlants.addAll(plants)
                applySort(currentSort)
            }
        }
    }

    private fun openDetail(plant: Plant, position: Int) {
        // Pass all plant IDs so PlantDetailActivity can swipe between plants
        val allIds = LongArray(adapter.getAllPlants().size) { adapter.getAllPlants()[it].id }
        val intent = Intent(this, PlantDetailActivity::class.java).apply {
            putExtra("plant", plant)
            putExtra("plant_ids", allIds)
            putExtra("current_index", position)
        }
        detailLauncher.launch(intent)
        overridePendingTransition(R.anim.slide_in_right, 0)
    }

    private fun onWaterPlant(plant: Plant, position: Int) {
        // Tapping the water button today marks it done; tapping again undoes it
        val isToday = plant.lastWateredDate?.let {
            TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it) == 0L
        } ?: false
        val updated = if (isToday) plant.copy(lastWateredDate = null)
        else plant.copy(lastWateredDate = System.currentTimeMillis())
        thread {
            db.plantDao().updatePlant(updated)
            WidgetUpdater.update(this)
            runOnUiThread {
                val i = allPlants.indexOfFirst { it.id == updated.id }
                if (i >= 0) allPlants[i] = updated
                applySort(currentSort)
            }
        }
    }

    private fun setupSwipeActions(recyclerView: RecyclerView) {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (adapter.isHeader(position)) return
                val plant = adapter.getPlantAt(position)
                if (direction == ItemTouchHelper.LEFT) {
                    val diedAt = System.currentTimeMillis()
                    thread { db.plantDao().updatePlant(plant.copy(diedDate = diedAt)) }

                    allPlants.removeAll { it.id == plant.id }
                    applySort(currentSort)
                    HapticHelper.warning(this@MainActivity)

                    val rootView = findViewById<View>(android.R.id.content)
                    Snackbar.make(rootView, "Moved ${plant.name} to the graveyard", Snackbar.LENGTH_LONG)
                        .setAction("Undo") {
                            thread { db.plantDao().updatePlant(plant.copy(diedDate = null)) }
                            allPlants.add(plant)
                            applySort(currentSort)
                        }
                        .setBackgroundTint("#2A4A2A".toColorInt())
                        .setTextColor("#F0EDE6".toColorInt())
                        .setActionTextColor("#A8C090".toColorInt())
                        .show()
                } else {
                    adapter.notifyItemChanged(position)
                    editingPosition = position
                    editPlantLauncher.launch(
                        Intent(this@MainActivity, EditPlantActivity::class.java)
                            .putExtra("plant", plant)
                    )
                }
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                     dX: Float, dY: Float, state: Int, active: Boolean) {
                val item  = vh.itemView
                val paint = Paint()
                val alpha = (abs(dX) / (item.width * 0.35f)).coerceIn(0f, 1f)
                if (dX < 0) {
                    paint.color = "#C0705A".toColorInt(); paint.alpha = (alpha * 255).toInt()
                    c.drawRoundRect(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat(), 32f, 32f, paint)
                    val tp = Paint().apply { color = Color.WHITE; this.alpha = (alpha * 255).toInt(); textSize = 36f; typeface = Typeface.create("sans-serif", Typeface.NORMAL); isAntiAlias = true }
                    c.drawText("graveyard", item.right - tp.measureText("graveyard") - 32f, item.top + item.height / 2f + tp.textSize / 3f, tp)
                } else if (dX > 0) {
                    paint.color = "#3D6B3D".toColorInt(); paint.alpha = (alpha * 255).toInt()
                    c.drawRoundRect(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat(),32f, 32f, paint)
                    val tp = Paint().apply { color = Color.WHITE; this.alpha = (alpha * 255).toInt(); textSize = 36f; typeface = Typeface.create("sans-serif", Typeface.NORMAL); isAntiAlias = true }
                    c.drawText("edit", item.left + 32f, item.top + item.height / 2f + tp.textSize / 3f, tp)
                }
                super.onChildDraw(c, rv, vh, dX, dY, state, active)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
                WateringScheduler.schedule(this)
            else notificationPermissionLauncher.launch(perm)
        } else WateringScheduler.schedule(this)
    }

    private fun updateEmptyState() {
        emptyState.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun waterAllInRoom(roomName: String) {
        val plantsInRoom = allPlants.filter {
            (it.location?.trim() ?: "No room") == roomName
        }
        showStyledDialog(
            title = "water all",
            message = "Mark ${plantsInRoom.size} plant${if (plantsInRoom.size == 1) "" else "s"} in $roomName as watered today.",
            positiveText = "water all"
        ) {
            thread {
                val now = System.currentTimeMillis()
                plantsInRoom.forEach { plant ->
                    val updated = plant.copy(lastWateredDate = now)
                    db.plantDao().updatePlant(updated)
                    val idx = allPlants.indexOfFirst { it.id == plant.id }
                    if (idx >= 0) allPlants[idx] = updated
                    if (ApiKeyManager.isLogEnabled(this, CareTask.CareType.WATER)) {
                        db.plantLogDao().insertLog(PlantLog(plantId = plant.id, note = "💧 Watered"))
                    }
                }
                runOnUiThread {
                    WidgetUpdater.update(this)
                    applySort(currentSort)
                }
            }
        }
    }
}