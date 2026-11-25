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
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h1200dp")
class DeltoidsScreenshotTest {
    @get:Rule val ui = createComposeRule()

    @Test fun frontDark() = card(ThemeMode.DARK, "Жим гантелей сидя", "deltoids-front-dark")
    @Test fun frontLight() = card(ThemeMode.LIGHT, "Жим гантелей сидя", "deltoids-front-light")
    @Test fun sideDark() = card(ThemeMode.DARK, "Подъём гантелей в стороны", "deltoids-side-dark")
    @Test fun sideLight() = card(ThemeMode.LIGHT, "Подъём гантелей в стороны", "deltoids-side-light")
    @Test fun rearDark() = card(ThemeMode.DARK, "Разведение гантелей в наклоне", "deltoids-rear-dark")
    @Test fun rearLight() = card(ThemeMode.LIGHT, "Разведение гантелей в наклоне", "deltoids-rear-light")
    @Test fun dipsDark() = card(ThemeMode.DARK, "Отжимания на брусьях на грудь", "deltoids-dips-dark")
    @Test fun dipsLight() = card(ThemeMode.LIGHT, "Отжимания на брусьях на грудь", "deltoids-dips-light")
    @Test fun gobletDark() = card(ThemeMode.DARK, "Гоблет-присед", "deltoids-goblet-dark")
    @Test fun gobletLight() = card(ThemeMode.LIGHT, "Гоблет-присед", "deltoids-goblet-light")
    @Test fun editorDark() = editor(ThemeMode.DARK, "deltoids-editor-dark")
    @Test fun editorLight() = editor(ThemeMode.LIGHT, "deltoids-editor-light")

    private fun card(theme: ThemeMode, title: String, filename: String) {
        val screen = ui.setScreenshotContent(theme) { ExercisePicker({}, {}, Modifier.padding(horizontal = 20.dp)) }
        ui.onNodeWithText("Название или группа мышц").performTextInput(title)
        ui.onNode(hasContentDescription("Мышцы и техника") and hasAnySibling(hasText(title))).performClick()
        ui.onNodeWithText(title).assertIsDisplayed()
        screen.captureScreen("$filename.png")
    }

    private fun editor(theme: ThemeMode, filename: String) {
        val workout = Workout(1, listOf(SetDraft(1, "custom", "Мой жим", "60", "8")))
        val screen = ui.setScreenshotContent(theme) {
            WorkoutContent(WorkoutState(day = workout.day, list = listOf(workout), opened = workout.id), {})
        }
        ui.onNode(hasContentDescription("Мышцы упражнения")).performClick()
        ui.awaitUntil(5000) { ui.onAllNodes(hasText("Сохранить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("передняя дельта").performScrollTo().performClick()
        ui.onNodeWithText("средняя дельта").performScrollTo().performClick().performClick()
        ui.onNodeWithText("задняя дельта").performScrollTo()
        screen.captureScreen("$filename.png")
    }
}
