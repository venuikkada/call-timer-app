package com.callgalli.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.callgalli.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android binds this service while any call exists, because CallGalli is the default
 * phone app (ROLE_DIALER). It hands every call to [CallTracker], which runs the timer
 * and calls [Call.disconnect] when the limit is reached.
 */
class CallGalliInCallService : InCallService() {

    private val tracker get() = container.callTracker

    override fun onCreate() {
        super.onCreate()
        tracker.attach(this)
        // If the phone restarted during a call, that call no longer exists: close it cleanly.
        val c = container
        // Wait a moment so calls Android hands back to us are re-attached first.
        c.appScope.launch {
            delay(ORPHAN_CHECK_DELAY_MILLIS)
            withContext(Dispatchers.Main) {
                val live = c.callTracker.liveSessionIds()
                c.appScope.launch { c.repository.closeOrphanSessions(live, c.callTracker.processStartedAt) }
            }
        }
    }

    override fun onDestroy() {
        tracker.detach(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        tracker.onCallAdded(call)
    }

    override fun onCallRemoved(call: Call) {
        tracker.onCallRemoved(call)
    }

    @Deprecated("Deprecated in API 34, still delivered on all supported versions")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        tracker.onAudioStateChanged(audioState)
    }

    private companion object {
        const val ORPHAN_CHECK_DELAY_MILLIS = 5_000L
    }
}
