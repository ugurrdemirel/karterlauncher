package com.karterlauncher.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.karterlauncher.data.hasValidatedInternet

/** Güvenilir internet (validated) durumunu ağ geri çağırmalarından türetir. */
@Composable
fun rememberHasValidatedInternet(): Boolean {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var online by remember { mutableStateOf(context.hasValidatedInternet()) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun refresh() {
            mainHandler.post {
                online = context.hasValidatedInternet()
            }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refresh()
            }

            override fun onLost(network: Network) {
                refresh()
            }

            override fun onUnavailable() {
                refresh()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                refresh()
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                refresh()
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        refresh()
        onDispose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }
    return online
}
