package app.calorietraining

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class GlassWorkoutsTest {
    @get:Rule val ui = createComposeRule()
    private val start = LocalDateTime.parse("2026-11-23T19:05")
    private val workout = Workout(1, listOf(
        SetDraft(1, "bench", "Жим лёжа", "60", "8", planned = true),
        SetDraft(2, "pullups", "Подтягивания", "", "10")
    ), "Верх тела", 52, day = start.toLocalDate(), startedAt = start)

    @Test fun feedAndDetailsUseGlassCards() {
        val state = mutableStateOf(WorkoutState(list = listOf(workout, workout.copy(
            id = 2, title = "Силовая", sets = emptyList(), day = start.toLocalDate().minusDays(1)
        ))))
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) { DiaryBackground {
                WorkoutContent(state.value, { event ->
                    if (event is WorkoutEvent.Open) state.value = state.value.copy(opened = event.id)
                })
            } }
        }
        ui.onAllNodesWithTag("glass-card").assertCountEquals(2)
        ui.onNodeWithText("Верх тела").performClick()
        ui.onNodeWithText("Подтягивания").assertIsDisplayed()
        ui.onAllNodesWithTag("glass-card").assertCountEquals(4)
    }

    @Test fun liveWorkoutUpdatesElapsedMinutes() {
        val clock = WorkoutTestClock().apply { set("2026-11-23T19:47") }
        val live = workout.copy(minutes = null, live = true)
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) { DiaryBackground {
                WorkoutContent(WorkoutState(list = listOf(live), opened = live.id, clock = clock), {})
            } }
        }
        ui.onNode(hasText("42 мин") and hasAnySibling(hasText("ВРЕМЯ"))).assertIsDisplayed()
        ui.mainClock.autoAdvance = false
        clock.set("2026-11-23T19:48")
        ui.mainClock.advanceTimeBy(60_000)
        ui.onNode(hasText("43 мин") and hasAnySibling(hasText("ВРЕМЯ"))).assertIsDisplayed()
    }

    @Test fun nutritionHistoryDayRowsAreInsideGlass() {
        val app = RuntimeEnvironment.getApplication()
        val model = HistoryViewModel(app)
        val monday = model.uiState.value.monday
        FoodStore(app).use { store ->
            store.save(monday, FoodDraft(name = "Обед", weighted = false, energy = "900"))
        }
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) { DiaryBackground { HistoryScreen(model, {}, {}) } }
        }
        ui.awaitUntil(10_000) { model.uiState.value.totals.containsKey(monday) }
        val dayRow = hasClickAction() and hasText("900 ккал")
        val scroll = ui.onNode(hasScrollToIndexAction())
        scroll.performScrollToNode(dayRow)
        ui.onNode(dayRow and hasAnyAncestor(hasTestTag("glass-card"))).assertIsDisplayed()
        val emptyDay = hasClickAction() and hasText("Нет записей") and hasText(monday.plusDays(1).caption())
        scroll.performScrollToNode(emptyDay)
        ui.onNode(emptyDay and hasAnyAncestor(hasTestTag("glass-card"))).assertIsDisplayed()
    }

    @Test fun myProductsShowFullSourceLabels() {
        val products = listOf(
            MyProduct(id = 1, name = "Кефир", source = "openfoodfacts"),
            MyProduct(id = 2, name = "Кофе")
        )
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) { DiaryBackground { MyProductsScreen(products, {}, {}, {}) } }
        }
        ui.onNodeWithText("Open Food Facts").assertIsDisplayed()
        ui.onNodeWithText("Мой продукт").assertIsDisplayed()
        ui.onNodeWithText("OFF").assertDoesNotExist()
    }
}
