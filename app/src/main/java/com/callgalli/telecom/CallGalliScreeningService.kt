package com.callgalli.telecom

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.callgalli.container
import com.callgalli.rules.CallDecision
import com.callgalli.rules.PhoneNumbers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs before an incoming call rings (ROLE_CALL_SCREENING). Numbers at Stage 3 are
 * rejected here, so the phone never rings. The call still appears in the system call
 * log, and in CallGalli's Blocked Log as "Blocked by limit".
 */
class CallGalliScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, allow())
            return
        }
        val c = container
        val raw = callDetails.handle?.schemeSpecificPart
        val number = PhoneNumbers.normalize(raw)

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
