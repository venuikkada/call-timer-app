package com.callgalli.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callgalli.container
import kotlinx.coroutines.launch

/** Answer / Decline / End call buttons in notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getLongExtra(EXTRA_KEY, -1)
        val tracker = context.container.callTracker
        when (intent.action) {
            ACTION_ANSWER -> tracker.answer(key)
            ACTION_DECLINE -> tracker.decline(key)
            ACTION_HANG_UP -> tracker.hangUp(key)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.callgalli.ANSWER"
        const val ACTION_DECLINE = "com.callgalli.DECLINE"
        const val ACTION_HANG_UP = "com.callgalli.HANG_UP"
        const val EXTRA_KEY = "key"
    }
}

/**
 * After a restart no call can still be going on. Any call that was open when the
 * phone switched off is closed, counting talk time up to its last saved heartbeat.
 * Stages are stored per day in the database, so limits survive the reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                c.repository.closeOrphanSessions(c.callTracker.liveSessionIds(), c.callTracker.processStartedAt)
            } finally {
                pending.finish()
            }
        }
    }
}
