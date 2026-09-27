package de.davis.keygo.feature.password_health.presentation.component

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import de.davis.keygo.core.security.presentation.rememberHandoffStarter
import de.davis.keygo.core.ui.components.KeyGoSwitch
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.presentation.segmentContainerColor

private const val TAG = "NotificationSwitch"

private enum class PermissionDialog { Rationale, OpenSettings }

@Composable
internal fun NotificationSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
) {
    // Below API 33 the permission does not exist, so Accompanist would always report it denied.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        PermissionGatedSwitch(checked, onChange, shapes)
    else NotificationSwitchContent(checked, onChange, shapes)
}

@OptIn(ExperimentalPermissionsApi::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun PermissionGatedSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
) {
    var dialog by remember { mutableStateOf<PermissionDialog?>(null) }
    var enableWhenGranted by remember { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) { granted ->
        if (granted) onChange(true)
        asked = true
    }

    // Accompanist re-reads the status on resume, which is how a grant made in settings shows up.
    LaunchedEffect(permission.status.isGranted) {
        if (permission.status.isGranted && enableWhenGranted) onChange(true)
        enableWhenGranted = false
    }

    NotificationSwitchContent(
        checked = checked && permission.status.isGranted,
        onChange = { enabled ->
            when {
                !enabled || permission.status.isGranted -> onChange(enabled)
                permission.status.shouldShowRationale -> dialog = PermissionDialog.Rationale
                asked -> dialog = PermissionDialog.OpenSettings
                else -> permission.launchPermissionRequest()
            }
        },
        shapes = shapes,
    )

    when (dialog) {
        PermissionDialog.Rationale -> PermissionDialog(
            text = R.string.notification_permission_rationale,
            confirm = R.string.notification_permission_grant,
            onConfirm = {
                dialog = null
                asked = true
                permission.launchPermissionRequest()
            },
            onDismiss = { dialog = null },
        )

        PermissionDialog.OpenSettings -> OpenSettingsDialog(
            onOpen = {
                dialog = null
                enableWhenGranted = true
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

@Composable
private fun OpenSettingsDialog(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val openSystemScreen = rememberHandoffStarter()

    PermissionDialog(
        text = R.string.notification_permission_denied_permanently,
        confirm = R.string.notification_permission_open_settings,
        onConfirm = {
            onOpen()
            openSystemScreen.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            ).onFailure {
                Log.w(TAG, "No activity found to handle the app details settings", it)
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun PermissionDialog(
    @StringRes text: Int,
    @StringRes confirm: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.notification_permission_cancel))
            }
        },
        title = { Text(text = stringResource(R.string.notification_permission_title)) },
        text = { Text(text = stringResource(text)) },
        icon = { Icon(imageVector = Icons.Default.Notifications, contentDescription = null) },
    )
}

@Composable
private fun NotificationSwitchContent(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
) {
    KeyGoSwitch(
        checked = checked,
        onCheckedChange = onChange,
        supportingContent = {
            Text(text = stringResource(R.string.health_notifications_description))
        },
        verticalAlignment = Alignment.CenterVertically,
        colors = ListItemDefaults.segmentedColors(containerColor = segmentContainerColor),
        shapes = shapes,
    ) {
        Text(text = stringResource(R.string.health_notifications_enable))
    }
}
