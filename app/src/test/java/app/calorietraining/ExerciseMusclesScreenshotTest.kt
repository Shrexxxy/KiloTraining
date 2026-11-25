package app.calorietraining

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class ExerciseMusclesScreenshotTest {
    @get:Rule val ui = createComposeRule()
    private lateinit var screen: ScreenCapture

    @Test fun cableRowDark() = captureDetails(ThemeMode.DARK, "Тяга горизонтального блока", "muscles-card-dark")
    @Test fun cableRowLight() = captureDetails(ThemeMode.LIGHT, "Тяга горизонтального блока", "muscles-card-light")
    @Test fun squatDark() = captureDetails(ThemeMode.DARK, "Приседания со штангой", "muscles-squat-dark")

    @Test fun catalogLight() {
        catalog(ThemeMode.LIGHT)
        capture("catalog-light")
    }

    @Test fun catalogDark() {
        catalog(ThemeMode.DARK)
        ui.onAllNodesWithContentDescription("Мышцы и техника").onFirst().assertIsDisplayed()
        capture("catalog-muscles-icon-dark")
    }

    private fun captureDetails(theme: ThemeMode, title: String, filename: String) {
        catalog(theme)
        ui.onNodeWithText("Название или группа мышц").performTextInput(title)
        ui.onNode(hasContentDescription("Мышцы и техника") and hasAnySibling(hasText(title))).performClick()
        ui.onNodeWithText(title).assertIsDisplayed()
        capture(filename)
    }

    private fun catalog(theme: ThemeMode) {
        screen = ui.setScreenshotContent(theme, training = true) { ExercisePicker({}, {}, Modifier.padding(horizontal = 20.dp)) }
    }

    private fun capture(filename: String) {
        screen.captureScreen("$filename.png")
    }
}
