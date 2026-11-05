package dev.inspecto.android.ui.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.inspecto.android.R

/**
 * Android 17 (API 37) lets an app reach addresses of the local network only with
 * `ACCESS_LOCAL_NETWORK`. Inspecto is deployed on the owner's computer, so the phone talks to it
 * over the local network (Wi-Fi) and cannot work without this permission.
 */
private const val LOCAL_NETWORK_PERMISSION_SDK = 37

/** Shows [content] once the app may reach the local network; asks for the permission otherwise. */
@Composable
fun LocalNetworkPermissionGate(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK) {
        content()
        return
    }
    val context = LocalContext.current
    val permission = Manifest.permission.ACCESS_LOCAL_NETWORK

    fun isGranted() = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(isGranted()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            granted = result
            denied = !result
        }
    // The user may grant the permission in the system settings and come back.
    LifecycleResumeEffect(Unit) {
        granted = isGranted()
        onPauseOrDispose { }
    }
    if (granted) {
        content()
    } else {
        LocalNetworkPermissionScreen(
            denied = denied,
            onRequest = { launcher.launch(permission) },
            onOpenSettings = {
                val intent =
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
    }
}

/** Explains why the permission is needed; after a refusal also offers the system settings. */
@Composable
fun LocalNetworkPermissionScreen(
    denied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.local_network_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.local_network_text), style = MaterialTheme.typography.bodyLarge)
            if (denied) {
                Text(stringResource(R.string.local_network_denied), color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onRequest) { Text(stringResource(R.string.local_network_allow)) }
            if (denied) {
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.local_network_settings)) }
            }
        }
    }
}
