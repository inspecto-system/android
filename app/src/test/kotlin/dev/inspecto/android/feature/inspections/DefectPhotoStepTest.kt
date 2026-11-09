package dev.inspecto.android.feature.inspections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.inspecto.android.api.models.SuggestionItem
import dev.inspecto.android.feature.photos.PreparedPhoto
import dev.inspecto.android.ui.theme.InspectoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru")
class DefectPhotoStepTest {
    @get:Rule
    val compose = createComposeRule()

    private val crack = SuggestionItem(UUID.randomUUID(), "CRACK", "Трещина", BigDecimal("0.81"))
    private val stain = SuggestionItem(UUID.randomUUID(), "STAIN", "Пятно", BigDecimal("0.12"))
    private val photo = PreparedPhoto("p-1", File("p-1.jpg"))

    private fun show(
        state: DefectPhotoState,
        onChoose: (UUID) -> Unit = {},
    ) = compose.setContent {
        InspectoTheme { DefectPhotoStep(state, crack.categoryId, onTakePhoto = {}, onChoose = onChoose) }
    }

    @Test
    fun shouldShowSuggestionsWithPercentAndChooseOne() {
        var chosen: UUID? = null
        show(
            DefectPhotoState(
                DefectPhoto(photo, UUID.randomUUID(), Suggestions(UUID.randomUUID(), listOf(crack, stain))),
            ),
        ) {
            chosen = it
        }

        compose.onNodeWithText("Похоже на:").assertIsDisplayed()
        compose.onNodeWithText("Трещина — 81 %").assertIsSelected()
        compose.onNodeWithText("Пятно — 12 %").performClick()
        assertEquals(stain.categoryId, chosen)
        compose.onNodeWithText("Другое фото").assertIsDisplayed()
    }

    @Test
    fun shouldExplainWhenThereIsNoSuggestion() {
        show(DefectPhotoState(DefectPhoto(photo, UUID.randomUUID(), Suggestions(null, emptyList()))))

        compose.onNodeWithText("Подсказки нет — выберите категорию сами").assertIsDisplayed()
        compose.onNodeWithText("Фото прикрепится к дефекту").assertIsDisplayed()
    }

    @Test
    fun shouldExplainThatWithoutNetworkThePhotoWaits() {
        show(DefectPhotoState(DefectPhoto(photo, photoId = null)))

        compose.onNodeWithText("Нет сети: фото отправится позже, подсказки не будет").assertIsDisplayed()
    }

    @Test
    fun shouldOfferToTakeAPhoto() {
        show(DefectPhotoState())

        compose.onNodeWithText("Сфотографировать дефект").assertIsDisplayed()
    }
}
