package dev.inspecto.android.core.network

import dev.inspecto.android.api.apis.DictionariesApi
import dev.inspecto.android.api.apis.PropertiesApi
import dev.inspecto.android.api.models.SeverityLevel
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import java.time.OffsetDateTime
import java.util.UUID

/** The generated client works with the HTTP stack of [NetworkModule] against real JSON. */
class NetworkModuleTest {
    private val server = MockWebServer()
    private lateinit var retrofit: Retrofit

    @BeforeEach
    fun startServer() {
        server.start()
        retrofit =
            NetworkModule.provideRetrofit(
                NetworkModule.provideOkHttpClient(),
                NetworkModule.provideJson(),
                server.url("/api/v1/").toString(),
            )
    }

    @AfterEach
    fun stopServer() {
        server.close()
    }

    @Test
    fun shouldSendEnumHeaderAsContractValueAndParseList() =
        runTest {
            server.enqueue(json("""[{"code":"CRITICAL","name":"Critical","rank":4}]"""))

            val response =
                retrofit
                    .create(DictionariesApi::class.java)
                    .listSeverities(DictionariesApi.AcceptLanguageListSeverities.EN)

            val request = server.takeRequest()
            assertEquals("/api/v1/dictionaries/severities", request.url.encodedPath)
            assertEquals("en", request.headers["Accept-Language"])
            assertEquals(listOf(SeverityLevel(SeverityLevel.Code.CRITICAL, "Critical", 4)), response.body())
        }

    @Test
    fun shouldParseIdentifiersTimestampsAndIgnoreUnknownFields() =
        runTest {
            val id = UUID.randomUUID()
            server.enqueue(
                json(
                    """
                    {"id":"$id","name":"Квартира","type":"APARTMENT","address":"ул. Мира, 1",
                     "city":null,"archivedAt":null,"createdAt":"2026-11-05T10:00:00Z",
                     "updatedAt":"2026-11-05T10:00:00Z","version":0,"rooms":[],
                     "inspectionCount":0,"openDefectCount":0,"futureField":{"x":1}}
                    """.trimIndent(),
                ),
            )

            val property = retrofit.create(PropertiesApi::class.java).getProperty(id).body()!!

            assertEquals(id, property.id)
            assertEquals("Квартира", property.name)
            assertEquals(OffsetDateTime.parse("2026-11-05T10:00:00Z"), property.createdAt)
            assertNull(property.city)
            assertTrue(property.rooms.isEmpty())
            assertEquals("/api/v1/properties/$id", server.takeRequest().url.encodedPath)
        }

    @Test
    fun shouldUseConfiguredBaseUrl() {
        assertTrue(NetworkModule.provideApiBaseUrl().endsWith("/api/v1/"))
    }

    private fun json(body: String) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
}
