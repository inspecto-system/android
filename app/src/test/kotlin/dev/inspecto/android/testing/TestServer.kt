package dev.inspecto.android.testing

import dev.inspecto.android.api.apis.AuthApi
import dev.inspecto.android.core.auth.AuthInterceptor
import dev.inspecto.android.core.auth.SessionRepository
import dev.inspecto.android.core.auth.TokenAuthenticator
import dev.inspecto.android.core.auth.TokenCipher
import dev.inspecto.android.core.auth.TokenStore
import dev.inspecto.android.core.network.NetworkModule
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.Base64

/** Reversible stand-in for the Android Keystore cipher, which is not available on the JVM. */
class FakeCipher : TokenCipher {
    override fun encrypt(plain: String): String = "enc:" + Base64.getEncoder().encodeToString(plain.toByteArray())

    override fun decrypt(encrypted: String): String = String(Base64.getDecoder().decode(encrypted.removePrefix("enc:")))
}

/** The app's HTTP stack and session wired to a [MockWebServer], as Hilt wires them in the app. */
class TestServer {
    val server = MockWebServer().apply { start() }
    val store =
        TokenStore(
            InMemoryDataStore(),
            FakeCipher(),
        )
    private val baseUrl = server.url("/api/v1/").toString()
    private val json = NetworkModule.provideJson()
    private val plainClient = NetworkModule.provideOkHttpClient()
    private val plainRetrofit = NetworkModule.retrofit(plainClient, json, baseUrl)

    val session: SessionRepository =
        SessionRepository(
            authApi = plainRetrofit.create(AuthApi::class.java),
            authorizedAuthApi = { authorizedRetrofit.create(AuthApi::class.java) },
            store = store,
        )

    private val authorizedClient: OkHttpClient =
        plainClient
            .newBuilder()
            .addInterceptor(AuthInterceptor(session))
            .authenticator(TokenAuthenticator(session))
            .build()

    val authorizedRetrofit: Retrofit = NetworkModule.retrofit(authorizedClient, json, baseUrl)

    fun enqueue(
        code: Int,
        body: String = "",
    ) {
        server.enqueue(
            MockResponse
                .Builder()
                .code(code)
                .addHeader("Content-Type", if (code < 400) "application/json" else "application/problem+json")
                .body(body)
                .build(),
        )
    }

    fun close() = server.close()

    companion object {
        fun tokens(
            access: String,
            refresh: String,
        ) = """{"accessToken":"$access","tokenType":"Bearer","expiresIn":900,"refreshToken":"$refresh"}"""

        fun problem(
            status: Int,
            code: String,
            errors: String = "[]",
        ) = """{"type":"https://inspecto.dev/problems/x","title":"t","status":$status,"code":"$code",""" +
            """"requestId":"r","errors":$errors}"""
    }
}
