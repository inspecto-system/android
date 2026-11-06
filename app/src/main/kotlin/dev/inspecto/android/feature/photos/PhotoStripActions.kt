package dev.inspecto.android.feature.photos

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.inspecto.android.R
import dev.inspecto.android.core.db.PendingPhoto
import java.util.UUID

/** What the photo strip of an observation needs from the screen. */
class PhotoStripActions(
    val onCamera: () -> Unit,
    val thumbnail: suspend (UUID) -> Bitmap?,
    val preview: suspend (PendingPhoto) -> Bitmap?,
    val onRetry: (PendingPhoto) -> Unit,
    val onRemove: (PendingPhoto) -> Unit,
)

/**
 * Photos of an observation: attached ones from media-service, then queued ones from the device
 * with their state. The camera button counts both against the limit of the server.
 */
@Composable
fun PhotoStrip(
    attached: List<UUID>,
    pending: List<PendingPhoto>,
    max: Int?,
    editable: Boolean,
    actions: PhotoStripActions,
) {
    val count = attached.size + pending.size
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (count > 0) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                attached.forEach { id ->
                    val bitmap by produceState<Bitmap?>(null, id) { value = actions.thumbnail(id) }
                    Thumbnail(bitmap, label = null)
                }
                pending.forEach { photo ->
                    val bitmap by produceState<Bitmap?>(null, photo.id) { value = actions.preview(photo) }
                    Thumbnail(
                        bitmap,
                        stringResource(
                            if (photo.errorCode ==
                                null
                            ) {
                                R.string.photo_queued
                            } else {
                                R.string.photo_failed
                            },
                        ),
                    )
                }
            }
        }
        pending.filter { it.errorCode != null }.forEach { photo -> FailedPhoto(photo, actions) }
        if (editable) {
            val full = max != null && count >= max
            TextButton(onClick = actions.onCamera, enabled = !full) {
                Text(
                    max?.let { stringResource(R.string.photo_button_of, count, it) }
                        ?: stringResource(R.string.photo_button, count),
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(
    bitmap: Bitmap?,
    label: String?,
) {
    Box(Modifier.size(THUMBNAIL_DP.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(THUMBNAIL_DP.dp),
            )
        }
        label?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        ).fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun FailedPhoto(
    photo: PendingPhoto,
    actions: PhotoStripActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(photoErrorMessage(photo.errorCode)),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { actions.onRetry(photo) }) { Text(stringResource(R.string.retry)) }
        TextButton(onClick = { actions.onRemove(photo) }) { Text(stringResource(R.string.delete)) }
    }
}

/** Message for a photo that the queue set aside. */
fun photoErrorMessage(code: String?): Int =
    when (code) {
        "PHOTO_LIMIT_EXCEEDED" -> R.string.photo_error_limit
        "INVALID_STATE_TRANSITION" -> R.string.photo_error_completed
        "IMAGE_TOO_LARGE", "IMAGE_DIMENSIONS_TOO_LARGE" -> R.string.photo_error_too_large
        PhotoUploader.FILE_MISSING -> R.string.photo_error_missing
        else -> R.string.photo_error_other
    }

private const val THUMBNAIL_DP = 72
