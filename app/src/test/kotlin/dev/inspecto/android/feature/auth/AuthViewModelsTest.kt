package dev.inspecto.android.feature.auth

import dev.inspecto.android.R
import dev.inspecto.android.core.auth.SessionRepository
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val session = mockk<SessionRepository>(relaxed = true)

    @Test
    fun shouldValidateLoginFormBeforeCallingServer() =
        runTest(main.dispatcher) {
            val viewModel = LoginViewModel(session)

            viewModel.signIn()
            advanceUntilIdle()

            assertEquals(R.string.validation_email, viewModel.state.value.emailError)
            assertEquals(R.string.validation_password_required, viewModel.state.value.passwordError)
            coVerify(exactly = 0) { session.login(any(), any()) }
        }

    @Test
    fun shouldSignInAndClearErrorsWhileTyping() =
        runTest(main.dispatcher) {
            val viewModel = LoginViewModel(session)
            viewModel.signIn()
            viewModel.onEmailChange("user@example.com")
            viewModel.onPasswordChange("secret")

            viewModel.signIn()
            advanceUntilIdle()

            coVerify { session.login("user@example.com", "secret") }
            assertNull(viewModel.state.value.emailError)
            assertFalse(viewModel.state.value.submitting)
        }

    @Test
    fun shouldShowServerErrorOfSignIn() =
        runTest(main.dispatcher) {
            coEvery { session.login(any(), any()) } throws AppError.Api(401, "INVALID_CREDENTIALS")
            val viewModel = LoginViewModel(session)
            viewModel.onEmailChange("user@example.com")
            viewModel.onPasswordChange("wrong")

            viewModel.signIn()
            advanceUntilIdle()

            assertEquals(R.string.error_invalid_credentials, viewModel.state.value.errorRes)
            assertFalse(viewModel.state.value.submitting)

            viewModel.onPasswordChange("other")
            assertNull(viewModel.state.value.errorRes)
        }

    @Test
    fun shouldRequireNameAndLongPasswordForRegistration() =
        runTest(main.dispatcher) {
            val viewModel = RegisterViewModel(session)
            viewModel.onFullNameChange("  ")
            viewModel.onEmailChange("user@example.com")
            viewModel.onPasswordChange("short")

            viewModel.signUp()
            advanceUntilIdle()

            assertEquals(R.string.validation_required, viewModel.state.value.fullNameError)
            assertEquals(R.string.validation_password_length, viewModel.state.value.passwordError)
            coVerify(exactly = 0) { session.register(any(), any(), any()) }
        }

    @Test
    fun shouldRegisterAndReportNetworkProblem() =
        runTest(main.dispatcher) {
            coEvery { session.register(any(), any(), any()) } throws AppError.Network(IOException())
            val viewModel = RegisterViewModel(session)
            viewModel.onFullNameChange("Иван Петров")
            viewModel.onEmailChange("user@example.com")
            viewModel.onPasswordChange("Kitchen-Window-42")

            viewModel.signUp()
            advanceUntilIdle()

            coVerify { session.register("Иван Петров", "user@example.com", "Kitchen-Window-42") }
            assertEquals(R.string.error_network, viewModel.state.value.errorRes)
            assertNull(viewModel.state.value.fullNameError)
        }
}
