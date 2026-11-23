package app.calorietraining

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

internal val addMealButton = hasText("+ Добавить") or hasText("Добавить")

internal fun SemanticsNodeInteractionsProvider.mealList() = onNode(hasScrollToIndexAction())

internal fun androidx.compose.ui.test.junit4.ComposeTestRule.addInMeal(meal: Meal) {
    mealList().performScrollToNode(hasText(meal.label))
    val header = onNodeWithText(meal.label).fetchSemanticsNode().boundsInRoot
    val buttons = onAllNodes(addMealButton)
    val index = buttons.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.top >= header.top }
    assertTrue("У приёма ${meal.label} должна быть кнопка добавления", index >= 0)
    buttons[index].performScrollTo().performClick()
    val title = "Добавить · ${meal.label}"
    waitUntil(10_000) {
        onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp")
class MealsScreenTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()

    private val day = LocalDate.now()
    private val meals = listOf(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER, Meal.SNACK)

    @Before fun openNutrition() {
        ui.onNode(hasText("Питание") and isSelectable()).performClick()
    }

    private fun text(label: String) = ui.onNodeWithText(label)
    private fun waitFor(label: String) = ui.waitUntil(10_000) {
        ui.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
    }
    private fun seed(block: FoodStore.() -> Unit) {
        FoodStore(ui.activity.application).use(block)
        waitFor(day.caption())
        ui.onNodeWithContentDescription("Предыдущий день").performClick()
        waitFor(day.minusDays(1).caption())
        ui.onNodeWithContentDescription("Следующий день").performClick()
        waitFor(day.caption())
    }
    private fun add(meal: Meal) {
        waitFor(Meal.BREAKFAST.label)
        ui.addInMeal(meal)
        text("Добавить · ${meal.label}").assertIsDisplayed()
    }
    private fun save(name: String, meal: Meal) {
        text("Сохранить").performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithContentDescription("Выбрать дату, ${day.caption()}").fetchSemanticsNodes().isNotEmpty()
        }
        ui.mealList().performScrollToNode(hasText(name))
        text(name).assertIsDisplayed()
        FoodStore(ui.activity.application).use {
            assertEquals(meal, it.entries(day).single { row -> row.name == name }.meal)
        }
    }

    @Test fun m1EmptyDayShowsFourMeals() {
        for (meal in meals) {
            add(meal)
            ui.onNode(hasContentDescription("Назад") or hasText("Назад")).performClick()
        }
        ui.mealList().performScrollToNode(hasText(Meal.SNACK.label))
        text("Без категории").assertDoesNotExist()
        listOf("Сканировать штрихкод", "Найти", "Вручную", "Рецепты", "За этот день пока нет записей")
            .forEach { text(it).assertDoesNotExist() }
    }

    @Test fun m2ManualEntryInLunch() {
        add(Meal.LUNCH)
        text("Ввести вручную").performClick()
        text("На 100 г").performClick()
        text("Название").performTextInput("Гречка")
        text("Вес порции, г").performTextInput("150")
        text("Ккал на 100 г").performTextInput("110")
        save("Гречка", Meal.LUNCH)
    }

    @Test fun m3FrequentFoodsPrecedeRecentWithoutDuplicates() {
        seed {
            repeat(5) { index ->
                save(day.minusDays(2L + index), FoodDraft(name = "Овсянка", energy = "145",
                    macros = Macros("5", "3", "22")))
            }
            save(day.minusDays(1), FoodDraft(name = "Банан", energy = "90"))
        }
        add(Meal.BREAKFAST)
        text("Из дневника").assertIsDisplayed()
        assertTrue(text("Овсянка").fetchSemanticsNode().boundsInRoot.top <
            text("Банан").fetchSemanticsNode().boundsInRoot.top)
        text("Овсянка").performClick()
        text("Завтрак").assertIsSelected()
        for (value in listOf("Овсянка", "145", "5", "3", "22")) {
            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,
                androidx.compose.ui.text.AnnotatedString(value))).assertExists()
        }
    }

    @Test fun m4BarcodeFromDinner() {
        seed { saveMyProduct(MyProduct(name = "Йогурт", barcode = "12345678", energy = "120")) }
        add(Meal.DINNER)
        ui.onNodeWithContentDescription("Сканировать штрихкод").performClick()
        text("Цифры штрихкода").performTextInput("12345678")
        text("Найти продукт").performClick()
        waitFor("Йогурт")
        text("Ужин").assertIsSelected()
        text("Вес порции, г").performTextInput("75")
        save("Йогурт", Meal.DINNER)
    }

    @Test fun m5RecipeInSnack() {
        seed {
            saveRecipe(Recipe(name = "Рисовая каша", cooked = "300", ingredients =
                listOf(FoodDraft(name = "Рис", weighted = true, energy = "350", grams = "100"))))
        }
        add(Meal.SNACK)
        text("Рецепты").performClick()
        waitFor("Записать порцию")
        text("Записать порцию").performClick()
        text("Перекус").assertIsSelected()
        text("Вес порции, г").performTextInput("150")
        save("Рисовая каша", Meal.SNACK)
    }

    @Test fun m6MoveLunchEntryToDinner() {
        seed { save(day, FoodDraft(name = "Гречка", energy = "165", meal = Meal.LUNCH)) }
        waitFor("Гречка")
        ui.mealList().performScrollToNode(hasText("Гречка"))
        text("Гречка").performClick()
        text("Обед").assertIsSelected()
        text("Ужин").performClick()
        save("Гречка", Meal.DINNER)
    }

    @Test fun m7LegacyEntryCanBeCategorized() {
        seed { save(day, FoodDraft(name = "Гречка", energy = "165", meal = Meal.UNSPECIFIED)) }
        waitFor("Питание")
        ui.mealList().performScrollToNode(hasText("Без категории"))
        val legacyTop = text("Без категории").fetchSemanticsNode().boundsInRoot.top
        assertTrue(text("Перекус").fetchSemanticsNode().boundsInRoot.top < legacyTop)
        assertTrue(ui.onAllNodes(addMealButton).fetchSemanticsNodes().none { it.boundsInRoot.top > legacyTop })
        ui.mealList().performScrollToNode(hasText("Гречка"))
        text("Гречка").performClick()
        meals.forEach { text(it.label).assertIsNotSelected() }
        text("Завтрак").performClick()
        save("Гречка", Meal.BREAKFAST)
        ui.mealList().performScrollToNode(hasText(Meal.SNACK.label))
        text("Без категории").assertDoesNotExist()
    }

    @Test fun m8MealSelectorAboveName() {
        add(Meal.LUNCH)
        text("Ввести вручную").performClick()
        assertTrue(text("Завтрак").fetchSemanticsNode().boundsInRoot.top <
            text("Название").fetchSemanticsNode().boundsInRoot.top)
    }
}
