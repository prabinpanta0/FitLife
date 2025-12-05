package com.example.fitlife.ui.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fitlife.R
import com.example.fitlife.data.model.GeoLocation
import com.example.fitlife.data.model.LocationType

class LocationAdapter(
    private val onLocationClick: (GeoLocation) -> Unit,
    private val onNavigateClick: (GeoLocation) -> Unit,
    private val onDeleteClick: (GeoLocation) -> Unit
) : ListAdapter<GeoLocation, LocationAdapter.LocationViewHolder>(LocationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location, parent, false)
        return LocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivLocationType: ImageView = itemView.findViewById(R.id.ivLocationType)
        private val tvLocationName: TextView = itemView.findViewById(R.id.tvLocationName)
        private val tvLocationType: TextView = itemView.findViewById(R.id.tvLocationType)
        private val tvLocationAddress: TextView = itemView.findViewById(R.id.tvLocationAddress)
        private val btnNavigate: ImageButton = itemView.findViewById(R.id.btnNavigate)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(location: GeoLocation) {
            tvLocationName.text = location.name
            tvLocationType.text = location.locationType.displayName
            tvLocationAddress.text = location.address.ifEmpty { "No address" }

            // Set icon based on type
            ivLocationType.setImageResource(getLocationIcon(location.locationType))

            itemView.setOnClickListener { onLocationClick(location) }
            btnNavigate.setOnClickListener { onNavigateClick(location) }
            btnDelete.setOnClickListener { onDeleteClick(location) }
        }

        private fun getLocationIcon(type: LocationType): Int {
            return when (type) {
                LocationType.GYM -> R.drawable.ic_fitness
                LocationType.YOGA_STUDIO -> R.drawable.ic_star
                LocationType.PARK -> R.drawable.ic_location
                LocationType.HOME -> R.drawable.ic_home
                LocationType.POOL -> R.drawable.ic_location
                LocationType.OTHER -> R.drawable.ic_location
            }
        }
    }

    class LocationDiffCallback : DiffUtil.ItemCallback<GeoLocation>() {
        override fun areItemsTheSame(oldItem: GeoLocation, newItem: GeoLocation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GeoLocation, newItem: GeoLocation): Boolean {
            return oldItem == newItem
        }
    }
}
