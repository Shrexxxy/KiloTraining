package app.calorietraining

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp")
class ProfileThemeTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()

    @Test fun selectsAndPersistsThemeFromProfile() {
        ui.onNodeWithText("Профиль").performClick()
        ui.onNodeWithText("Тёмная").performClick().assertIsSelected()
        assertEquals(ThemeMode.DARK, ThemeSettings(ui.activity).mode)
        ui.onNodeWithText("Авто").performClick().assertIsSelected()
        assertEquals(ThemeMode.SYSTEM, ThemeSettings(ui.activity).mode)
    }
}
