package dev.inspecto.android.feature.properties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import dev.inspecto.android.api.models.CreateRoomRequest
import dev.inspecto.android.api.models.PropertyDetails
import dev.inspecto.android.api.models.Room
import dev.inspecto.android.ui.components.ErrorText
import dev.inspecto.android.ui.components.FormField

/** Dialog shown over the property card. */
private sealed interface RoomDialog {
    data object Add : RoomDialog

    data class Rename(
        val room: Room,
    ) : RoomDialog

    data class Delete(
        val room: Room,
    ) : RoomDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyDetailsScreen(
    onBack: () -> Unit,
    viewModel: PropertyDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<RoomDialog?>(null) }
    val property = state.property
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(property?.name.orEmpty()) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            if (property != null) {
                ExtendedFloatingActionButton(onClick = { dialog = RoomDialog.Add }) {
                    Text(stringResource(R.string.room_add))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                property != null ->
                    PropertyContent(
                        property = property,
                        errorRes = state.errorRes,
                        onRename = { dialog = RoomDialog.Rename(it) },
                        onDelete = { dialog = RoomDialog.Delete(it) },
                    )
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else ->
                    Column(Modifier.padding(16.dp)) {
                        ErrorText(state.errorRes)
                        TextButton(onClick = viewModel::reload) { Text(stringResource(R.string.retry)) }
                    }
            }
        }
    }
    RoomDialogs(dialog, viewModel, onDismiss = { dialog = null })
}

@Composable
private fun PropertyContent(
    property: PropertyDetails,
    errorRes: Int?,
    onRename: (Room) -> Unit,
    onDelete: (Room) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(propertyTypeLabel(property.type.name)), color = MaterialTheme.colorScheme.primary)
            Text(property.address, style = MaterialTheme.typography.bodyLarge)
            property.city?.let { Text(it) }
            property.areaSqm?.let {
                Text(
                    stringResource(R.string.property_area_value, it.stripTrailingZeros().toPlainString()),
                )
            }
            property.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            ErrorText(errorRes, Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.rooms_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (property.rooms.isEmpty()) {
                Text(stringResource(R.string.rooms_empty))
            }
        }
        items(property.rooms, key = { it.id }) { room -> RoomRow(room, onRename, onDelete) }
    }
}

@Composable
private fun RoomRow(
    room: Room,
    onRename: (Room) -> Unit,
    onDelete: (Room) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(roomTypeLabel(room.roomType.name)), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onRename(room) }) { Text(stringResource(R.string.room_rename)) }
            TextButton(onClick = { onDelete(room) }) { Text(stringResource(R.string.delete)) }
        }
    }
}

@Composable
private fun RoomDialogs(
    dialog: RoomDialog?,
    viewModel: PropertyDetailsViewModel,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> Unit
        RoomDialog.Add ->
            RoomFormDialog(initialName = "", chooseType = true, onDismiss = onDismiss) { name, type ->
                viewModel.addRoom(name, type)
            }
        is RoomDialog.Rename ->
            RoomFormDialog(initialName = dialog.room.name, chooseType = false, onDismiss = onDismiss) { name, _ ->
                viewModel.renameRoom(dialog.room, name)
            }
        is RoomDialog.Delete ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.room_delete_title)) },
                text = { Text(stringResource(R.string.room_delete_text, dialog.room.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteRoom(dialog.room)
                        onDismiss()
                    }) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomFormDialog(
    initialName: String,
    chooseType: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, CreateRoomRequest.RoomType) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var type by remember { mutableStateOf(CreateRoomRequest.RoomType.LIVING) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (chooseType) R.string.room_add else R.string.room_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(name, { name = it }, R.string.room_name)
                if (chooseType) {
                    Text(stringResource(R.string.room_type), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CreateRoomRequest.RoomType.entries.forEach { option ->
                            FilterChip(
                                selected = type == option,
                                onClick = { type = option },
                                label = { Text(stringResource(roomTypeLabel(option.name))) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(name, type)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
