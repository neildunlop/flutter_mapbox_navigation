package com.eopeter.flutter_mapbox_navigation.activity

import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.transition.Fade
import androidx.transition.Scene
import androidx.transition.TransitionManager
import com.eopeter.flutter_mapbox_navigation.models.Waypoint
import com.eopeter.flutter_mapbox_navigation.models.WaypointSet
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.internal.extensions.flowRoutesUpdated
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.ui.base.lifecycle.UIBinder
import com.mapbox.navigation.ui.base.lifecycle.UIComponent
import eopeter.flutter_mapbox_navigation.R
import eopeter.flutter_mapbox_navigation.databinding.InfoPanelContentLayoutBinding

class MyInfoPanelContentComponent(private val content: TextView) : UIComponent() {

    override fun onAttached(mapboxNavigation: MapboxNavigation) {
        super.onAttached(mapboxNavigation)
        mapboxNavigation.flowRoutesUpdated().observe {
            content.isVisible = it.navigationRoutes.isNotEmpty()
        }
    }
}


class MyInfoPanelContentBinder(waypointSet: WaypointSet, private val onWaypointStatusChangeListener: WaypointAdapter.OnWaypointStatusChangedListener) : UIBinder {

    private val waypoints = waypointSet.asList()

    override fun bind(viewGroup: ViewGroup): MapboxNavigationObserver {
        val scene = Scene.getSceneForLayout(
            viewGroup,
            R.layout.info_panel_content_layout,
            viewGroup.context
        )
        TransitionManager.go(scene, Fade())

        val binding = InfoPanelContentLayoutBinding.bind(viewGroup)

        bindWaypointUIElements(binding, onWaypointStatusChangeListener, waypoints)

        return MyInfoPanelContentComponent(binding.content)
    }

    private fun bindWaypointUIElements(
        binding: InfoPanelContentLayoutBinding,
        waypointStatusChangeListener: WaypointAdapter.OnWaypointStatusChangedListener,
        waypoints: List<Waypoint>
    ) {

        //This makes a collection of waypoints with status - no longer needed
//        this@NavigationActivity.waypoints =
//            waypoints.map { waypoint -> WaypointWithStatus(waypoint, true) }

        //This converts waypoint data items into presentable UI components -
        val waypointAdapter =
//            WaypointAdapter(waypoints) { position: Int, isChecked: Boolean ->
//                waypointStatusChangeListener(position, isChecked)
//            }
            WaypointAdapter(waypoints, waypointStatusChangeListener)
        val waypointRecyclerView = binding.waypointlist
        waypointRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false)
            adapter = waypointAdapter
        }

        //This forces the route to recalculate when the waypoint collection is modified
//        getNavigableRoute(
//            this@NavigationActivity.waypoints,
//            this@NavigationActivity::previewRoute
//        )
    }
}