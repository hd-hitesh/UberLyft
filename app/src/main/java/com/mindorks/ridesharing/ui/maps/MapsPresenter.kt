package com.mindorks.ridesharing.ui.maps

import android.util.Log
import android.view.View
import com.google.android.gms.maps.model.LatLng
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.simulator.WebSocket
import com.mindorks.ridesharing.simulator.WebSocketListener
import com.mindorks.ridesharing.utils.Constants
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln

class MapsPresenter(private val networkService: NetworkService): WebSocketListener {

    companion object{
        private val TAG = "MapsPresenter"
    }

    private var view: MapsView? = null
    private lateinit var webSocket: WebSocket

    fun requestNearByCab(latLng: LatLng){
        val jsonObject = JSONObject()
        jsonObject.put(Constants.TYPE,Constants.NEAR_BY_CABS)
        jsonObject.put(Constants.LAT,latLng.latitude)
        jsonObject.put(Constants.LNG,latLng.longitude)
        webSocket.sendMessage(jsonObject.toString())
    }

    fun onAttach(view: MapsView){
        this.view = view
        webSocket = networkService.createWebSocket(this)
        webSocket.connect()
    }

    fun onDetach(){
        webSocket.disconnect()
        view = null
    }

    override fun onConnect() {
        Log.d(TAG,"onConnect")
    }

    override fun onMessage(data: String) {
        Log.d(TAG,"onMessage : $data")
        val jsonObject = JSONObject(data)
        when(jsonObject.getString(Constants.TYPE)){
            Constants.NEAR_BY_CABS -> {
                handleOnMessageBearByCabs(jsonObject)
            }
            Constants.CAB_BOOKED ->{
                view?.informCabBooked()
            }
            Constants.PICKUP_PATH, Constants.TRIP_PATH -> {
                val jsonArray = jsonObject.getJSONArray("path")
                val pickUpPath = arrayListOf<LatLng>()
                for (i in 0 until jsonArray.length()) {
                    val lat = (jsonArray.get(i) as JSONObject).getDouble("lat")
                    val lng = (jsonArray.get(i) as JSONObject).getDouble("lng")
                    val latLng = LatLng(lat, lng)
                    pickUpPath.add(latLng)
                }
                view?.showPath(pickUpPath)
            }
        }
    }

    fun requestCab(pickupLatLng: LatLng, dropLatLng: LatLng ){
        val jsonObject = JSONObject()
        jsonObject.put(Constants.TYPE,Constants.REQUEST_CAB)
        jsonObject.put("pickUpLat",pickupLatLng.latitude)
        jsonObject.put("pickUpLng",pickupLatLng.longitude)
        jsonObject.put("dropUpLat",dropLatLng.latitude)
        jsonObject.put("dropUpLng",dropLatLng.longitude)
        webSocket.sendMessage(jsonObject.toString())

    }

    private fun handleOnMessageBearByCabs(jsonObject: JSONObject) {
        val nearByCabLocations = arrayListOf<LatLng>()
        val jsonArray = jsonObject.getJSONArray(Constants.LOCATIONS)
        for (i in 0 until jsonArray.length()){
            val lat = (jsonArray.get(i) as JSONObject).getDouble(Constants.LAT)
            val lng = (jsonArray.get(i) as JSONObject).getDouble(Constants.LNG)
            val latLng = LatLng(lat, lng)
            nearByCabLocations.add(latLng)
        }
        view?.showNearByCabs(nearByCabLocations)
    }

    override fun onDisconnect() {
        Log.d(TAG,"onDisconnect")
    }

    override fun onError(error: String) {
        Log.d(TAG,"onError : $error")
    }
}