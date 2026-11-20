package com.callgalli.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.callgalli.R
import com.callgalli.system.BatteryHelper
import com.callgalli.system.Roles
import com.callgalli.ui.components.BigButton
import com.callgalli.ui.components.BigOutlinedButton
import com.callgalli.ui.settings.StatusRow
import com.callgalli.ui.theme.Spacing

/** 4 short screens that explain each permission in plain words. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }

    var isDialer by remember { mutableStateOf(false) }
    var isScreener by remember { mutableStateOf(false) }
    var contactsOk by remember { mutableStateOf(false) }
    var phoneOk by remember { mutableStateOf(false) }
    var notificationsOk by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    LifecycleResumeEffect(refresh) {
        isDialer = Roles.isDefaultDialer(context)
        isScreener = Roles.isCallScreener(context)
        contactsOk = granted(Manifest.permission.READ_CONTACTS)
        phoneOk = granted(Manifest.permission.READ_PHONE_STATE) && granted(Manifest.permission.CALL_PHONE)
        notificationsOk = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        onPauseOrDispose { }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        // Step dots
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            repeat(4) { i ->
                Surface(
                    color = if (i <= page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier.size(10.dp),
                ) {}
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            when (page) {
                0 -> {
                    Header(Icons.Outlined.Timer, stringResource(R.string.onb1_title))
                    Body(stringResource(R.string.onb1_body))
                    Bullet(stringResource(R.string.onb1_point1))
                    Bullet(stringResource(R.string.onb1_point2))
                    Bullet(stringResource(R.string.onb1_point3))
                    PrivacyNote()
                }
                1 -> {
                    Header(Icons.Outlined.Phone, stringResource(R.string.onb2_title))
                    Body(stringResource(R.string.onb2_body))
                    StatusRow(stringResource(R.string.setup_default_phone_app), isDialer) {
                        Roles.dialerRequest(context)?.let { roleLauncher.launch(it) } ?: Roles.openDefaultAppsSettings(context)
                    }
                    Body(stringResource(R.string.onb2_screening_body))
                    StatusRow(stringResource(R.string.setup_call_screening), isScreener) {
                        Roles.screeningRequest(context)?.let { roleLauncher.launch(it) } ?: Roles.openDefaultAppsSettings(context)
                    }
                }
                2 -> {
                    Header(Icons.Outlined.Contacts, stringResource(R.string.onb3_title))
                    Body(stringResource(R.string.onb3_contacts_body))
                    StatusRow(stringResource(R.string.perm_contacts), contactsOk) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                    }
                    Body(stringResource(R.string.onb3_phone_body))
                    StatusRow(stringResource(R.string.perm_phone_state), phoneOk) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE))
                    }
                }
                else -> {
                    Header(Icons.Outlined.NotificationsActive, stringResource(R.string.onb4_title))
                    Body(stringResource(R.string.onb4_body))
                    StatusRow(stringResource(R.string.perm_notifications), notificationsOk) {
                        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                    Body(stringResource(R.string.onb4_battery_body))
                    TextButton(onClick = { BatteryHelper.openBatterySettings(context) }) { Text(stringResource(R.string.action_open_battery)) }
                    PrivacyNote()
                }
            }
        }
        if (page < 3) {
            BigButton(stringResource(R.string.action_next), onClick = { page++ })
        } else {
            BigButton(stringResource(R.string.action_start), onClick = onDone)
        }
        if (page > 0) BigOutlinedButton(stringResource(R.string.action_back), onClick = { page-- })
        else Spacer(Modifier.size(56.dp))
    }
}

@Composable
private fun Header(icon: ImageVector, title: String) {
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.fillMaxSize()) {}
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
    }
    Text(title, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

@Composable
private fun Bullet(text: String) = Row {
    Text("•  ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PrivacyNote() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(Icons.Outlined.Shield, contentDescription = null)
            Text(stringResource(R.string.privacy_line), style = MaterialTheme.typography.titleSmall)
        }
    }
}
