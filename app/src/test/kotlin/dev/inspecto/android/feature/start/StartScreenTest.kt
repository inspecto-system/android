package dev.inspecto.android.feature.start

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.inspecto.android.ui.navigation.InspectoNavHost
import dev.inspecto.android.ui.theme.InspectoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI on the JVM (Robolectric); runs on the JUnit 4 API through the vintage engine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "ru")
    fun shouldOpenStartScreenWithAppNameAndPurpose() {
        compose.setContent { InspectoTheme { InspectoNavHost() } }
        compose.waitForIdle()

        compose.onNodeWithText("Inspecto").assertIsDisplayed()
        compose.onNodeWithText("Осмотр и приёмка недвижимости").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun shouldShowEnglishTextsInEnglishLocale() {
        compose.setContent { InspectoTheme(darkTheme = true) { StartScreen() } }

        compose.onNodeWithText("Property inspection and acceptance").assertIsDisplayed()
    }
}
