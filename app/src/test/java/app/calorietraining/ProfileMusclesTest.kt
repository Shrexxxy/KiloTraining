package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp")
class ProfileMusclesTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createComposeRule()

    private val description = "Нагрузка мышц за 7 дней: грудь 3, плечи 1,5, трицепс 1,5"

    @Test fun weeklyMusclesAreIndependentOfProfilePeriod() {
        seed("Жим штанги лёжа", 3)
        seed("Жим штанги лёжа", 2, LocalDate.now().minusDays(7))
        openProfile()
        scrollToMuscles()
        assertBenchLoad()
        for (period in listOf("7 дней", "30 дней", "Всё время")) {
            ui.onNode(hasScrollAction()).performScrollToIndex(0)
            ui.onNodeWithText(period).performClick()
            ui.awaitUntil(10_000) {
                ui.onAllNodes(hasText(period) and isSelected()).fetchSemanticsNodes().isNotEmpty()
            }
            scrollToMuscles()
            assertBenchLoad()
        }
    }

    @Test fun emptyWeekShowsNoWorkingSets() {
        openProfile()
        scrollToMuscles()
        ui.onNodeWithText("За 7 дней нет рабочих подходов").assertExists()
        assertNoDiagram()
    }

    @Test fun unknownOnlyShowsCountWithoutDiagram() {
        seed("Мой жим", 2)
        openProfile()
        scrollToMuscles()
        ui.onNodeWithText("Подходов без данных о мышцах: 2").assertExists()
        ui.onNodeWithText("За 7 дней нет рабочих подходов").assertDoesNotExist()
        assertNoDiagram()
    }

    @Test
    @Config(qualifiers = "ru-rRU-w393dp-h1800dp")
    fun darkScreenshot() = capture("profile-muscles-dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "ru-rRU-w393dp-h1800dp")
    fun lightScreenshot() = capture("profile-muscles-light", ThemeMode.LIGHT)

    private fun capture(filename: String, mode: ThemeMode) {
        seed("Жим штанги лёжа", 3)
        val screen = ui.setScreenshotContent(mode) { DiaryApp(mode, {}) }
        loadProfile()
        scrollToMuscles()
        assertBenchLoad()
        val footer = ui.onNodeWithText("Подходы: основная мышца 1, дополнительная 0,5")
        footer.assertIsDisplayed()
        val end = footer.fetchSemanticsNode().boundsInRoot.bottom
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        screen.captureScreen("$filename.png", heightPx = (end + 24 * density).toInt())
    }

    private fun assertBenchLoad() {
        ui.onNodeWithText("Грудь — 3").assertExists()
        ui.onNodeWithContentDescription(description).assertExists()
    }

    private fun assertNoDiagram() {
        ui.onNodeWithContentDescription("Нагрузка мышц за 7 дней", substring = true).assertDoesNotExist()
    }

    private fun openProfile() {
        ui.setContent { DiaryTheme(ThemeMode.DARK) { DiaryApp(ThemeMode.DARK, {}) } }
        loadProfile()
    }

    private fun loadProfile() {
        ui.onNode(hasText("Профиль") and isSelectable()).performClick()
        ui.awaitUntil(10_000) {
            ui.onAllNodesWithText("Калории по дням").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollToMuscles() {
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Мышцы за неделю"))
    }

    private fun seed(exercise: String, count: Int, day: LocalDate = LocalDate.now()) {
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            val workout = store.newWorkout(day)
            repeat(count) { store.saveSet(workout, SetDraft(exercise = exercise, weight = "60", reps = "8")) }
        }
    }
}
