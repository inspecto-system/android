package dev.inspecto.android.feature.inspections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.inspecto.android.R
import dev.inspecto.android.api.models.ChecklistItem
import dev.inspecto.android.api.models.DefectCategory
import dev.inspecto.android.api.models.InspectionDetails
import dev.inspecto.android.api.models.InspectionRoom
import dev.inspecto.android.api.models.ObservationWithDefect
import dev.inspecto.android.api.models.OpenDefect
import dev.inspecto.android.ui.components.ErrorText
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Quick marks of a checklist item, in the order of the buttons. */
private val quickMarks =
    listOf(
        ChecklistItem.Result.OK to R.string.result_ok,
        ChecklistItem.Result.DEFECT to R.string.result_defect,
        ChecklistItem.Result.NOT_APPLICABLE to R.string.result_not_applicable,
    )

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Local date of the device for a timestamp of the API. */
fun OffsetDateTime.localDate(): String = dateFormat.format(atZoneSameInstant(ZoneId.systemDefault()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionScreen(
    onBack: () -> Unit,
    viewModel: InspectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inspection = state.inspection
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        inspection
                            ?.let {
                                it.title
                                    ?: stringResource(R.string.inspection_default_title, it.inspectedAt.localDate())
                            }.orEmpty(),
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                inspection != null -> InspectionContent(inspection, state, viewModel)
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else ->
                    Column(Modifier.padding(16.dp)) {
                        ErrorText(state.errorRes)
                        TextButton(onClick = viewModel::reload) { Text(stringResource(R.string.retry)) }
                    }
            }
        }
    }
    state.defectDraft?.let { draft ->
        DefectDialog(
            draft = draft,
            categories = state.categories,
            saving = state.busy,
            onDismiss = { viewModel.showDefectDialog(null) },
            onSave = viewModel::saveDefect,
        )
    }
}

@Composable
private fun InspectionContent(
    inspection: InspectionDetails,
    state: InspectionState,
    viewModel: InspectionViewModel,
) {
    Column(Modifier.fillMaxSize()) {
        StatusBar(inspection, state, viewModel)
        if (inspection.rooms.isEmpty()) return@Column
        val roomIndex = state.roomIndex.coerceIn(0, inspection.rooms.lastIndex)
        PrimaryScrollableTabRow(selectedTabIndex = roomIndex, edgePadding = 16.dp) {
            inspection.rooms.forEachIndexed { index, room ->
                Tab(
                    selected = index == roomIndex,
                    onClick = { viewModel.selectRoom(index) },
                    text = { Text(room.room.name) },
                )
            }
        }
        RoomChecklist(inspection.rooms[roomIndex], state.openDefectsIn(roomIndex), state, viewModel)
    }
}

@Composable
private fun StatusBar(
    inspection: InspectionDetails,
    state: InspectionState,
    viewModel: InspectionViewModel,
) {
    var confirmComplete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val completedAt = inspection.completedAt
        Text(
            if (completedAt != null) {
                stringResource(R.string.inspection_completed_at, completedAt.localDate())
            } else {
                stringResource(inspectionStatusLabel(inspection.status.name))
            },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
        ErrorText(state.errorRes)
        when (inspection.status) {
            InspectionDetails.Status.DRAFT -> {
                Text(stringResource(R.string.inspection_draft_hint), style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::start, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.inspection_start))
                }
            }
            InspectionDetails.Status.IN_PROGRESS ->
                OutlinedButton(
                    onClick = { confirmComplete = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.inspection_complete)) }
            InspectionDetails.Status.COMPLETED -> Unit
        }
    }
    if (confirmComplete) {
        CompleteDialog(
            uncheckedCount = state.uncheckedCount,
            unmarkedDefects = state.unmarkedOpenDefects,
            onConfirm = {
                confirmComplete = false
                viewModel.complete()
            },
            onDismiss = { confirmComplete = false },
        )
    }
}

@Composable
private fun CompleteDialog(
    uncheckedCount: Int,
    unmarkedDefects: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inspection_complete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.inspection_complete_text))
                if (uncheckedCount > 0) {
                    Text(
                        stringResource(R.string.inspection_unchecked, uncheckedCount),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (unmarkedDefects > 0) {
                    Text(
                        stringResource(R.string.inspection_unmarked_defects, unmarkedDefects),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.inspection_complete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RoomChecklist(
    room: InspectionRoom,
    openDefects: List<OpenDefect>,
    state: InspectionState,
    viewModel: InspectionViewModel,
) {
    val described = room.observations.mapNotNull { it.checklistItemId }.toSet()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        openDefectsSection(openDefects, enabled = state.editable && !state.busy, onMark = viewModel::markOpenDefect)
        items(room.checklist, key = { it.id }) { item ->
            ChecklistRow(
                item = item,
                enabled = state.editable && !state.busy,
                needsDefect = state.editable && item.result == ChecklistItem.Result.DEFECT && item.id !in described,
                onMark = { viewModel.mark(item, it) },
                onDescribe = { viewModel.showDefectDialog(DefectDraft.forItem(item)) },
            )
        }
        item {
            Text(
                stringResource(R.string.defects_in_room),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (room.observations.isEmpty()) {
                Text(stringResource(R.string.defects_empty))
            }
        }
        items(room.observations, key = { it.id }) { DefectRow(it, state.categories) }
        if (state.editable) {
            item {
                TextButton(onClick = { viewModel.showDefectDialog(DefectDraft.forRoom(room.room.id)) }) {
                    Text(stringResource(R.string.defect_add))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    enabled: Boolean,
    needsDefect: Boolean,
    onMark: (ChecklistItem.Result) -> Unit,
    onDescribe: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(elementGroupLabel(item.elementGroup.name)), style = MaterialTheme.typography.bodySmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                quickMarks.forEachIndexed { index, (result, label) ->
                    SegmentedButton(
                        selected = item.result == result,
                        onClick = { onMark(result) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, quickMarks.size),
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }
            if (needsDefect) {
                TextButton(onClick = onDescribe) { Text(stringResource(R.string.defect_describe)) }
            }
        }
    }
}

@Composable
private fun DefectRow(
    observation: ObservationWithDefect,
    categories: List<DefectCategory>,
) {
    val defect = observation.defect
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(defect.title, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(
                    if (observation.status == ObservationWithDefect.Status.RESOLVED) {
                        stringResource(R.string.open_defect_resolved)
                    } else {
                        null
                    },
                    stringResource(severityLabel(observation.severity.name)),
                    categories.firstOrNull { it.id == defect.categoryId }?.name,
                    stringResource(elementGroupLabel(defect.elementGroup.name)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            defect.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
