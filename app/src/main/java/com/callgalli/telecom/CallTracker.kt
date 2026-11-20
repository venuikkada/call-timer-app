package com.callgalli.telecom

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.callgalli.data.CallOutcome
import com.callgalli.data.CallRepository
import com.callgalli.data.ContactsRepository
import com.callgalli.data.SettingsStore
import com.callgalli.rules.CallClock
import com.callgalli.rules.CallDecision
import com.callgalli.rules.Direction
import com.callgalli.rules.PhoneNumbers
import com.callgalli.rules.TimerBand
import com.callgalli.rules.TimerDisplay
import com.callgalli.rules.UntimedReason
import com.callgalli.rules.WarningTracker
import com.callgalli.system.Haptics
import com.callgalli.ui.incall.InCallActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Call.getState() is deprecated from Android 12; Details.getState() only exists from Android 12. */
private val Call.currentState: Int
    get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) details.state
    else @Suppress("DEPRECATION") state

enum class CallPhase { RINGING, DIALING, ACTIVE, HOLDING, ENDING }

/** What the screens show for one call. */
data class LiveCall(
    val key: Long,
    val number: String,
    val rawNumber: String,
    val name: String?,
    val direction: Direction,
    val phase: CallPhase,
    val connected: Boolean,
    val timerRunning: Boolean,
    val elapsedMillis: Long,
    val limitMillis: Long?,
    val remainingMillis: Long?,
    val band: TimerBand?,
    val exemptReason: UntimedReason?,
    val needsOutgoingWarning: Boolean,
    val inConference: Boolean,
) {
    val label: String get() = name ?: rawNumber.ifBlank { number }
}

data class WarningBanner(val callKey: Long, val label: String, val secondsLeft: Int)

data class InCallState(
    val calls: List<LiveCall> = emptyList(),
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
    val banner: WarningBanner? = null,
) {
    /** The call the in-call screen focuses on: ringing first (needs an answer), then active. */
    val primary: LiveCall?
        get() = calls.firstOrNull { it.phase == CallPhase.RINGING }
            ?: calls.firstOrNull { it.phase == CallPhase.ACTIVE }
            ?: calls.firstOrNull { it.phase == CallPhase.DIALING }
            ?: calls.firstOrNull()

    val hasCalls: Boolean get() = calls.isNotEmpty()
}

/**
 * Heart of the in-call logic. Receives calls from [CallGalliInCallService], asks the
 * rules engine for a limit when a call is picked up, runs the clock (paused on hold),
 * fires the 60 s / 30 s warnings and calls [Call.disconnect] at zero.
 *
 * Everything runs on the main thread; database work is suspended, never blocking.
 */
class CallTracker(
    context: Context,
    private val repository: CallRepository,
    private val contacts: ContactsRepository,
    private val settings: SettingsStore,
    private val notifications: CallNotifications,
) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Sessions started before this moment belong to an earlier process (e.g. before a reboot). */
    val processStartedAt: Long = System.currentTimeMillis()

    private var nextKey = 1L
    private val tracked = LinkedHashMap<Call, Tracked>()
    private val conferences = mutableSetOf<Call>()
    private var service: InCallService? = null
    private var tickJob: Job? = null
    private var bannerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Numbers the user already confirmed on the dial pad (Stage 3 warning). */
    private val preConfirmed = mutableSetOf<String>()

    private val _state = MutableStateFlow(InCallState())
    val state: StateFlow<InCallState> = _state.asStateFlow()

    private class Tracked(val key: Long, val call: Call, val number: String, val rawNumber: String, val direction: Direction) {
        var name: String? = null
        val session = CompletableDeferred<Long?>()
        var connected = false
        var connectedAtElapsed = 0L
        var clock: CallClock? = null
        var warnings: WarningTracker? = null
        var exemptReason: UntimedReason? = null
        var endedByLimit = false
        var needsOutgoingWarning = false
        var rejected = false
        var finished = false
        var lastHeartbeatElapsed = 0L
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = onCallState(call, state)
        override fun onParentChanged(call: Call, parent: Call?) = refresh()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = refresh()
        override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
    }

    // ---- Wiring from the InCallService ----------------------------------------------------

    fun attach(inCallService: InCallService) {
        service = inCallService
    }

    fun detach(inCallService: InCallService) {
        if (service === inCallService) service = null
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun liveSessionIds(): Set<Long> =
        tracked.values.mapNotNull { t -> if (t.session.isCompleted) t.session.getCompleted() else null }.toSet()

    fun preConfirmOutgoing(number: String) {
        preConfirmed += number
    }

    fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        if (call.details.hasProperty(Call.Details.PROPERTY_CONFERENCE)) {
            // A conference counts as one call: we keep timing its first participant (see isRunning).
            conferences += call
            refresh()
            return
        }
        val raw = call.details.handle?.schemeSpecificPart.orEmpty()
        val direction = if (call.details.callDirection == Call.Details.DIRECTION_OUTGOING) Direction.OUTGOING else Direction.INCOMING
        val t = Tracked(nextKey++, call, PhoneNumbers.normalize(raw), raw, direction)
        tracked[call] = t
        startTicking()
        CallTimerService.start(app)

        val state = call.currentState
        if (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING) {
            // The call was already going on: Android restarted CallGalli mid-call (app swiped
            // away or killed). Continue the saved timer instead of starting a new one.
            t.connected = true
            t.connectedAtElapsed = SystemClock.elapsedRealtime()
            scope.launch { resume(t, raw, direction) }
            refresh()
            return
        }

        scope.launch {
            t.name = withContext(Dispatchers.IO) { contacts.lookupName(raw) }
            if (direction == Direction.INCOMING && call.currentState == Call.STATE_RINGING) {
                // Normally call screening already rejected Stage 3 numbers before they rang.
                // This is the safety net if screening is not enabled.
                if (repository.decideIncoming(t.number) == CallDecision.Reject) {
                    t.rejected = true
                    t.session.complete(null)
                    call.reject(false, null)
                    repository.recordBlocked(t.number, t.name)
                    return@launch
                }
            }
            if (direction == Direction.OUTGOING) {
                val decision = repository.decideOutgoing(t.number)
                t.needsOutgoingWarning = decision is CallDecision.ConfirmNeeded && !preConfirmed.remove(t.number)
            }
            t.session.complete(repository.startSession(t.number, t.name, direction))
            refresh()
            showUiFor(t)
        }
        onCallState(call, call.currentState)
    }

    fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        conferences -= call
        tracked[call]?.let { finish(it) }
        tracked.remove(call)
        refresh()
        if (tracked.isEmpty()) onAllCallsGone()
    }

    fun onAudioStateChanged(audioState: CallAudioState?) {
        audioState ?: return
        _state.value = _state.value.copy(muted = audioState.isMuted, speakerOn = audioState.route == CallAudioState.ROUTE_SPEAKER)
    }

    // ---- Call state -----------------------------------------------------------------------

    private fun onCallState(call: Call, state: Int) {
        val t = tracked[call]
        if (t == null) {
            refresh(); return
        }
        when (state) {
            Call.STATE_ACTIVE -> if (!t.connected) onConnected(t)
            Call.STATE_DISCONNECTED -> finish(t)
        }
        if (state != Call.STATE_RINGING) notifications.cancelIncoming(t.key)
        refresh()
    }

    private fun onConnected(t: Tracked) {
        t.connected = true
        t.connectedAtElapsed = SystemClock.elapsedRealtime()
        scope.launch { startClock(t) }
    }

    /** Picked up: use up a stage, get the limit, start counting from the moment it connected. */
    private suspend fun startClock(t: Tracked) {
        val sessionId = t.session.await() ?: return
        val result = repository.onConnected(sessionId, t.number, t.name)
        t.exemptReason = result.reason
        installClock(t, CallClock(result.limitMillis).also { it.start(t.connectedAtElapsed) })
    }

    private fun installClock(t: Tracked, clock: CallClock) {
        t.clock = clock
        if (clock.limitMillis != null) {
            t.warnings = WarningTracker(settings.current.rules.warningSeconds)
            acquireWakeLock()
        }
        t.lastHeartbeatElapsed = SystemClock.elapsedRealtime()
        refresh()
    }

    /** Re-attach to a call that was already active when this process started. */
    private suspend fun resume(t: Tracked, raw: String, direction: Direction) {
        t.name = withContext(Dispatchers.IO) { contacts.lookupName(raw) }
        val saved = repository.findResumableSession(t.number)
        if (saved == null) {
            // No record of it (e.g. data was cleared): treat it as a call that just connected.
            t.session.complete(repository.startSession(t.number, t.name, direction))
            startClock(t)
            return
        }
        t.session.complete(saved.id)
        // The call kept going while CallGalli was not running, so that time counts too.
        val missed = (System.currentTimeMillis() - saved.lastHeartbeatAt).coerceAtLeast(0)
        t.exemptReason = if (saved.limitMillis == null) repository.exemptReason(t.number) else null
        installClock(t, CallClock(saved.limitMillis, saved.activeMillis + missed).also { it.start(t.connectedAtElapsed) })
    }

    private fun finish(t: Tracked) {
        if (t.finished) return
        t.finished = true
        notifications.cancelIncoming(t.key)
        val now = SystemClock.elapsedRealtime()
        t.clock?.pause(now)
        val talked = t.clock?.elapsed(now) ?: 0L
        val outcome = when {
            t.endedByLimit -> CallOutcome.ENDED_BY_LIMIT
            t.connected -> CallOutcome.ENDED
            else -> CallOutcome.NOT_CONNECTED
        }
        if (t.endedByLimit) {
            val minutes = ((t.clock?.limitMillis ?: 0L) / 60_000L).toInt()
            notifications.showEndedByLimit(t.key, t.name ?: t.rawNumber.ifBlank { t.number }, minutes)
        }
        scope.launch {
            val sessionId = t.session.await() ?: return@launch
            repository.endSession(sessionId, talked, outcome)
        }
    }

    private fun onAllCallsGone() {
        stopTicking()
        releaseWakeLock()
        bannerJob?.cancel()
        _state.value = InCallState()
        CallTimerService.stop(app)
    }

    // ---- Ticking --------------------------------------------------------------------------

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MILLIS)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        for (t in tracked.values.toList()) {
            val clock = t.clock ?: continue
            if (t.finished) continue
            if (isRunning(t)) clock.resume(now) else clock.pause(now)

            val remaining = clock.remaining(now)
            if (remaining != null) {
                t.warnings?.check(remaining)?.let { if (remaining > 0) warn(t, it) }
                if (clock.isExpired(now) && !t.endedByLimit) {
                    t.endedByLimit = true
                    Log.i(TAG, "Limit reached for call ${t.key}, disconnecting")
                    (t.call.parent ?: t.call).disconnect()
                }
            }
            if (now - t.lastHeartbeatElapsed >= HEARTBEAT_MILLIS) {
                t.lastHeartbeatElapsed = now
                val elapsed = clock.elapsed(now)
                scope.launch { t.session.await()?.let { repository.heartbeat(it, elapsed) } }
            }
        }
        refresh()
    }

    /**
     * Time runs only while this call is ACTIVE and not part of a held conference.
     * In a conference only the participant that connected first is timed, so the
     * conference counts as one call. Call waiting: the call on hold is paused.
     */
    private fun isRunning(t: Tracked): Boolean {
        if (t.call.currentState != Call.STATE_ACTIVE) return false
        val parent = t.call.parent ?: return true
        if (parent.currentState == Call.STATE_HOLDING) return false
        val lead = tracked.values.filter { it.call.parent == parent && it.connected }.minByOrNull { it.connectedAtElapsed }
        return lead === t
    }

    private fun warn(t: Tracked, secondsLeft: Int) {
        val label = t.name ?: t.rawNumber.ifBlank { t.number }
        Haptics.shortWarning(app)
        notifications.showWarning(t.key, label, secondsLeft)
        bannerJob?.cancel()
        _state.value = _state.value.copy(banner = WarningBanner(t.key, label, secondsLeft))
        bannerJob = scope.launch {
            delay(BANNER_MILLIS)
            _state.value = _state.value.copy(banner = null)
        }
    }

    private fun refresh() {
        val now = SystemClock.elapsedRealtime()
        val calls = tracked.values.filter { !it.rejected }.map { t ->
            val clock = t.clock
            val remaining = clock?.remaining(now)
            LiveCall(
                key = t.key,
                number = t.number,
                rawNumber = t.rawNumber,
                name = t.name,
                direction = t.direction,
                phase = phaseOf(t.call),
                connected = t.connected,
                timerRunning = clock?.isRunning == true,
                elapsedMillis = clock?.elapsed(now) ?: 0L,
                limitMillis = clock?.limitMillis,
                remainingMillis = remaining,
                band = remaining?.let { TimerDisplay.band(it) },
                exemptReason = t.exemptReason,
                needsOutgoingWarning = t.needsOutgoingWarning,
                inConference = t.call.parent != null,
            )
        }
        _state.value = _state.value.copy(calls = calls)
    }

    private fun phaseOf(call: Call): CallPhase = when (call.currentState) {
        Call.STATE_RINGING, Call.STATE_NEW -> CallPhase.RINGING
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL, Call.STATE_SELECT_PHONE_ACCOUNT -> CallPhase.DIALING
        Call.STATE_ACTIVE -> if (call.parent?.currentState == Call.STATE_HOLDING) CallPhase.HOLDING else CallPhase.ACTIVE
        Call.STATE_HOLDING -> CallPhase.HOLDING
        else -> CallPhase.ENDING
    }

    // ---- UI -------------------------------------------------------------------------------

    private fun showUiFor(t: Tracked) {
        val live = _state.value.calls.firstOrNull { it.key == t.key } ?: return
        if (live.phase == CallPhase.RINGING && notifications.canPost()) {
            // Full-screen on the lock screen, heads-up while the phone is in use.
            notifications.showIncoming(live)
        } else {
            openInCallScreen()
        }
    }

    fun openInCallScreen() {
        runCatching {
            app.startActivity(Intent(app, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "Could not open in-call screen", it) }
    }

    // ---- User actions ---------------------------------------------------------------------

    private fun find(key: Long): Tracked? = tracked.values.firstOrNull { it.key == key }

    fun answer(key: Long) {
        find(key)?.call?.answer(VideoProfile.STATE_AUDIO_ONLY)
        notifications.cancelIncoming(key)
    }

    fun decline(key: Long) {
        find(key)?.call?.reject(false, null)
        notifications.cancelIncoming(key)
    }

    fun hangUp(key: Long) {
        val call = find(key)?.call ?: return
        if (call.currentState == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
    }

    fun toggleHold(key: Long) {
        val call = find(key)?.call ?: return
        val target = call.parent ?: call
        if (target.currentState == Call.STATE_HOLDING) target.unhold() else target.hold()
    }

    /** User saw the Stage 3 warning on an outgoing call and chose to continue. */
    fun confirmOutgoing(key: Long) {
        find(key)?.needsOutgoingWarning = false
        refresh()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    }

    fun playDtmf(key: Long, digit: Char) {
        val call = find(key)?.call ?: return
        call.playDtmfTone(digit)
        scope.launch {
            delay(150)
            call.stopDtmfTone()
        }
    }

    // ---- Keep the CPU awake while a timed call runs (screen off) --------------------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = app.getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CallGalli:callTimer").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private companion object {
        const val TAG = "CallTracker"
        const val TICK_MILLIS = 500L
        const val HEARTBEAT_MILLIS = 5_000L
        const val BANNER_MILLIS = 6_000L
        const val MAX_WAKE_LOCK_MILLIS = 3L * 60 * 60 * 1000
    }
}
