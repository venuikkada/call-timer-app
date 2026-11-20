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
import androidx.core.app.Person
import com.callgalli.R
import com.callgalli.rules.TimerDisplay
import com.callgalli.ui.MainActivity
import com.callgalli.ui.incall.InCallActivity

/** All notifications CallGalli shows. Channels are created once at app start. */
class CallNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_INCOMING, context.getString(R.string.channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                    // Android itself plays the ringtone; the notification stays silent.
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(CH_ONGOING, context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW),
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

    // ---- Incoming call --------------------------------------------------------------------

    fun showIncoming(call: LiveCall) {
        val caller = Person.Builder().setName(call.label).setImportant(true).build()
        val notification = NotificationCompat.Builder(context, CH_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(call.label)
            .setContentText(context.getString(R.string.incall_incoming))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(inCallIntent(), true)
            .setContentIntent(inCallIntent())
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    actionIntent(CallActionReceiver.ACTION_DECLINE, call.key),
                    // Answer opens the call screen directly: Android 12+ blocks opening
                    // an activity from a broadcast started by a notification button.
                    answerIntent(call.key),
                )
            )
            .build()
        post(incomingId(call.key), notification)
    }

    fun cancelIncoming(key: Long) = manager.cancel(incomingId(key))

    // ---- Ongoing call (foreground service) ------------------------------------------------

    fun ongoing(state: InCallState): Notification {
        val call = state.primary
        val builder = NotificationCompat.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(inCallIntent())

        if (call == null) {
            return builder.setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notif_watching_calls))
                .build()
        }
        builder.setContentTitle(call.label)
        val remaining = call.remainingMillis
        when {
            !call.connected -> builder.setContentText(context.getString(R.string.incall_calling))
            remaining == null -> builder.setContentText(context.getString(R.string.incall_untimed))
            call.timerRunning -> builder
                .setContentText(context.getString(R.string.notif_time_left_running))
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + remaining)
                .setShowWhen(true)
            else -> builder.setContentText(context.getString(R.string.notif_paused_on_hold, TimerDisplay.mmss(remaining)))
        }
        builder.addAction(0, context.getString(R.string.action_end_call), actionIntent(CallActionReceiver.ACTION_HANG_UP, call.key))
        return builder.build()
    }

    // ---- Warnings and results -------------------------------------------------------------

    fun showWarning(key: Long, label: String, secondsLeft: Int) {
        val notification = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.warning_title, secondsLeft))
            .setContentText(context.getString(R.string.warning_body, label))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(inCallIntent())
            .setTimeoutAfter(8_000)
            .setAutoCancel(true)
            .build()
        post(WARNING_ID, notification)
    }

    fun showEndedByLimit(key: Long, label: String, minutes: Int) {
        manager.cancel(WARNING_ID)
        val notification = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.ended_by_limit_title))
            .setContentText(context.getString(R.string.ended_by_limit_body, label, minutes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainIntent())
            .setAutoCancel(true)
            .build()
        post(ENDED_ID_BASE + (key % 1000).toInt(), notification)
    }

    // ---- Intents --------------------------------------------------------------------------

    private fun inCallIntent(): PendingIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun answerIntent(key: Long): PendingIntent = PendingIntent.getActivity(
        context, 3,
        Intent(context, InCallActivity::class.java)
            .setAction(CallActionReceiver.ACTION_ANSWER)
            .putExtra(CallActionReceiver.EXTRA_KEY, key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun mainIntent(): PendingIntent = PendingIntent.getActivity(
        context, 2,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(action: String, key: Long): PendingIntent = PendingIntent.getBroadcast(
        context, (action.hashCode() * 31 + key).toInt(),
        Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(CallActionReceiver.EXTRA_KEY, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun incomingId(key: Long) = INCOMING_ID_BASE + (key % 1000).toInt()

    companion object {
        const val CH_INCOMING = "incoming_calls"
        const val CH_ONGOING = "call_timer"
        const val CH_ALERTS = "limit_alerts"
        const val ONGOING_ID = 1
        private const val WARNING_ID = 2
        private const val INCOMING_ID_BASE = 1000
        private const val ENDED_ID_BASE = 3000
    }
}
