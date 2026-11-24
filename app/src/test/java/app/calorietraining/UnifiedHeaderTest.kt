package app.calorietraining

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal fun ComposeTestRule.headerBack() {
    val back = onNodeWithContentDescription("Назад")
    back.assertIsDisplayed().assertHasClickAction()
    val top = back.fetchSemanticsNode().boundsInRoot.top
    assertTrue("Стрелка возврата должна быть выше 120 dp", top < with(density) { 120.dp.toPx() })
    for (label in listOf("Назад", "Отмена", "Вернуться к вводу")) {
        onNode(hasText(label) and hasClickAction()).assertDoesNotExist()
    }
    back.performClick()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp")
class UnifiedHeaderTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()

    private fun text(label: String) = ui.onNodeWithText(label)
    private fun await(label: String) {
        ui.awaitUntil(10_000) { ui.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun breakfast() {
        text("Добавить еду").performScrollTo().performClick()
        await("Добавить · Завтрак")
    }

    private fun scanner() = ui.onNodeWithContentDescription("Сканировать штрихкод").performClick()

    @Test fun opensBreakfastAfterLoading() {
        breakfast()
        text("Добавить · Завтрак").assertIsDisplayed()
    }

    @Test fun scannerHeaderReturnsToMeal() {
        breakfast()
        scanner()
        ui.headerBack()
        await("Добавить · Завтрак")
    }

    @Test fun newProductStartsEmptyInBreakfast() {
        breakfast()
        text("Ввести вручную").assertDoesNotExist()
        text("Новый продукт").performClick()
        text("Завтрак").assertIsSelected()
        ui.onNode(hasText("Название") and hasSetTextAction() and
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))).assertExists()
    }

    @Test fun editorHeaderDiscardsUnsavedEntry() {
        lunch()
        text("Новый продукт").performClick()
        discardEntry { ui.headerBack() }
    }

    @Test fun systemBackDiscardsUnsavedEntry() {
        lunch()
        text("Новый продукт").performClick()
        discardEntry { ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() } }
    }

    private fun lunch() {
        ui.onNode(hasText("Питание") and isSelectable()).performClick()
        await("Обед")
        ui.addInMeal(Meal.LUNCH)
    }

    private fun discardEntry(back: () -> Unit) {
        text("Название").performTextInput("Черновик")
        back()
        text("Добавить · Обед").assertIsDisplayed()
        text("Новая запись").assertDoesNotExist()
        FoodStore(ui.activity.application).use { assertTrue(it.entries(LocalDate.now()).isEmpty()) }
    }

    @Test fun systemBackReturnsFromScannerToMeal() {
        breakfast()
        scanner()
        await("Цифры штрихкода")
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        await("Добавить · Завтрак")
    }

    @Test fun scannerControlsSpanScreen() {
        breakfast()
        scanner()
        val width = ui.onRoot().fetchSemanticsNode().boundsInRoot.width
        val minimum = width - with(ui.density) { 48.dp.toPx() }
        val controls = listOf(
            hasText("Цифры штрихкода") and hasSetTextAction(),
            hasText("Открыть камеру") and hasClickAction(),
            hasText("Найти продукт") and hasClickAction()
        )
        controls.forEach { matcher ->
            val bounds = ui.onNode(matcher).fetchSemanticsNode().boundsInRoot
            assertTrue("$matcher: ${bounds.width} < $minimum", bounds.width >= minimum)
        }
    }

    @Test fun recipeEditorReturnsToListAndMealHeaderReturnsToNutrition() {
        breakfast()
        text("Рецепты").performClick()
        await("Создать рецепт")
        text("Создать рецепт").performClick()
        ui.headerBack()
        await("Создать рецепт")
        text("Добавить · Завтрак").assertIsDisplayed()
        ui.headerBack()
        ui.mealList().assertExists()
        text("Создать рецепт").assertDoesNotExist()
    }

    @Test fun measurementHeaderReturnsToProfile() {
        ui.onNode(hasText("Профиль") and isSelectable()).performClick()
        await("Калории по дням")
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Записать замер"))
        text("Записать замер").performClick()
        ui.headerBack()
        text("Замер тела").assertDoesNotExist()
        ui.onNodeWithContentDescription("Настройки").assertIsDisplayed()
    }

    @Test fun productsHeaderReturnsToSearch() {
        breakfast()
        text("Рецепты").performClick()
        await("Создать рецепт")
        text("Создать рецепт").performClick()
        text("Добавить ингредиент").performClick()
        text("Найти продукт").performScrollTo().performClick()
        await("Поиск продуктов")
        ui.onNode(hasText("Мои продукты") and hasClickAction()).performClick()
        ui.headerBack()
        await("Поиск продуктов")
        ui.headerBack()
        text("Ингредиент").assertIsDisplayed()
    }
}
