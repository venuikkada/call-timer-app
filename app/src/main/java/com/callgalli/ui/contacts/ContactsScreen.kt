package com.callgalli.ui.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callgalli.R
import com.callgalli.data.SettingsStore
import com.callgalli.ui.AppViewModelFactory
import com.callgalli.ui.ContactRow
import com.callgalli.ui.ContactsViewModel
import com.callgalli.ui.components.Avatar
import com.callgalli.ui.components.BigButton
import com.callgalli.ui.components.stageTint
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.callgalli.ui.components.ConfirmDialog
import com.callgalli.ui.components.EmptyState
import com.callgalli.ui.components.StatusChip
import com.callgalli.ui.components.formatNumber
import com.callgalli.ui.components.resetLabel
import com.callgalli.ui.theme.Spacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    contentPadding: PaddingValues,
    settingsStore: SettingsStore,
    vm: ContactsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    var resetTarget by remember { mutableStateOf<ContactRow?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.reload() }
    LaunchedEffect(Unit) { vm.reload() }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            placeholder = { Text(stringResource(R.string.contacts_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Text(
            stringResource(R.string.contacts_long_press_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.m),
        )

        if (!hasPermission) {
            EmptyState(Icons.Outlined.Contacts, stringResource(R.string.contacts_permission_title), stringResource(R.string.contacts_permission_body))
            BigButton(
                stringResource(R.string.action_allow_contacts),
                onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                modifier = Modifier.padding(horizontal = Spacing.m),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Spacing.m),
            modifier = Modifier.weight(1f),
        ) {
            if (hasPermission && rows.isEmpty()) item {
                EmptyState(Icons.Outlined.Search, stringResource(R.string.contacts_empty_title), stringResource(R.string.contacts_empty_body))
            }
            items(rows, key = { it.number }) { row ->
                val resetText = stringResource(R.string.contacts_reset)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .combinedClickable(onClick = {}, onLongClick = { resetTarget = row })
                        .semantics { onLongClick(label = resetText) { resetTarget = row; true } }
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(row.name, row.number, if (row.alwaysAllowed) MaterialTheme.colorScheme.primary else stageTint(row.stage))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(row.name ?: formatNumber(row.number), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (row.name != null) Text(formatNumber(row.number), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (row.alwaysAllowed) {
                            Text(stringResource(R.string.status_always_allowed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        } else {
                            StatusChip(row.stage, settings.rules.repeatCallMinutes, resetLabel(settings.rules.resetMinuteOfDay))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(checked = row.alwaysAllowed, onCheckedChange = { vm.setAlwaysAllowed(row, it) })
                        Text(stringResource(R.string.contacts_always_allow), style = MaterialTheme.typography.labelSmall)
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = Spacing.m))
            }
        }
    }

    resetTarget?.let { row ->
        ConfirmDialog(
            title = stringResource(R.string.contacts_reset_title),
            message = stringResource(R.string.contacts_reset_body, row.name ?: formatNumber(row.number)),
            confirmText = stringResource(R.string.contacts_reset),
            onConfirm = { vm.reset(row) },
            onDismiss = { resetTarget = null },
        )
    }
}
