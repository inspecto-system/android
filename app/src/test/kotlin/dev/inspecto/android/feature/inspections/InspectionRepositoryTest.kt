package dev.inspecto.android.feature.inspections

import dev.inspecto.android.api.apis.DictionariesApi
import dev.inspecto.android.api.apis.InspectionsApi
import dev.inspecto.android.feature.inspections.InspectionFixtures.bedroom
import dev.inspecto.android.feature.inspections.InspectionFixtures.details
import dev.inspecto.android.feature.inspections.InspectionFixtures.inspectionId
import dev.inspecto.android.feature.inspections.InspectionFixtures.kitchen
import dev.inspecto.android.feature.inspections.InspectionFixtures.now
import dev.inspecto.android.feature.inspections.InspectionFixtures.propertyId
import dev.inspecto.android.testing.TestServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/** Requests on the wire match platform/api/core.yaml. */
class InspectionRepositoryTest {
    private val server = TestServer()
    private val retrofit = server.authorizedRetrofit
    private val repository =
        InspectionRepository(
            retrofit.create(InspectionsApi::class.java),
            retrofit.create(DictionariesApi::class.java),
        )
    private val inspectionJson =
        """{"id":"$inspectionId","propertyId":"$propertyId","inspectorId":"$propertyId",
            "inspectedAt":"2026-11-06T10:00:00Z","status":"IN_PROGRESS","createdAt":"2026-11-06T10:00:00Z",
            "updatedAt":"2026-11-06T10:00:00Z","version":4}"""

    private val locale: Locale = Locale.getDefault()

    @AfterEach
    fun close() {
        Locale.setDefault(locale)
        server.close()
    }

    @Test
    fun shouldCreateInspectionWithOrderedRoomsAndLanguage() =
        runTest {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            server.enqueue(201, inspectionJson.replace("\"version\":4", "\"version\":0,\"rooms\":[]"))

            repository.create(
                propertyId,
                NewInspection(listOf(bedroom.id, kitchen.id), listOf("ROOM_STANDARD"), " ", now),
            )

            val request = server.server.takeRequest()
            assertEquals("/api/v1/properties/$propertyId/inspections", request.url.encodedPath)
            assertEquals("ru", request.headers["Accept-Language"])
            val body = body(request.body?.utf8())
            assertEquals(
                listOf(bedroom.id.toString(), kitchen.id.toString()),
                body.getValue("roomIds").jsonArray.map { it.jsonPrimitive.content },
            )
            assertEquals(
                "ROOM_STANDARD",
                body
                    .getValue("templateCodes")
                    .jsonArray
                    .single()
                    .jsonPrimitive.content,
            )
            assertEquals(false, body.containsKey("title"))
        }

    @Test
    fun shouldStartAndCompleteWithVersion() =
        runTest {
            server.enqueue(200, inspectionJson)
            server.enqueue(200, inspectionJson.replace("IN_PROGRESS", "COMPLETED"))

            repository.start(details())
            repository.complete(details())

            val start = server.server.takeRequest()
            assertEquals("PATCH", start.method)
            assertEquals("""{"version":3,"status":"IN_PROGRESS"}""", start.body?.utf8())
            val complete = server.server.takeRequest()
            assertEquals("/api/v1/inspections/$inspectionId/complete", complete.url.encodedPath)
            assertEquals("""{"version":3}""", complete.body?.utf8())
        }

    private fun body(text: String?): JsonObject = Json.parseToJsonElement(checkNotNull(text)).jsonObject
}
