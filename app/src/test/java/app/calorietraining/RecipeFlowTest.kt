package app.calorietraining

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35], qualifiers="ru-rRU-w393dp-h850dp")
class RecipeFlowTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun node(label: String) = compose.onNodeWithText(label)
    private fun waitFor(label: String) = compose.awaitUntil(10_000) {
        runCatching { node(label).fetchSemanticsNode() }.isSuccess
    }

    @Test fun barcodeIngredientBecomesLoggedPortion() {
        node("Питание").performClick()
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            store.cacheProduct("12345678", Product("Рис", "350", Macros("7","0","80")))
        }
        waitFor("Завтрак")
        compose.addInMeal(Meal.BREAKFAST)
        node("Рецепты").performClick()
        waitFor("Создать рецепт")
        node("Создать рецепт").performClick()
        node("Название блюда").performTextInput("Рисовая каша")
        node("Вес готового блюда без посуды, г").performTextInput("300")
        node("Добавить ингредиент").performClick()
        node("Сканировать штрихкод").performClick()
        node("Цифры штрихкода").performTextInput("12345678")
        node("Найти продукт").performClick()
        waitFor("Рис")
        node("Вес ингредиента, г").performScrollTo().performTextInput("100")
        node("Сохранить").performClick()
        node("Сохранить рецепт").performClick()
        waitFor("Записать порцию")
        node("Записать порцию").performClick()
        node("Вес порции, г").performScrollTo().performTextInput("150")
        node("Сохранить").performClick()
        waitFor("175")
    }
}
