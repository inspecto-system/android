package dev.inspecto.android.core.auth

import dev.inspecto.android.api.apis.PropertiesApi
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.testing.TestServer
import dev.inspecto.android.testing.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** Session and token renewal over the real HTTP stack (ADR-007, ADR-008). */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {
    private lateinit var api: TestServer

    private fun start(): TestServer = TestServer().also { api = it }

    @AfterEach
    fun close() {
        if (::api.isInitialized) {
            api.close()
        }
    }

    @Test
    fun shouldSignInAsAndroidClientAndKeepRefreshTokenEncrypted() =
        runTest {
            val api = start()
            api.enqueue(200, TestServer.tokens("access-1", "refresh-1"))

            api.session.login(" user@example.com ", "Kitchen-Window-42")

            val request = api.server.takeRequest()
            assertEquals("/api/v1/auth/login", request.url.encodedPath)
            assertTrue(request.body!!.utf8().contains(""""client":"android""""))
            assertTrue(request.body!!.utf8().contains(""""email":"user@example.com""""))
            assertEquals(SessionState.SIGNED_IN, api.session.state.value)
            assertEquals("access-1", api.session.accessToken)
            assertEquals("refresh-1", api.store.refreshToken())
        }

    @Test
    fun shouldReportWrongCredentialsAsApiError() =
        runTest {
            val api = start()
            api.enqueue(401, TestServer.problem(401, "INVALID_CREDENTIALS"))

            val error = assertFailsWith<AppError.Api> { api.session.login("user@example.com", "wrong-password") }

            assertEquals("INVALID_CREDENTIALS", error.code)
            assertEquals(SessionState.UNKNOWN, api.session.state.value)
        }

    @Test
    fun shouldRegisterAndThenSignIn() =
        runTest {
            val api = start()
            api.enqueue(
                201,
                """{"id":"${UUID.randomUUID()}","email":"a@b.c","fullName":"Тест","role":"INSPECTOR",""" +
                    """"locale":"ru","createdAt":"2026-11-05T10:00:00Z","version":0}""",
            )
            api.enqueue(200, TestServer.tokens("access-1", "refresh-1"))

            api.session.register(" Иван Петров ", "a@b.c", "Kitchen-Window-42")

            assertTrue(
                api.server
                    .takeRequest()
                    .body!!
                    .utf8()
                    .contains(""""fullName":"Иван Петров""""),
            )
            assertEquals(
                "/api/v1/auth/login",
                api.server
                    .takeRequest()
                    .url.encodedPath,
            )
            assertEquals(SessionState.SIGNED_IN, api.session.state.value)
        }

    @Test
    fun shouldRestoreSessionFromStoredRefreshToken() =
        runTest {
            val api = start()
            api.session.restore()
            assertEquals(SessionState.SIGNED_OUT, api.session.state.value)

            api.store.saveRefreshToken("refresh-1")
            api.session.restore()
            assertEquals(SessionState.SIGNED_IN, api.session.state.value)
        }

    @Test
    fun shouldRenewExpiredAccessTokenAndRepeatRequest() =
        runTest {
            val api = start()
            api.enqueue(200, TestServer.tokens("access-1", "refresh-1"))
            api.session.login("user@example.com", "Kitchen-Window-42")
            api.server.takeRequest()
            api.enqueue(401, TestServer.problem(401, "TOKEN_EXPIRED"))
            api.enqueue(200, TestServer.tokens("access-2", "refresh-2"))
            api.enqueue(200, """{"page":0,"size":20,"total":0,"items":[]}""")

            val response = api.authorizedRetrofit.create(PropertiesApi::class.java).listProperties()

            assertTrue(response.isSuccessful)
            assertEquals("Bearer access-1", api.server.takeRequest().headers["Authorization"])
            val refresh: RecordedRequest = api.server.takeRequest()
            assertEquals("/api/v1/auth/refresh", refresh.url.encodedPath)
            assertTrue(refresh.body!!.utf8().contains("refresh-1"))
            assertEquals("Bearer access-2", api.server.takeRequest().headers["Authorization"])
            assertEquals("refresh-2", api.store.refreshToken())
        }

    @Test
    fun shouldSignOutWhenRefreshTokenIsRejected() =
        runTest {
            val api = start()
            api.store.saveRefreshToken("revoked")
            api.session.restore()
            api.enqueue(401, TestServer.problem(401, "UNAUTHENTICATED"))
            api.enqueue(401, TestServer.problem(401, "UNAUTHENTICATED"))

            val response = api.authorizedRetrofit.create(PropertiesApi::class.java).listProperties()

            assertEquals(401, response.code())
            assertEquals(SessionState.SIGNED_OUT, api.session.state.value)
            assertNull(api.store.refreshToken())
        }

    @Test
    fun shouldReuseTokenRenewedByAnotherRequest() =
        runTest {
            val api = start()
            api.store.saveRefreshToken("refresh-1")
            api.enqueue(200, TestServer.tokens("access-2", "refresh-2"))

            assertEquals("access-2", api.session.refreshAfter("access-1"))
            assertEquals("access-2", api.session.refreshAfter("access-1"))

            assertEquals(1, api.server.requestCount)
        }

    @Test
    fun shouldKeepSessionWhenServerIsUnreachableDuringRefresh() =
        runTest {
            val api = start()
            api.store.saveRefreshToken("refresh-1")
            api.session.restore()
            api.close()

            assertNull(api.session.refreshAfter(null))

            assertEquals(SessionState.SIGNED_IN, api.session.state.value)
            assertEquals("refresh-1", api.store.refreshToken())
        }

    @Test
    fun shouldRevokeRefreshTokenOnLogout() =
        runTest {
            val api = start()
            api.enqueue(200, TestServer.tokens("access-1", "refresh-1"))
            api.session.login("user@example.com", "Kitchen-Window-42")
            api.server.takeRequest()
            api.enqueue(204)

            api.session.logout()

            val logout = api.server.takeRequest()
            assertEquals("/api/v1/auth/logout", logout.url.encodedPath)
            assertEquals("Bearer access-1", logout.headers["Authorization"])
            assertTrue(logout.body!!.utf8().contains("refresh-1"))
            assertEquals(SessionState.SIGNED_OUT, api.session.state.value)
            assertNull(api.session.accessToken)
            assertNull(api.store.refreshToken())
        }

    @Test
    fun shouldSignOutLocallyWhenOffline() =
        runTest {
            val api = start()
            api.store.saveRefreshToken("refresh-1")
            api.close()

            api.session.logout()

            assertEquals(SessionState.SIGNED_OUT, api.session.state.value)
            assertNull(api.store.refreshToken())
        }
}
