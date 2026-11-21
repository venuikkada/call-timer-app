package com.callgalli.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callgalli.R

/**
 * Main On/Off switch. Turning it OFF asks once for confirmation, because the limits are
 * there to help the user; turning it ON is instant.
 */
@Composable
fun PowerSwitch(enabled: Boolean, onSet: (Boolean) -> Unit, onHero: Boolean = false) {
    var confirmOff by remember { mutableStateOf(false) }
    val request: (Boolean) -> Unit = { on -> if (on) onSet(true) else confirmOff = true }
    val fg = if (onHero) Color.White else MaterialTheme.colorScheme.onSurface
    val sub = if (onHero) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = if (onHero) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .toggleable(value = enabled, role = Role.Switch, onValueChange = request)
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, tint = fg)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (enabled) R.string.power_on_title else R.string.power_off_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = fg,
                )
                Text(
                    stringResource(if (enabled) R.string.power_on_sub else R.string.power_off_sub),
                    style = MaterialTheme.typography.bodyMedium, color = sub,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = null, // the whole row toggles
                colors = if (onHero) SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF0B6E69),
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.25f),
                    uncheckedBorderColor = Color.White.copy(alpha = 0.7f),
                ) else SwitchDefaults.colors(),
            )
        }
    }

    if (confirmOff) {
        ConfirmDialog(
            title = stringResource(R.string.power_confirm_title),
            message = stringResource(R.string.power_confirm_body),
            confirmText = stringResource(R.string.power_confirm_action),
            onConfirm = { onSet(false) },
            onDismiss = { confirmOff = false },
            danger = true,
        )
    }
}
