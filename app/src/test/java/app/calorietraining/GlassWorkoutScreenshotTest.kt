package app.calorietraining

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class GlassWorkoutScreenshotTest {
    @get:Rule val ui = createComposeRule()

    private val start = LocalDateTime.parse("2026-11-23T19:05")
    private val bench = SetDraft(1, "bench", "Жим штанги лёжа", "60", "8", doneAt = start.plusMinutes(39))
    private val workout = Workout(1, listOf(
        SetDraft(3, "incline", "Жим гантелей на наклонной скамье", "22", "10", doneAt = start.plusMinutes(34)),
        bench,
        bench.copy(id = 2, weight = "62,5", reps = "6", planned = true, doneAt = null)
    ), "Грудь и трицепс", null, day = start.toLocalDate(), startedAt = start, live = true)
    private val state = WorkoutState(
        day = workout.day,
        list = listOf(workout, workout.copy(id = 2, title = "Верх тела", minutes = 46,
            day = workout.day.minusDays(2), startedAt = start.minusDays(2), live = false)),
        opened = workout.id,
        clock = WorkoutTestClock().apply { set("2026-11-23T19:47") },
    )

    @Test fun feedDark() = captureWorkout(ThemeMode.DARK, "workouts-list-dark.png", state.copy(opened = null))
    @Test fun liveDark() = captureLive(ThemeMode.DARK, "workout-live-dark.png")
    @Test fun liveLight() = captureLive(ThemeMode.LIGHT, "workout-live-light.png")
    @Test fun newSetDark() = captureEditor("set-new-dark.png", bench.copy(id = null, doneAt = null))
    @Test fun editSetDark() = captureEditor("set-edit-dark.png", bench)

    @Test fun myProductsDark() = capture(ThemeMode.DARK, "my-products-dark.png") {
        MyProductsScreen(listOf(
            MyProduct(id = 1, name = "Сырок глазированный", brand = "Молочная",
                weighted = true, energy = "410", source = "openfoodfacts"),
            MyProduct(id = 2, name = "Кофе", energy = "25"),
        ), {}, {}, {})
    }

    private fun captureLive(mode: ThemeMode, filename: String) {
        captureWorkout(mode, filename, state)
        val lastAddSet = hasText("+ Подход") and hasAnyAncestor(
            hasTestTag("glass-card") and hasAnyDescendant(hasText(bench.exercise))
        )
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(lastAddSet)
        ui.onNode(lastAddSet).assertIsDisplayed()
    }

    private fun captureEditor(filename: String, draft: SetDraft) {
        val previous = PreviousExercise(workout.day.minusDays(2), listOf(PreviousSet("57,5", "8")))
        captureWorkout(ThemeMode.DARK, filename, state.copy(session = workout.id, draft = draft, previous = previous))
    }

    private fun captureWorkout(mode: ThemeMode, filename: String, screen: WorkoutState) =
        capture(mode, filename) { WorkoutContent(screen, {}) }

    private fun capture(mode: ThemeMode, filename: String, content: @Composable () -> Unit) {
        lateinit var view: View
        ui.setContent {
            DiaryTheme(mode) {
                view = LocalView.current
                DiaryBackground { content() }
            }
        }
        // В Robolectric captureToImage ждёт перерисовку окна, которая здесь не вызывается.
        val bitmap = ui.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val dir = File("build/screenshots/glass-workouts").apply { mkdirs() }
        File(dir, filename).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
