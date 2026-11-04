package com.gonzaroman.acornix.device

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gonzaroman.acornix.MainActivity
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.TriggerType
import com.gonzaroman.acornix.web.ShortcutRunner

/**
 * Listens for the charger, the battery level and wifi while there are shortcuts waiting for them. Android only
 * delivers those events to an app that is running, so this shows a quiet "Automations are on" notification,
 * like other automation apps do. Bluetooth and times of day don't need it.
 */
class AutomationService : Service() {
    private var lastBattery = -1
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> fire(TriggerType.CHARGER_CONNECTED, null)
                Intent.ACTION_POWER_DISCONNECTED -> fire(TriggerType.CHARGER_DISCONNECTED, null)
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val previous = lastBattery
                    lastBattery = level
                    if (previous < 0) return
                    // Only when the level goes below a threshold, not on every change below it.
                    container.triggers.all.value.filter { it.type == TriggerType.BATTERY_LOW }.forEach { t ->
                        val limit = t.value.toIntOrNull() ?: 20
                        if (previous > limit && level <= limit) ShortcutRunner.run(this@AutomationService, t.appId, "battery")
                    }
                }
            }
        }
    }

    private fun fire(type: TriggerType, value: String?) {
        container.triggers.matching(type, value).forEach { ShortcutRunner.run(this, it.appId, type.name.lowercase()) }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.automations_channel), NotificationManager.IMPORTANCE_MIN))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.automations_on))
            .setContentText(getString(R.string.automations_on_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        ServiceCompat.startForeground(this, 1042, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        listenToWifi()
    }

    private fun listenToWifi() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        var connected: String? = null
        val onChange: (Network, NetworkCapabilities) -> Unit = { _, caps ->
            if (connected == null) {
                val ssid = ssidOf(caps)
                connected = ssid
                fire(TriggerType.WIFI_CONNECTED, ssid)
            }
        }
        val onLost: (Network) -> Unit = {
            connected?.let { was -> connected = null; fire(TriggerType.WIFI_DISCONNECTED, was) }
        }
        // From Android 12 the callback must ask for location info to see the wifi's name.
        val callback = if (Build.VERSION.SDK_INT >= 31) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onChange(network, caps)
                override fun onLost(network: Network) = onLost(network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onChange(network, caps)
                override fun onLost(network: Network) = onLost(network)
            }
        }
        runCatching {
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
            wifiCallback = callback
        }
    }

    /** The wifi's name, when Android shares it (it needs the location permission); otherwise "". */
    private fun ssidOf(caps: NetworkCapabilities): String {
        val info = if (Build.VERSION.SDK_INT >= 29) caps.transportInfo as? WifiInfo else null
        @Suppress("DEPRECATION")
        val ssid = (info ?: (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).connectionInfo)?.ssid
        return ssid?.removeSurrounding("\"")?.takeIf { it != WifiManager.UNKNOWN_SSID.removeSurrounding("<", ">") && !it.contains("unknown ssid") }.orEmpty()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!container.triggers.needsService) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        wifiCallback?.let { runCatching { (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "automations"

        /** Starts the service when some shortcut waits for the charger, battery or wifi, and stops it otherwise. */
        fun sync(context: Context) {
            val intent = Intent(context, AutomationService::class.java)
            if (context.container.triggers.needsService) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                runCatching { context.stopService(intent) }
            }
        }
    }
}

/** Bluetooth devices connecting and disconnecting reach Acornix even when it's closed. */
class BluetoothTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> TriggerType.BLUETOOTH_CONNECTED
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> TriggerType.BLUETOOTH_DISCONNECTED
            else -> return
        }
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val name = if (Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) runCatching { device?.name }.getOrNull() else null
        context.container.triggers.matching(type, name).forEach { ShortcutRunner.run(context, it.appId, "bluetooth") }
    }
}
