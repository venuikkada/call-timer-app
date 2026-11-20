package com.callgalli.ui.allowed

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callgalli.R
import com.callgalli.data.RuleEntity
import com.callgalli.rules.EmergencyNumbers
import com.callgalli.ui.AllowedViewModel
import com.callgalli.ui.AppViewModelFactory
import com.callgalli.ui.components.BigButton
import com.callgalli.ui.components.BigOutlinedButton
import com.callgalli.ui.components.ConfirmDialog
import com.callgalli.ui.components.SectionCard
import com.callgalli.ui.components.formatNumber
import com.callgalli.ui.theme.Spacing

/** Opens the system contact picker for a single phone number. Needs no contacts permission. */
private class PickPhone : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: android.content.Context, input: Unit) =
        Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}

@Composable
fun AlwaysAllowedScreen(
    contentPadding: PaddingValues,
    vm: AllowedViewModel = viewModel(factory = AppViewModelFactory),
) {
    val allowed by vm.allowed.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<RuleEntity?>(null) }
    val pickContact = rememberLauncherForActivityResult(PickPhone()) { uri ->
        uri?.let { vm.readPicked(it) }?.let { vm.add(it.name, it.rawNumber) }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.m, end = Spacing.m,
            top = contentPadding.calculateTopPadding() + Spacing.s,
            bottom = contentPadding.calculateBottomPadding() + Spacing.m,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item {
            Text(stringResource(R.string.allowed_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard(title = stringResource(R.string.allowed_emergency_title)) {
                EmergencyNumbers.ALL.forEach { number ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.allowed_locked), tint = MaterialTheme.colorScheme.primary)
                        Text(number, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.m).weight(0.3f))
                        Text(emergencyLabel(number), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.7f))
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.allowed_your_list), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.s))
        }
        if (allowed.isEmpty()) item {
            Text(stringResource(R.string.allowed_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(allowed, key = { it.number }) { rule ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                        Text(rule.displayName ?: formatNumber(rule.number), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (rule.displayName != null) Text(formatNumber(rule.number), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { removeTarget = rule }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_remove))
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                BigButton(stringResource(R.string.allowed_pick_contact), onClick = { pickContact.launch(Unit) }, icon = Icons.Outlined.Contacts)
                BigOutlinedButton(stringResource(R.string.allowed_add_number), onClick = { showAdd = true }, icon = Icons.Outlined.PersonAdd)
            }
        }
    }

    if (showAdd) AddNumberDialog(onAdd = { name, number -> vm.add(name, number) }, onDismiss = { showAdd = false })

    removeTarget?.let { rule ->
        ConfirmDialog(
            title = stringResource(R.string.allowed_remove_title),
            message = stringResource(R.string.allowed_remove_body, rule.displayName ?: formatNumber(rule.number)),
            confirmText = stringResource(R.string.action_remove),
            onConfirm = { vm.remove(rule) },
            onDismiss = { removeTarget = null },
            danger = true,
        )
    }
}

@Composable
private fun AddNumberDialog(onAdd: (String, String) -> Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.allowed_add_number)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true)
                OutlinedTextField(
                    number, { number = it; error = false },
                    label = { Text(stringResource(R.string.field_number)) },
                    singleLine = true,
                    isError = error,
                    supportingText = { if (error) Text(stringResource(R.string.error_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onAdd(name, number)) onDismiss() else error = true }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun emergencyLabel(number: String): String = stringResource(
    when (number) {
        "112" -> R.string.emergency_112
        "100" -> R.string.emergency_100
        "101" -> R.string.emergency_101
        "102" -> R.string.emergency_102
        else -> R.string.emergency_108
    }
)
