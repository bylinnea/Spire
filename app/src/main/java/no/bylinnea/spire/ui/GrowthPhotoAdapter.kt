package no.bylinnea.spire.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import no.bylinnea.spire.R
import no.bylinnea.spire.data.PlantPhoto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for the growth photo timeline. Tap opens the photo fullscreen,
 * long press triggers the delete flow.
 */
class GrowthPhotoAdapter(
    private val photos: MutableList<PlantPhoto>,
    private val onPhotoTapped: (PlantPhoto) -> Unit,
    private val onPhotoLongPressed: (PlantPhoto) -> Unit
) : RecyclerView.Adapter<GrowthPhotoAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.growthPhotoImage)
        val date: TextView   = itemView.findViewById(R.id.growthPhotoDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_growth_photo, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val photo = photos[position]
        Glide.with(holder.itemView.context)
            .load(photo.photoUri)
            .centerCrop()
            .into(holder.image)
        holder.date.text = dateFormat.format(Date(photo.timestamp))
        holder.itemView.setOnClickListener { onPhotoTapped(photo) }
        holder.itemView.setOnLongClickListener { onPhotoLongPressed(photo); true }
    }

    override fun getItemCount() = photos.size

    @SuppressLint("NotifyDataSetChanged")
    fun setPhotos(newPhotos: List<PlantPhoto>) {
        photos.clear()
        photos.addAll(newPhotos)
        notifyDataSetChanged()
    }

    fun addPhoto(photo: PlantPhoto) {
        photos.add(0, photo)
        notifyItemInserted(0)
    }

    fun removePhoto(photo: PlantPhoto) {
        val idx = photos.indexOfFirst { it.id == photo.id }
        if (idx >= 0) { photos.removeAt(idx); notifyItemRemoved(idx) }
    }
}