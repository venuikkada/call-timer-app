package com.callgalli.telecom

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.callgalli.R
import com.callgalli.ui.MainActivity

/** All notifications CallGalli shows. Channels are created once at app start. */
class CallNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_ONGOING, context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
                NotificationChannel(CH_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false) // CallGalli vibrates itself, once.
                },
            )
        )
    }

    fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        // The runtime permission only exists from Android 13.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun post(id: Int, notification: Notification) {
        if (!canPost()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    // ---- Foreground service: "watching" when idle, live countdown during a call ----------

    fun ongoing(state: InCallState): Notification {
        val call = state.call
        val builder = NotificationCompat.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainIntent())

        if (call == null || call.ringing) {
            return builder.setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notif_watching_calls))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
        }
        builder.setContentTitle(call.label).setCategory(NotificationCompat.CATEGORY_CALL)
        val remaining = call.remainingMillis
        when {
            remaining == null -> builder.setContentText(context.getString(R.string.incall_untimed))
            else -> builder
                .setContentText(context.getString(R.string.notif_time_left_running))
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + remaining)
                .setShowWhen(true)
        }
        if (CallEnder.canEnd(context)) {
            builder.addAction(0, context.getString(R.string.action_end_call), hangUpIntent())
        }
        return builder.build()
    }

    // ---- Warnings and results -------------------------------------------------------------

    fun showWarning(label: String, secondsLeft: Int) {
        val notification = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.warning_title, secondsLeft))
            .setContentText(context.getString(R.string.warning_body, label))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setTimeoutAfter(8_000)
            .setAutoCancel(true)
            .build()
        post(WARNING_ID, notification)
    }

    fun showEndedByLimit(label: String, minutes: Int) {
        manager.cancel(WARNING_ID)
        val notification = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.ended_by_limit_title))
            .setContentText(context.getString(R.string.ended_by_limit_body, label, minutes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainIntent())
            .setAutoCancel(true)
            .build()
        post(ENDED_ID, notification)
    }

    /** User dialled a number that already used up today's calls. */
    fun showOverLimitOutgoing(label: String) {
        val builder = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.dialer_blocked_title))
            .setContentText(context.getString(R.string.dialer_blocked_body, label))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.dialer_blocked_body, label)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (CallEnder.canEnd(context)) builder.addAction(0, context.getString(R.string.action_hang_up), hangUpIntent())
        post(OVER_LIMIT_ID, builder.build())
    }

    fun cancelOverLimitOutgoing() = manager.cancel(OVER_LIMIT_ID)

    /** endCall() failed (permission removed): ask the user to hang up. */
    fun showCouldNotEnd(label: String) {
        val notification = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.could_not_end_title))
            .setContentText(context.getString(R.string.could_not_end_body, label))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainIntent())
            .setAutoCancel(true)
            .build()
        post(ENDED_ID, notification)
    }

    // ---- Intents --------------------------------------------------------------------------

    private fun mainIntent(): PendingIntent = PendingIntent.getActivity(
        context, 2,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun hangUpIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 4,
        Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_HANG_UP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CH_ONGOING = "call_timer"
        const val CH_ALERTS = "limit_alerts"
        const val ONGOING_ID = 1
        private const val WARNING_ID = 2
        private const val ENDED_ID = 3
        private const val OVER_LIMIT_ID = 4
    }
}
