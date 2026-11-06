package dev.inspecto.android.feature.inspections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.inspecto.android.R
import dev.inspecto.android.feature.properties.roomTypeLabel
import dev.inspecto.android.ui.components.ErrorText
import dev.inspecto.android.ui.components.FormField
import dev.inspecto.android.ui.components.SubmitButton
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInspectionScreen(
    onBack: () -> Unit,
    onCreated: (UUID) -> Unit,
    viewModel: CreateInspectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inspection_new_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                CreateInspectionForm(state, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateInspectionForm(
    state: CreateInspectionState,
    viewModel: CreateInspectionViewModel,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FormField(state.title, viewModel::onTitleChange, R.string.inspection_title_field)
            Text(
                stringResource(R.string.inspection_templates),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.templates.forEach { template ->
                    FilterChip(
                        selected = template.code in state.selectedTemplates,
                        onClick = { viewModel.toggleTemplate(template.code) },
                        label = { Text(template.name) },
                    )
                }
            }
            Text(
                stringResource(R.string.inspection_rooms),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(state.rooms, key = { it.id }) { room ->
            Row(
                Modifier.fillMaxWidth().clickable { viewModel.toggleRoom(room.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = room.id in state.selectedRooms, onCheckedChange = { viewModel.toggleRoom(room.id) })
                Text(room.name, Modifier.weight(1f))
                Text(stringResource(roomTypeLabel(room.roomType.name)), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            ErrorText(state.errorRes)
            SubmitButton(
                label = R.string.inspection_create,
                submitting = state.submitting,
                onClick = viewModel::save,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
