package com.callgalli.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callgalli.R
import com.callgalli.rules.Stage
import com.callgalli.rules.TimerBand
import com.callgalli.rules.TimerDisplay
import com.callgalli.ui.theme.LocalStatusColors
import com.callgalli.ui.theme.Spacing
import java.util.Date

/** Rounded card used on every screen. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (title != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (icon != null) IconBadge(icon, MaterialTheme.colorScheme.primary, 36.dp)
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            content()
        }
    }
}

/** "Fresh" (green), "Limited to 5 min" (amber), "Blocked until midnight" (red). */
@Composable
fun StatusChip(stage: Stage, repeatMinutes: Int, resetLabel: String, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    val (bg, fg, text) = when (stage) {
        Stage.FRESH -> Triple(colors.greenContainer, colors.onGreenContainer, stringResource(R.string.status_fresh))
        Stage.LIMITED -> Triple(colors.amberContainer, colors.onAmberContainer, stringResource(R.string.status_limited, repeatMinutes))
        Stage.BLOCKED -> Triple(colors.redContainer, colors.onRedContainer, stringResource(R.string.status_blocked, resetLabel))
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun stageColor(stage: Stage): Color {
    val c = LocalStatusColors.current
    return when (stage) {
        Stage.FRESH -> c.green
        Stage.LIMITED -> c.amber
        Stage.BLOCKED -> c.red
    }
}

@Composable
fun UsageBar(progress: Float, stage: Stage, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = stageColor(stage),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** Small floating pill: "07:42 left", coloured green / amber / red. */
@Composable
fun TimerPill(remainingMillis: Long, band: TimerBand, paused: Boolean, modifier: Modifier = Modifier) {
    val c = LocalStatusColors.current
    val (bg, fg) = when (band) {
        TimerBand.GREEN -> c.green to c.onGreen
        TimerBand.AMBER -> c.amber to c.onAmber
        TimerBand.RED -> c.red to c.onRed
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50), shadowElevation = 4.dp, modifier = modifier) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (paused) stringResource(R.string.pill_paused, TimerDisplay.mmss(remainingMillis))
                else stringResource(R.string.pill_left, TimerDisplay.mmss(remainingMillis)),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            )
        }
    }
}

/** Full-width banner for the 60 s / 30 s warnings. */
@Composable
fun WarningBannerBar(secondsLeft: Int, label: String, modifier: Modifier = Modifier) {
    val c = LocalStatusColors.current
    val red = secondsLeft <= 30
    Surface(
        color = if (red) c.red else c.amber,
        contentColor = if (red) c.onRed else c.onAmber,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = 12.dp)) {
            Text(stringResource(R.string.warning_title, secondsLeft), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.warning_body, label), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Big, thumb-friendly primary button. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, danger: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = if (danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
        else ButtonDefaults.buttonColors(),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(Spacing.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BigOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(Spacing.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmText, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.xl, horizontal = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Formatting helpers -------------------------------------------------------------------

/** "98480 22338" for 10-digit Indian numbers, otherwise the digits as they are. */
fun formatNumber(normalized: String): String =
    if (normalized.length == 10) normalized.substring(0, 5) + " " + normalized.substring(5) else normalized

@Composable
fun formatTalk(millis: Long): String {
    val minutes = ((millis + 30_000) / 60_000).toInt()
    return if (minutes >= 60) stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    else pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
}

@Composable
fun formatClock(epochMillis: Long): String = DateFormat.getTimeFormat(LocalContext.current).format(Date(epochMillis))

/** "midnight" or e.g. "5:00 AM" for the reset time. */
@Composable
fun resetLabel(resetMinuteOfDay: Int): String {
    if (resetMinuteOfDay == 0) return stringResource(R.string.reset_midnight)
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, resetMinuteOfDay / 60)
        set(java.util.Calendar.MINUTE, resetMinuteOfDay % 60)
    }
    return DateFormat.getTimeFormat(LocalContext.current).format(cal.time)
}
