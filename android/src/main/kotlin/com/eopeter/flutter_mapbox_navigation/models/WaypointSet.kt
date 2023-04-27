package com.eopeter.flutter_mapbox_navigation.models

import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point

/**
 * Helper class that that does 2 things:
 * 1. It stores waypoints
 * 2. Converts the stored waypoints to the [RouteOptions] params
 */
class WaypointSet {

    private val _waypoints = mutableListOf<Waypoint>()

    val isEmpty get() = _waypoints.isEmpty()

    fun add(waypoint: Waypoint) {
        _waypoints.add(waypoint)
    }

    fun add(waypoints: List<Waypoint>) {
        for (waypoint in waypoints) {
            _waypoints.add(waypoint)
        }
    }

    fun clear() {
        _waypoints.clear()
    }

    fun asList(): List<Waypoint> {
        return _waypoints
    }

    fun activeWaypointSet(): WaypointSet {
        val activeWaypointSet = WaypointSet()
        activeWaypointSet.add(_waypoints.filter { it -> it.isActive })
        return activeWaypointSet
    }

    /***
     * Silent waypoint isn't really a waypoint.
     * It's just a coordinate that used to build a route.
     * That's why to make a waypoint silent we exclude its index from the waypointsIndices.
     */
    fun waypointsIndices(): List<Int> {
        return _waypoints.mapIndexedNotNull { index, _ ->
            if (_waypoints.isSilentWaypoint(index)) {
                null
            } else index
        }
    }

    /**
     * Returns names for added waypoints.
     * Silent waypoint can't have a name unless they're converted to regular because of position.
     * First and last waypoint can't be silent.
     */
    fun waypointsNames(): List<String> = _waypoints
        // silent waypoints can't have a name
        .filterIndexed { index, _ ->
            !_waypoints.isSilentWaypoint(index)
        }
        .map {
            it.name
        }

    fun coordinatesList(): List<Point> {
        return _waypoints.map { it.point }
    }

    private fun List<Waypoint>.isSilentWaypoint(index: Int) =
        //this[index].type == WaypointType.Silent && canWaypointBeSilent(index)
        this[index].isSilent && canWaypointBeSilent(index)

    // the first and the last waypoint can't be silent
    private fun List<Waypoint>.canWaypointBeSilent(index: Int): Boolean {
        val isLastWaypoint = index == this.size - 1
        val isFirstWaypoint = index == 0
        return !isLastWaypoint && !isFirstWaypoint
    }
}
