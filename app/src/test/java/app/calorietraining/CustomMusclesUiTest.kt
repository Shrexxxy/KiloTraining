package app.calorietraining

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class CustomMusclesUiTest {
    @get:Rule val ui = createComposeRule()
    private val workout = Workout(1, listOf(SetDraft(1, "custom", "Мой жим", "60", "8")))
    private val icon = hasContentDescription("Мышцы упражнения")

    @Test fun savesSelectionAndShowsItOnReopen() {
        showWorkout()
        openMuscles()
        select("грудь", 1)
        select("трицепс", 2)
        ui.onNodeWithText("Сохранить").performClick()
        ui.awaitUntil(5000) { ui.onAllNodes(icon).fetchSemanticsNodes().isNotEmpty() }
        openMuscles()
        assertRole("грудь", "Основная")
        assertRole("трицепс", "Дополнительная")
        ui.onNodeWithContentDescription("Основные мышцы: грудь", substring = true).assertExists()
    }

    @Test fun backDiscardsSelection() {
        showWorkout()
        openMuscles()
        select("грудь", 1)
        ui.onNodeWithContentDescription("Назад").performClick()
        openMuscles()
        assertRole("грудь", "Не работает")
    }

    @Test fun capturesCustomEditorDark() = captureEditor(ThemeMode.DARK, "custom-muscles-dark")
    @Test fun capturesCustomEditorLight() = captureEditor(ThemeMode.LIGHT, "custom-muscles-light")

    @Test fun capturesWorkoutIconDark() {
        val screen = showWorkout()
        ui.onNode(icon).assertIsDisplayed()
        screen.captureScreen("workout-custom-icon-dark.png")
    }

    private fun captureEditor(theme: ThemeMode, name: String) {
        val screen = showWorkout(theme)
        openMuscles()
        select("грудь", 1)
        screen.captureScreen("$name.png")
    }

    private fun showWorkout(theme: ThemeMode = ThemeMode.DARK) = ui.setScreenshotContent(theme) {
        WorkoutContent(WorkoutState(day = workout.day, list = listOf(workout), opened = workout.id), {})
    }

    private fun openMuscles() {
        ui.onNode(icon).performClick()
        ui.awaitUntil(5000) { ui.onAllNodes(hasText("Сохранить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun select(muscle: String, times: Int) {
        val chip = ui.onNodeWithText(muscle)
        chip.performScrollTo()
        repeat(times) { chip.performClick() }
    }

    private fun assertRole(muscle: String, role: String) {
        ui.onNodeWithText(muscle).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, role))
    }
}
