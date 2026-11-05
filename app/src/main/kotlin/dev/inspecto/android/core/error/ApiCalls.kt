package dev.inspecto.android.core.error

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/** Fields of RFC 9457 problem details used by the app (platform/api/common.yaml, Problem). */
@Serializable
internal data class Problem(
    val code: String? = null,
    val errors: List<ProblemFieldError> = emptyList(),
)

@Serializable
internal data class ProblemFieldError(
    val field: String,
    val code: String,
)

private val problemJson = Json { ignoreUnknownKeys = true }

/**
 * Runs a generated API call and returns its body.
 *
 * @throws AppError.Network when the server is unreachable
 * @throws AppError.Api when the server answers with an error status
 * @throws AppError.InvalidResponse when the body of the answer cannot be read
 */
suspend fun <T> apiCall(call: suspend () -> Response<T>): T {
    val response = respond(call)
    if (!response.isSuccessful) {
        throw toApiError(response)
    }
    @Suppress("UNCHECKED_CAST") // a successful response of an operation without a body is Unit
    return response.body() ?: Unit as T
}

/** Classifies failures of the transport and of reading the body. */
private suspend fun <T> respond(call: suspend () -> Response<T>): Response<T> =
    try {
        call()
    } catch (e: IOException) {
        throw AppError.Network(e)
    } catch (e: SerializationException) {
        throw AppError.InvalidResponse(e)
    }

internal fun toApiError(response: Response<*>): AppError.Api {
    val problem =
        response.errorBody()?.string()?.let { body ->
            try {
                problemJson.decodeFromString(Problem.serializer(), body)
            } catch (ignored: SerializationException) {
                null // not problem details (for example an HTML page of a proxy): only the status is known
            } catch (ignored: IllegalArgumentException) {
                null
            }
        }
    return AppError.Api(
        status = response.code(),
        code = problem?.code,
        fieldErrors = problem?.errors.orEmpty().associate { it.field to it.code },
    )
}
