package app.calorietraining

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class GlassPolishTest {
    @get:Rule val ui = createComposeRule()
    private lateinit var view: View

    @Test fun glassCardHasEvenBrightness() {
        show(background = false) {
            Box(Modifier.fillMaxSize().background(Color(0xFF202124)), contentAlignment = Alignment.Center) {
                GlassCard(LocalDiaryAccents.current.kcal, Modifier.size(280.dp, 120.dp)) {}
            }
        }
        val bounds = ui.onNodeWithTag("glass-card").fetchSemanticsNode().boundsInRoot
        val bitmap = screenshot("glass-card-dark")
        val y = bounds.center.y.roundToInt()
        val inset = (7 * view.resources.displayMetrics.density).roundToInt()
        val center = Color(bitmap.getPixel(bounds.center.x.roundToInt(), y)).luminance()
        val edge = Color(bitmap.getPixel(bounds.right.roundToInt() - inset, y)).luminance()
        assertTrue("Яркость в центре: $center, у рамки: $edge", abs(center - edge) <= center * .03f)
    }

    @Test fun newFoodFieldsAreTagged() {
        foodEditor(ThemeMode.DARK)
        assertDiaryFields()
    }

    @Test fun foodEditorDark() = foodEditor(ThemeMode.DARK)

    @Test fun foodEditorLight() = foodEditor(ThemeMode.LIGHT)

    @Test fun goalDialogFieldsAreTagged() {
        show { GoalDialog(LocalDate.now(), DailyGoals(), {}) { _, _ -> } }
        assertDiaryFields()
    }

    @Test fun workoutSetFieldsAreTagged() {
        val draft = SetDraft(group = "bench", exercise = "Жим штанги лёжа", weight = "60", reps = "8")
        val workout = Workout(1, emptyList(), "Силовая", null, day = LocalDate.now())
        show { WorkoutContent(WorkoutState(list = listOf(workout), opened = 1, session = 1, draft = draft), {}) }
        ui.onNodeWithText("Повторы").assertExists()
        screenshot("workout-set-dark")
        assertDiaryFields()
    }

    @Test fun productCardFieldsAreTagged() {
        val product = MyProduct(id = 1, name = "Творог", weighted = true, energy = "120")
        show { MyProductsScreen(listOf(product), {}, {}, {}) }
        ui.onNodeWithText(product.name).performClick()
        ui.onNodeWithText("Правка продукта").assertIsDisplayed()
        assertDiaryFields()
    }

    @Test fun excessNutritionShowsMacroAndCalories() {
        val entry = FoodEntry(1, "Дневной рацион", "2500", "", 2500.toBigDecimal(),
            meal = Meal.LUNCH, macros = Macros(protein = "98", fat = "79", carbs = "320"))
        val goals = DailyGoals(2200, MacroGoals(protein = 140, fat = 72, carbs = 350))
        show { FoodContent(FoodUiState(rows = listOf(entry), goals = goals, loaded = true), {}) }
        screenshot("food-rings-over-dark")
        ui.onNodeWithText("сверх 300 ккал").assertIsDisplayed()
        ui.onNodeWithText("+7 г").assertIsDisplayed()
        ui.onAllNodes(hasText("+", substring = true) and !hasClickAction()).assertCountEquals(1)
        ui.onNode(hasTestTag("nutrition-rings") and hasContentDescription("сверх 7 г", substring = true))
            .assertIsDisplayed()
    }

    private fun foodEditor(mode: ThemeMode) {
        show(mode) {
            FoodContent(FoodUiState(draft = FoodDraft(weighted = true), loaded = true), {})
        }
        ui.onNodeWithText("Ккал на 100 г").performScrollTo().assertIsDisplayed()
        screenshot(if (mode == ThemeMode.DARK) "food-editor-dark" else "food-editor-light")
    }

    private fun assertDiaryFields() {
        ui.onAllNodes(hasSetTextAction(), useUnmergedTree = true).onFirst().assertExists()
        ui.onAllNodes(hasSetTextAction() and !hasTestTag("diary-field"), useUnmergedTree = true).assertCountEquals(0)
    }

    private fun show(mode: ThemeMode = ThemeMode.DARK, background: Boolean = true, content: @Composable () -> Unit) {
        ui.setContent {
            DiaryTheme(mode) {
                view = LocalView.current
                if (background) DiaryBackground { content() } else content()
            }
        }
    }

    private fun screenshot(name: String): Bitmap {
        val bitmap = ui.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val directory = File("build/screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        return bitmap
    }
}
