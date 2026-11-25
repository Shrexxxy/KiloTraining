package app.calorietraining

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
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
class DeltoidsUiTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val upper = hasTestTag("muscle-figure-upper")
    private val full = hasTestTag("muscle-figure-full")

    @Test fun onlyRearDeltCanBeSelectedSavedAndReopened() {
        val workout = Workout(1, listOf(SetDraft(1, "custom", "Мой жим", "60", "8")))
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) {
                WorkoutContent(WorkoutState(day = workout.day, list = listOf(workout), opened = workout.id), {})
            }
        }
        val icon = hasContentDescription("Мышцы упражнения")
        ui.onNode(icon).performClick()
        ui.awaitUntil(5000) { ui.onAllNodes(hasText("Сохранить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("задняя дельта").performScrollTo().performClick()
        ui.onNodeWithText("Сохранить").performClick()
        ui.awaitUntil(5000) { ui.onAllNodes(icon).fetchSemanticsNodes().isNotEmpty() }
        FoodStore(app).use {
            assertEquals(ExerciseTechnique(listOf(Muscle.RearDelts), emptyList(), emptyList()), it.customMuscles()["мой жим"])
        }
        ui.onNode(icon).performClick()
        ui.awaitUntil(5000) { ui.onAllNodes(hasText("Сохранить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        for ((label, role) in listOf("передняя дельта" to "Не работает", "средняя дельта" to "Не работает", "задняя дельта" to "Основная")) {
            ui.onNodeWithText(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, role))
        }
        ui.onNodeWithContentDescription("Основные мышцы: задняя дельта", substring = true).assertExists()
    }

    @Test fun cardWithoutLegsShowsOnlyUpperFigure() {
        openCard("Отжимания на брусьях на грудь")
        ui.onNode(upper).assertExists()
        ui.onNode(full).assertDoesNotExist()
    }

    @Test fun cardWithAnyLegMuscleShowsWholeFigure() {
        openCard("Гоблет-присед")
        ui.onNode(full).assertExists()
        ui.onNode(upper).assertDoesNotExist()
    }

    @Test fun deltCardWithoutLegsIsUpperToo() {
        openCard("Подъём гантелей в стороны")
        ui.onNode(upper).assertExists()
    }

    private fun openCard(title: String) {
        ui.setContent { DiaryTheme(ThemeMode.DARK) { ExercisePicker({}, {}) } }
        ui.onNodeWithText("Название или группа мышц").performTextInput(title)
        ui.onNode(hasContentDescription("Мышцы и техника") and hasAnySibling(hasText(title))).performClick()
        ui.onNodeWithText(title).assertIsDisplayed()
    }
}
