package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class ExerciseMusclesUiTest {
    @get:Rule val ui = createComposeRule()
    private val row = "Тяга горизонтального блока"
    private val bench = "Жим штанги лёжа"
    private val icon = hasContentDescription("Мышцы и техника")
    private var selected: String? = null

    @Test fun catalogOpensDetailsAndReturnsWithoutSelecting() {
        showCatalogRow()
        ui.onNode(icon).performClick()
        ui.onNodeWithText(row).assertIsDisplayed()
        ui.onNodeWithContentDescription("Основные мышцы: середина спины", substring = true).assertIsDisplayed()
        ui.onNodeWithContentDescription("Назад").performClick()
        ui.onNodeWithText("Каталог упражнений").assertIsDisplayed()
        ui.onNodeWithText(row).assertIsDisplayed()
        assertNull(selected)
    }

    @Test fun tappingCatalogRowSelectsExercise() {
        showCatalogRow()
        ui.onNodeWithText(row).performClick()
        ui.runOnIdle { assertEquals(row, selected) }
    }

    @Test fun workoutShowsMusclesOnlyForCatalogExercise() {
        val set = SetDraft(1, "bench", bench, "60", "8")
        val workout = Workout(1, listOf(set, set.copy(id = 2, group = "custom", exercise = "Мой жим")))
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) {
                WorkoutContent(WorkoutState(day = workout.day, list = listOf(workout), opened = workout.id), {})
            }
        }
        val list = ui.onNode(hasScrollToIndexAction())
        val customCard = hasTestTag("glass-card") and hasAnyDescendant(hasText("Мой жим"))
        list.performScrollToNode(customCard)
        ui.onNode(customCard).assertIsDisplayed()
        ui.onNode(icon and hasAnyAncestor(customCard)).assertDoesNotExist()
        list.performScrollToNode(icon)
        ui.onNode(icon).performClick()
        ui.onNodeWithText(bench).assertIsDisplayed()
        ui.onNodeWithText("Как делать").assertExists()
    }

    private fun showCatalogRow() {
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) { ExercisePicker({ selected = it.name }, {}) }
        }
        ui.onNodeWithText("Название или группа мышц").performTextInput("сидя нижний блок")
    }
}
