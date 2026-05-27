package no.bylinnea.spire.data

/** Sealed class used by PlantAdapter to render both room headers and plant rows in the same RecyclerView list. */
sealed class RoomItem {
    data class Header(val roomName: String, val plantCount: Int) : RoomItem()
    data class PlantRow(val plant: Plant) : RoomItem()
}