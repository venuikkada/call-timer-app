package com.callgalli.ui.dialer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callgalli.R
import com.callgalli.rules.CallDecision
import com.callgalli.ui.AppViewModelFactory
import com.callgalli.ui.DialerViewModel
import com.callgalli.ui.components.ConfirmDialog
import com.callgalli.ui.theme.LocalStatusColors
import com.callgalli.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Simple dial pad. Android requires a phone app to have one before it can become the
 * default phone app. Before dialing a Stage 3 number it shows a warning.
 */
@Composable
fun DialerScreen(
    initialNumber: String,
    contentPadding: PaddingValues,
    vm: DialerViewModel = viewModel(factory = AppViewModelFactory),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var number by rememberSaveable(initialNumber) { mutableStateOf(initialNumber) }
    var confirmFor by remember { mutableStateOf<String?>(null) }

    fun placeCall(raw: String) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        try {
            telecom.placeCall(Uri.fromParts("tel", raw, null), Bundle())
        } catch (_: SecurityException) {
            // CALL_PHONE was revoked; the permission request below will run next time.
        }
    }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok && number.isNotBlank()) placeCall(number)
    }

    fun onCallPressed() {
        val raw = number.trim()
        if (raw.isEmpty()) return
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            callPermission.launch(Manifest.permission.CALL_PHONE)
            return
        }
        scope.launch {
            if (vm.check(raw) is CallDecision.ConfirmNeeded) confirmFor = raw else placeCall(raw)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = Spacing.l, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            number.ifEmpty { stringResource(R.string.dialer_hint) },
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp),
            color = if (number.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.l))
        Keypad(onKey = { number += it })
        Spacer(Modifier.height(Spacing.l))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(64.dp))
            FilledIconButton(
                onClick = ::onCallPressed,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = LocalStatusColors.current.green),
            ) {
                Icon(Icons.Outlined.Call, contentDescription = stringResource(R.string.action_call), tint = LocalStatusColors.current.onGreen, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { number = number.dropLast(1) }, modifier = Modifier.size(64.dp), enabled = number.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = stringResource(R.string.action_delete_digit))
            }
        }
    }

    confirmFor?.let { raw ->
        val name = remember(raw) { vm.nameFor(raw) } ?: raw
        ConfirmDialog(
            title = stringResource(R.string.dialer_blocked_title),
            message = stringResource(R.string.dialer_blocked_body, name),
            confirmText = stringResource(R.string.dialer_call_anyway),
            onConfirm = {
                vm.preConfirm(raw)
                placeCall(raw)
            },
            onDismiss = { confirmFor = null },
        )
    }
}

private val KEYS = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "", "#" to ""),
)

/** 3×4 keypad with large round keys, used by the dial pad and the in-call screen. */
@Composable
fun Keypad(onKey: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        KEYS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                row.forEach { (digit, letters) ->
                    Surface(
                        onClick = { onKey(digit) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(76.dp),
                    ) {
                        Column(
                            Modifier.heightIn(min = 76.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(digit, style = MaterialTheme.typography.headlineSmall)
                            if (letters.isNotEmpty()) {
                                Text(letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
