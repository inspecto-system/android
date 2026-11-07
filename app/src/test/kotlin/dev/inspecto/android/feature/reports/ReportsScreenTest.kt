package dev.inspecto.android.feature.reports

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.inspecto.android.report.api.models.ReportJob
import dev.inspecto.android.ui.theme.InspectoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime
import java.util.UUID

/** Reports list (Robolectric, Russian locale). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru")
class ReportsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun job(
        status: ReportJob.Status,
        errorCode: String? = null,
    ) = ReportJob(
        id = UUID.randomUUID(),
        inspectionId = UUID.randomUUID(),
        status = status,
        createdAt = OffsetDateTime.parse("2026-11-07T10:00:00Z"),
        pageCount = if (status == ReportJob.Status.DONE) 4 else null,
        sizeBytes = if (status == ReportJob.Status.DONE) 2_048_000 else null,
        errorCode = errorCode,
    )

    @Test
    fun readyReportCanBeOpenedAndFailedOneIsExplained() {
        val ready = job(ReportJob.Status.DONE)
        val opened = mutableListOf<ReportJob>()
        compose.setContent {
            InspectoTheme {
                ReportsContent(
                    ReportsState(
                        reports = listOf(ready, job(ReportJob.Status.FAILED, "REPORT_TOO_LARGE")),
                        loading = false,
                    ),
                    onOrder = {},
                    onOpen = { opened += it },
                )
            }
        }

        compose.onNodeWithText("Страниц: 4", substring = true).assertIsDisplayed()
        compose.onNodeWithText("слишком большим", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Открыть").performClick()
        assertEquals(listOf(ready), opened)
    }

    @Test
    fun orderButtonIsDisabledWhileGenerating() {
        compose.setContent {
            InspectoTheme {
                ReportsContent(ReportsState(loading = false, generating = true), onOrder = {}, onOpen = {})
            }
        }

        compose.onNodeWithText("Сформировать отчёт PDF").assertIsNotEnabled()
        compose.onNodeWithText("Отчёт формируется", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Отчётов пока нет.").assertIsDisplayed()
    }
}
