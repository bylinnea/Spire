package no.bylinnea.spire.ui

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import no.bylinnea.spire.R
import no.bylinnea.spire.data.Plant
import no.bylinnea.spire.data.PlantDatabase
import no.bylinnea.spire.util.HapticHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Shows all plants that have been marked as dead.
 * Swipe right to restore a plant, swipe left to permanently delete it.
 * Reloads on every resume so it stays in sync with changes made elsewhere.
 */
class GraveyardActivity : BaseActivity() {

    private lateinit var db: PlantDatabase
    private lateinit var adapter: GraveyardAdapter
    private val deadPlants = mutableListOf<Plant>()
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graveyard)

        db = PlantDatabase.getDatabase(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        adapter = GraveyardAdapter(deadPlants, dateFormat)
        val recycler = findViewById<RecyclerView>(R.id.graveyardRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        setupSwipe(recycler)
    }

    override fun onResume() {
        super.onResume()
        loadPlants()
    }

    private fun loadPlants() {
        thread {
            val plants = db.plantDao().getDeadPlants()
            runOnUiThread {
                deadPlants.clear()
                deadPlants.addAll(plants)
                adapter.notifyDataSetChanged()
                updateEmptyState()
            }
        }
    }

    private fun updateEmptyState() {
        findViewById<View>(R.id.graveyardEmpty).visibility =
            if (deadPlants.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupSwipe(recycler: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val plant = deadPlants[position]

                if (direction == ItemTouchHelper.RIGHT) {
                    thread { db.plantDao().updatePlant(plant.copy(diedDate = null)) }

                    deadPlants.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    updateEmptyState()
                    HapticHelper.success(this@GraveyardActivity)

                    Snackbar.make(recycler, "${plant.name} restored 🌱", Snackbar.LENGTH_LONG)
                        .setAction("Undo") {
                            thread { db.plantDao().updatePlant(plant.copy(diedDate = plant.diedDate)) }
                            deadPlants.add(position.coerceAtMost(deadPlants.size), plant)
                            adapter.notifyItemInserted(position)
                            updateEmptyState()
                        }
                        .setBackgroundTint("#2A4A2A".toColorInt())
                        .setTextColor("#F0EDE6".toColorInt())
                        .setActionTextColor("#A8C090".toColorInt())
                        .show()
                } else {
                    // Reset the swipe animation before showing the dialog,
                    // so the item snaps back if the user cancels
                    adapter.notifyItemChanged(position)
                    showStyledDialog(
                        title        = "delete forever?",
                        message      = "This will permanently remove ${plant.name} and all their care history. This cannot be undone.",
                        positiveText = "delete forever",
                        negativeText = "cancel"
                    ) {
                        deadPlants.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        updateEmptyState()
                        HapticHelper.warning(this@GraveyardActivity)

                        val rootView = findViewById<View>(android.R.id.content)
                        Snackbar.make(rootView, "${plant.name} permanently deleted", Snackbar.LENGTH_LONG)
                            .addCallback(object : Snackbar.Callback() {
                                override fun onDismissed(snackbar: Snackbar, event: Int) {
                                    thread { db.plantDao().deletePlant(plant) }
                                }
                            })
                            .setBackgroundTint("#C0705A".toColorInt())
                            .setTextColor("#F0EDE6".toColorInt())
                            .show()
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, state: Int, active: Boolean
            ) {
                val item  = vh.itemView
                val paint = Paint()
                val alpha = (abs(dX) / (item.width * 0.35f)).coerceIn(0f, 1f)

                if (dX > 0) {
                    paint.color = "#3D6B3D".toColorInt()
                    paint.alpha = (alpha * 255).toInt()
                    c.drawRoundRect(item.left.toFloat(), item.top.toFloat(),
                        item.left + dX, item.bottom.toFloat(), 32f, 32f, paint)
                    val tp = Paint().apply {
                        color = Color.WHITE; this.alpha = (alpha * 255).toInt()
                        textSize = 32f; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        isAntiAlias = true
                    }
                    c.drawText("restore", item.left + 28f,
                        item.top + item.height / 2f + tp.textSize / 3f, tp)
                } else if (dX < 0) {
                    paint.color = "#C0705A".toColorInt()
                    paint.alpha = (alpha * 255).toInt()
                    c.drawRoundRect(item.right + dX, item.top.toFloat(),
                        item.right.toFloat(), item.bottom.toFloat(), 32f, 32f, paint)
                    val tp = Paint().apply {
                        color = Color.WHITE; this.alpha = (alpha * 255).toInt()
                        textSize = 32f; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        isAntiAlias = true
                    }
                    c.drawText("delete", item.right - tp.measureText("delete") - 28f,
                        item.top + item.height / 2f + tp.textSize / 3f, tp)
                }
                super.onChildDraw(c, rv, vh, dX, dY, state, active)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recycler)
    }

    inner class GraveyardAdapter(
        private val plants: List<Plant>,
        private val dateFormat: SimpleDateFormat
    ) : RecyclerView.Adapter<GraveyardAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val photo: ImageView = view.findViewById(R.id.graveyardPhoto)
            val emoji: TextView  = view.findViewById(R.id.graveyardEmoji)
            val name: TextView   = view.findViewById(R.id.graveyardName)
            val species: TextView = view.findViewById(R.id.graveyardSpecies)
            val dateAcquired: TextView = view.findViewById(R.id.graveyardDateAcquired)
            val diedLabel: TextView = view.findViewById(R.id.graveyardDiedDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_graveyard, parent, false))

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val plant = plants[position]

            if (plant.photoUri != null) {
                holder.photo.visibility = View.VISIBLE
                holder.emoji.visibility = View.GONE
                Glide.with(holder.photo.context)
                    .load(plant.photoUri)
                    .circleCrop()
                    .into(holder.photo)
            } else {
                holder.photo.visibility = View.GONE
                holder.emoji.visibility = View.VISIBLE
            }

            holder.name.text = plant.name

            if (!plant.species.isNullOrBlank()) {
                holder.species.visibility = View.VISIBLE
                holder.species.text = plant.species
            } else {
                holder.species.visibility = View.GONE
            }

            if (plant.dateAcquired != null) {
                holder.dateAcquired.visibility = View.VISIBLE
                holder.dateAcquired.text = "acquired ${dateFormat.format(Date(plant.dateAcquired))}"
            } else {
                holder.dateAcquired.visibility = View.GONE
            }

            holder.diedLabel.text = plant.diedDate
                ?.let { "✝  ${dateFormat.format(Date(it))}" }
                ?: "✝  date unknown"
        }

        override fun getItemCount() = plants.size

    }
}
