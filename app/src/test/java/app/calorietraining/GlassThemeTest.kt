package app.calorietraining

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class GlassThemeTest {
    @get:Rule val ui = createComposeRule()

    @Test fun darkMealAccents() = checkMealAccents(ThemeMode.DARK)
    @Test fun lightMealAccents() = checkMealAccents(ThemeMode.LIGHT)

    @Test fun foodCards() {
        val rows = listOf(Meal.BREAKFAST, Meal.LUNCH).mapIndexed { index, meal ->
            FoodEntry(index.toLong() + 1, meal.label, "100", "", 100.toBigDecimal(), meal, "08:00", Macros())
        }
        ui.setContent { DiaryTheme(ThemeMode.DARK) {
            FoodContent(FoodUiState(rows = rows, loaded = true), {})
        } }
        ui.onAllNodesWithTag("glass-card").assertCountEquals(3)
    }

    @Test fun appBackground() {
        OnboardingSettings(org.robolectric.RuntimeEnvironment.getApplication()).skipped = true
        ui.setContent { DiaryTheme(ThemeMode.DARK) { DiaryApp(ThemeMode.DARK, {}) } }
        ui.onNodeWithTag("diary-background").assertExists()
    }

    private fun checkMealAccents(mode: ThemeMode) {
        lateinit var expected: List<Color>
        lateinit var actual: List<Color>
        ui.setContent { DiaryTheme(mode) {
            val accents = LocalDiaryAccents.current
            expected = listOf(accents.carbs, accents.protein, accents.fat, accents.kcal,
                MaterialTheme.colorScheme.onSurfaceVariant)
            actual = listOf(glassAccent(Meal.BREAKFAST), glassAccent(Meal.LUNCH),
                glassAccent(Meal.DINNER), glassAccent(Meal.SNACK), glassAccent(Meal.UNSPECIFIED))
        } }
        ui.runOnIdle { assertEquals(expected, actual) }
    }
}
