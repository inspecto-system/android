package dev.inspecto.android.feature.properties

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.inspecto.android.R
import dev.inspecto.android.api.models.Property
import dev.inspecto.android.ui.components.ErrorText
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyListScreen(
    onCreate: () -> Unit,
    onOpen: (UUID) -> Unit,
    viewModel: PropertyListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Reload when the screen comes back, for example after a property was created.
    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose { }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.properties_title)) },
                actions = { TextButton(onClick = viewModel::signOut) { Text(stringResource(R.string.sign_out)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate) { Text(stringResource(R.string.property_add)) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.properties_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            PropertyListContent(state, onOpen, viewModel::reload)
        }
    }
}

@Composable
private fun PropertyListContent(
    state: PropertyListState,
    onOpen: (UUID) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.loading && state.items.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.errorRes != null ->
            Column(Modifier.padding(16.dp)) {
                ErrorText(state.errorRes)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        state.items.isEmpty() -> {
            val empty = if (state.query.isBlank()) R.string.properties_empty else R.string.properties_not_found
            Text(stringResource(empty), modifier = Modifier.padding(16.dp))
        }
        else ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { property -> PropertyCard(property, onOpen) }
            }
    }
}

@Composable
private fun PropertyCard(
    property: Property,
    onOpen: (UUID) -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(property.id) }) {
        Column(Modifier.padding(16.dp)) {
            Text(property.name, style = MaterialTheme.typography.titleMedium)
            Text(property.address, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(propertyTypeLabel(property.type.name)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
