package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class WorkoutStatusScreenshotTest {
    @get:Rule val ui = createComposeRule()

    private val start = LocalDateTime.parse("2026-11-20T18:00")
    private val bench = SetDraft(1, "bench", "Жим штанги лёжа", "60", "8", doneAt = start.plusMinutes(10))
    private val workout = Workout(1, listOf(bench, bench.copy(id = 2, weight = "62,5", reps = "6", doneAt = start.plusMinutes(14)),
        SetDraft(3, "row", "Тяга в наклоне", "45", "10", doneAt = start.plusMinutes(30))),
        "Грудь и спина", 52, day = start.toLocalDate(), startedAt = start, finishedAt = start.plusMinutes(52), live = true)
    private val state = WorkoutState(day = workout.day, list = listOf(workout), opened = workout.id,
        clock = WorkoutTestClock().apply { set("2026-11-25T12:00") })

    @Test fun viewDark() = capture(ThemeMode.DARK, "workout-view-dark.png", state)
    @Test fun viewLight() = capture(ThemeMode.LIGHT, "workout-view-light.png", state)
    @Test fun editDark() = capture(ThemeMode.DARK, "workout-edit-dark.png", state.copy(editing = workout.id))
    @Test fun editLight() = capture(ThemeMode.LIGHT, "workout-edit-light.png", state.copy(editing = workout.id))
    @Test fun deleteDark() = captureDelete(ThemeMode.DARK, "workout-delete-dark.png")
    @Test fun deleteLight() = captureDelete(ThemeMode.LIGHT, "workout-delete-light.png")

    private fun capture(mode: ThemeMode, filename: String, screen: WorkoutState) {
        ui.setScreenshotContent(mode) { WorkoutContent(screen, {}) }.captureScreen(filename)
    }

    private fun captureDelete(mode: ThemeMode, filename: String) {
        val screen = ui.setScreenshotContent(mode) { WorkoutContent(state.copy(editing = workout.id), {}) }
        ui.onNodeWithText("Действия").performClick()
        ui.onNodeWithText("Удалить").performClick()
        ui.onNodeWithText("Удалить тренировку?").assertIsDisplayed()
        screen.captureScreen(filename, includeDialog = true)
    }
}
