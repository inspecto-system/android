package dev.inspecto.android.feature.properties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.inspecto.android.R
import dev.inspecto.android.api.models.CreatePropertyRequest
import dev.inspecto.android.ui.components.ErrorText
import dev.inspecto.android.ui.components.FormColumn
import dev.inspecto.android.ui.components.FormField
import dev.inspecto.android.ui.components.SubmitButton
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePropertyScreen(
    onBack: () -> Unit,
    onCreated: (UUID) -> Unit,
    viewModel: CreatePropertyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.property_new_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        FormColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            FormField(state.name, viewModel::onNameChange, R.string.property_name, error = state.nameError)
            Text(stringResource(R.string.property_type), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CreatePropertyRequest.Type.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChange(type) },
                        label = { Text(stringResource(propertyTypeLabel(type.name))) },
                    )
                }
            }
            FormField(state.address, viewModel::onAddressChange, R.string.property_address, error = state.addressError)
            FormField(state.city, viewModel::onCityChange, R.string.property_city)
            FormField(
                state.area,
                viewModel::onAreaChange,
                R.string.property_area,
                error = state.areaError,
                keyboardType = KeyboardType.Decimal,
            )
            FormField(state.notes, viewModel::onNotesChange, R.string.property_notes, singleLine = false)
            ErrorText(state.errorRes)
            SubmitButton(R.string.save, state.submitting, viewModel::save)
        }
    }
}
