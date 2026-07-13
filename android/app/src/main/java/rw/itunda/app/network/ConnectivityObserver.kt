package rw.itunda.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Real, proactive connectivity signal via ConnectivityManager.NetworkCallback -- fires
 * the moment a real, validated internet-capable network appears, unlike
 * MainViewModel's pre-existing `isOffline` flag, which is purely reactive (only ever
 * set inside fetchData()'s IOException catch, after a request has already failed).
 * Used to trigger a real replay of the offline action queue the instant connectivity
 * actually returns, rather than waiting for the user to happen to pull-to-refresh.
 */
class ConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start(onAvailable: () -> Unit) {
        if (callback != null) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        val newCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onAvailable()
            }
        }
        callback = newCallback
        connectivityManager.registerNetworkCallback(request, newCallback)
    }

    fun stop() {
        callback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered (e.g. system tore it down) -- not an error.
            }
        }
        callback = null
    }
}
