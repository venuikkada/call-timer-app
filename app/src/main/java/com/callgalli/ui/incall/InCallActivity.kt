package com.callgalli.ui.incall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callgalli.R
import com.callgalli.container
import com.callgalli.rules.TimerDisplay
import com.callgalli.rules.UntimedReason
import com.callgalli.telecom.CallActionReceiver
import com.callgalli.telecom.CallPhase
import com.callgalli.telecom.CallTimerService
import com.callgalli.telecom.CallTracker
import com.callgalli.telecom.InCallState
import com.callgalli.telecom.LiveCall
import com.callgalli.ui.components.TimerPill
import com.callgalli.ui.components.WarningBannerBar
import com.callgalli.ui.components.formatNumber
import com.callgalli.ui.dialer.Keypad
import com.callgalli.ui.theme.CallGalliTheme
import com.callgalli.ui.theme.LocalStatusColors
import com.callgalli.ui.theme.Spacing

/** Incoming / ongoing call screen. Shown over the lock screen. */
class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tracker = container.callTracker
        // We are in the foreground now, so starting the timer service is always allowed.
        if (tracker.state.value.hasCalls) CallTimerService.start(this)
        handleAnswer(intent)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val state by tracker.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.hasCalls) { if (!state.hasCalls) finish() }
            CallGalliTheme(settings.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    InCallScreen(state, tracker)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleAnswer(intent)
    }

    /** "Answer" tapped in the incoming-call notification. */
    private fun handleAnswer(intent: android.content.Intent?) {
        if (intent?.action == CallActionReceiver.ACTION_ANSWER) {
            container.callTracker.answer(intent.getLongExtra(CallActionReceiver.EXTRA_KEY, -1))
            intent.action = null
        }
    }
}

@Composable
private fun InCallScreen(state: InCallState, tracker: CallTracker) {
    val call = state.primary ?: return
    var showKeypad by remember { mutableStateOf(false) }
    val others = state.calls.filter { it.key != call.key }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // Full-width warning banner at 60 s / 30 s.
        AnimatedVisibility(visible = state.banner != null) {
            state.banner?.let { WarningBannerBar(it.secondsLeft, it.label) }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            TimerArea(call)
            Spacer(Modifier.height(Spacing.s))
            Text(call.label, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (call.name != null) Text(formatNumber(call.number), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(phaseText(call), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            others.forEach { other ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                        Text(other.label + " · " + phaseText(other), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (other.phase == CallPhase.HOLDING) TextButton(onClick = { tracker.toggleHold(other.key) }) { Text(stringResource(R.string.incall_swap)) }
                    }
                }
            }

            if (call.needsOutgoingWarning) OutgoingWarning(call, tracker)

            if (showKeypad && call.phase == CallPhase.ACTIVE) {
                Spacer(Modifier.weight(1f))
                Keypad(onKey = { tracker.playDtmf(call.key, it.first()) })
            }
        }

        if (call.phase == CallPhase.RINGING) {
            RingingButtons(call, tracker)
        } else {
            InCallButtons(call, state, tracker, showKeypad) { showKeypad = !showKeypad }
        }
    }
}

@Composable
private fun TimerArea(call: LiveCall) {
    val remaining = call.remainingMillis
    when {
        call.exemptReason == UntimedReason.EMERGENCY || call.exemptReason == UntimedReason.ALWAYS_ALLOWED -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(if (call.exemptReason == UntimedReason.EMERGENCY) R.string.incall_emergency else R.string.incall_always_allowed),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        remaining != null && call.band != null -> TimerPill(remaining, call.band, paused = !call.timerRunning)
        call.connected -> Text(stringResource(R.string.incall_untimed), style = MaterialTheme.typography.titleMedium)
        else -> Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun phaseText(call: LiveCall): String = when (call.phase) {
    CallPhase.RINGING -> stringResource(R.string.incall_incoming)
    CallPhase.DIALING -> stringResource(R.string.incall_calling)
    CallPhase.HOLDING -> stringResource(R.string.incall_on_hold)
    CallPhase.ENDING -> stringResource(R.string.incall_ending)
    CallPhase.ACTIVE -> TimerDisplay.mmss(call.elapsedMillis)
}

@Composable
private fun OutgoingWarning(call: LiveCall, tracker: CallTracker) {
    val colors = LocalStatusColors.current
    Surface(color = colors.redContainer, contentColor = colors.onRedContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.dialer_blocked_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.dialer_blocked_body, call.label), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(onClick = { tracker.hangUp(call.key) }) { Text(stringResource(R.string.action_hang_up)) }
                TextButton(onClick = { tracker.confirmOutgoing(call.key) }) { Text(stringResource(R.string.dialer_call_anyway)) }
            }
        }
    }
}

@Composable
private fun RingingButtons(call: LiveCall, tracker: CallTracker) {
    val c = LocalStatusColors.current
    Row(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundAction(Icons.Outlined.CallEnd, stringResource(R.string.action_decline), c.red, c.onRed) { tracker.decline(call.key) }
        RoundAction(Icons.Outlined.Call, stringResource(R.string.action_answer), c.green, c.onGreen) { tracker.answer(call.key) }
    }
}

@Composable
private fun InCallButtons(call: LiveCall, state: InCallState, tracker: CallTracker, keypadOn: Boolean, onToggleKeypad: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToggleAction(if (state.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, stringResource(R.string.action_mute), state.muted) { tracker.setMuted(!state.muted) }
            ToggleAction(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.action_speaker), state.speakerOn) { tracker.setSpeaker(!state.speakerOn) }
            ToggleAction(Icons.Outlined.Dialpad, stringResource(R.string.action_keypad), keypadOn, onToggleKeypad)
            ToggleAction(
                if (call.phase == CallPhase.HOLDING) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                stringResource(if (call.phase == CallPhase.HOLDING) R.string.action_resume else R.string.action_hold),
                call.phase == CallPhase.HOLDING,
            ) { tracker.toggleHold(call.key) }
        }
        val c = LocalStatusColors.current
        RoundAction(Icons.Outlined.CallEnd, stringResource(R.string.action_end_call), c.red, c.onRed) { tracker.hangUp(call.key) }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick, modifier = Modifier.size(76.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = bg, contentColor = fg)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(34.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.s))
    }
}

@Composable
private fun ToggleAction(icon: ImageVector, label: String, checked: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconToggleButton(checked = checked, onCheckedChange = { onClick() }, modifier = Modifier.size(60.dp)) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.xs))
    }
}
