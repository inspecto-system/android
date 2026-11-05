package dev.inspecto.android.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.inspecto.android.R
import dev.inspecto.android.ui.components.ErrorText
import dev.inspecto.android.ui.components.FormColumn
import dev.inspecto.android.ui.components.FormField
import dev.inspecto.android.ui.components.SubmitButton

@Composable
fun LoginScreen(
    onRegister: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthLayout {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.start_subtitle), style = MaterialTheme.typography.bodyLarge)
        FormColumn(Modifier.padding(top = 24.dp)) {
            FormField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = R.string.auth_email,
                error = state.emailError,
                keyboardType = KeyboardType.Email,
            )
            FormField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = R.string.auth_password,
                error = state.passwordError,
                password = true,
            )
            ErrorText(state.errorRes)
            SubmitButton(R.string.auth_sign_in, state.submitting, viewModel::signIn)
            TextButton(onClick = onRegister) { Text(stringResource(R.string.auth_to_register)) }
        }
    }
}

@Composable
fun RegisterScreen(
    onLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthLayout {
        Text(stringResource(R.string.auth_register_title), style = MaterialTheme.typography.headlineMedium)
        FormColumn(Modifier.padding(top = 24.dp)) {
            FormField(
                value = state.fullName,
                onValueChange = viewModel::onFullNameChange,
                label = R.string.auth_full_name,
                error = state.fullNameError,
            )
            FormField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = R.string.auth_email,
                error = state.emailError,
                keyboardType = KeyboardType.Email,
            )
            FormField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = R.string.auth_password,
                error = state.passwordError,
                hint = R.string.auth_password_hint,
                password = true,
            )
            ErrorText(state.errorRes)
            SubmitButton(R.string.auth_sign_up, state.submitting, viewModel::signUp)
            TextButton(onClick = onLogin) { Text(stringResource(R.string.auth_to_login)) }
        }
    }
}

@Composable
private fun AuthLayout(content: @Composable () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}
