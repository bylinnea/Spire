package no.bylinnea.spire.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import androidx.core.net.toUri
import no.bylinnea.spire.util.ApiKeyManager
import no.bylinnea.spire.util.CareTask
import no.bylinnea.spire.util.HapticHelper
import no.bylinnea.spire.R
import no.bylinnea.spire.util.activeWinterAwareTasks
import no.bylinnea.spire.data.Plant
import no.bylinnea.spire.data.RoomItem
import no.bylinnea.spire.util.winterAwareCareTasks

/**
 * Adapter for the main plant list. Supports two view types: room headers and plant rows.
 * When groupByRoom is true, plants are grouped alphabetically by location with a header per room.
 */
class PlantAdapter(
    private val onWaterClicked: (Plant, Int) -> Unit,
    private val onPlantTapped: (Plant, Int) -> Unit,
    private val onWaterRoomClicked: ((String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<RoomItem>()
    private var lastAnimatedPosition = -1

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_PLANT  = 1
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val roomName:   TextView = itemView.findViewById(R.id.headerRoomName)
        val plantCount: TextView = itemView.findViewById(R.id.headerPlantCount)
        val waterAll:   TextView = itemView.findViewById(R.id.headerWaterAll)
    }

    class PlantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card:            CardView  = itemView.findViewById(R.id.plantCard)
        val photo:           ImageView = itemView.findViewById(R.id.plantPhoto)
        val emojiBackground: TextView  = itemView.findViewById(R.id.emojiBackground)
        val statusIcon:      TextView  = itemView.findViewById(R.id.statusIcon)
        val nameText:        TextView  = itemView.findViewById(R.id.plantName)
        val scheduleText:    TextView  = itemView.findViewById(R.id.plantSchedule)
        val lastWateredText: TextView  = itemView.findViewById(R.id.lastWatered)
        val statusDot:       View      = itemView.findViewById(R.id.statusDot)
        val waterButton:     TextView  = itemView.findViewById(R.id.buttonWater)
        val dueTasksRow:     LinearLayout = itemView.findViewById(R.id.dueTasksRow)
        val dueTasksText:    TextView  = itemView.findViewById(R.id.dueTasksText)
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is RoomItem.Header   -> VIEW_TYPE_HEADER
        is RoomItem.PlantRow -> VIEW_TYPE_PLANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_room_header, parent, false)
            )
            else -> PlantViewHolder(
                inflater.inflate(R.layout.item_plant, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is RoomItem.Header   -> bindHeader(holder as HeaderViewHolder, item)
            is RoomItem.PlantRow -> bindPlant(holder as PlantViewHolder, item.plant, position)
        }
    }

    override fun getItemCount() = items.size

    @SuppressLint("SetTextI18n")
    private fun bindHeader(holder: HeaderViewHolder, header: RoomItem.Header) {
        holder.roomName.text   = header.roomName
        holder.plantCount.text = "${header.plantCount} plant${if (header.plantCount == 1) "" else "s"}"
        if (onWaterRoomClicked != null) {
            holder.waterAll.visibility = View.VISIBLE
            holder.waterAll.setOnClickListener {
                onWaterRoomClicked.invoke(header.roomName)
            }
        } else {
            holder.waterAll.visibility = View.GONE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindPlant(holder: PlantViewHolder, plant: Plant, position: Int) {
        val ctx = holder.itemView.context

        if (plant.photoUri != null) {
            holder.photo.visibility          = View.VISIBLE
            holder.statusIcon.visibility     = View.INVISIBLE
            holder.emojiBackground.visibility = View.INVISIBLE
            Glide.with(ctx)
                .load(plant.photoUri.toUri())
                .transform(CircleCrop())
                // Falls back to emoji if the URI is no longer accessible (e.g. after reinstall)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?, target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        holder.photo.visibility = View.GONE
                        holder.emojiBackground.visibility = View.VISIBLE
                        holder.statusIcon.visibility = View.VISIBLE  // ← add this
                        return true
                    }
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any, target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        holder.photo.visibility = View.VISIBLE
                        holder.emojiBackground.visibility = View.GONE
                        return false
                    }
                })
                .into(holder.photo)
        } else {
            holder.photo.visibility          = View.GONE
            holder.statusIcon.visibility     = View.VISIBLE
            holder.emojiBackground.visibility = View.VISIBLE
        }

        holder.nameText.text     = plant.name
        val effectiveInterval = if (ApiKeyManager.isWinterModeEnabled(ctx) &&
            plant.winterScheduleDisabled != true &&
            plant.winterWateringIntervalDays != null) {
            plant.winterWateringIntervalDays
        } else {
            plant.wateringIntervalDays
        }
        holder.scheduleText.text = "every $effectiveInterval day(s)"

        val waterTask = plant.winterAwareCareTasks(ctx).first()
        val isWateredToday = waterTask.isDoneToday
        val isOverdue      = waterTask.isOverdue

        holder.lastWateredText.text = waterTask.statusText
        holder.waterButton.text     = if (isWateredToday) "undo" else "water now"

        if (isOverdue) {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.status_overdue_bg))
            holder.statusDot.background.setTint(ContextCompat.getColor(ctx, R.color.status_overdue_dot))
            if (plant.photoUri == null) holder.statusIcon.text = "🥀"
        } else {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_card))
            holder.statusDot.background.setTint(ContextCompat.getColor(ctx, R.color.status_ok_dot))
            if (plant.photoUri == null) holder.statusIcon.text = "🌿"
        }

        holder.waterButton.setOnClickListener {
            holder.waterButton.startAnimation(AnimationUtils.loadAnimation(ctx, R.anim.bounce))
            HapticHelper.success(ctx)
            onWaterClicked(plant, holder.adapterPosition)
        }

        val otherDue = plant.activeWinterAwareTasks(ctx)
            .filter { it.type != CareTask.CareType.WATER && it.isDueToday }
        if (otherDue.isNotEmpty()) {
            holder.dueTasksRow.visibility = View.VISIBLE
            holder.dueTasksText.text = otherDue.joinToString("  ") { "${it.type.emoji} ${it.type.label}" }
        } else {
            holder.dueTasksRow.visibility = View.GONE
        }

        holder.card.setOnClickListener { onPlantTapped(plant, holder.adapterPosition) }

        if (position > lastAnimatedPosition) {
            holder.itemView.startAnimation(AnimationUtils.loadAnimation(ctx, R.anim.slide_in_up))
            lastAnimatedPosition = position
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setPlants(newPlants: List<Plant>, groupByRoom: Boolean = false) {
        items.clear()
        if (groupByRoom) {
            val grouped = newPlants.groupBy { it.location?.trim() ?: "" }
            // Sort rooms alphabetically, pushing plants with no room to the bottom
            val sorted  = grouped.entries.sortedWith(compareBy {
                if (it.key.isEmpty()) "zzz" else it.key.lowercase()
            })
            for ((room, plants) in sorted) {
                val roomName = room.ifEmpty { "No room" }
                items.add(RoomItem.Header(roomName, plants.size))
                plants.forEach { items.add(RoomItem.PlantRow(it)) }
            }
        } else {
            newPlants.forEach { items.add(RoomItem.PlantRow(it)) }
        }
        notifyDataSetChanged()
    }

    fun getPlantAt(position: Int): Plant {
        val item = items[position]
        return (item as? RoomItem.PlantRow)?.plant
            ?: throw IllegalArgumentException("Item at $position is a header, not a plant")
    }

    fun isHeader(position: Int) = items[position] is RoomItem.Header

    fun getAllPlants(): List<Plant> = items.filterIsInstance<RoomItem.PlantRow>().map { it.plant }
}