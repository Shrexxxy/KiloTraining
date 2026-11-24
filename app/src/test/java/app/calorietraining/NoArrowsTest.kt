package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class NoArrowsTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createComposeRule()
    private val today = LocalDate.now()

    @Test fun weightGainShowsSignedDeltaAndDescription() {
        homeWithWeight("82", "82.4")
        ui.onNodeWithText("+0,4 кг").assertIsDisplayed()
        val arrows = hasText("↑", substring = true) or hasText("↓", substring = true)
        val arrowCount = ui.onAllNodes(arrows, useUnmergedTree = true).fetchSemanticsNodes().size
        ui.onNodeWithContentDescription("Изменение с прошлого замера: +0,4 кг").assertIsDisplayed()
        assertEquals(0, arrowCount)
    }

    @Test fun sameWeightShowsNoChange() {
        homeWithWeight("82", "82")
        ui.onNodeWithText("без изменений").assertIsDisplayed()
    }

    @Test fun weightLossUsesMinusSign() {
        val screen = homeWithWeight("82.8", "82.4")
        ui.onNodeWithText("−0,4 кг").assertIsDisplayed()
        ui.onNodeWithContentDescription("Изменение с прошлого замера: −0,4 кг").assertIsDisplayed()
        screen.captureScreen("home-weight-dark.png").recycle()
    }

    @Test fun weightLossLightScreenshot() {
        homeWithWeight("82.8", "82.4", ThemeMode.LIGHT).captureScreen("home-weight-light.png").recycle()
    }

    @Test fun profileShowsGoalWithTargetIcon() {
        seedDiary()
        val screen = launch()
        openTab("Профиль")
        waitForText("Похудеть")
        ui.onNodeWithText("Похудеть").assertIsDisplayed()
        ui.onNodeWithTag("goal-icon", useUnmergedTree = true).assertIsDisplayed()
        ui.onAllNodesWithContentDescription("Цель", useUnmergedTree = true).assertCountEquals(0)
        ui.onNodeWithText("ЦЕЛЬ").assertIsDisplayed()
        ui.onAllNodesWithText("↓", substring = true, useUnmergedTree = true).assertCountEquals(0)
        screen.captureScreen("profile-goal-dark.png").recycle()
    }

    @Test fun homeProfileAndNutritionHaveNoArrowText() {
        seedDiary()
        launch()
        val arrows = "↑↓→←⬆⬇➜➔↗↘▲▼"
            .map { hasText(it.toString(), substring = true) }
            .reduce { matchers, matcher -> matchers or matcher }
        val violations = mutableSetOf<String>()
        fun inspect(screen: String) {
            ui.onAllNodes(arrows, useUnmergedTree = true).fetchSemanticsNodes().forEach {
                violations += "$screen: ${it.config}"
            }
        }

        waitForText("82,4 кг")
        waitForText("Последняя тренировка", substring = true)
        inspect("Главная")

        openTab("Профиль")
        waitForText("Похудеть")
        waitForText("Калории по дням")
        ui.onNodeWithText("30 дней").assertIsSelected()
        inspect("Профиль")
        val profileList = ui.onNode(hasScrollToIndexAction())
        for (section in listOf("Калории по дням", "БЖУ", "Мои замеры", "Тренировки",
            "Жим штанги лёжа", "Сравнение периодов", "Когда я обычно ем")) {
            profileList.performScrollToNode(hasText(section) and hasAnyAncestor(hasScrollToIndexAction()))
            inspect("Профиль")
        }

        openTab("Питание")
        waitForText("Изменить цель")
        inspect("Питание")
        for (section in listOf("Каша", "Обед", "Ужин", "Перекус")) {
            ui.mealList().performScrollToNode(hasText(section))
            inspect("Питание")
        }

        check(violations.isEmpty()) { violations.joinToString("\n") }
    }

    private fun homeWithWeight(previous: String, latest: String, mode: ThemeMode = ThemeMode.DARK): ScreenCapture {
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            store.saveMeasurement(Measurement(today.minusDays(2), previous))
            store.saveMeasurement(Measurement(today.minusDays(1), latest))
        }
        val screen = launch(mode)
        val label = "${latest.replace('.', ',')} кг"
        waitForText(label)
        ui.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        return screen
    }

    private fun seedDiary() {
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            store.saveOnboarding(
                UserProfile(Sex.MALE, today.year - 35, 180, ActivityLevel.MODERATE, GoalKind.LOSE),
                Measurement(today.minusDays(1), "82.4", "20"),
                DailyGoals(2100),
            )
            store.setGoals(today, DailyGoals(2100))
            store.saveMeasurement(Measurement(today.minusDays(2), "82", "21"))
            store.save(today, FoodDraft(name = "Каша", energy = "350", meal = Meal.BREAKFAST))
            store.saveSet(store.newWorkout(today.minusDays(2)),
                SetDraft(exercise = "Жим штанги лёжа", weight = "60", reps = "8"))
            store.newWorkout(today.minusDays(31))
        }
    }

    private fun launch(mode: ThemeMode = ThemeMode.DARK) = ui.setScreenshotContent(mode) {
        DiaryApp(mode, {})
    }

    private fun openTab(label: String) {
        ui.onNode(hasText(label) and isSelectable()).performClick()
    }

    private fun waitForText(label: String, substring: Boolean = false) {
        ui.awaitUntil(10_000) { ui.onAllNodesWithText(label, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }
}
