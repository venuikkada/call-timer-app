package com.callgalli.telecom

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.callgalli.container
import com.callgalli.rules.CallDecision
import com.callgalli.rules.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Optional extra (ROLE_CALL_SCREENING). Android only sends calls from numbers that are
 * NOT saved in contacts to a screening app that is not the phone app. For those, Stage 3
 * numbers are rejected here so the phone never rings. The call still appears in the system call
 * log, and in CallGalli's Blocked Log as "Blocked by limit".
 */
class CallGalliScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val c = container
        val raw = callDetails.handle?.schemeSpecificPart
        val number = PhoneNumbers.normalize(raw)
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            // Outgoing: never blocked here, but it tells the watcher which number was dialled.
            c.callMonitor.onOutgoingNumber(raw)
            respondToCall(callDetails, allow())
            return
        }

        c.appScope.launch {
            // Android waits only ~5 s for an answer. If anything is slow, let the call ring.
            val decision = runCatching { withTimeoutOrNull(3_000) { c.repository.decideIncoming(number) } }
                .onFailure { Log.e(TAG, "Screening failed, allowing call", it) }
                .getOrNull()

            if (decision == CallDecision.Reject) {
                respondToCall(
                    callDetails,
                    CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipCallLog(false)      // keep it in the phone's call log
                        .setSkipNotification(true)  // no "missed call" noise
                        .build(),
                )
                withContext(Dispatchers.Main) { c.callMonitor.onScreenedBlocked(number) }
                c.repository.recordBlocked(number, c.contacts.lookupName(raw))
            } else {
                respondToCall(callDetails, allow())
            }
        }
    }

    private fun allow() = CallResponse.Builder().build()

    private companion object {
        const val TAG = "CallScreening"
    }
}
