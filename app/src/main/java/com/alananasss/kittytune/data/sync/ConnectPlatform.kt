package com.alananasss.kittytune.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.LinkProperties
import android.os.Handler
import android.os.Looper
import com.alananasss.kittytune.KittyTuneApp

internal object ConnectPlatform {
    fun trace(event: String) { if (com.alananasss.kittytune.BuildConfig.DEBUG) android.util.Log.d("KittyConnect", event) }
    const val mobile = true
    private val prefs by lazy { KittyTuneApp.instance.getSharedPreferences("sync_state", Context.MODE_PRIVATE) }
    var relayUrl: String
        get() = prefs.getString("connect_relay_url", "").orEmpty()
        set(value) { prefs.edit().putString("connect_relay_url", value).apply() }
    var autoHeadphones: Boolean
        get() = prefs.getBoolean("connect_auto_headphones", true)
        set(value) { prefs.edit().putBoolean("connect_auto_headphones", value).apply() }
    private val audio by lazy { KittyTuneApp.instance.getSystemService(android.media.AudioManager::class.java) }
    fun canAutoTransfer() = audio?.mode == android.media.AudioManager.MODE_NORMAL
    private var audioCallback: android.media.AudioDeviceCallback? = null
    @Synchronized fun observeHeadphones(active: Boolean) {
        if (active && autoHeadphones && audioCallback == null) {
            val callback = object : android.media.AudioDeviceCallback() {
                private fun changed() {
                    val connected = audio?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.any { device ->
                        device.type in setOf(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                            android.media.AudioDeviceInfo.TYPE_USB_DEVICE, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                            android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
                    } == true
                    ConnectManager.headphonesChanged(connected)
                }
                override fun onAudioDevicesAdded(devices: Array<out android.media.AudioDeviceInfo>) = changed()
                override fun onAudioDevicesRemoved(devices: Array<out android.media.AudioDeviceInfo>) = changed()
            }
            runCatching { audio?.registerAudioDeviceCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
                audioCallback = callback }
        } else if (!active || !autoHeadphones) {
            audioCallback?.let { audio?.unregisterAudioDeviceCallback(it) }; audioCallback = null
        }
    }
    fun relayEndpoint() = relayUrl
    fun startListener() {} // Android uses one outbound duplex connection, including for receiving commands.
    private val connectivity by lazy { KittyTuneApp.instance.getSystemService(ConnectivityManager::class.java) }
    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val networkState = ConnectNetworkState<Network>()
    private val networkHandler = Handler(Looper.getMainLooper())
    fun canUseLan() = networkState.route?.capabilities?.lan == true
    fun canConnect() = networkState.route != null

    /** Bind both DNS and sockets to the announced route, never the previous default network. */
    fun routeClient(client: okhttp3.OkHttpClient): okhttp3.OkHttpClient? {
        val network = networkState.route?.network ?: return null
        return client.newBuilder().socketFactory(network.socketFactory)
            .dns { host -> network.getAllByName(host).toList() }.build()
    }

    private fun capabilities(value: NetworkCapabilities) = ConnectNetworkState.Capabilities(
        transports = listOf(NetworkCapabilities.TRANSPORT_CELLULAR, NetworkCapabilities.TRANSPORT_WIFI,
            NetworkCapabilities.TRANSPORT_ETHERNET, NetworkCapabilities.TRANSPORT_VPN,
            NetworkCapabilities.TRANSPORT_BLUETOOTH).fold(0) { mask, transport ->
            if (value.hasTransport(transport)) mask or (1 shl transport) else mask
        },
        lan = value.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || value.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        internet = value.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            value.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    )
    private fun properties(value: LinkProperties) = listOf(value.interfaceName,
        value.linkAddresses.map { it.toString() }.sorted(), value.dnsServers.map { it.hostAddress }.sorted(),
        value.routes.map { it.toString() }.sorted(), value.httpProxy?.toString()).joinToString("|")
    @Synchronized
    fun setActive(value: Boolean) {
        if (value) {
            SyncScheduler.start(); SyncService.startIfWanted()
            if (networkCallback == null) {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    private val reconnect = Runnable {
                        if (networkCallback === this) ConnectManager.networkChanged()
                    }
                    private fun changed(value: Boolean) {
                        // Invoked on main, outside the platform/state locks. Ignore callbacks after unregister.
                        if (value && networkCallback === this) {
                            trace("network route ${if (canUseLan()) "LAN" else if (canConnect()) "Internet" else "unavailable"}")
                            networkHandler.removeCallbacks(reconnect)
                            if (!canConnect()) ConnectManager.networkChanged()
                            else {
                                // Android delivers route/capability changes in a short burst. Dial once after it settles.
                                networkHandler.postDelayed(reconnect, 150)
                            }
                        }
                    }
                    override fun onAvailable(network: Network) {
                        if (networkCallback === this) changed(networkState.available(network))
                    }
                    override fun onLost(network: Network) {
                        if (networkCallback === this) changed(networkState.lost(network))
                    }
                    override fun onCapabilitiesChanged(network: Network, value: NetworkCapabilities) {
                        if (networkCallback === this) changed(networkState.capabilities(network, capabilities(value)))
                    }
                    override fun onLinkPropertiesChanged(network: Network, value: LinkProperties) {
                        if (networkCallback === this) changed(networkState.properties(network, properties(value)))
                    }
                    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                        if (networkCallback === this) changed(networkState.blocked(network, blocked))
                    }
                }
                // Seed once outside callbacks. Subsequent route updates only use callback arguments.
                networkState.clear()
                connectivity?.activeNetwork?.let { network ->
                    networkState.available(network)
                    connectivity?.getNetworkCapabilities(network)?.let { networkState.capabilities(network, capabilities(it)) }
                    connectivity?.getLinkProperties(network)?.let { networkState.properties(network, properties(it)) }
                }
                runCatching {
                    networkCallback = callback
                    connectivity?.registerDefaultNetworkCallback(callback, networkHandler)
                }.onFailure { networkCallback = null }
            }
        } else {
            networkCallback?.let { runCatching { connectivity?.unregisterNetworkCallback(it) } }
            networkCallback = null
            networkState.clear()
            SyncScheduler.stop(); SyncService.stop()
        }
    }
}
