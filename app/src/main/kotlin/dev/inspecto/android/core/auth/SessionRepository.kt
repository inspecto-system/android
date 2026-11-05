package dev.inspecto.android.core.auth

import dagger.Lazy
import dev.inspecto.android.api.apis.AuthApi
import dev.inspecto.android.api.models.LoginRequest
import dev.inspecto.android.api.models.RefreshRequest
import dev.inspecto.android.api.models.RegisterRequest
import dev.inspecto.android.api.models.TokenResponse
import dev.inspecto.android.core.error.apiCall
import dev.inspecto.android.core.network.AuthorizedApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the user is signed in; [UNKNOWN] until the stored session has been read. */
enum class SessionState { UNKNOWN, SIGNED_OUT, SIGNED_IN }

/**
 * Session of the signed-in user (ADR-007, ADR-008): the access token is kept in memory, the
 * refresh token in [TokenStore]. Refresh tokens rotate on every use, so concurrent refreshes are
 * serialized: a request that failed with an older access token reuses the token another request
 * has just obtained.
 */
@Singleton
class SessionRepository
    @Inject
    constructor(
        private val authApi: AuthApi,
        @AuthorizedApi private val authorizedAuthApi: Lazy<AuthApi>,
        private val store: TokenStore,
    ) {
        private val mutableState = MutableStateFlow(SessionState.UNKNOWN)
        private val refreshLock = Mutex()

        @Volatile
        var accessToken: String? = null
            private set

        val state: StateFlow<SessionState> = mutableState.asStateFlow()

        /** Reads the stored session after app start. */
        suspend fun restore() {
            mutableState.value = if (store.refreshToken() != null) SessionState.SIGNED_IN else SessionState.SIGNED_OUT
        }

        /** Signs in; the refresh token is returned in the body for the Android client. */
        suspend fun login(
            email: String,
            password: String,
        ) {
            val tokens =
                apiCall { authApi.login(LoginRequest(email.trim(), password, LoginRequest.Client.ANDROID)) }
            accept(tokens)
        }

        /** Creates an account and signs in with it. */
        suspend fun register(
            fullName: String,
            email: String,
            password: String,
        ) {
            apiCall { authApi.register(RegisterRequest(email.trim(), password, fullName.trim())) }
            login(email, password)
        }

        /** Signs out on the device at once and revokes the refresh token on the server if reachable. */
        suspend fun logout() {
            val refreshToken = store.refreshToken()
            try {
                if (refreshToken != null) {
                    authorizedAuthApi.get().logout(RefreshRequest(refreshToken))
                }
            } catch (ignored: IOException) {
                // Offline: the token expires on the server by itself (30 days).
            } finally {
                signOutLocally()
            }
        }

        /**
         * Returns an access token to retry a request that was rejected with [rejectedToken], or `null`
         * when the session has ended (the user is signed out) or the server is unreachable.
         */
        suspend fun refreshAfter(rejectedToken: String?): String? =
            refreshLock.withLock {
                val current = accessToken
                if (current != null && current != rejectedToken) {
                    return current
                }
                val refreshToken = store.refreshToken()
                if (refreshToken == null) {
                    signOutLocally()
                    return null
                }
                val response =
                    try {
                        authApi.refreshTokens(refreshRequest = RefreshRequest(refreshToken))
                    } catch (ignored: IOException) {
                        // Server unreachable: keep the session and let the request fail as a network error.
                        return null
                    }
                val tokens = response.body()
                when {
                    response.isSuccessful && tokens != null -> accept(tokens)
                    response.code() == HttpURLConnection.HTTP_UNAUTHORIZED -> signOutLocally()
                }
                accessToken
            }

        private suspend fun accept(tokens: TokenResponse) {
            tokens.refreshToken?.let { store.saveRefreshToken(it) }
            accessToken = tokens.accessToken
            mutableState.value = SessionState.SIGNED_IN
        }

        private suspend fun signOutLocally() {
            accessToken = null
            store.clear()
            mutableState.value = SessionState.SIGNED_OUT
        }
    }
