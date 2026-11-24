package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class ProfileHistoryStyleTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val today = LocalDate.now()
    private val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val exercise = "Жим штанги лёжа"
    private lateinit var screen: ScreenCapture

    @Test fun comparisonShowsSeparatePeriodsAndDifference() {
        profile(ThemeMode.DARK)
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Сравнение периодов"))
        ui.onNodeWithText("Было · прошлые 30 дней / стало · последние 30 дней").assertExists()
        ui.onNodeWithContentDescription("Тренировки, было 1, стало 3, разница +2", substring = true).assertExists()
        ui.onAllNodesWithText("→", substring = true).assertCountEquals(0)
        screen.captureScreen("profile-dark.png")
    }

    @Test fun profileShowsWorkoutTilesAndTodayInNutrition() {
        profile(ThemeMode.LIGHT)
        screen.captureScreen("profile-light.png")
        val list = ui.onNode(hasScrollAction())
        val workoutChart = hasContentDescription("Тренировки по неделям, занятий 3", substring = true)
        list.performScrollToNode(workoutChart)
        ui.onNode(workoutChart).assertIsDisplayed()
        for ((label, value) in listOf("ПОДХОДОВ" to "4",
            "ПОВТОРЕНИЙ" to "32", "ОБЪЁМ" to "1\u00a0920 кг")) {
            list.performScrollToNode(hasText(label))
            ui.onNodeWithText(label).assertIsDisplayed()
            ui.onNode(hasText(value) and hasAnySibling(hasText(label))).assertIsDisplayed()
        }
        list.performScrollToNode(hasText("Сегодня: 1\u00a0536 ккал"))
        ui.onNodeWithText("Сегодня: 1\u00a0536 ккал").assertIsDisplayed()
        ui.onNodeWithText("Питание сегодня").assertDoesNotExist()
    }

    @Test fun exerciseTabShowsSelectedExerciseAndCompletedSet() {
        history()
        ui.onNodeWithText("Питание").assertIsDisplayed()
        ui.onNodeWithText("Упражнения").performClick().assertIsSelected()
        ui.onNodeWithText(exercise).assertIsDisplayed()
        ui.onNodeWithText("60 кг × 8").assertIsDisplayed()
        screen.captureScreen("history-exercise-dark.png")
    }

    @Test fun weeklyNutritionShowsSevenBarsAndRecordedDayAverage() {
        history()
        ui.onAllNodesWithTag("week-bar").assertCountEquals(7)
        ui.onNodeWithText("3\u00a0600 ккал").assertIsDisplayed()
        ui.onNodeWithText("Среднее за день с записями · 1\u00a0200 ккал").assertIsDisplayed()
        screen.captureScreen("history-food-dark.png")
    }

    private fun profile(mode: ThemeMode) {
        FoodStore(app).use { store ->
            listOf(0L, 1L, 3L, 31L).forEach { offset ->
                val session = store.newWorkout(today.minusDays(offset))
                store.saveSet(session, SetDraft(exercise = exercise, weight = "60", reps = "8"))
                if (offset == 0L) store.saveSet(session, SetDraft(exercise = exercise, weight = "60", reps = "8"))
            }
            store.save(today, FoodDraft(name = "Обед", weighted = false, energy = "1536",
                mealTime = "13:00", macros = Macros(protein = "90")))
            store.saveMeasurement(Measurement(today, "82", "21"))
            store.saveMeasurement(Measurement(today.minusDays(5), "83", "22"))
        }
        val model = ProfileViewModel(app)
        screen = ui.setScreenshotContent(mode) {
            ProfileScreen(model, {})
        }
        ui.awaitUntil(timeoutMillis = 10_000) { model.uiState.value.stats != null }
        ui.waitForIdle()
    }

    private fun history() {
        FoodStore(app).use { store ->
            listOf(900, 1200, 1500).forEachIndexed { i, calories ->
                store.save(monday.plusDays(i.toLong()), FoodDraft(name = "Обед", weighted = false,
                    energy = calories.toString()))
            }
            store.saveSet(store.newWorkout(monday), SetDraft(exercise = exercise, weight = "60", reps = "8"))
        }
        val model = HistoryViewModel(app)
        screen = ui.setScreenshotContent(ThemeMode.DARK) {
            HistoryScreen(model, {}, {})
        }
        ui.awaitUntil(timeoutMillis = 10_000) { !model.uiState.value.loading }
        ui.waitForIdle()
    }
}
