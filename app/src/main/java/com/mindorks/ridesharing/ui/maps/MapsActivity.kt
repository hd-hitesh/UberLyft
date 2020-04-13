package com.mindorks.ridesharing.ui.maps

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.mindorks.ridesharing.R
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.utils.MapUtils
import com.mindorks.ridesharing.utils.PermissionUtils
import com.mindorks.ridesharing.utils.ViewUtils

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, MapsView {

    companion object{
        private val TAG = "MapsActivity"
        private val LOCATION_PERMISSION_REQUEST_CODE= 999
    }

    private lateinit var presenter: MapsPresenter
    private lateinit var googleMap: GoogleMap
    private var fusedLocationProviderClient : FusedLocationProviderClient?= null
    private lateinit var locationCallback: LocationCallback
    private var currentLatLng : LatLng? = null
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
    }

    private fun moveCamera(latLng: LatLng?){
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun animateCamera(latLng: LatLng?){
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(15.5f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun addCarMarkerAndGet(latLng: LatLng) : Marker{
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getCArBitMap(this))
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

    override fun onDestroy() {
        presenter.onDetach()
        super.onDestroy()
    }

    override fun showNearByCabs(latLngList: List<LatLng>) {
        nearByCabMarkerList.clear()
        for (latlng in latLngList){
            val nearByCabMarker = addCarMarkerAndGet(latlng)
            nearByCabMarkerList.add(nearByCabMarker)
        }
    }

}
