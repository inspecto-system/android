package dev.inspecto.android.feature.photos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.inspecto.android.R
import dev.inspecto.android.ui.components.ErrorText
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) request.launch(Manifest.permission.CAMERA) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.photo_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.photo_done)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (granted) {
                CameraContent(viewModel)
            } else {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.photo_permission))
                    Button(onClick = { request.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.local_network_allow))
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraContent(viewModel: CameraViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        remember {
            LifecycleCameraController(context).apply {
                setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
                imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            }
        }
    LaunchedEffect(lifecycleOwner) { controller.bindToLifecycle(lifecycleOwner) }
    Column(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.max?.let { stringResource(R.string.photo_count_of, state.count, it) }
                        ?: stringResource(R.string.photo_count, state.count),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(
                    enabled = !state.full && !state.saving,
                    onClick = { capture(context, controller, viewModel) },
                ) { Text(stringResource(R.string.photo_take)) }
            }
            if (state.full) {
                Text(stringResource(R.string.photo_limit_reached), color = MaterialTheme.colorScheme.error)
            }
            ErrorText(state.errorRes)
        }
    }
}

private fun capture(
    context: Context,
    controller: LifecycleCameraController,
    viewModel: CameraViewModel,
) {
    // The shot goes to the cache first; the compressed copy is what stays in the queue.
    val file = File(context.cacheDir, "shot-${UUID.randomUUID()}.jpg")
    controller.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) = viewModel.onShot(file)

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                viewModel.onCaptureFailed()
            }
        },
    )
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
