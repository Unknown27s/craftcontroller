package io.github.unknown27s.dsucontroller

import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.unknown27s.dsucontroller.core.DsuCodec
import io.github.unknown27s.dsucontroller.core.DsuServer
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Foreground-only host. Settings and layout UI are independent of UDP transport. */
class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var server: DsuServer
    private lateinit var connectivity: ConnectivityManager
    private var observedNetwork: Network? = null
    private var observedAddresses: Set<String> = emptySet()
    private var callbackRegistered = false
    private var inputFocused by mutableStateOf(false)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                if (observedNetwork != network) {
                    stopWhenActive("Stopped: default network changed; restart on your trusted LAN")
                    observedAddresses = emptySet()
                }
                observedNetwork = network
            }
        }
        override fun onLost(network: Network) {
            runOnUiThread {
                if (network == observedNetwork) {
                    observedNetwork = null
                    observedAddresses = emptySet()
                    stopWhenActive("Stopped: network lost; reconnect to your LAN")
                }
            }
        }
        override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
            val addresses = listenerAddresses(linkProperties.linkAddresses.map { it.address })
            runOnUiThread {
                if (network == observedNetwork) {
                    if (observedAddresses != addresses) stopWhenActive("Stopped: phone IPv4 changed; restart with the new address")
                    observedAddresses = addresses
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("controller", MODE_PRIVATE)
        val mac = runCatching { prefs.getString("identity", null) }.getOrNull()?.takeIf { it.length == 12 }?.let { value ->
            runCatching { ByteArray(6) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
        } ?: ByteArray(6).also {
            SecureRandom().nextBytes(it)
            it[0] = 2
            prefs.edit().putString("identity", it.joinToString("") { b -> "%02x".format(b.toInt() and 255) }).apply()
        }
        server = DsuServer(DsuCodec(SecureRandom().nextInt(), mac))
        connectivity = getSystemService(ConnectivityManager::class.java)
        observedNetwork = connectivity.activeNetwork
        observedAddresses = observedNetwork?.let { network ->
            connectivity.getLinkProperties(network)?.let { properties -> listenerAddresses(properties.linkAddresses.map { it.address }) }
        } ?: emptySet()
        connectivity.registerDefaultNetworkCallback(networkCallback)
        callbackRegistered = true
        setContent {
            ControllerApp(prefs, server, window, ::lanAddresses, inputFocused) { port -> server.start(scope, port) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        inputFocused = hasFocus
        // A transient system overlay must release input, not destroy the subscription.
        // Actual backgrounding still stops the socket in onPause.
        if (!hasFocus && ::server.isInitialized) server.input.set(io.github.unknown27s.dsucontroller.core.ControllerState())
    }

    override fun onPause() {
        if (::server.isInitialized) stopWhenActive("Stopped: app backgrounded or screen locked; restart in Settings")
        super.onPause()
    }

    override fun onDestroy() {
        if (::server.isInitialized) server.stop()
        if (callbackRegistered) connectivity.unregisterNetworkCallback(networkCallback)
        scope.cancel()
        super.onDestroy()
    }

    private fun stopWhenActive(reason: String) {
        // Do not overwrite an earlier network/socket failure while already stopped.
        val status = server.status.value
        if (status.running || status.message == "Starting…") server.stop(reason)
    }

    private fun lanAddresses(): String = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
            .mapNotNull { it.hostAddress }.distinct().joinToString(" / ")
            .ifEmpty { "No LAN IPv4 address detected" }
    }.getOrDefault("Unable to list LAN addresses")
}
