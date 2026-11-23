package app.calorietraining

import android.os.Looper
import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import org.robolectric.RuntimeEnvironment
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class WorkoutScreenTest {
    @get:Rule val ui = createComposeRule()
    private val warmup = SetDraft(1, "bench", "Жим лёжа", "40", "12", warmup = true)
    private val working = warmup.copy(id = 2, weight = "60", reps = "8", warmup = false)
    private val planned = working.copy(id = 4, weight = "62,5", reps = "6", planned = true)
    private val workout = Workout(7, listOf(warmup, working, working.copy(id = 3), planned,
        SetDraft(5, "pullups", "Подтягивания", "", "10")))
    private var event: WorkoutEvent? = null

    private fun show(session: Workout) {
        ui.setContent { MaterialTheme {
            WorkoutContent(WorkoutState(day = LocalDate.now().minusDays(1), list = listOf(session), opened = session.id), { event = it })
        } }
    }

    private fun reveal(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        return ui.onNode(matcher)
    }

    @Test fun volumeIncludesWarmupAndCompletedWeightedSets() {
        assertEquals(0, workout.volume().compareTo(1440.toBigDecimal()))
    }

    @Test fun ringFractionStaysWithinBounds() {
        assertEquals(0.7f, restFraction(84_000, 120_000), 0.0001f)
        assertEquals(0f, restFraction(0, 120_000), 0f)
        assertEquals(1f, restFraction(130_000, 120_000), 0f)
        assertEquals(0f, restFraction(5_000, 0), 0f)
    }

    @Test fun metricsTilesAndNextSetMatchWorkout() {
        show(workout)
        reveal(hasText("4/5")).assertIsDisplayed()
        reveal(hasText("1\u00a0440 кг")).assertIsDisplayed()
        reveal(hasText("40 кг × 12")).assertIsDisplayed()
        reveal(hasContentDescription("Подход 4: 62,5 кг × 6, в плане")).assertTextEquals("62,5 кг × 6")
        reveal(hasText("Следующий · Жим лёжа")).assertIsDisplayed()
        ui.onNode(hasText("62,5 кг × 6") and !hasClickAction()).assertIsDisplayed()
        ui.onNodeWithText("Засчитать").performClick()
        assertEquals(WorkoutEvent.Complete(7, 4), event)
        val earlierGroup = planned.copy(id = 6, group = "other")
        assertEquals(planned, workout.copy(sets = listOf(warmup, earlierGroup, planned)).nextPlanned())
    }

    @Test fun completedWorkoutHasNoNextAction() {
        show(workout.copy(sets = listOf(working)))
        ui.onNodeWithText("Засчитать").assertDoesNotExist()
    }

    @Test fun tappingTileEditsThatSet() {
        show(workout)
        reveal(hasContentDescription("Подход 2: 60 кг × 8, выполнен")).performClick()
        assertEquals(WorkoutEvent.Edit(7, working), event)
    }

    @Test fun editorCompletesLaterPlannedSetWithoutChangingFirst() {
        val app = RuntimeEnvironment.getApplication()
        val day = LocalDate.now()
        FoodStore(app).use { store ->
            val session = store.newWorkout(day)
            store.saveSet(session, working.copy(id = null), planned = true)
            store.saveSet(session, planned.copy(id = null), planned = true)
        }
        val model = WorkoutViewModel(app)
        ui.setContent { MaterialTheme { WorkoutContent(model.uiState.collectAsState().value, model::event) } }
        ui.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            model.uiState.value.list.isNotEmpty()
        }
        ui.runOnIdle { model.event(WorkoutEvent.Open(model.uiState.value.list.single().id)) }
        reveal(hasContentDescription("Подход 2: 62,5 кг × 6, в плане")).performClick()
        ui.onNodeWithText("Отметить выполненным").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("1/2").fetchSemanticsNodes().isNotEmpty() }
        FoodStore(app).use { store ->
            val sets = store.workouts(day).single().sets
            assertTrue(sets[0].planned)
            assertFalse(sets[1].planned)
        }
    }

    @Test fun runningTimerExtendsAndStops() {
        var change: Pair<Long, Long>? = null
        val deadline = SystemClock.elapsedRealtime() + 84_000
        ui.setContent { RestTimer(deadline, 120_000) { end, total -> change = end to total } }
        ui.onNodeWithText("1:24").assertIsDisplayed()
        ui.onNodeWithTag("rest-ring").assertIsDisplayed()
        ui.onNodeWithText("+30 с").performClick()
        assertEquals(deadline + 30_000 to 150_000L, change)
        ui.onNodeWithText("Стоп").performClick()
        assertEquals(0L to 0L, change)
    }

    @Test fun presetStartsTimerImmediately() {
        var change: Pair<Long, Long>? = null
        var clickTime = 0L
        ui.setContent { RestTimer(0, 0) { end, total ->
            clickTime = SystemClock.elapsedRealtime()
            change = end to total
        } }
        ui.onNodeWithText("2 мин").performClick()
        assertEquals(clickTime + 120_000 to 120_000L, change)
    }
}
