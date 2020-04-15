package com.mindorks.ridesharing.ui.maps

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.mindorks.ridesharing.R
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.utils.AnimationUtils
import com.mindorks.ridesharing.utils.MapUtils
import com.mindorks.ridesharing.utils.PermissionUtils
import com.mindorks.ridesharing.utils.ViewUtils
import kotlinx.android.synthetic.main.activity_maps.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, MapsView {

    companion object{
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE= 999
        private const val PICKUP_REQUEST_CODE = 1
        private const val DROP_REQUEST_CODE = 2
    }

    private lateinit var presenter: MapsPresenter
    private lateinit var googleMap: GoogleMap
    private var fusedLocationProviderClient : FusedLocationProviderClient?= null
    private lateinit var locationCallback: LocationCallback
    private var currentLatLng : LatLng? = null
    private var pickUpLatLng: LatLng? = null
    private var dropLatLng: LatLng? = null
    private var greyPolyline: Polyline? = null
    private var blackPolyline: Polyline? = null
    private var destinationMarker: Marker? = null
    private var movingCabMarker: Marker? = null
    private var originMarker: Marker? = null
    private var previousLatLngFromServer : LatLng? = null
    private var currentLatLngFromServer : LatLng? = null

    private val nearByCabMarkerList = arrayListOf<Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        presenter = MapsPresenter(NetworkService())
        presenter.onAttach(this)

        setUpClickListener()
    }

    private fun setUpClickListener() {
        pickUpTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(PICKUP_REQUEST_CODE)
        }
        dropTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(DROP_REQUEST_CODE)
        }
        requestCabButton.setOnClickListener {
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = getString(R.string.requesting_your_cab)
            requestCabButton.isEnabled = false
            pickUpTextView.isEnabled = false
            dropTextView.isEnabled = false
            presenter.requestCab(pickUpLatLng!!,dropLatLng!!)
        }
        nextRideButton.setOnClickListener {
            reset()
        }
    }

    private fun launchLocationAutoCompleteActivity(requestCode: Int) {
        val fields: List<Place.Field> =
            listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .build(this)
        startActivityForResult(intent, requestCode)
    }

    private fun moveCamera(latLng: LatLng?){
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun animateCamera(latLng: LatLng?){
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(15.5f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun setCurrentLocationAsPickUp(){
        pickUpLatLng = currentLatLng
        pickUpTextView.text = getString(R.string.current_location)
    }

    private fun addCarMarkerAndGet(latLng: LatLng) : Marker{
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getCArBitMap(this))
        return googleMap.addMarker(MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor))
    }

    private fun addOriginDestinationMarkerAndGet(latLng: LatLng): Marker {
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getDestinationBitmap())
        return googleMap.addMarker(MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor))
    }

    private fun enableMyLocationOnMap(){
        googleMap.setPadding(0,ViewUtils.dpToPx(48f),0,0)
        googleMap.isMyLocationEnabled = true
    }

    private fun setUpLocationListener(){
        //for getting the current location update after every two seconds
        fusedLocationProviderClient = FusedLocationProviderClient(this)
        val locationRequest = LocationRequest()
            .setInterval(2000)
            .setFastestInterval(2000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

        locationCallback = object : LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (currentLatLng == null){
                    for (location in locationResult.locations){
                        if (currentLatLng == null){
                            currentLatLng = LatLng(location.latitude,location.longitude)
                            setCurrentLocationAsPickUp()
                            enableMyLocationOnMap()
                            moveCamera(currentLatLng)
                            animateCamera(currentLatLng)
                            presenter.requestNearByCab(currentLatLng!!)
                        }
                    }
                }
                // for example update the location of the user on server
            }
        }
        fusedLocationProviderClient?.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
    }

    private fun reset() {
            statusTextView.visibility = View.GONE
            nextRideButton.visibility = View.GONE
            nearByCabMarkerList.forEach { it.remove() }
            nearByCabMarkerList.clear()
            previousLatLngFromServer = null
            currentLatLngFromServer = null
            if (currentLatLng != null) {
                moveCamera(currentLatLng)
                animateCamera(currentLatLng)
                setCurrentLocationAsPickUp()
                presenter.requestNearByCab(currentLatLng!!)
            } else {
                pickUpTextView.text = ""
            }
            pickUpTextView.isEnabled = true
            dropTextView.isEnabled = true
            dropTextView.text = ""
            movingCabMarker?.remove()
            greyPolyline?.remove()
            blackPolyline?.remove()
            originMarker?.remove()
            destinationMarker?.remove()
            dropLatLng = null
            greyPolyline = null
            blackPolyline = null
            originMarker = null
            destinationMarker = null
            movingCabMarker = null
    }

    override fun onStart() {
        super.onStart()
        when
        {
            PermissionUtils.isAccessFineLocationPermissionGranted(this) -> {
                when{
                    PermissionUtils.isLocationEnabled(this) ->{
                        // fetch the location
                        setUpLocationListener()
                    }
                    else ->{
                        PermissionUtils.showGPSNotEnableDialog(this)
                    }
                }
            }
            else -> {
                PermissionUtils.requestAccessFineLocationPermission(this,
                    LOCATION_PERMISSION_REQUEST_CODE)
            }
        }//end of when
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
             LOCATION_PERMISSION_REQUEST_CODE ->{
                 if (grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                     when{
                         PermissionUtils.isLocationEnabled(this) ->{
                             // fetch the location
                             setUpLocationListener()
                         }
                         else ->{
                             PermissionUtils.showGPSNotEnableDialog(this)
                         }
                     }
                 } else {
                     Toast.makeText(this,"Location Permission not granted",Toast.LENGTH_SHORT)
                         .show()
                 }
             }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICKUP_REQUEST_CODE || requestCode == DROP_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    Log.d(TAG, "Place: " + place.name + ", " + place.id + ", " + place.latLng)
                    when (requestCode) {
                        PICKUP_REQUEST_CODE -> {
                            pickUpTextView.text = place.name
                            pickUpLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                        DROP_REQUEST_CODE -> {
                            dropTextView.text = place.name
                            dropLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    val status: Status = Autocomplete.getStatusFromIntent(data!!)
                    Log.d(TAG, status.statusMessage!!)
                }
                Activity.RESULT_CANCELED -> {
                    Log.d(TAG, "Place Selection Canceled")
                }
            }
        }
    }

    private fun checkAndShowRequestButton(){
        if (pickUpLatLng != null && dropLatLng != null){
            requestCabButton.visibility = View.VISIBLE
            requestCabButton.isEnabled = true
        }
    }

    override fun onDestroy() {
        presenter.onDetach()
        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun showNearByCabs(latLngList: List<LatLng>) {
        nearByCabMarkerList.clear()
        for (latLng in latLngList){
            val nearByCabMarker = addCarMarkerAndGet(latLng)
            nearByCabMarkerList.add(nearByCabMarker)
        }
    }

    override fun informCabBooked() {
        nearByCabMarkerList.forEach {
            it.remove()
        }
        nearByCabMarkerList.clear()
        requestCabButton.visibility = View.GONE
        statusTextView.text = getString(R.string.your_cab_is_booked)
    }

    override fun showPath(latLngList: List<LatLng>) {
        val builder = LatLngBounds.Builder()
        for (latLng in latLngList){
            builder.include(latLng)
        }
        val bounds = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 2))
        val polylineOptions = PolylineOptions()
        polylineOptions.color(Color.GRAY)
        polylineOptions.width(5f)
        polylineOptions.addAll(latLngList)
        greyPolyline = googleMap.addPolyline(polylineOptions)

        val blackPolylineOptions = PolylineOptions()
        blackPolylineOptions.color(Color.BLACK)
        blackPolylineOptions.width(5f)
        blackPolyline = googleMap.addPolyline(blackPolylineOptions)

        originMarker = addOriginDestinationMarkerAndGet(latLngList[0])
        originMarker?.setAnchor(0.5f, 0.5f)
        destinationMarker = addOriginDestinationMarkerAndGet(latLngList[latLngList.size - 1])
        destinationMarker?.setAnchor(0.5f, 0.5f)

        val polylineAnimator = AnimationUtils.polyLineAnimator()
        polylineAnimator.addUpdateListener { valueAnimator ->
            val percentValue = (valueAnimator.animatedValue as Int)
            val index = (greyPolyline?.points!!.size * (percentValue / 100.0f)).toInt()
            blackPolyline?.points = greyPolyline?.points!!.subList(0, index)
        }
        polylineAnimator.start()
    }

    override fun updateCabLocation(latLng: LatLng) {
        if (movingCabMarker == null) {
            movingCabMarker = addCarMarkerAndGet(latLng)
        }
        if (previousLatLngFromServer == null) {
            currentLatLngFromServer = latLng
            previousLatLngFromServer = latLng
            movingCabMarker?.position = currentLatLngFromServer
            movingCabMarker?.setAnchor(0.5f, 0.5f)
            animateCamera(currentLatLng)
        } else {
            currentLatLngFromServer = previousLatLngFromServer
            previousLatLngFromServer = latLng
            val valueAnimator = AnimationUtils.cabAnimator()
            valueAnimator.addUpdateListener { va ->
                if (currentLatLngFromServer != null && previousLatLngFromServer != null) {
                    val multiplier = va.animatedFraction
                    val nextLocation = LatLng(
                        multiplier * currentLatLngFromServer!!.latitude + (1 - multiplier) * previousLatLngFromServer!!.latitude,
                        multiplier * currentLatLngFromServer!!.longitude + (1 - multiplier) * previousLatLngFromServer!!.longitude
                    )
                    movingCabMarker?.position = nextLocation
                    movingCabMarker?.setAnchor(0.5f, 0.5f)
                    val rotation = MapUtils.getRotation(previousLatLngFromServer!!, nextLocation)
                    if (!rotation.isNaN()) {
                        movingCabMarker?.rotation = rotation
                    }
                    animateCamera(nextLocation)
                }
            }
            valueAnimator.start()
        }
    }

    override fun informCabIsArriving() {
        statusTextView.text = getString(R.string.your_cab_is_arriving)
    }

    override fun informCabArrived() {
        statusTextView.text = getString(R.string.your_cab_has_arrived)
        greyPolyline?.remove()
        blackPolyline?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }


    override fun informTripStart() {
        statusTextView.text = getString(R.string.you_are_on_a_trip)
        previousLatLngFromServer = null
    }

    override fun informTripEnd() {
        statusTextView.text = getString(R.string.trip_end)
        nextRideButton.visibility = View.VISIBLE
        greyPolyline?.remove()
        blackPolyline?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun showRoutesNotAvailableError() {
        val error = getString(R.string.route_not_available_choose_different_locations)
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        reset()
    }

    override fun showDirectionApiFailedError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        reset()
    }
}
