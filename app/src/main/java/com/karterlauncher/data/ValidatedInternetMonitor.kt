package com.karterlauncher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ValidatedInternetMonitor(context: Context) {
    private val app = context.applicationContext
    private val _connected = MutableStateFlow(app.hasValidatedInternet())
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (callback != null) return
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                ping()
            }

            override fun onLost(network: Network) {
                ping()
            }

            override fun onUnavailable() {
                ping()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                ping()
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                ping()
            }
        }
        callback = cb
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, cb)
        ping()
    }

    fun stop() {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = callback ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
        callback = null
    }

    private fun ping() {
        _connected.value = app.hasValidatedInternet()
    }
}
