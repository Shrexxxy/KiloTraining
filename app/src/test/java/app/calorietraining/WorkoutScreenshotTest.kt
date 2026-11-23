package app.calorietraining

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class WorkoutScreenshotTest {
    @get:Rule val ui = createComposeRule()

    @Test fun dark() = capture(ThemeMode.DARK, "workout-dark.png")
    @Test fun light() = capture(ThemeMode.LIGHT, "workout-light.png")
    @Test fun feedDark() = capture(ThemeMode.DARK, "workouts-list-dark.png", screen = "feed")
    @Test fun liveDark() = capture(ThemeMode.DARK, "workout-live-dark.png", screen = "live")
    @Test fun newSetDark() = capture(ThemeMode.DARK, "set-new-dark.png", screen = "new")
    @Test fun editSetDark() = capture(ThemeMode.DARK, "set-edit-dark.png", screen = "edit")

    @Test fun editSetHeaderDark() = capture(ThemeMode.DARK, "header-workout-set-dark.png", screen = "edit")

    private fun capture(mode: ThemeMode, filename: String, screen: String = "detail") {
        val bench = "Жим штанги лёжа"
        val incline = "Жим гантелей на наклонной скамье"
        val extension = "Разгибание рук на блоке с канатом"
        fun set(id: Long, exercise: String, kg: String, reps: String, planned: Boolean = false, warmup: Boolean = false) =
            SetDraft(id, exercise, exercise, kg, reps, planned, warmup)
        val start = LocalDateTime.parse("2026-11-23T19:05")
        val workout = Workout(1, listOf(
            set(1, bench, "40", "12", warmup = true), set(2, bench, "60", "8"),
            set(3, bench, "60", "8"), set(4, bench, "62,5", "6", planned = true),
            set(5, incline, "22", "10"), set(6, incline, "22", "10"), set(7, incline, "22", "9", planned = true),
            set(8, extension, "25", "12"), set(9, extension, "25", "12", planned = true),
            set(10, extension, "25", "12", planned = true)
        ).map { set ->
            set.copy(doneAt = if (set.planned) null else start.plusSeconds(150 * set.id!!))
        }, "Грудь и трицепс", if (screen in listOf("live", "feed")) null else 52,
            day = start.toLocalDate(), startedAt = start, live = screen in listOf("live", "feed"))
        val clock = WorkoutTestClock().apply { set("2026-11-23T19:26:40") }
        val entries = listOf(workout,
            Workout(2, workout.sets.take(3), "Верх тела", 46,
                LocalDate.parse("2026-11-21"), LocalDateTime.parse("2026-11-21T18:00")),
            Workout(3, emptyList(), "Силовая", 34, LocalDate.parse("2026-11-20")))
        val draft = when (screen) {
            "new" -> workout.sets[2].copy(id = null, doneAt = null)
            "edit" -> workout.sets[2]
            else -> null
        }
        lateinit var view: View
        ui.setContent { DiaryTheme(mode) {
            view = LocalView.current
            WorkoutContent(WorkoutState(list = entries, opened = workout.id.takeUnless { screen == "feed" },
                session = workout.id.takeIf { draft != null }, draft = draft, clock = clock), {})
        } }
        // В Robolectric captureToImage ждёт обратный вызов перерисовки окна, который здесь не приходит.
        val bitmap = ui.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
