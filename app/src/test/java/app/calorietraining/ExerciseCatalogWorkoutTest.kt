package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h900dp")
class ExerciseCatalogWorkoutTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createComposeRule()

    @Test fun selectedExerciseIsSavedAndCountsInWeeklyMuscles() {
        val name = "Жим Арнольда"
        val anchor = "Жим штанги лёжа"
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            val workout = store.newWorkout(LocalDate.now())
            store.saveSet(workout, SetDraft(exercise = anchor, weight = "60", reps = "8"))
        }
        ui.setContent { DiaryTheme(ThemeMode.DARK) { DiaryApp(ThemeMode.DARK, {}) } }
        ui.onNode(hasText("Тренировки") and isSelectable()).performClick()
        await("Записать тренировку")
        val list = ui.onNode(hasScrollToIndexAction())
        val workoutCaption = LocalDate.now().caption()
        list.performScrollToNode(hasText(workoutCaption, substring = true))
        ui.onAllNodesWithText(workoutCaption, substring = true).onFirst().performClick()
        await("Редактировать")
        ui.onNodeWithText("Редактировать").performClick()
        list.performScrollToNode(hasText("Добавить упражнение"))
        ui.onNodeWithText("Добавить упражнение").performClick()
        ui.onNodeWithText("Выбрать из каталога").performClick()
        ui.onNodeWithText("Название или группа мышц").performTextInput("арнольд")
        ui.onNodeWithText(name).performClick()
        ui.onNodeWithText("Вес одной гантели, кг").performTextInput("12")
        ui.onNodeWithText("Повторы").performTextInput("8")
        ui.onNodeWithText("Записать подход").performScrollTo().performClick()
        await("Добавить упражнение")
        list.performScrollToNode(hasText(name))
        ui.onNodeWithText(name).assertExists()
        ui.onNodeWithContentDescription("Назад").performClick()
        ui.onNode(hasText("Профиль") and isSelectable()).performClick()
        await("Калории по дням")
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Мышцы за неделю"))
        ui.onNodeWithText("Передняя дельта — 1,5").assertExists()
        ui.onNodeWithText("Подходов без данных о мышцах", substring = true).assertDoesNotExist()
    }

    private fun await(text: String) {
        ui.awaitUntil(10_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
