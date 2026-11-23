package app.calorietraining

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class PhoneFixesTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun activityUsesPortrait() {
        val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation)
    }

    @Test fun busyFoodKeepsActionsEnabledAndDelaysProgress() {
        val busy = mutableStateOf(true)
        ui.mainClock.autoAdvance = false
        ui.setContent { FoodContent(FoodUiState(busy = busy.value), {}) }
        ui.onAllNodes(addMealButton)[0].assertIsEnabled()
        ui.onAllNodes(addMealButton)[1].assertIsEnabled()
        ui.onNodeWithTag("loading").assertDoesNotExist()
        ui.mainClock.advanceTimeBy(400)
        ui.onNodeWithTag("loading").assertExists()
        busy.value = false
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithTag("loading").assertDoesNotExist()
    }

    @Test fun busyProfileIgnoresSecondRequest() {
        ui.runOnIdle {
            val profile = ProfileViewModel(app)
            profile.refresh()
            profile.event(ProfileEvent.Open(LocalDate.now()))
            assertTrue(profile.uiState.value.busy)
            assertNull(profile.uiState.value.draft)
        }
    }

    @Test fun latestHistoryWeekWins() {
        val history = HistoryViewModel(app)
        val firstWeek = history.uiState.value.monday.minusWeeks(1)
        val secondWeek = firstWeek.plusWeeks(2)
        ui.runOnIdle {
            history.load(firstWeek)
            history.load(secondWeek)
            assertTrue(history.uiState.value.loading)
        }
        awaitHistory(history)
        assertEquals(secondWeek, history.uiState.value.monday)
        assertFalse(history.uiState.value.loading)
    }

    @Test fun latestHistoryExerciseWins() {
        val history = HistoryViewModel(app)
        val monday = history.uiState.value.monday
        val first = "Жим штанги лёжа"
        val second = "Приседания"
        val session = FoodStore(app).use { store ->
            store.newWorkout(monday).also {
                store.saveSet(it, SetDraft(exercise = first, reps = "5"))
                store.saveSet(it, SetDraft(exercise = second, reps = "8"))
            }
        }
        ui.runOnIdle {
            history.load(monday, first)
            history.load(monday, second)
        }
        awaitHistory(history)
        assertEquals(second, history.uiState.value.exercise)
        assertEquals(session, history.uiState.value.sessions.single().id)
        assertEquals("8", history.uiState.value.sessions.single().sets.single().reps)
    }

    private fun awaitHistory(history: HistoryViewModel) {
        ui.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            !history.uiState.value.loading
        }
        assertFalse(history.uiState.value.error)
    }

    @Test fun ringsFollowCalorieCarbProteinFatOrder() {
        ui.setContent { FoodContent(FoodUiState(loaded = true, goals = DailyGoals(2200, MacroGoals(140,70,250))), {}) }
        val description = ui.onNodeWithTag("nutrition-rings").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertEquals(listOf("Калории","Углеводы","Белки","Жиры"), description.split(", ").map { it.substringBefore(' ') })
    }

    @Test fun profileOffersAuto() {
        val model = ProfileViewModel(app)
        ui.setContent { ProfileScreen(model, ThemeMode.SYSTEM, {}) }
        ui.onNodeWithText("Авто").assertIsDisplayed()
        ui.onNodeWithText("Как в системе").assertDoesNotExist()
    }

    @Test fun workoutOmitsRemainingFilter() {
        ui.setContent { WorkoutContent(WorkoutState(list = listOf(Workout(1, emptyList()))), {}) }
        ui.onNodeWithText("Только оставшиеся").assertDoesNotExist()
    }

    private fun editor(name: String): androidx.compose.runtime.MutableState<SetDraft> {
        val draft = mutableStateOf(SetDraft(exercise = name))
        ui.setContent { WorkoutContent(WorkoutState(session = 1, draft = draft.value), {
            if (it is WorkoutEvent.Edit) draft.value = it.draft
        }) }
        return draft
    }

    @Test fun weightLabelFollowsEquipment() {
        val draft = editor("Жим гантелей лёжа")
        for ((name,label) in listOf("Жим гантелей лёжа" to "Вес одной гантели, кг", "Жим штанги лёжа" to "Вес, кг",
            "Жим гантелей стоя" to "Вес одной гантели, кг", "Сгибание рук молотком" to "Вес одной гантели, кг")) {
            ui.runOnIdle { draft.value = draft.value.copy(exercise = name) }
            ui.onNodeWithText(label).assertExists()
        }
    }

    @Test fun catalogNameIgnoresCaseAndYo() {
        editor("жим штанги лежа")
        ui.onNodeWithText("Название упражнения").assertDoesNotExist()
        ui.onNodeWithText("жим штанги лежа").assertExists()
        ui.onNodeWithText("Сменить").assertHasClickAction()
    }

    @Test fun catalogNameCanBeReplacedWithEmptyCustomName() {
        editor("Жим штанги лёжа")
        ui.onNodeWithText("Название упражнения").assertDoesNotExist()
        ui.onNodeWithText("Жим штанги лёжа").assertExists()
        ui.onNodeWithText("Сменить").assertHasClickAction()
        ui.onNodeWithText("Своё упражнение").performClick()
        val field = ui.onNodeWithText("Название упражнения").fetchSemanticsNode()
        assertEquals("", field.config[SemanticsProperties.EditableText].text)
    }
}
