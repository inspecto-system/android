package dev.inspecto.android.feature.reports

import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.report.api.apis.ReportsApi
import dev.inspecto.android.report.api.models.ReportJob
import dev.inspecto.android.testing.TestServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Locale
import java.util.UUID

/** Requests on the wire match platform/api/report.yaml; the PDF is stored byte for byte. */
class ReportRepositoryTest {
    private val server = TestServer()
    private val inspectionId: UUID = UUID.randomUUID()
    private val reportId: UUID = UUID.randomUUID()
    private val locale: Locale = Locale.getDefault()

    @TempDir
    lateinit var directory: File

    private val repository by lazy {
        ReportRepository(
            server.authorizedRetrofit.create(ReportsApi::class.java),
            server.authorizedRetrofit.create(ReportFileApi::class.java),
            directory,
        )
    }

    @AfterEach
    fun close() {
        Locale.setDefault(locale)
        server.close()
    }

    @Test
    fun shouldOrderReportInDeviceLanguageWithIdempotencyKey() =
        runTest {
            Locale.setDefault(Locale.forLanguageTag("en-GB"))
            server.enqueue(202, job("QUEUED"))

            val job = repository.request(inspectionId, "key-1")

            val request = server.server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/reports", request.url.encodedPath)
            assertEquals("key-1", request.headers["Idempotency-Key"])
            val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
            assertEquals(inspectionId.toString(), body["inspectionId"]!!.jsonPrimitive.content)
            assertEquals("en", body["locale"]!!.jsonPrimitive.content)
            assertEquals(ReportJob.Status.QUEUED, job.status)
        }

    @Test
    fun shouldListReportsOfInspection() =
        runTest {
            server.enqueue(200, "[${job("DONE", pages = 3)}]")

            val reports = repository.list(inspectionId)

            assertEquals(
                "inspectionId=$inspectionId",
                server.server
                    .takeRequest()
                    .url.query,
            )
            assertEquals(3, reports.single().pageCount)
        }

    @Test
    fun shouldDownloadPdfOnceAndReuseFile() =
        runTest {
            val pdf = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0xFF.toByte(), 0x0A)
            server.server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/pdf")
                    .body(Buffer().write(pdf))
                    .build(),
            )

            val file = repository.download(reportId)
            val again = repository.download(reportId)

            assertEquals(
                "/api/v1/reports/$reportId/file",
                server.server
                    .takeRequest()
                    .url.encodedPath,
            )
            assertArrayEquals(pdf, file.readBytes())
            assertEquals(file, again)
            assertEquals(1, server.server.requestCount)
            assertFalse(File(directory, "$reportId.part").exists())
        }

    @Test
    fun shouldReportNotReadyWithoutLeavingFile() =
        runTest {
            server.enqueue(409, TestServer.problem(409, "REPORT_NOT_READY"))

            val error = assertThrows<AppError.Api> { repository.download(reportId) }

            assertEquals("REPORT_NOT_READY", error.code)
            assertFalse(File(directory, "$reportId.pdf").exists())
        }

    private fun job(
        status: String,
        pages: Int? = null,
    ) = """{"id":"$reportId","inspectionId":"$inspectionId","status":"$status","locale":"ru",""" +
        """"createdAt":"2026-11-07T10:00:00Z"${pages?.let { ",\"pageCount\":$it,\"sizeBytes\":1024" }.orEmpty()}}"""
}
