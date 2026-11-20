package com.callgalli.telecom

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.callgalli.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Foreground service with a "phone call" notification. It keeps CallGalli's process
 * at high priority so the countdown keeps running with the screen off or the app closed.
 * The notification shows a live countdown ("07:42 left") and an End call button.
 */
class CallTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tracker = container.callTracker
        val notifications = container.notifications
        try {
            ServiceCompat.startForeground(
                this, CallNotifications.ONGOING_ID, notifications.ongoing(tracker.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            )
        } catch (e: Exception) {
            // E.g. CallGalli is not the default phone app any more. The InCallService still runs the timer.
            Log.w(TAG, "Could not start foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!observing) {
            observing = true
            scope.launch {
                tracker.state
                    // Rebuild only when something visible changes, not on every tick.
                    .distinctUntilChangedBy { s ->
                        val p = s.primary
                        listOf(p?.key, p?.phase, p?.connected, p?.timerRunning, p?.limitMillis, p?.name, s.calls.size)
                    }
                    .collect { s ->
                        if (!s.hasCalls) {
                            ServiceCompat.stopForeground(this@CallTimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            notifications.ongoing(s).let { n ->
                                runCatching {
                                    getSystemService(android.app.NotificationManager::class.java).notify(CallNotifications.ONGOING_ID, n)
                                }
                            }
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallTimerService"

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, CallTimerService::class.java))
            } catch (e: Exception) {
                // Android 12+ can refuse background starts; the in-call screen retries when it opens.
                Log.w(TAG, "Foreground service start refused", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallTimerService::class.java))
        }
    }
}
