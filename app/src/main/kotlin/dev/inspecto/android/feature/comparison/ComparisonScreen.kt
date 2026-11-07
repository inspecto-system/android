package dev.inspecto.android.feature.comparison

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.inspecto.android.R
import dev.inspecto.android.api.models.ComparedDefect
import dev.inspecto.android.api.models.InspectionSummary
import dev.inspecto.android.api.models.StateCounts
import dev.inspecto.android.feature.inspections.localDate
import dev.inspecto.android.feature.inspections.severityLabel
import dev.inspecto.android.ui.components.ErrorText
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    onBack: () -> Unit,
    viewModel: ComparisonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        ComparisonContent(
            state,
            ComparisonActions(viewModel::selectBase, viewModel::selectTarget, viewModel::toggleFilter),
            Modifier.padding(padding),
        )
    }
}

/** What the comparison screen can do. */
internal class ComparisonActions(
    val onBase: (UUID) -> Unit,
    val onTarget: (UUID) -> Unit,
    val onFilter: (ComparedDefect.State) -> Unit,
)

@Composable
internal fun ComparisonContent(
    state: ComparisonState,
    actions: ComparisonActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            if (state.inspections.size < 2 && !state.loading) {
                Text(stringResource(R.string.compare_need_two))
            } else {
                InspectionPicker(R.string.compare_base, state.inspections, state.base, actions.onBase)
                InspectionPicker(R.string.compare_target, state.inspections, state.target, actions.onTarget)
            }
            ErrorText(state.errorRes)
            if (state.loading) CircularProgressIndicator(Modifier.padding(top = 8.dp))
        }
        val comparison = state.comparison ?: return@LazyColumn
        item { Summary(comparison.summary, state.filter, actions.onFilter) }
        if (state.visibleDefects.isEmpty()) {
            item { Text(stringResource(R.string.compare_empty)) }
        }
        state.visibleDefects.groupBy { it.roomName }.forEach { (room, defects) ->
            item(key = "room-$room") {
                Text(room, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(defects, key = { it.defectId }) { DefectRow(it) }
        }
    }
}

@Composable
private fun InspectionPicker(
    @StringRes label: Int,
    inspections: List<InspectionSummary>,
    selected: UUID?,
    onSelect: (UUID) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(inspections.find { it.id == selected }?.let { inspectionName(it) }.orEmpty())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                inspections.forEach { inspection ->
                    DropdownMenuItem(
                        text = { Text(inspectionName(inspection)) },
                        onClick = {
                            expanded = false
                            onSelect(inspection.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun inspectionName(inspection: InspectionSummary): String {
    val date = inspection.inspectedAt.localDate()
    return inspection.title?.let { "$date · $it" } ?: stringResource(R.string.inspection_default_title, date)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(
    summary: StateCounts,
    filter: ComparedDefect.State?,
    onFilter: (ComparedDefect.State) -> Unit,
) {
    val counts =
        listOf(
            ComparedDefect.State.NEW to summary.new,
            ComparedDefect.State.RESOLVED to summary.resolved,
            ComparedDefect.State.REMAINING to summary.remaining,
            ComparedDefect.State.NOT_CHECKED to summary.notChecked,
        )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        counts.forEach { (state, count) ->
            FilterChip(
                selected = filter == state,
                onClick = { onFilter(state) },
                label = { Text(stringResource(R.string.compare_count, stringResource(stateLabel(state)), count)) },
            )
        }
    }
}

@Composable
private fun DefectRow(defect: ComparedDefect) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(defect.title, style = MaterialTheme.typography.bodyLarge)
            val state =
                stringResource(stateLabel(defect.state)) +
                    if (defect.reopened == true) " · " + stringResource(R.string.compare_reopened) else ""
            Text(state, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(severityLabel(defect.severity.name)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@StringRes
internal fun stateLabel(state: ComparedDefect.State): Int =
    when (state) {
        ComparedDefect.State.NEW -> R.string.compare_state_new
        ComparedDefect.State.RESOLVED -> R.string.compare_state_resolved
        ComparedDefect.State.REMAINING -> R.string.compare_state_remaining
        ComparedDefect.State.NOT_CHECKED -> R.string.compare_state_not_checked
    }
