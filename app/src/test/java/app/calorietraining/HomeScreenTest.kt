package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp")
class HomeScreenTest {
    private val today get() = LocalDate.now()

    @get:Rule(order = 0)
    val seed = object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            OnboardingSettings(app).skipped = true
            FoodStore(app).use { store ->
                when (description.methodName) {
                    "nutritionSummaryOpensToday" -> {
                        store.save(today, FoodDraft(name = "Обед", energy = "1115"))
                        store.save(today.minusDays(1), FoodDraft(name = "Вчерашняя запись", energy = "400"))
                        store.setGoals(today, DailyGoals(kcal = 2120))
                    }
                    "nutritionWithoutGoal" -> store.save(today, FoodDraft(name = "Обед", energy = "500"))
                    "showsAndRecordsWeight" -> {
                        store.saveMeasurement(Measurement(today.minusDays(2), "82"))
                        store.saveMeasurement(Measurement(today.minusDays(1), "82.4"))
                    }
                }
            }
        }
    }

    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()

    private fun text(value: String) = ui.onNodeWithText(value)
    private fun tab(value: String) = ui.onNode(hasText(value) and isSelectable())
    private fun waitFor(matcher: SemanticsMatcher) {
        ui.waitUntil(10_000) { ui.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun startsAtHomeWithFiveTabs() {
        val tabs = listOf("Главная", "Питание", "Тренировки", "История", "Профиль")
        tab("Главная").assertIsSelected()
        ui.onAllNodes(isSelectable()).assertCountEquals(tabs.size)
        val positions = tabs.map { tab(it).fetchSemanticsNode().boundsInRoot.left }
        assertEquals(positions.sorted(), positions)
    }

    @Test fun nutritionSummaryOpensToday() {
        tab("Питание").performClick()
        waitFor(hasText("Обед"))
        ui.onNodeWithContentDescription("Предыдущий день").performClick()
        waitFor(hasText("Вчерашняя запись"))
        tab("Главная").performClick()
        waitFor(hasText("осталось 1005 ккал"))
        ui.onNode(hasText("осталось 1005 ккал") and hasClickAction()).performClick()
        tab("Питание").assertIsSelected()
        waitFor(hasText("Обед"))
        text("Вчерашняя запись").assertDoesNotExist()
    }

    @Test fun nutritionWithoutGoal() {
        waitFor(hasText("Задать цель"))
        ui.onNodeWithText("500 ккал", substring = true).assertIsDisplayed()
        text("Задать цель").performClick()
        tab("Питание").assertIsSelected()
    }

    @Test fun addFoodOpensNutrition() {
        text("Добавить еду").performClick()
        tab("Питание").assertIsSelected()
    }

    @Test fun showsAndRecordsWeight() {
        waitFor(hasText("82,4 кг"))
        text("82,4 кг").performScrollTo().assertIsDisplayed()
        text("+0,4 кг").assertIsDisplayed()
        text("Записать вес").performScrollTo().performClick()
        text("Вес, кг").performTextInput("81,9")
        text("Сохранить").performClick()
        waitFor(hasText("81,9 кг"))
        text("81,9 кг").assertIsDisplayed()
        FoodStore(ui.activity.application).use { store ->
            store.readableDatabase.rawQuery(
                "SELECT weight FROM measurement WHERE day = ?", arrayOf(today.toString())
            ).use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(81.9, row.getString(0).replace(',', '.').toDouble(), 0.0)
            }
        }
    }

    @Test fun offersWeightEntryWithoutMeasurements() {
        waitFor(hasText("Вес ещё не записан"))
        text("Вес ещё не записан").performScrollTo().assertIsDisplayed()
        text("Записать вес").performScrollTo().assertIsDisplayed()
    }

    @Test fun startsAndReopensLiveWorkout() {
        waitFor(hasText("Начать тренировку") and isEnabled())
        text("Начать тренировку").performScrollTo().performClick()
        tab("Тренировки").assertIsSelected()
        waitFor(hasText("Завершить тренировку"))
        FoodStore(ui.activity.application).use { assertEquals(1, it.workouts(activeOnly = true).size) }
        tab("Главная").performClick()
        val live = hasText("Идёт тренировка", substring = true)
        waitFor(live)
        ui.onNode(live).performScrollTo().assertIsDisplayed()
        text("Начать тренировку").assertDoesNotExist()
        text("Открыть").performScrollTo().performClick()
        tab("Тренировки").assertIsSelected()
        waitFor(hasText("Завершить тренировку"))
    }

    @Test fun settingsMoveOutOfProfileAndOpenBackup() {
        tab("Профиль").performClick()
        text("Оформление").assertDoesNotExist()
        text("Сервер продуктов").assertDoesNotExist()
        ui.onNodeWithContentDescription("Настройки").performClick()
        text("Оформление").assertIsDisplayed()
        text("Сервер продуктов").performScrollTo().assertIsDisplayed()
        text("Светлая").performScrollTo().performClick().assertIsSelected()
        assertEquals(ThemeMode.LIGHT, ThemeSettings(ui.activity).mode)
        text("Резервная копия").performScrollTo().performClick()
        text("Сохранить копию").assertIsDisplayed()
        text("Назад").performScrollTo().performClick()
        text("Оформление").assertExists()
        text("Назад").performScrollTo().performClick()
        tab("Профиль").assertIsSelected()
    }

    @Test fun historyHasNoBackupButton() {
        tab("История").performClick()
        text("Резервная копия").assertDoesNotExist()
    }
}
