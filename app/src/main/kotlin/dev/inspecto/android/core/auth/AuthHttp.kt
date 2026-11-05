package dev.inspecto.android.core.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

private const val AUTHORIZATION = "Authorization"
private const val BEARER = "Bearer "

/** Adds the access token of the session to every request of the authorized HTTP client. */
class AuthInterceptor
    @Inject
    constructor(
        private val session: SessionRepository,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val token = session.accessToken ?: return chain.proceed(chain.request())
            return chain.proceed(chain.request().withToken(token))
        }
    }

/**
 * Renews the access token once when the server answers `401` and repeats the request; gives up
 * (returns `null`, so the `401` reaches the caller) when the session cannot be renewed.
 */
class TokenAuthenticator
    @Inject
    constructor(
        private val session: SessionRepository,
    ) : Authenticator {
        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            if (response.priorResponse != null) {
                return null
            }
            val rejected = response.request.header(AUTHORIZATION)?.removePrefix(BEARER)
            // OkHttp calls authenticators synchronously on its own thread.
            val token = runBlocking { session.refreshAfter(rejected) }
            return token?.let { response.request.withToken(it) }
        }
    }

private fun Request.withToken(token: String): Request = newBuilder().header(AUTHORIZATION, BEARER + token).build()
