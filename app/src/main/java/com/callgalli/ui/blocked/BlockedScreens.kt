package com.callgalli.ui.blocked

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PhoneDisabled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callgalli.R
import com.callgalli.data.CallOutcome
import com.callgalli.ui.AppViewModelFactory
import com.callgalli.ui.BlockedViewModel
import com.callgalli.ui.components.EmptyState
import com.callgalli.ui.components.SectionCard
import com.callgalli.ui.components.formatClock
import com.callgalli.ui.components.formatNumber
import com.callgalli.ui.components.formatTalk
import com.callgalli.ui.theme.LocalStatusColors
import com.callgalli.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun BlockedLogScreen(
    contentPadding: PaddingValues,
    onOpenNumber: (String) -> Unit,
    vm: BlockedViewModel = viewModel(factory = AppViewModelFactory),
) {
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.m, end = Spacing.m,
            top = contentPadding.calculateTopPadding() + Spacing.s,
            bottom = contentPadding.calculateBottomPadding() + Spacing.m,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item {
            Text(stringResource(R.string.blocked_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (blocked.isEmpty()) item {
            EmptyState(Icons.Outlined.PhoneDisabled, stringResource(R.string.blocked_empty_title), stringResource(R.string.blocked_empty_body))
        }
        items(blocked, key = { it.number }) { row ->
            SectionCard(modifier = Modifier.clickable { onOpenNumber(row.number) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.displayName ?: formatNumber(row.number), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (row.displayName != null) Text(formatNumber(row.number), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            pluralStringResource(R.plurals.blocked_count, row.count, row.count) + " · " +
                                stringResource(R.string.blocked_last_at, formatClock(row.lastAt)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalStatusColors.current.red,
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.blocked_see_history))
                }
            }
        }
    }
}

/** 7 days of history for one number. */
@Composable
fun NumberHistoryScreen(
    number: String,
    contentPadding: PaddingValues,
    vm: BlockedViewModel = viewModel(factory = AppViewModelFactory),
) {
    val historyFlow = remember(number) { vm.history(number) }
    val sessionsFlow = remember(number) { vm.sessions(number) }
    val days by historyFlow.collectAsStateWithLifecycle(emptyList())
    val sessions by sessionsFlow.collectAsStateWithLifecycle(emptyList())
    val name = days.firstNotNullOfOrNull { it.displayName } ?: sessions.firstNotNullOfOrNull { it.displayName }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.m, end = Spacing.m,
            top = contentPadding.calculateTopPadding() + Spacing.s,
            bottom = contentPadding.calculateBottomPadding() + Spacing.m,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item {
            Text(name ?: formatNumber(number), style = MaterialTheme.typography.headlineSmall)
            if (name != null) Text(formatNumber(number), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Text(stringResource(R.string.history_last_7_days), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.s)) }
        items(days, key = { it.dayKey }) { day ->
            SectionCard {
                Text(LocalDate.parse(day.dayKey).format(dateFormat), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.history_day_line, day.answeredCalls, formatTalk(day.talkMillis), day.blockedCalls),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (day.blockedCalls > 0) LocalStatusColors.current.red else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (sessions.isNotEmpty()) {
            item { Text(stringResource(R.string.history_calls), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.s)) }
            items(sessions, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        LocalDate.parse(s.dayKey).format(dateFormat) + " · " + formatClock(s.startedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(outcomeLabel(s.outcome, s.activeMillis), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun outcomeLabel(outcome: String, activeMillis: Long): String = when (runCatching { CallOutcome.valueOf(outcome) }.getOrNull()) {
    CallOutcome.ENDED_BY_LIMIT -> stringResource(R.string.outcome_ended_by_limit)
    CallOutcome.BLOCKED_BY_LIMIT -> stringResource(R.string.outcome_blocked_by_limit)
    CallOutcome.NOT_CONNECTED -> stringResource(R.string.outcome_not_connected)
    CallOutcome.INTERRUPTED -> stringResource(R.string.outcome_interrupted)
    CallOutcome.IN_PROGRESS -> stringResource(R.string.outcome_in_progress)
    CallOutcome.ENDED, null -> formatTalk(activeMillis)
}
