package dev.inspecto.android.feature.inspections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.inspecto.android.R
import dev.inspecto.android.api.models.Observation
import dev.inspecto.android.api.models.OpenDefect

/** Decisions of a repeat inspection about an earlier defect, in the order of the buttons. */
private val decisions =
    listOf(
        Observation.Status.OPEN to R.string.open_defect_remains,
        Observation.Status.RESOLVED to R.string.open_defect_resolved,
    )

/**
 * Defects found in earlier inspections and still open: the inspector marks each one explicitly as
 * remaining or resolved (ADR-004); nothing is resolved automatically.
 */
fun LazyListScope.openDefectsSection(
    openDefects: List<OpenDefect>,
    enabled: Boolean,
    onMark: (OpenDefect, Observation.Status) -> Unit,
) {
    if (openDefects.isEmpty()) return
    item {
        Text(stringResource(R.string.open_defects_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.open_defects_hint), style = MaterialTheme.typography.bodySmall)
    }
    items(openDefects, key = { "open-" + it.defect.id }) { openDefect ->
        OpenDefectRow(openDefect, enabled) { onMark(openDefect, it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenDefectRow(
    openDefect: OpenDefect,
    enabled: Boolean,
    onMark: (Observation.Status) -> Unit,
) {
    val defect = openDefect.defect
    val current = openDefect.currentObservation?.status
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(defect.title, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(severityLabel(defect.severity.name)) + " · " +
                    stringResource(elementGroupLabel(defect.elementGroup.name)),
                style = MaterialTheme.typography.bodySmall,
            )
            if (current == null) {
                Text(
                    stringResource(R.string.open_defect_not_marked),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                decisions.forEachIndexed { index, (status, label) ->
                    SegmentedButton(
                        selected = current == status,
                        onClick = { if (current != status) onMark(status) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, decisions.size),
                    ) { Text(stringResource(label)) }
                }
            }
        }
    }
}
