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
class ProfileChartsUiTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createComposeRule()

    @Test fun weekCaloriesExposeAverageAndStrictlyAboveGoalDays() {
        val monday = LocalDate.now().minusDays(6)
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            seedProfileCalories(store, monday)
        }
        openProfile()
        ui.onNodeWithText("7 дней").performClick()
        val chart = hasContentDescription("среднее 2067 ккал", substring = true, ignoreCase = true) and
            hasContentDescription("выше цели 1 день", substring = true, ignoreCase = true)
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Калории по дням"))
        ui.awaitUntil(10_000) { ui.onAllNodes(chart).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(chart).assertExists()
        ui.onNodeWithText("В СРЕДНЕМ ЗА ДЕНЬ").assertDoesNotExist()
    }

    @Test fun personalDataExposeBmiAndRetainGoalAction() {
        val today = LocalDate.now()
        FoodStore(RuntimeEnvironment.getApplication()).use { store ->
            store.saveOnboarding(
                UserProfile(Sex.MALE, today.year - 32, 182, ActivityLevel.LIGHT, GoalKind.MAINTAIN),
                Measurement(today.minusDays(1), "90"), DailyGoals(2200)
            )
            store.saveMeasurement(Measurement(today, "82"))
        }
        openProfile()
        ui.onNode(
            hasContentDescription("ИМТ", substring = true) and hasContentDescription("24,8", substring = true)
        ).assertExists()
        ui.onNodeWithText("Пересчитать цель").assertExists()
        ui.onNodeWithText("Пол").assertDoesNotExist()
    }

    @Test fun emptyProfileOffersEntriesInsteadOfCharts() {
        openProfile()
        for ((section, invitation) in listOf(
            "Калории по дням" to "Добавь запись питания.",
            "БЖУ" to "Добавь БЖУ к записям питания.",
            "Мои замеры" to "Добавь первый замер",
            "Тренировки" to "Добавь тренировку.",
            "Жим штанги лёжа" to "Добавь рабочий подход жима лёжа.",
            "Сравнение периодов" to "Добавь записи для сравнения периодов.",
            "Когда я обычно ем" to "Добавь время еды."
        )) {
            ui.onNode(hasScrollAction()).performScrollToNode(hasText(section))
            ui.onAllNodesWithText(invitation, substring = true, ignoreCase = true).onFirst().assertExists()
            ui.onNodeWithContentDescription("График", substring = true).assertDoesNotExist()
        }
    }

    private fun openProfile() {
        ui.setContent { DiaryTheme(ThemeMode.DARK) { DiaryApp(ThemeMode.DARK, {}) } }
        ui.onNode(hasText("Профиль") and isSelectable()).performClick()
        ui.awaitUntil(10_000) { ui.onAllNodesWithText("Калории по дням").fetchSemanticsNodes().isNotEmpty() }
    }
}

internal fun seedProfileCalories(store: FoodStore, monday: LocalDate) {
    store.setGoals(monday, DailyGoals(2200))
    store.setGoals(monday.plusDays(2), DailyGoals(2000))
    for ((offset, kcal) in listOf(0L to "1800", 2L to "2400", 4L to "2000")) {
        store.save(monday.plusDays(offset), FoodDraft(name = "Обед", energy = kcal))
    }
}
