package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class AppScreenshotTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createComposeRule()

    @Test fun food() = capture("Питание", "app-food-dark.png")

    @Test fun homeDark() = captureHome("home-dark.png")
    @Test fun homeLight() = captureHome("home-light.png", ThemeMode.LIGHT)
    @Test fun homeLiveWorkout() = captureHome("home-live-workout-dark.png", live = true)
    @Test fun settings() = capture("Настройки", "settings-dark.png")
    @Test fun settingsHeaderLight() = capture("Настройки", "header-settings-light.png", ThemeMode.LIGHT)
    @Test fun scannerHeaderDark() = captureMeal("header-scanner-dark.png") {
        ui.onNodeWithContentDescription("Сканировать штрихкод").performClick()
        ui.onNodeWithText("Штрихкод продукта").assertIsDisplayed()
    }
    @Test fun newMealProductDark() = captureMeal("meals-add-new-product-dark.png") {
        ui.onNodeWithText("Новый продукт").assertIsDisplayed()
    }

    private fun captureMeal(filename: String, openScreen: () -> Unit) {
        val screen = ui.setScreenshotContent(ThemeMode.DARK) {
            DiaryApp(ThemeMode.DARK, {})
        }
        ui.onNodeWithText("Добавить еду").performScrollTo().performClick()
        ui.awaitUntil(10_000) { ui.onAllNodesWithText("Добавить · Завтрак").fetchSemanticsNodes().isNotEmpty() }
        openScreen()
        screen.captureScreen(filename)
    }

    private fun captureHome(filename: String, mode: ThemeMode = ThemeMode.DARK, live: Boolean = false) {
        val today = LocalDate.now()
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            store.setGoals(today, DailyGoals(2120, MacroGoals(120, 70, 250)))
            store.save(today, FoodDraft(name = "Обед", energy = "1115", macros = Macros("65", "40", "124")))
            store.saveMeasurement(Measurement(today.minusDays(2), "82.8"))
            store.saveMeasurement(Measurement(today.minusDays(1), "82.4"))
            if (live) store.newWorkout(LocalDateTime.now().minusMinutes(12), live = true)
            else store.newWorkout(today.minusDays(2))
        }
        capture("Главная", filename, mode, live)
    }

    private fun capture(tab: String, filename: String, mode: ThemeMode = ThemeMode.DARK, live: Boolean = false) {
        val screen = ui.setScreenshotContent(mode) {
            DiaryApp(mode, {})
        }
        if (tab != "Главная") {
            val destination = if (tab == "Настройки") "Профиль" else tab
            ui.onNode(hasText(destination) and isSelectable()).performClick()
        }
        if (tab == "Настройки") ui.onNodeWithContentDescription("Настройки").performClick()
        val ready = when (tab) {
            "Главная" -> if (live) "Идёт тренировка" else "Последняя тренировка"
            "Профиль" -> "Сравнение периодов"
            "Настройки" -> "Сервер продуктов"
            else -> "Завтрак"
        }
        ui.awaitUntil(10_000) { ui.onAllNodesWithText(ready, substring = true).fetchSemanticsNodes().isNotEmpty() }
        if (tab == "Главная") {
            ui.awaitUntil(10_000) { ui.onAllNodesWithText("82,4 кг").fetchSemanticsNodes().isNotEmpty() }
        }
        screen.captureScreen(filename)
    }
}
