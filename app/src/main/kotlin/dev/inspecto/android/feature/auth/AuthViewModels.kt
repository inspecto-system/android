package dev.inspecto.android.feature.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.inspecto.android.R
import dev.inspecto.android.core.auth.SessionRepository
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.messageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shortest password accepted by the server (SECURITY.md §2). */
internal const val MIN_PASSWORD_LENGTH = 10

/**
 * Fields of the sign-in and registration forms. Field errors and [errorRes] are string resources;
 * successful sign-in changes the session state, which switches the app to the main screens.
 */
data class AuthFormState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    @StringRes val fullNameError: Int? = null,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    @StringRes val errorRes: Int? = null,
    val submitting: Boolean = false,
)

/** Common behaviour of the sign-in and registration forms. */
abstract class AuthFormViewModel : ViewModel() {
    protected val mutableState = MutableStateFlow(AuthFormState())
    val state: StateFlow<AuthFormState> = mutableState.asStateFlow()

    fun onEmailChange(value: String) =
        mutableState.update { it.copy(email = value, emailError = null, errorRes = null) }

    fun onPasswordChange(value: String) =
        mutableState.update { it.copy(password = value, passwordError = null, errorRes = null) }

    protected fun submit(action: suspend (AuthFormState) -> Unit) {
        val current = state.value
        if (current.submitting) {
            return
        }
        val validated = validate(current)
        mutableState.value = validated
        if (validated.fullNameError != null || validated.emailError != null || validated.passwordError != null) {
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(submitting = true, errorRes = null) }
            try {
                action(validated)
                mutableState.update { it.copy(submitting = false) }
            } catch (e: AppError) {
                mutableState.update { it.copy(submitting = false, errorRes = e.messageRes()) }
            }
        }
    }

    protected abstract fun validate(form: AuthFormState): AuthFormState

    protected fun emailError(email: String): Int? = if ('@' in email.trim()) null else R.string.validation_email
}

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val session: SessionRepository,
    ) : AuthFormViewModel() {
        fun signIn() = submit { session.login(it.email, it.password) }

        override fun validate(form: AuthFormState): AuthFormState =
            form.copy(
                emailError = emailError(form.email),
                passwordError = if (form.password.isEmpty()) R.string.validation_password_required else null,
            )
    }

@HiltViewModel
class RegisterViewModel
    @Inject
    constructor(
        private val session: SessionRepository,
    ) : AuthFormViewModel() {
        fun onFullNameChange(value: String) =
            mutableState.update { it.copy(fullName = value, fullNameError = null, errorRes = null) }

        fun signUp() = submit { session.register(it.fullName, it.email, it.password) }

        override fun validate(form: AuthFormState): AuthFormState =
            form.copy(
                fullNameError = if (form.fullName.isBlank()) R.string.validation_required else null,
                emailError = emailError(form.email),
                passwordError =
                    if (form.password.length < MIN_PASSWORD_LENGTH) R.string.validation_password_length else null,
            )
    }
