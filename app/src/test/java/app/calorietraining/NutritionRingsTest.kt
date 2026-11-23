package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class NutritionRingsTest {
    @get:Rule val ui = createComposeRule()

    private fun entry(kcal: Int, protein: String = "98", meal: Meal = Meal.BREAKFAST) =
        FoodEntry(kcal.toLong(), "Блюдо $kcal", kcal.toString(), "", kcal.toBigDecimal(),
            meal = meal, macros = Macros(protein = protein))

    private fun show(goals: DailyGoals, rows: List<FoodEntry> = listOf(entry(1536)), day: LocalDate = LocalDate.now()) {
        ui.setContent { DiaryTheme(ThemeMode.LIGHT) {
            FoodContent(FoodUiState(day = day, rows = rows, goals = goals, loaded = true), {})
        } }
    }

    @Test fun fractionsRespectMissingValuesAndCapAtOne() {
        val partial = ringState(1536.toBigDecimal(), 2200)!!
        assertEquals(0.698f, partial.fraction, 0.001f)
        assertNull(partial.over)
        val over = ringState(2500.toBigDecimal(), 2200)!!
        assertEquals(1.136f, over.fraction, 0.001f)
        assertEquals(300, over.over)
        assertTrue(over.known)
        assertEquals(RingState(0f, null, false), ringState(null, 140))
        assertNull(ringState(1536.toBigDecimal(), null))
    }

    @Test fun fractionsExtendThroughTwoTurnsIncludingZeroGoals() {
        assertEquals(1.097f, ringState(79.toBigDecimal(), 72)!!.fraction, 0.001f)
        assertEquals(7, ringState(79.toBigDecimal(), 72)!!.over)
        assertEquals(RingState(1.5f, 50, true), ringState(150.toBigDecimal(), 100))
        assertEquals(RingState(2f, 150, true), ringState(250.toBigDecimal(), 100))
        assertEquals(RingState(1f, null, true), ringState(0.toBigDecimal(), 0))
        assertEquals(RingState(2f, 5, true), ringState(5.toBigDecimal(), 0))
    }

    @Test fun todayShowsTotalsTargetsAndRemainingCalories() {
        show(DailyGoals(2200, MacroGoals(protein = 140)))
        for (label in listOf("1\u00a0536", " / 2\u00a0200", "98", " / 140 г", "осталось 664 ккал", "Изменить цель")) {
            ui.onNodeWithText(label).assertIsDisplayed()
        }
        ui.onNodeWithContentDescription("Калории 1536 из 2200", substring = true).assertIsDisplayed()
    }

    @Test fun pastDayShowsShortfallAndAllowsEditing() {
        show(DailyGoals(2200), day = LocalDate.now().minusDays(1))
        ui.onNodeWithText("недобор 664 ккал").assertIsDisplayed()
        ui.onNodeWithText("Изменить цель").assertExists()
        ui.onNodeWithText("Задать дневную цель").assertDoesNotExist()
    }

    @Test fun excessIsReported() {
        show(DailyGoals(2000), listOf(entry(2300)))
        ui.onNodeWithText("сверх 300 ккал").assertIsDisplayed()
    }

    @Test fun withoutGoalsOnlyLegendAppears() {
        show(DailyGoals())
        ui.onNodeWithTag("nutrition-rings").assertDoesNotExist()
        ui.onNodeWithText("1\u00a0536").assertIsDisplayed()
        ui.onNodeWithText("Задать дневную цель").assertIsDisplayed()
    }

    @Test fun incompleteProteinShowsDashAndExplanation() {
        show(DailyGoals(macros = MacroGoals(protein = 140)), listOf(entry(520), entry(730, "")))
        ui.onNodeWithText("—").assertIsDisplayed()
        ui.onNodeWithText("Прочерк означает, что для итога не хватает данных").assertIsDisplayed()
        ui.onNodeWithContentDescription("нет данных", substring = true).assertExists()
    }

    @Test fun bothMealsHaveTotalsAndCopyActions() {
        show(DailyGoals(), listOf(entry(520), entry(730, meal = Meal.LUNCH)))
        for (label in listOf("Завтрак", "Обед")) ui.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        for (label in listOf("520 ккал", "730 ккал")) ui.onAllNodesWithText(label)[0].performScrollTo().assertIsDisplayed()
        ui.onAllNodesWithText("Повторить").assertCountEquals(2)
    }
}
