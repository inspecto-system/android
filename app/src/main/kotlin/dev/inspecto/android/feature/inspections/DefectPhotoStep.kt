package dev.inspecto.android.feature.inspections

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.inspecto.android.R
import dev.inspecto.android.feature.photos.hasCameraPermission
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Starts the system camera writing into a file of the app (shared through the FileProvider) and
 * hands the file over when a photo was taken. Asks for the camera permission first: the app
 * declares it for CameraX, so the system camera may not be started without it.
 */
@Composable
fun rememberShotLauncher(onShot: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    var shot by remember { mutableStateOf<File?>(null) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            val file = shot
            shot = null
            if (taken && file != null && file.length() > 0) onShot(file) else file?.delete()
        }

    fun open() {
        val file = File(File(context.cacheDir, "shots").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        shot = file
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        camera.launch(uri)
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) open()
        }
    return { if (hasCameraPermission(context)) open() else permission.launch(Manifest.permission.CAMERA) }
}

/** Photo button and the categories suggested for the photo; a tap chooses a category. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DefectPhotoStep(
    state: DefectPhotoState,
    categoryId: UUID?,
    onTakePhoto: () -> Unit,
    onChoose: (UUID) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val label =
            when {
                state.working -> R.string.defect_photo_working
                state.photo != null -> R.string.defect_photo_retake
                else -> R.string.defect_photo_take
            }
        OutlinedButton(onClick = onTakePhoto, enabled = !state.working) { Text(stringResource(label)) }
        val photo = state.photo ?: return@Column
        val items = photo.suggestions?.items.orEmpty()
        when {
            photo.photoId == null -> Hint(R.string.defect_suggestions_offline)
            items.isEmpty() -> Hint(R.string.defect_suggestions_none)
            else -> {
                Text(stringResource(R.string.defect_suggestions_hint), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { item ->
                        FilterChip(
                            selected = item.categoryId == categoryId,
                            onClick = { onChoose(item.categoryId) },
                            label = {
                                val percent = (item.probability.toDouble() * PERCENT).roundToInt()
                                Text(stringResource(R.string.defect_suggestion_item, item.name, percent))
                            },
                        )
                    }
                }
            }
        }
        Hint(R.string.defect_photo_attached)
    }
}

@Composable
private fun Hint(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall)
}

private const val PERCENT = 100
