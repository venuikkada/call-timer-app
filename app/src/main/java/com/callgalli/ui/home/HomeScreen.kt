package com.callgalli.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callgalli.R
import com.callgalli.data.NumberToday
import com.callgalli.system.BatteryHelper
import com.callgalli.system.Setup
import com.callgalli.telecom.CallEnder
import com.callgalli.telecom.InCallState
import com.callgalli.ui.AppViewModelFactory
import com.callgalli.ui.HomeViewModel
import com.callgalli.ui.components.SectionCard
import com.callgalli.ui.components.StatTile
import com.callgalli.ui.components.StatusChip
import com.callgalli.ui.components.TimerPill
import com.callgalli.ui.components.UsageBar
import com.callgalli.ui.components.WarningBannerBar
import com.callgalli.ui.components.formatNumber
import com.callgalli.ui.components.formatTalk
import com.callgalli.ui.components.resetLabel
import com.callgalli.ui.theme.LocalStatusColors
import com.callgalli.ui.theme.Spacing

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenNumber: (String) -> Unit,
    vm: HomeViewModel = viewModel(factory = AppViewModelFactory),
) {
    val context = LocalContext.current
    val summary by vm.summary.collectAsStateWithLifecycle()
    val numbers by vm.numbers.collectAsStateWithLifecycle()
    val inCall by vm.inCall.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var setupMissing by remember { mutableStateOf(false) }
    var batteryWarning by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        setupMissing = !Setup.hasCallPermissions(context)
        batteryWarning = BatteryHelper.needsAttention(context)
        onPauseOrDispose { }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.m, end = Spacing.m,
            top = contentPadding.calculateTopPadding() + Spacing.s,
            bottom = contentPadding.calculateBottomPadding() + Spacing.m,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (setupMissing) item {
            AlertCard(
                icon = Icons.Outlined.WarningAmber,
                title = stringResource(R.string.home_setup_title),
                body = stringResource(R.string.home_setup_body),
                action = stringResource(R.string.action_fix),
                onAction = onOpenSettings,
            )
        }
        if (batteryWarning) item {
            AlertCard(
                icon = Icons.Outlined.BatteryAlert,
                title = stringResource(R.string.battery_title),
                body = stringResource(R.string.battery_body),
                action = stringResource(R.string.action_open_battery),
                onAction = { BatteryHelper.openBatterySettings(context) },
            )
        }

        if (inCall.hasCall) item { ActiveNowCard(inCall, vm::hangUp) }

        item {
            SectionCard(title = stringResource(R.string.home_today)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    StatTile(summary.callsTaken.toString(), stringResource(R.string.home_calls_taken), Modifier.weight(1f))
                    StatTile(formatTalk(summary.talkMillis), stringResource(R.string.home_talk_time), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    StatTile(summary.callsBlocked.toString(), stringResource(R.string.home_calls_blocked), Modifier.weight(1f))
                    StatTile(summary.minutesSaved.toString(), stringResource(R.string.home_minutes_saved), Modifier.weight(1f))
                }
            }
        }

        item {
            Text(stringResource(R.string.home_numbers_today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.s))
        }
        if (numbers.isEmpty()) item {
            Text(stringResource(R.string.home_no_numbers), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(numbers, key = { it.number }) { row ->
            NumberRow(row, settings.rules.repeatCallMinutes, resetLabel(settings.rules.resetMinuteOfDay)) { onOpenNumber(row.number) }
        }
    }
}

@Composable
private fun ActiveNowCard(state: InCallState, onHangUp: () -> Unit) {
    val call = state.call ?: return
    val context = LocalContext.current
    SectionCard(
        title = stringResource(R.string.home_active_now),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(Icons.Outlined.PhoneInTalk, contentDescription = null)
            Text(call.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        val remaining = call.remainingMillis
        when {
            call.ringing -> Text(stringResource(R.string.incall_incoming))
            remaining != null && call.band != null ->
                TimerPill(remaining, call.band, paused = !call.timerRunning)
            else -> Text(stringResource(R.string.incall_untimed))
        }
        state.banner?.let { WarningBannerBar(it.secondsLeft, it.label) }
        if (!call.ringing && CallEnder.canEnd(context)) {
            TextButton(onClick = onHangUp) { Text(stringResource(R.string.action_end_call)) }
        }
    }
}

@Composable
private fun NumberRow(row: NumberToday, repeatMinutes: Int, reset: String, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.displayName ?: formatNumber(row.number), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.displayName != null) Text(formatNumber(row.number), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        StatusChip(row.stage, repeatMinutes, reset)
        UsageBar(row.progress, row.stage)
        Text(
            stringResource(R.string.home_number_detail, row.answeredCalls, formatTalk(row.talkMillis), row.blockedCalls),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AlertCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    val colors = LocalStatusColors.current
    SectionCard(containerColor = colors.amberContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(icon, contentDescription = null, tint = colors.onAmberContainer)
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onAmberContainer)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onAmberContainer)
        TextButton(onClick = onAction) { Text(action) }
    }
}
