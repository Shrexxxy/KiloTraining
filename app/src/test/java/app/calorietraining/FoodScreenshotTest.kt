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
    @Test fun light() = capture(ThemeMode.LIGHT, "food-light.png", "glass-food-light.png")

    @Test fun mealsEmptyDark() = captureState(ThemeMode.DARK, FoodUiState(loaded = true), "meals-empty-dark.png")

    @Config(qualifiers = "ru-rRU-w360dp-h1300dp")
    @Test fun mealsFilledDark() = capture(ThemeMode.DARK, "meals-filled-dark.png")

    @Test fun mealsAddDark() = captureState(ThemeMode.DARK, FoodUiState(loaded = true, addingMeal = Meal.LUNCH,
        frequent = listOf(FoodDraft(name = "Овсяная каша", weighted = true, energy = "140")),
        recent = listOf(FoodDraft(name = "Банан", weighted = true, energy = "89"),
            FoodDraft(name = "Творог", weighted = true, energy = "121"))), "meals-add-dark.png")

    @Test fun mealsEditorDark() = captureEditor(ThemeMode.DARK, "meals-editor-dark.png")

    @Test fun mealsEditorLight() = captureEditor(ThemeMode.LIGHT, "meals-editor-light.png")

    private fun captureEditor(mode: ThemeMode, filename: String) = captureState(mode,
        FoodUiState(loaded = true, addingMeal = Meal.LUNCH,
            draft = FoodDraft(name = "Гречка", weighted = true, energy = "110", grams = "150",
                meal = Meal.LUNCH, mealTime = "13:00", macros = Macros("4", "1", "22"))), filename)

    private fun capture(mode: ThemeMode, vararg filenames: String) {
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
        val screenshotRows = if ("meals-filled-dark.png" in filenames) listOf(
            rows[0], rows[3], rows[6], food(9, "Кефир", 95, 6, Meal.UNSPECIFIED, "20:00")
        ) else rows
        captureState(mode, FoodUiState(rows = screenshotRows,
            goals = DailyGoals(2200, MacroGoals(protein = 140)), loaded = true), *filenames)
    }

    private fun captureState(mode: ThemeMode, state: FoodUiState, vararg filenames: String) {
        lateinit var view: View
        ui.setContent { DiaryTheme(mode) {
            view = LocalView.current
            DiaryBackground {
                FoodContent(state, {})
            }
        } }
        // В Robolectric captureToImage ждёт обратный вызов перерисовки окна, который здесь не приходит.
        val bitmap = ui.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        filenames.forEach { filename ->
            File(dir, filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
