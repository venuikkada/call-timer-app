package com.callgalli.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callgalli.R
import com.callgalli.data.ThemeMode
import com.callgalli.rules.RuleSettings
import com.callgalli.system.BatteryHelper
import com.callgalli.system.Oem
import com.callgalli.system.Roles
import com.callgalli.system.Setup
import com.callgalli.ui.AppViewModelFactory
import com.callgalli.ui.SettingsViewModel
import com.callgalli.ui.components.BigButton
import com.callgalli.ui.components.ConfirmDialog
import com.callgalli.ui.components.SectionCard
import com.callgalli.ui.components.resetLabel
import com.callgalli.ui.theme.LocalStatusColors
import com.callgalli.ui.theme.Spacing

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    vm: SettingsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rules = settings.rules
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    var callPermsOk by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(false) }
    var isScreener by remember { mutableStateOf(false) }
    var powerSave by remember { mutableStateOf(false) }
    var optimized by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(refresh) {
        callPermsOk = Setup.hasCallPermissions(context)
        overlayOk = Setup.hasOverlay(context)
        isScreener = Roles.isCallScreener(context)
        powerSave = BatteryHelper.isPowerSaveOn(context)
        optimized = !BatteryHelper.isIgnoringOptimizations(context)
        onPauseOrDispose { }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh++
        // "Don't ask again" was chosen earlier: send the user to app settings.
        if (result.values.any { !it } && !Setup.hasCallPermissions(context)) Setup.openAppSettings(context)
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.m, end = Spacing.m,
            top = contentPadding.calculateTopPadding() + Spacing.s,
            bottom = contentPadding.calculateBottomPadding() + Spacing.m,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item {
            SectionCard(title = stringResource(R.string.settings_limits)) {
                Stepper(
                    label = stringResource(R.string.settings_first_call),
                    value = stringResource(R.string.unit_minutes_short, rules.firstCallMinutes),
                    onMinus = { vm.updateRules { it.copy(firstCallMinutes = (it.firstCallMinutes - 1).coerceIn(RuleSettings.MINUTES_RANGE)) } },
                    onPlus = { vm.updateRules { it.copy(firstCallMinutes = (it.firstCallMinutes + 1).coerceIn(RuleSettings.MINUTES_RANGE)) } },
                )
                Stepper(
                    label = stringResource(R.string.settings_repeat_call),
                    value = stringResource(R.string.unit_minutes_short, rules.repeatCallMinutes),
                    onMinus = { vm.updateRules { it.copy(repeatCallMinutes = (it.repeatCallMinutes - 1).coerceIn(RuleSettings.MINUTES_RANGE)) } },
                    onPlus = { vm.updateRules { it.copy(repeatCallMinutes = (it.repeatCallMinutes + 1).coerceIn(RuleSettings.MINUTES_RANGE)) } },
                )
                Text(stringResource(R.string.settings_limits_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard(title = stringResource(R.string.settings_reset_time)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { showTimePicker = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_reset_at), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(resetLabel(rules.resetMinuteOfDay), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.settings_warnings)) {
                Stepper(
                    label = stringResource(R.string.settings_first_warning),
                    value = stringResource(R.string.unit_seconds_short, rules.firstWarningSeconds),
                    onMinus = { vm.updateRules { it.copy(firstWarningSeconds = (it.firstWarningSeconds - 5).coerceIn(RuleSettings.WARNING_RANGE)) } },
                    onPlus = { vm.updateRules { it.copy(firstWarningSeconds = (it.firstWarningSeconds + 5).coerceIn(RuleSettings.WARNING_RANGE)) } },
                )
                Stepper(
                    label = stringResource(R.string.settings_second_warning),
                    value = stringResource(R.string.unit_seconds_short, rules.secondWarningSeconds),
                    onMinus = { vm.updateRules { it.copy(secondWarningSeconds = (it.secondWarningSeconds - 5).coerceIn(RuleSettings.WARNING_RANGE)) } },
                    onPlus = { vm.updateRules { it.copy(secondWarningSeconds = (it.secondWarningSeconds + 5).coerceIn(RuleSettings.WARNING_RANGE)) } },
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.settings_appearance)) {
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = settings.theme == mode, onClick = { vm.setTheme(mode) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.theme == mode, onClick = null)
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.m))
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.settings_language_value), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.settings_phone_setup)) {
                Text(stringResource(R.string.setup_keep_your_dialer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusRow(stringResource(R.string.setup_call_permissions), callPermsOk) {
                    permissionLauncher.launch(Setup.CALL_PERMISSIONS)
                }
                StatusRow(stringResource(R.string.setup_overlay), overlayOk) { Setup.openOverlaySettings(context) }
                StatusRow(stringResource(R.string.setup_call_screening_optional), isScreener) {
                    Roles.screeningRequest(context)?.let { roleLauncher.launch(it) } ?: Roles.openDefaultAppsSettings(context)
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.battery_title)) {
                StatusRow(stringResource(R.string.battery_saver_off), !powerSave) { BatteryHelper.openBatterySaver(context) }
                StatusRow(stringResource(R.string.battery_unrestricted), !optimized) { BatteryHelper.openBatterySettings(context) }
                val oemHint = when (BatteryHelper.oem()) {
                    Oem.XIAOMI -> R.string.battery_hint_xiaomi
                    Oem.SAMSUNG -> R.string.battery_hint_samsung
                    Oem.OPPO -> R.string.battery_hint_oppo
                    Oem.VIVO -> R.string.battery_hint_vivo
                    Oem.OTHER -> null
                }
                oemHint?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { BatteryHelper.openBatterySettings(context) }) { Text(stringResource(R.string.action_open_battery)) }
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.about_title)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.about_version) + " " + appVersion(context), style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.about_developer), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.developer_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.privacy_line), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            BigButton(stringResource(R.string.settings_clear_all), onClick = { confirmClear = true }, icon = Icons.Outlined.DeleteForever, danger = true)
        }
    }

    if (showTimePicker) {
        ResetTimeDialog(rules.resetMinuteOfDay, onDismiss = { showTimePicker = false }) { minute ->
            vm.updateRules { it.copy(resetMinuteOfDay = minute) }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_all_title),
            message = stringResource(R.string.settings_clear_all_body),
            confirmText = stringResource(R.string.settings_clear_all_confirm),
            onConfirm = { vm.clearAll() },
            onDismiss = { confirmClear = false },
            danger = true,
        )
    }
}

private fun appVersion(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.action_decrease, label)) }
        Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp))
        IconButton(onClick = onPlus) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_increase, label)) }
    }
}

@Composable
fun StatusRow(label: String, ok: Boolean, onFix: () -> Unit) {
    val colors = LocalStatusColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            contentDescription = stringResource(if (ok) R.string.status_done else R.string.status_needed),
            tint = if (ok) colors.green else colors.amber,
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = Spacing.m))
        if (!ok) TextButton(onClick = onFix) { Text(stringResource(R.string.action_fix)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResetTimeDialog(minuteOfDay: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reset_time)) },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute); onDismiss() }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
