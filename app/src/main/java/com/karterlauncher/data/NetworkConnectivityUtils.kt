package com.karterlauncher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** True when any connected network reports validated internet (captive portal resolved). */
fun Context.hasValidatedInternet(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return cm.allNetworks.any { network ->
        val caps = cm.getNetworkCapabilities(network) ?: return@any false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
