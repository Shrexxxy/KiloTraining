package app.calorietraining

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class WorkoutScreenshotTest {
    @get:Rule val ui = createComposeRule()

    @Test fun dark() = capture(ThemeMode.DARK, "workout-dark.png")
    @Test fun light() = capture(ThemeMode.LIGHT, "workout-light.png")

    private fun capture(mode: ThemeMode, filename: String) {
        val bench = "Жим штанги лёжа"
        val incline = "Жим гантелей на наклонной скамье"
        val extension = "Разгибание рук на блоке с канатом"
        fun set(id: Long, exercise: String, kg: String, reps: String, planned: Boolean = false, warmup: Boolean = false) =
            SetDraft(id, exercise, exercise, kg, reps, planned, warmup)
        val workout = Workout(1, listOf(
            set(1, bench, "40", "12", warmup = true), set(2, bench, "60", "8"),
            set(3, bench, "60", "8"), set(4, bench, "62,5", "6", planned = true),
            set(5, incline, "22", "10"), set(6, incline, "22", "10"), set(7, incline, "22", "9", planned = true),
            set(8, extension, "25", "12"), set(9, extension, "25", "12", planned = true),
            set(10, extension, "25", "12", planned = true)
        ), "Грудь и трицепс", 52)
        val deadline = SystemClock.elapsedRealtime() + 84_000
        lateinit var view: View
        ui.setContent { DiaryTheme(mode) {
            view = LocalView.current
            WorkoutContent(WorkoutState(list = listOf(workout)), {}) { RestTimer(deadline, 120_000) { _, _ -> } }
        } }
        // В Robolectric captureToImage ждёт обратный вызов перерисовки окна, который здесь не приходит.
        val bitmap = ui.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
