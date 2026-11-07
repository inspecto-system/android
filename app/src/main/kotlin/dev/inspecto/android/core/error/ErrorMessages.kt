package dev.inspecto.android.core.error

import androidx.annotation.StringRes
import dev.inspecto.android.R

/** Message for the user; the API code decides, the HTTP status is only a fallback. */
@StringRes
fun Throwable.messageRes(): Int =
    when (this) {
        is AppError.Network -> R.string.error_network
        is AppError.InvalidResponse -> R.string.error_unexpected
        is AppError.Api -> apiMessage()
        else -> R.string.error_unexpected
    }

@StringRes
private fun AppError.Api.apiMessage(): Int =
    when (code) {
        "INVALID_CREDENTIALS" -> R.string.error_invalid_credentials
        "ACCOUNT_LOCKED" -> R.string.error_account_locked
        "EMAIL_TAKEN" -> R.string.error_email_taken
        "RATE_LIMITED" -> R.string.error_rate_limited
        "NOT_FOUND" -> R.string.error_not_found
        "VERSION_CONFLICT" -> R.string.error_version_conflict
        "INVALID_STATE_TRANSITION" -> R.string.error_invalid_state
        "REPORT_NOT_READY" -> R.string.report_not_ready
        "VALIDATION_ERROR" ->
            if ("WEAK_PASSWORD" in fieldErrors.values) R.string.error_weak_password else R.string.error_validation
        else -> R.string.error_unexpected
    }
