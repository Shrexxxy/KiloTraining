package app.calorietraining

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class CustomMusclesUiTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
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

    @Test fun saveErrorKeepsScreenAndSelectionForRetry() {
        showWorkout()
        openMuscles()
        select("грудь", 1)
        select("трицепс", 2)
        FoodStore(app).use {
            it.writableDatabase.execSQL("""CREATE TRIGGER reject_muscles BEFORE INSERT ON exercise_muscle
                BEGIN SELECT RAISE(ABORT, 'запись отклонена'); END""")
        }
        try {
            ui.onNodeWithText("Сохранить").performClick()
            ui.awaitUntil(5000) { ui.onAllNodesWithText("Не удалось сохранить мышцы").fetchSemanticsNodes().isNotEmpty() }
            ui.awaitUntil(5000) { ui.onAllNodes(hasText("Сохранить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Не удалось сохранить мышцы").assertIsDisplayed()
            ui.onNode(icon).assertDoesNotExist()
            assertRole("грудь", "Основная")
            assertRole("трицепс", "Дополнительная")
        } finally {
            FoodStore(app).use { it.writableDatabase.execSQL("DROP TRIGGER reject_muscles") }
        }
        FoodStore(app).use { assertTrue(it.customMuscles().isEmpty()) }
        ui.onNodeWithText("Сохранить").assertIsEnabled().performClick()
        ui.awaitUntil(5000) { ui.onAllNodes(icon).fetchSemanticsNodes().isNotEmpty() }
        assertSaved()
        openMuscles()
        assertRole("грудь", "Основная")
        assertRole("трицепс", "Дополнительная")
    }

    @Test fun recreationKeepsUnsavedSelection() {
        val restored = StateRestorationTester(ui)
        restored.setContent { DiaryTheme(ThemeMode.DARK) { Content() } }
        openMuscles()
        select("грудь", 1)
        select("трицепс", 2)
        restored.emulateSavedInstanceStateRestore()
        ui.awaitUntil(5000) { ui.onAllNodes(hasText("Сохранить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Мой жим").assertIsDisplayed()
        ui.onNode(icon).assertDoesNotExist()
        assertRole("грудь", "Основная")
        assertRole("трицепс", "Дополнительная")
        FoodStore(app).use { assertTrue(it.customMuscles().isEmpty()) }
        ui.onNodeWithText("Сохранить").performClick()
        ui.awaitUntil(5000) { ui.onAllNodes(icon).fetchSemanticsNodes().isNotEmpty() }
        assertSaved()
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

    private fun showWorkout(theme: ThemeMode = ThemeMode.DARK) = ui.setScreenshotContent(theme) { Content() }

    @Composable
    private fun Content() =
        WorkoutContent(WorkoutState(day = workout.day, list = listOf(workout), opened = workout.id), {})

    private fun assertSaved() = FoodStore(app).use {
        assertEquals(mapOf("мой жим" to ExerciseTechnique(listOf(Muscle.Chest), listOf(Muscle.Triceps), emptyList())),
            it.customMuscles())
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
