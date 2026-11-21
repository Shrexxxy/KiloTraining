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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class FoodScreenshotTest {
    @get:Rule val ui = createComposeRule()

    @Test fun dark() = capture(ThemeMode.DARK, "food-dark.png")
    @Test fun light() = capture(ThemeMode.LIGHT, "food-light.png")

    private fun capture(mode: ThemeMode, filename: String) {
        fun food(id: Long, name: String, kcal: Int, protein: Int, meal: Meal, time: String) =
            FoodEntry(id, name, kcal.toString(), "", kcal.toBigDecimal(), meal, time, Macros(protein.toString()))
        val rows = listOf(
            food(1, "Овсяная каша", 240, 8, Meal.BREAKFAST, "08:10"),
            food(2, "Творог с ягодами", 180, 22, Meal.BREAKFAST, "08:10"),
            food(3, "Банан", 100, 1, Meal.BREAKFAST, "08:30"),
            food(4, "Гречка", 220, 8, Meal.LUNCH, "13:00"),
            food(5, "Куриное филе", 250, 36, Meal.LUNCH, "13:00"),
            food(6, "Овощной салат", 100, 3, Meal.LUNCH, "13:15"),
            food(7, "Омлет", 280, 19, Meal.DINNER, "19:10"),
            food(8, "Яблоко", 166, 1, Meal.DINNER, "19:20")
        )
        lateinit var view: View
        ui.setContent { DiaryTheme(mode) {
            view = LocalView.current
            FoodContent(FoodUiState(rows = rows, goals = DailyGoals(2200, MacroGoals(protein = 140)), loaded = true), {})
        } }
        // В Robolectric captureToImage ждёт обратный вызов перерисовки окна, который здесь не приходит.
        val bitmap = ui.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
