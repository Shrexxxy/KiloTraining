package app.calorietraining

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp")
class FoodScreenTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun text(value: String) = composeTestRule.onNodeWithText(value)

    private fun expectTotal(value: String) {
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
        text(value).assertIsDisplayed()
    }

    @Test fun manualMealsSurviveReopeningAndBelongToSelectedDate() {
        text("Питание").performClick()
        expectTotal("Завтрак")
        composeTestRule.addInMeal(Meal.BREAKFAST)
        text("Новый продукт").performClick()
        text("Сохранить").assertIsNotEnabled()
        text("Название").performTextInput("Бутерброд")
        text("Ккал на порцию").performTextInput("320")
        text("Сохранить").performClick()
        expectTotal("320")
        composeTestRule.addInMeal(Meal.BREAKFAST)
        text("Новый продукт").performClick()
        text("На 100 г").performClick()
        text("Название").performTextInput("Творог")
        text("Вес порции, г").performTextInput("150")
        text("Ккал на 100 г").performTextInput("200")
        text("Сохранить").performClick()
        expectTotal("620")
        composeTestRule.activityRule.scenario.recreate()
        expectTotal("620")

        composeTestRule.onNodeWithContentDescription("Следующий день").performClick()
        expectTotal(java.time.LocalDate.now().plusDays(1).caption())
        text("Бутерброд").assertDoesNotExist()
        text("Творог").assertDoesNotExist()
        composeTestRule.addInMeal(Meal.BREAKFAST)
        text("Новый продукт").performClick()
        text("Название").performTextInput("Яблоко")
        text("Ккал на порцию").performTextInput("90")
        text("Сохранить").performClick()
        expectTotal("90")
        composeTestRule.onNodeWithContentDescription("Предыдущий день").performClick()
        expectTotal("620")
        text("Бутерброд").performClick()
        text("Ккал на порцию").performTextReplacement("250")
        text("Сохранить").performClick()
        expectTotal("550")
        text("Творог").performClick()
        text("Удалить запись").performClick()
        text("Удалить").performClick()
        expectTotal("250")
        composeTestRule.onNodeWithContentDescription("Следующий день").performClick()
        expectTotal("90")
    }
}
