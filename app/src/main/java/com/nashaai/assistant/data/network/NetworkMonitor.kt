package com.nashaai.assistant.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Live network connectivity indicator.
 *
 * Uses ConnectivityManager.NetworkCallback to react to network changes and
 * exposes a cold Flow<Boolean>. The Flow re-emits only on transition
 * (distinctUntilChanged) so collectors do not thrash on frequent updates.
 *
 * The Flow also works when there is no default network at collect time: it
 * emits an initial value derived from [isCurrentlyOnline] and then updates as
 * callbacks arrive.
 *
 * Requires android.permission.ACCESS_NETWORK_STATE (declared in Block 1).
 */
class NetworkMonitor(private val context: Context) {

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Cold flow emitting true when an internet-capable network is active. */
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onLost(network: Network) {
                // A network was lost, but another may still be up; re-check.
                trySend(isCurrentlyOnline())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(
                    networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    )
                )
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            // e.g. SecurityException or no ConnectivityManager in test env.
            trySend(false)
        }

        // Emit initial value immediately so UI does not wait for a change.
        trySend(isCurrentlyOnline())

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
                // Ignore; may not have registered.
            }
        }
    }.distinctUntilChanged()

    /**
     * Synchronous best-effort check used for initial values and tests.
     * Returns false if no active network reports INTERNET capability.
     */
    fun isCurrentlyOnline(): Boolean = try {
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        false
    }
}
