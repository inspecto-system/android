package dev.inspecto.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Text field with a label and an optional error or hint below it. */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    @StringRes error: Int? = null,
    @StringRes hint: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(stringResource(it)) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Main action of a form; shows progress instead of the label while the request runs. */
@Composable
fun SubmitButton(
    @StringRes label: Int,
    submitting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(onClick = onClick, enabled = !submitting, modifier = modifier.fillMaxWidth()) {
        if (submitting) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier)
        } else {
            Text(stringResource(label))
        }
    }
}

/** Error message of a screen, for example a failed request. */
@Composable
fun ErrorText(
    @StringRes message: Int?,
    modifier: Modifier = Modifier,
) {
    if (message != null) {
        Text(
            text = stringResource(message),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
    }
}

/** Vertical form layout with consistent spacing. */
@Composable
fun FormColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
