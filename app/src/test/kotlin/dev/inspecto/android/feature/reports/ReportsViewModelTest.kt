package dev.inspecto.android.feature.reports

import androidx.lifecycle.SavedStateHandle
import dev.inspecto.android.R
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.report.api.models.ReportJob
import dev.inspecto.android.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = mockk<ReportRepository>(relaxed = true)
    private val inspectionId: UUID = UUID.randomUUID()
    private val reportId: UUID = UUID.randomUUID()
    private val pdf = File("report.pdf")

    private fun viewModel() =
        ReportsViewModel(SavedStateHandle(mapOf("inspectionId" to inspectionId.toString())), repository)

    private fun job(
        status: ReportJob.Status,
        errorCode: String? = null,
    ) = ReportJob(
        id = reportId,
        inspectionId = inspectionId,
        status = status,
        createdAt = OffsetDateTime.parse("2026-11-07T10:00:00Z"),
        errorCode = errorCode,
    )

    @Test
    fun shouldWaitUntilReportIsReadyThenDownloadAndOpenIt() =
        runTest(main.dispatcher) {
            coEvery { repository.request(inspectionId, any()) } returns job(ReportJob.Status.QUEUED)
            coEvery { repository.get(reportId) } returnsMany
                listOf(job(ReportJob.Status.RUNNING), job(ReportJob.Status.DONE))
            coEvery { repository.list(inspectionId) } returns listOf(job(ReportJob.Status.DONE))
            coEvery { repository.download(reportId) } returns pdf
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.order()
            advanceTimeBy(2_100)
            assertTrue(viewModel.state.value.generating, "still waiting after the first poll")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.generating)
            assertEquals(pdf, state.fileToOpen)
            assertNull(state.errorRes)
            assertEquals(ReportJob.Status.DONE, state.reports.single().status)
            coVerify(exactly = 2) { repository.get(reportId) }
            viewModel.opened()
            assertNull(viewModel.state.value.fileToOpen)
        }

    @ParameterizedTest
    @CsvSource("REPORT_TOO_LARGE, too_large", "DEPENDENCY_UNAVAILABLE, unavailable", "RENDER_FAILED, other")
    fun shouldExplainFailedReport(
        code: String,
        kind: String,
    ) = runTest(main.dispatcher) {
        coEvery { repository.request(inspectionId, any()) } returns job(ReportJob.Status.FAILED, code)
        val viewModel = viewModel()

        viewModel.order()
        advanceUntilIdle()

        val expected =
            when (kind) {
                "too_large" -> R.string.report_failed_too_large
                "unavailable" -> R.string.report_failed_unavailable
                else -> R.string.report_failed
            }
        assertEquals(expected, viewModel.state.value.errorRes)
        coVerify(exactly = 0) { repository.download(any()) }
    }

    @Test
    fun shouldStopWaitingAfterOneMinute() =
        runTest(main.dispatcher) {
            coEvery { repository.request(inspectionId, any()) } returns job(ReportJob.Status.QUEUED)
            coEvery { repository.get(reportId) } returns job(ReportJob.Status.RUNNING)
            val viewModel = viewModel()

            viewModel.order()
            advanceUntilIdle()

            assertEquals(R.string.report_still_generating, viewModel.state.value.errorRes)
            assertFalse(viewModel.state.value.generating)
            coVerify(exactly = 30) { repository.get(reportId) }
        }

    @Test
    fun shouldRepeatLostOrderWithSameKeyAndNewKeyForNextOrder() =
        runTest(main.dispatcher) {
            val keys = mutableListOf<String>()
            val key = slot<String>()
            coEvery { repository.request(inspectionId, capture(key)) } answers {
                keys += key.captured
                if (keys.size == 1) throw AppError.Network(IOException("lost")) else job(ReportJob.Status.FAILED)
            }
            val viewModel = viewModel()

            viewModel.order()
            advanceUntilIdle()
            assertEquals(R.string.error_network, viewModel.state.value.errorRes)
            viewModel.order()
            advanceUntilIdle()
            viewModel.order()
            advanceUntilIdle()

            assertEquals(keys[0], keys[1])
            assertTrue(keys[2] != keys[1], "an accepted order releases its key")
        }

    @Test
    fun shouldOpenReadyReportAndExplainMissingViewer() =
        runTest(main.dispatcher) {
            coEvery { repository.download(reportId) } returns pdf
            val viewModel = viewModel()

            viewModel.open(job(ReportJob.Status.RUNNING))
            advanceUntilIdle()
            coVerify(exactly = 0) { repository.download(any()) }

            viewModel.open(job(ReportJob.Status.DONE))
            advanceUntilIdle()
            assertEquals(pdf, viewModel.state.value.fileToOpen)
            assertNull(viewModel.state.value.downloading)

            viewModel.noViewer()
            assertNull(viewModel.state.value.fileToOpen)
            assertEquals(R.string.report_no_viewer, viewModel.state.value.errorRes)
        }

    @Test
    fun shouldShowDownloadAndListErrors() =
        runTest(main.dispatcher) {
            coEvery { repository.list(inspectionId) } throws AppError.Network(IOException())
            coEvery { repository.download(reportId) } throws AppError.Api(409, "REPORT_NOT_READY")
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(R.string.error_network, viewModel.state.value.errorRes)
            assertFalse(viewModel.state.value.loading)

            viewModel.open(job(ReportJob.Status.DONE))
            advanceUntilIdle()

            assertEquals(R.string.report_not_ready, viewModel.state.value.errorRes)
            assertNull(viewModel.state.value.fileToOpen)
        }
}
