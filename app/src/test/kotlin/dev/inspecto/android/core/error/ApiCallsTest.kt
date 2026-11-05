package dev.inspecto.android.core.error

import dev.inspecto.android.R
import dev.inspecto.android.testing.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import retrofit2.Response
import java.io.IOException

class ApiCallsTest {
    private val problemType = "application/problem+json".toMediaType()

    @Test
    fun shouldReturnBodyOfSuccessfulResponse() =
        runTest {
            assertEquals("ok", apiCall { Response.success("ok") })
        }

    @Test
    fun shouldReturnUnitForEmptySuccessfulResponse() =
        runTest {
            assertEquals(Unit, apiCall<Unit> { Response.success(204, null as Unit?) })
        }

    @Test
    fun shouldTurnIoFailureIntoNetworkError() =
        runTest {
            assertFailsWith<AppError.Network> { apiCall<String> { throw IOException("offline") } }
        }

    @Test
    fun shouldTurnUnreadableBodyIntoInvalidResponse() =
        runTest {
            assertFailsWith<AppError.InvalidResponse> {
                apiCall<String> { throw SerializationException("unexpected token") }
            }
        }

    @Test
    fun shouldReadCodeAndFieldErrorsOfProblem() =
        runTest {
            val body =
                """{"code":"VALIDATION_ERROR","status":400,""" +
                    """"errors":[{"field":"password","code":"WEAK_PASSWORD"}],"extra":1}"""
            val error =
                assertFailsWith<AppError.Api> {
                    apiCall<String> { Response.error(400, body.toResponseBody(problemType)) }
                }

            assertEquals(400, error.status)
            assertEquals("VALIDATION_ERROR", error.code)
            assertEquals(mapOf("password" to "WEAK_PASSWORD"), error.fieldErrors)
        }

    @Test
    fun shouldKeepStatusWhenErrorBodyIsNotProblem() =
        runTest {
            val error =
                assertFailsWith<AppError.Api> {
                    apiCall<String> { Response.error(502, "<html>Bad gateway</html>".toResponseBody(problemType)) }
                }

            assertEquals(502, error.status)
            assertNull(error.code)
        }

    @ParameterizedTest
    @CsvSource(
        "INVALID_CREDENTIALS, error_invalid_credentials",
        "ACCOUNT_LOCKED, error_account_locked",
        "EMAIL_TAKEN, error_email_taken",
        "RATE_LIMITED, error_rate_limited",
        "NOT_FOUND, error_not_found",
        "VERSION_CONFLICT, error_version_conflict",
        "VALIDATION_ERROR, error_validation",
        "SOMETHING_NEW, error_unexpected",
    )
    fun shouldMapApiCodeToMessage(
        code: String,
        message: String,
    ) {
        val expected = R.string::class.java.getField(message).getInt(null)
        assertEquals(expected, AppError.Api(400, code).messageRes())
    }

    @Test
    fun shouldExplainWeakPasswordAndNetworkProblems() {
        val weak = AppError.Api(400, "VALIDATION_ERROR", mapOf("password" to "WEAK_PASSWORD"))

        assertEquals(R.string.error_weak_password, weak.messageRes())
        assertEquals(R.string.error_network, AppError.Network(IOException()).messageRes())
        assertEquals(R.string.error_unexpected, IllegalStateException().messageRes())
    }
}
