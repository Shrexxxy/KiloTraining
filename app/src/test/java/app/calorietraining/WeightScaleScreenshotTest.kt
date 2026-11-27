package app.calorietraining

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class WeightScaleScreenshotTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createComposeRule()

    @Test fun cardDark() = card("weight-card-dark.png", ThemeMode.DARK, 1f)
    @Test fun cardLight() = card("weight-card-light.png", ThemeMode.LIGHT, 1f)
    @Test fun cardLargeFont() = card("weight-card-font2-dark.png", ThemeMode.DARK, 2f)
    @Test fun dialogDark() = dialog("weight-dialog-dark.png", ThemeMode.DARK, 1f)
    @Test fun dialogLight() = dialog("weight-dialog-light.png", ThemeMode.LIGHT, 1f)
    @Test fun dialogLargeFontDark() = dialog("weight-dialog-font2-dark.png", ThemeMode.DARK, 2f)
    @Test fun dialogLargeFontLight() = dialog("weight-dialog-font2-light.png", ThemeMode.LIGHT, 2f)

    private fun show(mode: ThemeMode, fontScale: Float): ScreenCapture {
        // Окно диалога берёт плотность из конфигурации, а не из композиции экрана.
        RuntimeEnvironment.setFontScale(fontScale)
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            store.saveMeasurement(Measurement(LocalDate.now().minusDays(2), "82.8"))
            store.saveMeasurement(Measurement(LocalDate.now().minusDays(1), "82.4"))
        }
        val screen = ui.setScreenshotContent(mode) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                DiaryApp(mode, {})
            }
        }
        ui.awaitUntil(10_000) { ui.onAllNodesWithText("82,4 кг").fetchSemanticsNodes().isNotEmpty() }
        return screen
    }

    private fun card(filename: String, mode: ThemeMode, fontScale: Float) {
        val screen = show(mode, fontScale)
        ui.onNodeWithText("Записать вес").performScrollTo().assertIsDisplayed()
        screen.captureScreen(filename).recycle()
    }

    private fun dialog(filename: String, mode: ThemeMode, fontScale: Float) {
        val screen = show(mode, fontScale)
        ui.onNodeWithText("Записать вес").performScrollTo().performClick()
        ui.awaitUntil(10_000) { ui.onAllNodesWithTag("weight-readout").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("weight-readout").performTextReplacement("500")
        ui.onNode(hasText("Сохранить") and hasClickAction()).assertIsDisplayed()
        ui.onNodeWithContentDescription("Увеличить на 0,1").assertIsDisplayed()
        ui.onNodeWithContentDescription("Уменьшить на 0,1").assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        ui.onNodeWithTag("weight-readout").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertFalse(results.single().didOverflowWidth)
        ui.onNodeWithTag("weight-readout").performTextReplacement("82,4")
        screen.captureScreen(filename, includeDialog = true).recycle()
    }
}
