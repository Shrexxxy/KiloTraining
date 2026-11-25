package app.calorietraining

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
    private lateinit var screen: ScreenCapture

    @Test fun glassCardHasEvenBrightness() {
        show() {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(280.dp, 120.dp).background(Color(0xFF202124))) {
                    GlassCard(LocalDiaryAccents.current.kcal, Modifier.fillMaxSize()) {}
                }
            }
        }
        val bounds = ui.onNodeWithTag("glass-card").fetchSemanticsNode().boundsInRoot
        val bitmap = screen.captureScreen("glass-card-dark.png")
        val y = bounds.center.y.roundToInt()
        val inset = (bounds.width * 7 / 280).roundToInt()
        val center = Color(bitmap.getPixel(bounds.center.x.roundToInt(), y)).luminance()
        val edge = Color(bitmap.getPixel(bounds.right.roundToInt() - inset, y)).luminance()
        assertTrue("Яркость в центре: $center, у рамки: $edge", abs(center - edge) <= center * .03f)
    }

    @Test fun newFoodFieldsAreTagged() {
        foodEditor(ThemeMode.DARK)
        assertDiaryFields()
        screen.captureScreen("food-editor-dark.png")
    }

    @Test fun foodEditorLight() {
        foodEditor(ThemeMode.LIGHT)
        screen.captureScreen("food-editor-light.png")
    }

    @Test fun goalDialogFieldsAreTagged() {
        show { GoalDialog(LocalDate.now(), DailyGoals(), {}) { _, _ -> } }
        assertDiaryFields()
    }

    @Test fun workoutSetFieldsAreTagged() {
        val draft = SetDraft(group = "bench", exercise = "Жим штанги лёжа", weight = "60", reps = "8")
        val workout = Workout(1, emptyList(), "Силовая", null, day = LocalDate.now())
        show { WorkoutContent(WorkoutState(list = listOf(workout), opened = 1, session = 1, draft = draft), {}) }
        ui.onNodeWithText("Повторы").assertExists()
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
        screen.captureScreen("food-rings-over-dark.png")
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
    }

    private fun assertDiaryFields() {
        ui.onAllNodes(hasSetTextAction(), useUnmergedTree = true).onFirst().assertExists()
        ui.onAllNodes(hasSetTextAction() and !hasTestTag("diary-field"), useUnmergedTree = true).assertCountEquals(0)
    }

    private fun show(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        screen = ui.setScreenshotContent(mode, content = content)
    }
}
