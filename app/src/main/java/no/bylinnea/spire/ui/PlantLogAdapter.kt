package no.bylinnea.spire.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import no.bylinnea.spire.R
import no.bylinnea.spire.data.PlantLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Adapter for the plant health log. Supports inline edit and delete via swipe in PlantDetailActivity. */
class PlantLogAdapter(
    private val entries: MutableList<PlantLog>
) : RecyclerView.Adapter<PlantLogAdapter.LogViewHolder>() {

    private val dateFormat = SimpleDateFormat("d MMM\nyyyy", Locale.getDefault())

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val date: TextView = itemView.findViewById(R.id.logDate)
        val note: TextView = itemView.findViewById(R.id.logNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = entries[position]
        holder.date.text = dateFormat.format(Date(entry.timestamp))
        holder.note.text = entry.note
    }

    override fun getItemCount() = entries.size

    fun getEntryAt(position: Int) = entries[position]

    fun setEntries(newEntries: List<PlantLog>) {
        entries.clear()
        entries.addAll(newEntries)
        notifyDataSetChanged()
    }

    fun updateEntry(position: Int, updated: PlantLog) {
        entries[position] = updated
        notifyItemChanged(position)
    }

    fun removeAt(position: Int) {
        entries.removeAt(position)
        notifyItemRemoved(position)
    }

    fun appendEntries(newEntries: List<PlantLog>) {
        val start = entries.size
        entries.addAll(newEntries)
        notifyItemRangeInserted(start, newEntries.size)
    }
}
