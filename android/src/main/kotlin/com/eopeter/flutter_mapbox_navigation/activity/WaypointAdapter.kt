package com.eopeter.flutter_mapbox_navigation.activity

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.eopeter.flutter_mapbox_navigation.models.Waypoint
import eopeter.flutter_mapbox_navigation.databinding.WaypointItemViewBinding

class WaypointAdapter(
    private val waypoints: List<Waypoint>,
    waypointStatusChangeListener: OnWaypointStatusChangedListener
) :
    RecyclerView.Adapter<WaypointAdapter.ViewHolder>() {

    private var waypointStatusChangedListener: OnWaypointStatusChangedListener? = waypointStatusChangeListener

    fun interface OnWaypointStatusChangedListener {
        fun onWaypointStatusChanged(position: Int, isChecked: Boolean)
    }

//    fun setWaypointStatusChangedListener(listener: OnWaypointStatusChangedListener) {
//        waypointStatusChangedListener = listener;
//    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = WaypointItemViewBinding.inflate(LayoutInflater.from(parent.context))
        return ViewHolder(binding, waypointStatusChangedListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val waypointType =
            if (position == 0) WaypointType.START else if (waypoints.size == position + 1) WaypointType.END else WaypointType.WAYPOINT
        with(holder) { bind(waypoints[position], position, waypointType) }
    }

    override fun getItemCount(): Int {
        return waypoints.size
    }


    class ViewHolder(
        private val binding: WaypointItemViewBinding,
        waypointStatusChangedListener: OnWaypointStatusChangedListener?
    ) : RecyclerView.ViewHolder(binding.root) {

        private val listener = waypointStatusChangedListener

        fun bind(waypoint: Waypoint, position: Int, waypointType: WaypointType) {
            if (waypointType == WaypointType.WAYPOINT) {
                binding.waypointChip.visibility = View.VISIBLE
                //binding.waypointChipArrow.visibility = View.VISIBLE
                binding.waypointDestinationChip.visibility = View.GONE
                binding.waypointChip.text = waypoint.name
                binding.waypointChip.setOnCheckedChangeListener { _, isChecked ->
                    listener?.onWaypointStatusChanged(position, isChecked)
                }
            } else {
                binding.waypointDestinationChip.visibility = View.VISIBLE
                binding.waypointChip.visibility = View.GONE
                //binding.waypointChipArrow.visibility = if (waypointType == WaypointType.END) View.GONE else View.VISIBLE
                binding.waypointDestinationChip.text = waypoint.name
            }
        }
    }

    enum class WaypointType {
        START,
        END,
        WAYPOINT
    }
}
