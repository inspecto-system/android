package dev.inspecto.android.feature.comparison

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.inspecto.android.api.models.ComparedDefect
import dev.inspecto.android.feature.comparison.ComparisonFixtures.comparison
import dev.inspecto.android.feature.comparison.ComparisonFixtures.defect
import dev.inspecto.android.feature.comparison.ComparisonFixtures.inspection
import dev.inspecto.android.ui.theme.InspectoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Comparison screen (Robolectric, Russian locale). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru")
class ComparisonScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val base = inspection("2026-10-01", "Приёмка")
    private val target = inspection("2026-11-01", "Повторный")

    @Test
    fun showsSummaryDefectsByRoomAndFilters() {
        val filters = mutableListOf<ComparedDefect.State>()
        val defects =
            listOf(
                defect("Трещина у окна", ComparedDefect.State.RESOLVED),
                defect("Пятно на потолке", ComparedDefect.State.NEW, reopened = true),
                defect("Царапина", ComparedDefect.State.REMAINING, inKitchen = false),
            )
        compose.setContent {
            InspectoTheme {
                ComparisonContent(
                    ComparisonState(
                        inspections = listOf(base, target),
                        base = base.id,
                        target = target.id,
                        comparison = comparison(base, target, defects),
                        loading = false,
                    ),
                    ComparisonActions({}, {}, { filters += it }),
                )
            }
        }

        compose.onNodeWithText("01.10.2026 · Приёмка").assertIsDisplayed()
        compose.onNodeWithText("Устранены: 1").assertIsDisplayed()
        compose.onNodeWithText("Не проверены: 0").assertIsDisplayed()
        compose.onNodeWithText("Остались: 1").performClick()
        assertEquals(listOf(ComparedDefect.State.REMAINING), filters)
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText("Новые · снова появился"))
        compose.onNodeWithText("Новые · снова появился").assertIsDisplayed()
        list.performScrollToNode(hasText("Спальня"))
        compose.onNodeWithText("Спальня").assertIsDisplayed()
    }

    @Test
    fun explainsThatTwoInspectionsAreNeeded() {
        compose.setContent {
            InspectoTheme {
                ComparisonContent(
                    ComparisonState(inspections = listOf(base), loading = false),
                    ComparisonActions({}, {}, {}),
                )
            }
        }

        compose.onNodeWithText("Для сравнения нужно хотя бы два осмотра.").assertIsDisplayed()
    }
}
