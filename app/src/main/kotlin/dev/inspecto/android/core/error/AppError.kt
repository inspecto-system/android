package dev.inspecto.android.core.error

import java.net.HttpURLConnection

/** Failure of a server call, already classified for the UI. */
sealed class AppError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** The server could not be reached (no network, timeout, server down). */
    class Network(
        cause: Throwable,
    ) : AppError("Network error", cause)

    /** The server answered with a body the app cannot read (for example, a newer, incompatible API). */
    class InvalidResponse(
        cause: Throwable,
    ) : AppError("Invalid response", cause)

    /**
     * The server answered with an error (RFC 9457 problem details).
     *
     * @property code machine-readable code of the API, for example {@code INVALID_CREDENTIALS}
     * @property fieldErrors field name to field error code, for example {@code password} to
     *     {@code WEAK_PASSWORD}
     */
    class Api(
        val status: Int,
        val code: String?,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : AppError("API error $status ${code.orEmpty()}")
}

private const val TOO_MANY_REQUESTS = 429

/**
 * Whether repeating the same request later may succeed: no network, a timeout, rate limiting, a
 * server failure, or an expired session that a new sign-in renews. Other errors need the user.
 */
fun AppError.isTemporary(): Boolean =
    when (this) {
        is AppError.Network -> true
        is AppError.InvalidResponse -> false
        is AppError.Api ->
            status == HttpURLConnection.HTTP_UNAUTHORIZED ||
                status == HttpURLConnection.HTTP_CLIENT_TIMEOUT ||
                status == TOO_MANY_REQUESTS ||
                status >= HttpURLConnection.HTTP_INTERNAL_ERROR
    }
