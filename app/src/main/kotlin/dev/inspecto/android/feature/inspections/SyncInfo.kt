package dev.inspecto.android.feature.inspections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.inspecto.android.R
import java.util.UUID

/** Photo the inspector wants to take; [local] observations exist only on the device so far (T-308). */
data class PhotoRequest(
    val propertyId: UUID,
    val observationId: UUID,
    val attached: Int,
    val local: Boolean,
)

/** State of the offline queue of the inspection and what the inspector can do about it. */
class SyncPanel(
    val queuedPhotos: Int,
    val droppedChanges: Int?,
    val onDiscardFailed: () -> Unit,
    val onDismissNotice: () -> Unit,
) {
    /** Completion freezes the inspection: queued changes and photos could no longer be sent. */
    fun blocksCompletion(state: InspectionState): Boolean = queuedPhotos > 0 || state.pending.isNotEmpty()
}

/** What the device still has to send, shown above the checklist. */
@Composable
fun SyncInfo(
    state: InspectionState,
    sync: SyncPanel,
) {
    Column {
        if (state.offline) {
            Hint(stringResource(R.string.sync_offline), MaterialTheme.colorScheme.primary)
        }
        val waiting = state.pending.size - state.failedChanges
        if (waiting > 0) {
            Hint(stringResource(R.string.sync_waiting, waiting), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sync.queuedPhotos > 0) {
            Hint(
                stringResource(R.string.photo_queue_blocks_completion, sync.queuedPhotos),
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.failedChanges > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sync_failed, state.failedChanges),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = sync.onDiscardFailed) { Text(stringResource(R.string.sync_discard)) }
            }
        }
        sync.droppedChanges?.let { dropped ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sync_completed_elsewhere, dropped),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = sync.onDismissNotice) { Text(stringResource(R.string.sync_understood)) }
            }
        }
    }
}

@Composable
private fun Hint(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)
}
