package com.callgalli.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callgalli.container

/** "End call" / "Hang up" buttons in notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_HANG_UP) context.container.callMonitor.hangUp()
    }

    companion object {
        const val ACTION_HANG_UP = "com.callgalli.HANG_UP"
    }
}

/**
 * Phone-state and outgoing-call broadcasts from the manifest. Android delivers these even
 * if the watcher service was stopped, and we use them to bring it back.
 * The state machine ignores duplicates, so receiving an event here and in the service is fine.
 */
class PhoneEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CallMonitorService.dispatchPhoneIntent(context, intent)
        CallMonitorService.start(context)
    }
}

/** After a restart: start watching again and close any call cut off by the reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (context.container.settings.current.onboardingDone) CallMonitorService.start(context)
    }
}
