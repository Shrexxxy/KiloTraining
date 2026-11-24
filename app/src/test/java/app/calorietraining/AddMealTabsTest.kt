package app.calorietraining

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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
class AddMealTabsTest {
    @get:Rule val ui = createComposeRule()

    @Test fun tabsAtDefaultFontScale() = checkTabs(1f)
    @Test fun tabsAtLargeFontScale() = checkTabs(1.3f)

    private fun checkTabs(fontScale: Float) {
        val screen = ui.setScreenshotContent {
            val density = Density(LocalDensity.current.density, fontScale)
            var state by remember {
                mutableStateOf(FoodUiState(loaded = true, addingMeal = Meal.LUNCH))
            }
            CompositionLocalProvider(LocalDensity provides density) {
                FoodContent(state, { event ->
                    if (event == FoodEvent.Venues) {
                        state = state.copy(venueTab = true, venues = listOf(Venue(1, "Тестовое кафе", null, 1)))
                    }
                })
            }
        }
        ui.onNodeWithText("Добавить · Обед").assertIsDisplayed()
        val labels = listOf("Из дневника", "Мои продукты", "Рецепты", "Заведения")
        val lineCounts = labels.associateWith { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            ui.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.single().lineCount
        }
        assertEquals(labels.associateWith { 1 }, lineCounts)
        if (fontScale == 1f) screen.captureScreen("add-meal-tabs-dark.png").recycle()

        ui.onNodeWithText("Заведения").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithText("Заведения").assertIsSelected()
        ui.onNodeWithText("Тестовое кафе").assertIsDisplayed()
    }
}
