package com.eopeter.flutter_mapbox_navigation

import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.LifecycleOwner
import com.eopeter.flutter_mapbox_navigation.models.MapBoxEvents
import com.eopeter.flutter_mapbox_navigation.models.MapBoxRouteProgressEvent
import com.eopeter.flutter_mapbox_navigation.models.Waypoint
import com.eopeter.flutter_mapbox_navigation.models.WaypointSet
import com.eopeter.flutter_mapbox_navigation.utilities.PluginUtilities
import com.google.gson.Gson
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.*
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.dropin.map.MapViewObserver
import eopeter.flutter_mapbox_navigation.R
import eopeter.flutter_mapbox_navigation.databinding.NavigationActivityBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import timber.log.Timber
import java.util.*


open class TurnByTurn(
    ctx: Context,
    act: Activity,
    bind: NavigationActivityBinding,
    accessToken: String
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler,
    Application.ActivityLifecycleCallbacks {

    open fun initFlutterChannelHandlers() {
        methodChannel?.setMethodCallHandler(this)
        eventChannel?.setStreamHandler(this)
    }

    open fun initNavigation() {
        val navigationOptions = NavigationOptions.Builder(context)
            .accessToken(token)
            .build()

        MapboxNavigationApp
            .setup(navigationOptions)
            .attach(activity as LifecycleOwner)

        // initialize navigation trip observers
        registerObservers()
        registerMapObservers()
    }

    override fun onMethodCall(methodCall: MethodCall, result: MethodChannel.Result) {
        when (methodCall.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }

            "enableOfflineRouting" -> {
                //downloadRegionForOfflineRouting(call, result)
            }

            "buildRoute" -> {
                buildRoute(methodCall, result)
            }

            "clearRoute" -> {
                clearRoute(methodCall, result)
            }

            "startNavigation" -> {
                startNavigation(methodCall, result)
            }

            "finishNavigation" -> {
                finishNavigation(methodCall, result)
            }

            "getDistanceRemaining" -> {
                result.success(distanceRemaining)
            }

            "getDurationRemaining" -> {
                result.success(durationRemaining)
            }

            else -> result.notImplemented()
        }
    }

    private fun buildRoute(methodCall: MethodCall, result: MethodChannel.Result) {
        isNavigationCanceled = false

        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null)
            setOptions(arguments)

        addedWaypoints.clear()
        val points = arguments?.get("wayPoints") as HashMap<*, *>
        for (item in points) {
            val point = item.value as HashMap<*, *>
            val latitude = point["Latitude"] as Double
            val longitude = point["Longitude"] as Double
            addedWaypoints.add(Waypoint(Point.fromLngLat(longitude, latitude)))
        }
        getRoute(context)
        result.success(true)
    }

    private fun getRoute(context: Context) {
        MapboxNavigationApp.current()!!.requestRoutes(
            routeOptions = RouteOptions
                .builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(context)
                .coordinatesList(addedWaypoints.coordinatesList())
                .waypointIndicesList(addedWaypoints.waypointsIndices())
                .waypointNamesList(addedWaypoints.waypointsNames())
                .alternatives(true)
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    currentRoutes = routes
                    val directionsRoutes = routes.map { it.directionsRoute }
                    PluginUtilities.sendEvent(
                        MapBoxEvents.ROUTE_BUILT,
                        Gson().toJson(directionsRoutes)
                    )
                    binding.navigationView.api.routeReplayEnabled(simulateRoute)
                    binding.navigationView.api.startRoutePreview(routes)
                }

                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) {
                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED)
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_CANCELLED)
                }
            }
        )
    }

    private fun clearRoute(methodCall: MethodCall, result: MethodChannel.Result) {
        currentRoutes = null;
    }

    private fun startNavigation(methodCall: MethodCall, result: MethodChannel.Result) {

        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null)
            setOptions(arguments)

        startNavigation()

        if (currentRoutes != null) {
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private fun finishNavigation(methodCall: MethodCall, result: MethodChannel.Result) {

        finishNavigation()

        if (currentRoutes != null) {
            result.success(true)
        } else {
            result.success(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startNavigation() {
        binding.navigationView.api.startActiveGuidance(currentRoutes!!);
    }

    private fun finishNavigation(isOffRouted: Boolean = false) {
        MapboxNavigationApp.current()!!.stopTripSession()
        isNavigationCanceled = true
    }

    private fun setOptions(arguments: Map<*, *>) {
        val navMode = arguments["mode"] as? String
        if (navMode != null) {
            if (navMode == "walking")
                navigationMode = DirectionsCriteria.PROFILE_WALKING;
            else if (navMode == "cycling")
                navigationMode = DirectionsCriteria.PROFILE_CYCLING;
            else if (navMode == "driving")
                navigationMode = DirectionsCriteria.PROFILE_DRIVING;
        }

        val simulated = arguments["simulateRoute"] as? Boolean
        if (simulated != null) {
            simulateRoute = simulated
        }

        val language = arguments["language"] as? String
        if (language != null)
            navigationLanguage = language

        val units = arguments["units"] as? String

        if (units != null) {
            if (units == "imperial")
                navigationVoiceUnits = DirectionsCriteria.IMPERIAL
            else if (units == "metric")
                navigationVoiceUnits = DirectionsCriteria.METRIC
        }

        mapStyleUrlDay = arguments?.get("mapStyleUrlDay") as? String
        mapStyleUrlNight = arguments?.get("mapStyleUrlNight") as? String

        initialLatitude = arguments["initialLatitude"] as? Double
        initialLongitude = arguments["initialLongitude"] as? Double

        val zm = arguments["zoom"] as? Double
        if (zm != null)
            zoom = zm

        val br = arguments["bearing"] as? Double
        if (br != null)
            bearing = br

        val tt = arguments["tilt"] as? Double
        if (tt != null)
            tilt = tt

        val optim = arguments["isOptimized"] as? Boolean
        if (optim != null)
            isOptimized = optim

        val anim = arguments["animateBuildRoute"] as? Boolean
        if (anim != null)
            animateBuildRoute = anim

        val altRoute = arguments["alternatives"] as? Boolean
        if (altRoute != null)
            alternatives = altRoute

        val voiceEnabled = arguments["voiceInstructionsEnabled"] as? Boolean
        if (voiceEnabled != null)
            voiceInstructionsEnabled = voiceEnabled

        val bannerEnabled = arguments["bannerInstructionsEnabled"] as? Boolean
        if (bannerEnabled != null)
            bannerInstructionsEnabled = bannerEnabled

        val longPress = arguments["longPressDestinationEnabled"] as? Boolean
        if (longPress != null)
            longPressDestinationEnabled = longPress
    }

    open fun registerObservers() {
        // register event listeners
        MapboxNavigationApp.current()?.registerLocationObserver(locationObserver)
        MapboxNavigationApp.current()?.registerRouteProgressObserver(routeProgressObserver)
        MapboxNavigationApp.current()?.registerArrivalObserver(arrivalObserver)
    }

    open fun unregisterObservers() {
        // unregister event listeners to prevent leaks or unnecessary resource consumption
        MapboxNavigationApp.current()?.unregisterLocationObserver(locationObserver)
        MapboxNavigationApp.current()?.unregisterRouteProgressObserver(routeProgressObserver)
        MapboxNavigationApp.current()?.unregisterArrivalObserver(arrivalObserver)
    }

    open fun registerMapObservers() {
        //binding.navigationView.registerMapObserver(poiDecorator)
        binding.navigationView.registerMapObserver(otherCarDecorator)
    }

    open fun unregisterMapObservers() {
        binding.navigationView.unregisterMapObserver(poiDecorator)
        binding.navigationView.unregisterMapObserver(otherCarDecorator)
    }

    //Flutter stream listener delegate methods
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        FlutterMapboxNavigationPlugin.eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        FlutterMapboxNavigationPlugin.eventSink = null
    }

    private val context: Context = ctx
    val activity: Activity = act
    private val token: String = accessToken
    open var methodChannel: MethodChannel? = null
    open var eventChannel: EventChannel? = null
    private var lastLocation: Location? = null

    /**
     * Helper class that keeps added waypoints and transforms them to the [RouteOptions] params.
     */
    private val addedWaypoints = WaypointSet()

    //Config
    var initialLatitude: Double? = null
    var initialLongitude: Double? = null

    //val wayPoints: MutableList<Point> = mutableListOf()
    var navigationMode = DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
    var simulateRoute = false
    var mapStyleUrlDay: String? = null
    var mapStyleUrlNight: String? = null
    var navigationLanguage = "en"
    var navigationVoiceUnits = DirectionsCriteria.IMPERIAL
    var zoom = 15.0
    var bearing = 0.0
    var tilt = 0.0
    var distanceRemaining: Float? = null
    var durationRemaining: Double? = null

    var alternatives = true

    var allowsUTurnAtWayPoints = false
    var enableRefresh = false
    var voiceInstructionsEnabled = true
    var bannerInstructionsEnabled = true
    var longPressDestinationEnabled = true
    var animateBuildRoute = true
    private var isOptimized = false

    private var currentRoutes: List<NavigationRoute>? = null
    private var isNavigationCanceled = false

    /**
     * Bindings to the example layout.
     */
    open val binding: NavigationActivityBinding = bind

    /**
     * Gets notified with location updates.
     *
     * Exposes raw updates coming directly from the location services
     * and the updates enhanced by the Navigation SDK (cleaned up and matched to the road).
     */
    private val locationObserver = object : LocationObserver {
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            lastLocation = locationMatcherResult.enhancedLocation
        }

        override fun onNewRawLocation(rawLocation: Location) {
            // no impl
        }
    }

    /**
     * Gets notified with progress along the currently active route.
     */
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        //update flutter events
        if (!isNavigationCanceled) {
            try {

                distanceRemaining = routeProgress.distanceRemaining
                durationRemaining = routeProgress.durationRemaining

                val progressEvent = MapBoxRouteProgressEvent(routeProgress)
                PluginUtilities.sendEvent(progressEvent)

            } catch (e: java.lang.Exception) {

            }
        }
    }

    private val arrivalObserver: ArrivalObserver = object : ArrivalObserver {
        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
            PluginUtilities.sendEvent(MapBoxEvents.ON_ARRIVAL)
        }

        override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {

        }

        override fun onWaypointArrival(routeProgress: RouteProgress) {

        }
    }

    /**
     * Notifies with attach and detach events on [MapView] - Uses Annotations
     */
    private val poiDecorator = object : MapViewObserver() {

        override fun onAttached(mapView: MapView) {
            addAnnotationToMap(mapView);
        }

        private fun addAnnotationToMap(mapView: MapView) {
            Timber.tag("Neil!").e("Adding annotations")
            // Create an instance of the Annotation API and get the PointAnnotationManager.
            bitmapFromDrawableRes(
                context,
                R.drawable.red_marker
            )?.let {
                val annotationApi = mapView?.annotations
                //val pointAnnotationManager = annotationApi?.createPointAnnotationManager(mapView!!)
                val pointAnnotationManager = annotationApi?.createPointAnnotationManager()
                // Set options for the resulting symbol layer.
                val pointAnnotationOptions1: PointAnnotationOptions = PointAnnotationOptions()
                    // Define a geographic coordinate.
                    .withPoint(Point.fromLngLat(-1.470070, 54.016173))
                    // Specify the bitmap you assigned to the point annotation
                    // The bitmap will be added to map style automatically.
                    .withIconImage(it)

                val pointAnnotationOptions2: PointAnnotationOptions = PointAnnotationOptions()
                    // Define a geographic coordinate.
                    .withPoint(Point.fromLngLat(-1.470826, 54.015643))
                    // Specify the bitmap you assigned to the point annotation
                    // The bitmap will be added to map style automatically.
                    .withIconImage(it)
                // Add the resulting pointAnnotation to the map.
                pointAnnotationManager?.create(pointAnnotationOptions1)
                pointAnnotationManager?.create(pointAnnotationOptions2)
            }
        }

        private fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int) =
            convertDrawableToBitmap(AppCompatResources.getDrawable(context, resourceId))

        private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
            if (sourceDrawable == null) {
                return null
            }
            return if (sourceDrawable is BitmapDrawable) {
                sourceDrawable.bitmap
            } else {
// copying drawable object to not manipulate on the same reference
                val constantState = sourceDrawable.constantState ?: return null
                val drawable = constantState.newDrawable().mutate()
                val bitmap: Bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth, drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        }
    }

    //Tries to use GeoJson Source
    private val otherCarDecorator = object : MapViewObserver() {

        override fun onAttached(mapView: MapView) {
            val latitude = 54.016173
            val longitude = -1.470070
            val startingPoint: Point = Point.fromLngLat(longitude, latitude)

            addCarsToMap(mapView, startingPoint)
            simulateCarMovement(mapView, startingPoint)
            //updateCarPositions(mapView)
        }

        private fun simulateCarMovement(mapView: MapView, previousPoint: Point) {

            var latitude = previousPoint.latitude()
            var longitude = previousPoint.longitude()

            // A handler is needed to called the API every x amount of seconds.
            val handler = Handler(Looper.getMainLooper())
                handler.post(object : Runnable {
                    override fun run() {
                        latitude += 0.000100
                        longitude -= 0.000100
                        animateMarkerPosition(mapView, Point.fromLngLat(longitude, latitude))

//                        updateCarPositions(
//                            mapView, Point.fromLngLat(
//                                longitude,
//                                latitude
//                            )
//                        )
                        //simulate a new position every second
                        handler.postDelayed(this, 1000)
                    }
                })



        }

        private fun addCarsToMap(mapView: MapView, startingPoint: Point) {
            // Get the style
            mapView.getMapboxMap().getStyle { style ->
                //add the bitmap to the style
                bitmapFromDrawableRes(
                    context,
                    R.drawable.car_marker
                )?.let {
                    style.addImage(
                        "car-marker-id",
                        it
                    )
                }

                // Specify a unique string as the source ID (SOURCE_ID)
                // and create the source data
                style.addSource(
                    geoJsonSource("car-marker-source-id")
                        .featureCollection(
                            FeatureCollection.fromFeature(
                                Feature.fromGeometry(
                                    Point.fromLngLat(
                                        startingPoint.longitude(),
                                        startingPoint.latitude()
                                    )
                                )
                            )
                        )
                )
                style.addLayer(symbolLayer("car-marker-layer-id", "car-marker-source-id") {
                    iconImage("car-marker-id")
                    iconIgnorePlacement(true)
                    iconAllowOverlap(true)
                    iconSize(.2)
                })
            }


        }

        //TODO: Probably pass a collection of position updates in here - each identified by the car number
        private fun updateCarPositions(mapView: MapView, point: Point) {
            mapView.getMapboxMap().getStyle { style ->
                val carPositionSource: GeoJsonSource? = style.getSourceAs("car-marker-source-id")
                if (carPositionSource != null) {
                    //here we are just directly updating the underlying data source which automatically updates the UI
                    carPositionSource.featureCollection(
                        FeatureCollection.fromFeature(
                            Feature.fromGeometry(
                                point
                            )
                        )
                    )
                }
            }
        }

        var markerAnimator = ValueAnimator()
        var currentPosition = Point.fromLngLat(-1.470070, 54.016173)


        private fun animateMarkerPosition(mapView: MapView, destinationPoint: Point) {
            // When the marker vehicle changes, we want to animate the marker to that
            // location - we do that by working out the fractional movements to animate the
            // marker and repeatedly update the geoJson source, letting the UI update
            // automatically for us.
            if (markerAnimator.isStarted) {
                currentPosition = markerAnimator.animatedValue as Point
                markerAnimator.cancel()
            }

            markerAnimator = ObjectAnimator
                 .ofObject(latLngEvaluator, currentPosition, destinationPoint)
                .setDuration(500)
            addAnimatorListener(markerAnimator, mapView)
            markerAnimator.start()

            currentPosition = destinationPoint
        }

        private fun addAnimatorListener(markerAnimator: ValueAnimator, mapView: MapView) {
            val animatorUpdateListener =
                AnimatorUpdateListener { valueAnimator ->
                    val animatedPosition: Point = valueAnimator.animatedValue as Point
                    updateCarPositions(mapView, Point.fromLngLat(
                        animatedPosition.longitude(),
                        animatedPosition.latitude()
                    ))
                }
            markerAnimator.addUpdateListener(animatorUpdateListener)
        }


        // Class is used to interpolate the marker animation.
        private val latLngEvaluator: TypeEvaluator<Point> = object : TypeEvaluator<Point> {
            override fun evaluate(fraction: Float, startValue: Point, endValue: Point): Point {
                val latitudeValue = startValue.latitude() + (endValue.latitude() - startValue.latitude()) * fraction
                val longitudeValue = startValue.longitude() + (endValue.longitude() - startValue.longitude()) * fraction
                return Point.fromLngLat(longitudeValue, latitudeValue)
            }
        }




//        private fun animateMarkerOld(iconLayer: SymbolLayer) {
//            //See: https://docs.mapbox.com/android/legacy/maps/examples/animate-marker-position/
//            //We animate the marker to the new position and THEN update the car position in the GeoJson
//            val markerAnimator = ValueAnimator()
//            markerAnimator.setObjectValues(1f, 2f)
//            markerAnimator.setDuration(300)
//            markerAnimator.addUpdateListener(AnimatorUpdateListener { animator ->
//                iconLayer.setProperties(
//                    PropertyFactory.iconSize(animator.animatedValue as Float)
//                )
//            })
//            markerAnimator.start()
//            markerSelected = true
//        }

        private fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int) =
            convertDrawableToBitmap(AppCompatResources.getDrawable(context, resourceId))

        private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
            if (sourceDrawable == null) {
                return null
            }
            return if (sourceDrawable is BitmapDrawable) {
                sourceDrawable.bitmap
            } else {
// copying drawable object to not manipulate on the same reference
                val constantState = sourceDrawable.constantState ?: return null
                val drawable = constantState.newDrawable().mutate()
                val bitmap: Bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth, drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        }
    }


    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d("Embedded", "onActivityCreated not implemented")
    }

    override fun onActivityStarted(activity: Activity) {
        Log.d("Embedded", "onActivityStarted not implemented")
    }

    override fun onActivityResumed(activity: Activity) {
        Log.d("Embedded", "onActivityResumed not implemented")
    }

    override fun onActivityPaused(activity: Activity) {
        Log.d("Embedded", "onActivityPaused not implemented")
    }

    override fun onActivityStopped(activity: Activity) {
        Log.d("Embedded", "onActivityStopped not implemented")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.d("Embedded", "onActivitySaveInstanceState not implemented")
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.d("Embedded", "onActivityDestroyed not implemented")
    }
}