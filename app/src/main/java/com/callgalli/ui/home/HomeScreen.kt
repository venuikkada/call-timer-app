package com.callgalli.ui.home

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhoneDisabled
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callgalli.R
import com.callgalli.data.AppSettings
import com.callgalli.data.NumberToday
import com.callgalli.data.TodaySummary
import com.callgalli.rules.TimerDisplay
import com.callgalli.rules.UntimedReason
import com.callgalli.system.BatteryHelper
import com.callgalli.system.Setup
import com.callgalli.telecom.CallEnder
import com.callgalli.telecom.InCallState
import com.callgalli.telecom.LiveCall
import com.callgalli.ui.AppViewModelFactory
import com.callgalli.ui.HomeViewModel
import com.callgalli.ui.components.Avatar
import com.callgalli.ui.components.CountdownRing
import com.callgalli.ui.components.HeroCard
import com.callgalli.ui.components.ListRow
import com.callgalli.ui.components.SectionTitle
import com.callgalli.ui.components.SoftCard
import com.callgalli.ui.components.StatCard
import com.callgalli.ui.components.StatusChip
import com.callgalli.ui.components.UsageBar
import com.callgalli.ui.components.WarningBannerBar
import com.callgalli.ui.components.formatNumber
import com.callgalli.ui.components.formatTalk
import com.callgalli.ui.components.resetLabel
import com.callgalli.ui.components.stageTint
import com.callgalli.ui.theme.LocalStatusColors
import com.callgalli.ui.theme.Spacing
import java.util.Calendar

/** One item of the "Finish setup" checklist. */
data class SetupStep(val icon: ImageVector, val title: String, val subtitle: String, val done: Boolean, val required: Boolean, val action: () -> Unit)

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

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val callPerms = stringResource(R.string.setup_call_permissions)
    val callPermsSub = stringResource(R.string.setup_call_permissions_sub)
    val overlay = stringResource(R.string.setup_overlay)
    val overlaySub = stringResource(R.string.setup_overlay_sub)
    val notif = stringResource(R.string.perm_notifications)
    val notifSub = stringResource(R.string.setup_notifications_sub)
    val battery = stringResource(R.string.battery_unrestricted)
    val batterySub = stringResource(R.string.setup_battery_sub)
    val steps = remember(refresh) {
        listOf(
            SetupStep(Icons.Outlined.Security, callPerms, callPermsSub, Setup.hasCallPermissions(context), true, onOpenSettings),
            SetupStep(Icons.Outlined.Layers, overlay, overlaySub, Setup.hasOverlay(context), false) { Setup.openOverlaySettings(context) },
            SetupStep(
                Icons.Outlined.NotificationsActive, notif, notifSub,
                Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                false,
            ) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            SetupStep(Icons.Outlined.BatteryAlert, battery, batterySub, !BatteryHelper.needsAttention(context), false) { BatteryHelper.openBatterySettings(context) },
        )
    }

    HomeContent(
        contentPadding = contentPadding,
        summary = summary,
        numbers = numbers,
        inCall = inCall,
        settings = settings,
        steps = steps,
        canEndCall = remember(refresh) { CallEnder.canEnd(context) },
        onHangUp = vm::hangUp,
        onOpenNumber = onOpenNumber,
    )
}

/** Stateless Home, so it can be previewed and screenshot-tested with sample data. */
@Composable
fun HomeContent(
    contentPadding: PaddingValues,
    summary: TodaySummary,
    numbers: List<NumberToday>,
    inCall: InCallState,
    settings: AppSettings,
    steps: List<SetupStep>,
    canEndCall: Boolean,
    onHangUp: () -> Unit,
    onOpenNumber: (String) -> Unit,
) {
    val colors = LocalStatusColors.current
    val ready = steps.filter { it.required }.all { it.done }
    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.m, end = Spacing.m,
            top = contentPadding.calculateTopPadding() + Spacing.s,
            bottom = contentPadding.calculateBottomPadding() + Spacing.l,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Hero(summary, settings, ready) }

        inCall.call?.let { call -> item { ActiveCallCard(call, inCall, canEndCall, onHangUp) } }

        if (steps.any { !it.done }) item { SetupChecklist(steps) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Icons.Outlined.Call, summary.callsTaken.toString(), stringResource(R.string.home_calls_taken), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatCard(Icons.Outlined.Schedule, formatTalk(summary.talkMillis), stringResource(R.string.home_talk_time), Color(0xFF5B8DEF), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Icons.Outlined.PhoneDisabled, summary.callsBlocked.toString(), stringResource(R.string.home_calls_blocked), colors.red, Modifier.weight(1f))
                    StatCard(Icons.Outlined.Savings, summary.minutesSaved.toString(), stringResource(R.string.home_minutes_saved), colors.green, Modifier.weight(1f))
                }
            }
        }

        item { SectionTitle(stringResource(R.string.home_numbers_today)) }
        if (numbers.isEmpty()) item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PhoneInTalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text(stringResource(R.string.home_no_numbers), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.home_no_numbers_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(numbers, key = { it.number }) { row ->
            NumberCard(row, settings.rules.repeatCallMinutes, resetLabel(settings.rules.resetMinuteOfDay)) { onOpenNumber(row.number) }
        }
    }
}

@Composable
private fun Hero(summary: TodaySummary, settings: AppSettings, ready: Boolean) {
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..16 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(greeting), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (ready) Color(0xFF9CF2B0) else Color(0xFFFFD36E)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (ready) R.string.hero_watching else R.string.hero_setup_needed),
                        style = MaterialTheme.typography.labelMedium, color = Color.White,
                    )
                }
            }
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
        Text(
            stringResource(R.string.hero_summary, summary.callsTaken, formatTalk(summary.talkMillis)),
            style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = 4.dp)) {
            HeroChip(stringResource(R.string.hero_rule_first, settings.rules.firstCallMinutes))
            HeroChip(stringResource(R.string.hero_rule_repeat, settings.rules.repeatCallMinutes))
            HeroChip(stringResource(R.string.hero_rule_reset, resetLabel(settings.rules.resetMinuteOfDay)))
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(12.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), maxLines = 1)
    }
}

@Composable
private fun SetupChecklist(steps: List<SetupStep>) {
    val done = steps.count { it.done }
    val colors = LocalStatusColors.current
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_setup_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(stringResource(R.string.home_setup_progress, done, steps.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LinearProgressIndicator(
            progress = { done.toFloat() / steps.size },
            modifier = Modifier.fillMaxWidth().heightIn(min = 6.dp).clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        steps.filter { !it.done }.forEach { step ->
            ListRow(
                icon = step.icon,
                iconTint = if (step.required) colors.red else colors.amber,
                title = step.title,
                subtitle = step.subtitle,
                onClick = step.action,
            )
        }
    }
}

@Composable
private fun ActiveCallCard(call: LiveCall, state: InCallState, canEndCall: Boolean, onHangUp: () -> Unit) {
    val colors = LocalStatusColors.current
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(colors.green))
            Text(stringResource(R.string.home_active_now), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }
        state.banner?.let { WarningBannerBar(it.secondsLeft, it.label, Modifier.clip(RoundedCornerShape(16.dp))) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val remaining = call.remainingMillis
            val limit = call.limitMillis
            when {
                call.ringing -> CountdownRing(1f, null, "•••", stringResource(R.string.incall_incoming))
                remaining != null && limit != null ->
                    CountdownRing(remaining.toFloat() / limit, call.band, TimerDisplay.mmss(remaining), stringResource(R.string.ring_left))
                else -> CountdownRing(
                    1f, null, TimerDisplay.mmss(call.elapsedMillis),
                    stringResource(if (call.exemptReason == UntimedReason.EMERGENCY || call.exemptReason == UntimedReason.ALWAYS_ALLOWED) R.string.ring_always_allowed else R.string.incall_untimed),
                )
            }
            Text(call.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (call.name != null) Text(formatNumber(call.number), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (call.exemptReason != null && remaining == null && !call.ringing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.incall_untimed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (!call.ringing && canEndCall) {
                Button(
                    onClick = onHangUp,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red, contentColor = colors.onRed),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Outlined.CallEnd, contentDescription = null)
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.action_end_call))
                }
            }
        }
    }
}

@Composable
private fun NumberCard(row: NumberToday, repeatMinutes: Int, reset: String, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(row.displayName, row.number, stageTint(row.stage))
            Column(Modifier.weight(1f)) {
                Text(row.displayName ?: formatNumber(row.number), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.home_number_detail, row.answeredCalls, formatTalk(row.talkMillis), row.blockedCalls),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UsageBar(row.progress, row.stage, Modifier.weight(1f))
            StatusChip(row.stage, repeatMinutes, reset)
        }
    }
}
