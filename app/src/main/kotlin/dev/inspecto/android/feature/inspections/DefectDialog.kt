package dev.inspecto.android.feature.inspections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.inspecto.android.R
import dev.inspecto.android.api.models.CreateDefectRequest
import dev.inspecto.android.api.models.DefectCategory
import dev.inspecto.android.ui.components.FormField
import java.util.UUID

/** Categories for an element group: the ones of that group first, then the others. */
internal fun categoriesFor(
    categories: List<DefectCategory>,
    group: CreateDefectRequest.ElementGroup,
): List<DefectCategory> =
    categories
        .filter { it.active }
        .sortedBy { if (it.elementGroup?.name == group.name) 0 else 1 }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DefectDialog(
    draft: DefectDraft,
    categories: List<DefectCategory>,
    saving: Boolean,
    photo: DefectPhotoState,
    onTakePhoto: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (DefectForm) -> Unit,
) {
    var group by remember { mutableStateOf(draft.elementGroup) }
    var categoryId by remember { mutableStateOf(categoriesFor(categories, draft.elementGroup).firstOrNull()?.id) }
    var severity by remember { mutableStateOf(CreateDefectRequest.Severity.MINOR) }
    var title by remember { mutableStateOf(draft.title) }
    var description by remember { mutableStateOf("") }
    // The most probable suggested category is preselected; the inspector may choose any other.
    val suggested =
        photo.photo
            ?.suggestions
            ?.items
            ?.firstOrNull()
            ?.categoryId
    LaunchedEffect(suggested) { suggested?.let { categoryId = it } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.defect_new_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(title, { title = it }, R.string.defect_title_field)
                DefectPhotoStep(photo, categoryId, onTakePhoto) { categoryId = it }
                CategoryField(categoriesFor(categories, group), categoryId) { categoryId = it }
                Text(stringResource(R.string.defect_severity), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreateDefectRequest.Severity.entries.forEach { option ->
                        FilterChip(
                            selected = severity == option,
                            onClick = { severity = option },
                            label = { Text(stringResource(severityLabel(option.name))) },
                        )
                    }
                }
                Text(stringResource(R.string.defect_element), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreateDefectRequest.ElementGroup.entries.forEach { option ->
                        FilterChip(
                            selected = group == option,
                            onClick = { group = option },
                            label = { Text(stringResource(elementGroupLabel(option.name))) },
                        )
                    }
                }
                FormField(description, { description = it }, R.string.defect_description, singleLine = false)
            }
        },
        confirmButton = {
            val selected = categoryId
            TextButton(
                enabled = !saving && title.isNotBlank() && selected != null,
                onClick = { selected?.let { onSave(DefectForm(it, group, severity, title, description)) } },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(
    categories: List<DefectCategory>,
    selectedId: UUID?,
    onSelect: (UUID) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = categories.firstOrNull { it.id == selectedId }?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.defect_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
