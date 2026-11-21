package com.callgalli.telecom

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.callgalli.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Always-on foreground service ("CallGalli is watching call time").
 * It keeps CallGalli alive so it hears every call made or received with the phone's
 * own dialer, runs the countdown with the screen off, and shows the floating pill.
 */
class CallMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = dispatchPhoneIntent(context, intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val c = container
        try {
            val notification = c.notifications.ongoing(c.callMonitor.state.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(this, CallNotifications.ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(CallNotifications.ONGOING_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!c.settings.current.enabled) {
            // Switched off between the start request and now: Android still needs startForeground
            // above, then we leave straight away.
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            c.callMonitor.overlay = TimerOverlay(applicationContext)
            c.callMonitor.initialise()
            ContextCompat.registerReceiver(
                this, phoneReceiver,
                IntentFilter().apply {
                    addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                    @Suppress("DEPRECATION") addAction(Intent.ACTION_NEW_OUTGOING_CALL)
                },
                ContextCompat.RECEIVER_EXPORTED, // system broadcasts
            )
            scope.launch {
                c.callMonitor.state
                    // Rebuild the notification only when something visible changes, not every tick.
                    .distinctUntilChangedBy { s -> listOf(s.call?.number, s.call?.ringing, s.call?.limitMillis, s.call?.name, s.call == null) }
                    .collect { s ->
                        runCatching {
                            getSystemService(android.app.NotificationManager::class.java)
                                .notify(CallNotifications.ONGOING_ID, c.notifications.ongoing(s))
                        }
                    }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(phoneReceiver) }
        container.callMonitor.overlay?.hide()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallMonitorService"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, CallMonitorService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Foreground service start refused", e)
            }
        }

        /** Shared by the service's receiver and the manifest receiver (whichever Android delivers). */
        fun dispatchPhoneIntent(context: Context, intent: Intent) {
            if (!context.container.settings.current.enabled) return
            val monitor = context.container.callMonitor
            when (intent.action) {
                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                    @Suppress("DEPRECATION")
                    monitor.onPhoneState(
                        intent.getStringExtra(TelephonyManager.EXTRA_STATE),
                        intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER),
                    )
                }
                @Suppress("DEPRECATION") Intent.ACTION_NEW_OUTGOING_CALL ->
                    @Suppress("DEPRECATION") monitor.onOutgoingNumber(intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER))
            }
        }
    }
}
