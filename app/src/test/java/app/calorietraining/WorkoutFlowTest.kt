package app.calorietraining

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
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
class WorkoutFlowTest {
    @get:Rule val ui = createComposeRule()
    private val app = RuntimeEnvironment.getApplication()
    private val clock = WorkoutTestClock()

    private fun show() {
        val model = WorkoutViewModel(app, clock)
        ui.setContent { MaterialTheme { WorkoutContent(model.uiState.collectAsState().value, model::event) } }
        ui.awaitUntil(10_000) {
            !model.uiState.value.busy
        }
    }

    private fun await(text: String): SemanticsNodeInteraction {
        ui.awaitUntil(5_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        return ui.onNodeWithText(text)
    }

    private fun reveal(text: String): SemanticsNodeInteraction {
        val list = hasScrollToIndexAction()
        ui.awaitUntil(5_000) { ui.onAllNodes(list).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(list).performScrollToNode(hasText(text))
        return ui.onNodeWithText(text)
    }

    private fun selectCalendarDay(day: String) {
        ui.onNodeWithText(", $day ноября 2026", substring = true).performClick()
        ui.onNodeWithText("Выбрать").performClick()
    }

    private fun fixtureList() = FoodStore(app).use { store ->
        store.fixture("Старая", "2026-11-20")
        store.fixture("Записанная", "2026-11-21T18:00")
        store.fixture("Живая", "2026-11-23T19:05", live = true)
    }

    @Test fun allWorkoutsAreNewestFirstAndLegacyDateHasNoTime() {
        fixtureList()
        show()
        val newest = reveal("Живая").fetchSemanticsNode().boundsInRoot.top
        val recorded = ui.onNodeWithText("Записанная").fetchSemanticsNode().boundsInRoot.top
        assertTrue(newest < recorded)
        reveal("Записанная").assertIsDisplayed()
        val middle = ui.onNodeWithText("Записанная").fetchSemanticsNode().boundsInRoot.top
        val oldest = ui.onNodeWithText("Старая").fetchSemanticsNode().boundsInRoot.top
        assertTrue(middle < oldest)
        reveal(LocalDate.parse("2026-11-20").caption()).assertIsDisplayed()
        ui.onNodeWithText("00:00", substring = true).assertDoesNotExist()
    }

    @Test fun startCreatesLiveWorkoutAndOnlyOneCanRun() {
        show()
        ui.onNodeWithText("Записать тренировку").assertIsEnabled()
        ui.onNodeWithText("Начать тренировку").performClick()
        await("Завершить тренировку")
        FoodStore(app).use { store ->
            assertEquals(listOf("2026-11-23", "2026-11-23T19:05", "1", null),
                store.row("SELECT day,started_at,live,finished_at FROM workout"))
        }
        ui.headerBack()
        await("Записать тренировку")
        ui.onNodeWithText("Идёт тренировка", substring = true).assertIsDisplayed()
        ui.onNodeWithText("Начать тренировку").assertDoesNotExist()
    }

    @Test fun recordingUsesSelectedPastDateAndTime() {
        show()
        ui.onNodeWithText("Записать тренировку").performClick()
        ui.onNodeWithText("Дата").performClick()
        selectCalendarDay("21")
        ui.onNodeWithText("Часы").performTextReplacement("18")
        ui.onNodeWithText("Минуты").performTextReplacement("00")
        ui.onNodeWithText("Записать").performClick()
        await("Добавить упражнение")
        FoodStore(app).use { store ->
            assertEquals(listOf("2026-11-21", "2026-11-21T18:00", "0"),
                store.row("SELECT day,started_at,live FROM workout"))
        }
    }

    @Test fun liveWorkoutShowsRestAcrossExercisesAndCompletes() {
        val id = FoodStore(app, clock = clock).use { store ->
            val session = store.fixture("Силовая", "2026-11-23T19:05", live = true)
            val bench = SetDraft(group = "bench", exercise = "Жим штанги лёжа", weight = "40", reps = "10")
            clock.set("2026-11-23T19:10:00")
            store.saveSet(session, bench)
            clock.set("2026-11-23T19:12:30")
            store.saveSet(session, SetDraft(group = "row", exercise = "Тяга", weight = "45", reps = "9"))
            clock.set("2026-11-23T19:15:00")
            store.saveSet(session, bench.copy(weight = "60", reps = "8"))
            session
        }
        clock.set("2026-11-23T19:16:40")
        show()
        ui.onNodeWithText("Силовая").performClick()
        ui.onNodeWithText("1:40").assertIsDisplayed()
        reveal("Тяга").assertIsDisplayed()
        val row = ui.onNodeWithText("Тяга").fetchSemanticsNode().boundsInRoot.top
        val bench = ui.onNodeWithText("Жим штанги лёжа").fetchSemanticsNode().boundsInRoot.top
        assertTrue(row < bench)
        reveal("Жим штанги лёжа").assertIsDisplayed()
        assertEquals(2, ui.onAllNodesWithText("отдых 2:30").fetchSemanticsNodes().size)
        val first = ui.onNodeWithText("40 кг × 10").fetchSemanticsNode().boundsInRoot
        val second = ui.onNodeWithText("60 кг × 8").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top < second.top || first.top == second.top && first.left < second.left)
        clock.set("2026-11-23T20:01:00")
        reveal("Завершить тренировку").performClick()
        ui.onNodeWithText("Завершить").performClick()
        ui.awaitUntil(5_000) { ui.onAllNodesWithText("Завершить тренировку").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("rest-ring").assertDoesNotExist()
        FoodStore(app).use { store ->
            assertEquals(listOf("2026-11-23T20:01", "56"),
                store.row("SELECT finished_at,minutes FROM workout WHERE id=$id"))
        }
    }

    @Test fun staleLiveWorkoutFinishesOnOpeningTab() {
        val id = FoodStore(app).use { store ->
            val session = store.fixture("Вчерашняя", "2026-11-23T19:05", live = true)
            store.fixtureSet(session, "bench", "Жим", "60", "8", "2026-11-23T19:15:00")
            session
        }
        clock.set("2026-11-23T22:20:00")
        show()
        ui.onNodeWithText("Идёт тренировка", substring = true).assertDoesNotExist()
        FoodStore(app).use { store ->
            assertEquals(listOf("2026-11-23T19:15", "10"),
                store.row("SELECT finished_at,minutes FROM workout WHERE id=$id"))
        }
    }

    @Test fun creationAndEditingHaveDistinctActionsAndNewSetInheritsValues() {
        FoodStore(app).use { store ->
            val id = store.fixture("Силовая", "2026-11-20")
            store.fixtureSet(id, "bench", "Жим штанги лёжа", "60", "8", null)
        }
        show()
        reveal("Силовая").performClick()
        reveal("Добавить упражнение").performClick()
        ui.onNodeWithText("Новое упражнение").assertIsDisplayed()
        ui.onNodeWithText("Удалить подход").assertDoesNotExist()
        ui.onNodeWithText("Выбрать из каталога").performClick()
        ui.onNodeWithText("Каталог упражнений").assertIsDisplayed()
        ui.headerBack()
        ui.onNodeWithText("Новое упражнение").assertIsDisplayed()
        ui.headerBack()
        reveal("60 кг × 8").performClick()
        ui.onNodeWithText("Изменить подход").assertIsDisplayed()
        ui.onNodeWithText("Удалить подход").assertExists()
        ui.onNodeWithText("Сохранить изменения").assertExists()
        ui.headerBack()
        reveal("+ Подход").performClick()
        ui.onNodeWithText("Новый подход · Жим штанги лёжа").assertIsDisplayed()
        ui.onNode(hasSetTextAction() and hasText("60")).assertExists()
        ui.onNode(hasSetTextAction() and hasText("8")).assertExists()
    }

    @Test fun returnToPlanClearsTimestampAndCompleteRecordsClockTime() {
        val id = FoodStore(app).use { store ->
            val session = store.fixture("Силовая", "2026-11-23T19:05", live = true)
            store.fixtureSet(session, "bench", "Жим", "60", "8", "2026-11-23T19:10:00")
            session
        }
        clock.set("2026-11-23T19:12:30")
        show()
        ui.onNodeWithText("Силовая").performClick()
        reveal("60 кг × 8").performClick()
        ui.onNodeWithText("Повторы").performTextReplacement("7")
        ui.onNodeWithText("Сохранить изменения").performScrollTo().performClick()
        await("Добавить упражнение")
        reveal("60 кг × 7").assertIsDisplayed()
        FoodStore(app).use { store ->
            assertEquals(listOf("0", "2026-11-23T19:10:00"), store.row("SELECT planned,done_at FROM lifting WHERE session=$id"))
        }
        reveal("60 кг × 7").performClick()
        ui.onNodeWithText("Вернуть в план").performScrollTo().performClick()
        await("Засчитать")
        FoodStore(app).use { store ->
            assertEquals(listOf("1", null), store.row("SELECT planned,done_at FROM lifting WHERE session=$id"))
        }
        reveal("Засчитать").performClick()
        ui.awaitUntil(5_000) { ui.onAllNodesWithText("Засчитать").fetchSemanticsNodes().isEmpty() }
        FoodStore(app).use { store ->
            assertEquals(listOf("0", "2026-11-23T19:12:30"), store.row("SELECT planned,done_at FROM lifting WHERE session=$id"))
        }
    }

    @Test fun jumpToDateUsesClosestEarlierWorkout() {
        fixtureList()
        show()
        ui.onNodeWithText("К дате").performClick()
        selectCalendarDay("22")
        ui.onNodeWithText("Записанная").assertIsDisplayed()
        ui.onNodeWithText("Живая").assertIsNotDisplayed()
    }

    @Test fun historyOpensSelectedWorkoutEvenWhenAnotherWorkoutHasSameDay() {
        FoodStore(app).use { store ->
            val requested = store.fixture("Из истории", "2026-11-20")
            store.fixtureSet(requested, "bench", "Жим", "60", "8", null)
            store.fixture("Другая тренировка", "2026-11-20")
        }
        OnboardingSettings(org.robolectric.RuntimeEnvironment.getApplication()).skipped = true
        ui.setContent { MaterialTheme { DiaryApp(ThemeMode.DARK) {} } }
        ui.onNodeWithText("История").performClick()
        await("Упражнения").performClick()
        reveal("Из истории").performClick()
        await("Добавить упражнение").assertIsDisplayed()
        ui.onNodeWithText("Из истории").assertIsDisplayed()
        ui.onNodeWithText("Другая тренировка").assertDoesNotExist()
    }

    private fun loadedModel(): WorkoutViewModel = WorkoutViewModel(app, clock).also(::awaitLoaded)

    private fun awaitLoaded(model: WorkoutViewModel) {
        ui.awaitUntil(10_000) { !model.uiState.value.busy }
    }

    private fun duringLoad(
        model: WorkoutViewModel,
        change: (FoodStore.() -> Unit)? = null,
        action: FoodStore.() -> Unit,
    ) {
        FoodStore(app).use { store ->
            val db = store.writableDatabase
            db.beginTransaction()
            try {
                change?.invoke(store)
                db.setTransactionSuccessful()
                store.action()
                assertTrue(model.uiState.value.busy)
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun recorded(title: String = "Силовая"): Long =
        FoodStore(app).use { it.fixture(title, "2026-11-20") }

    @Test fun opensDuringRefreshAndKeepsSelectionWithFreshRows() {
        val id = recorded()
        val model = loadedModel()
        duringLoad(model, { fixture("Добавленная", "2026-11-21") }) {
            model.event(WorkoutEvent.Refresh)
            model.event(WorkoutEvent.Open(id))
            assertEquals(id, model.uiState.value.opened)
        }
        awaitLoaded(model)
        assertEquals(id, model.uiState.value.opened)
        assertTrue(model.uiState.value.list.any { it.title == "Добавленная" })
    }

    @Test fun goesBackDuringRefreshAndStaysOnList() {
        val id = recorded()
        val model = loadedModel()
        model.event(WorkoutEvent.Open(id))
        duringLoad(model) {
            model.event(WorkoutEvent.Refresh)
            model.event(WorkoutEvent.BackToList)
            assertNull(model.uiState.value.opened)
        }
        awaitLoaded(model)
        assertNull(model.uiState.value.opened)
    }

    @Test fun lastTransitionDuringRefreshWins() {
        val first = recorded()
        val second = recorded("Вторая")
        val model = loadedModel()
        duringLoad(model) {
            model.event(WorkoutEvent.Refresh)
            model.event(WorkoutEvent.Open(first))
            model.event(WorkoutEvent.BackToList)
            model.event(WorkoutEvent.Open(second))
            assertEquals(second, model.uiState.value.opened)
        }
        awaitLoaded(model)
        assertEquals(second, model.uiState.value.opened)
    }

    @Test fun openingPreviousWorkoutTakesPriorityOverNewLiveWorkout() {
        val id = recorded()
        val model = loadedModel()
        duringLoad(model) {
            model.event(WorkoutEvent.StartLive)
            model.event(WorkoutEvent.Open(id))
            assertEquals(id, model.uiState.value.opened)
        }
        awaitLoaded(model)
        assertEquals(id, model.uiState.value.opened)
        assertEquals(1, model.uiState.value.list.count { it.active })
        assertEquals(2, model.uiState.value.list.size)
    }

    @Test fun startingLiveWorkoutWithoutNavigationOpensIt() {
        val model = loadedModel()
        duringLoad(model) { model.event(WorkoutEvent.StartLive) }
        awaitLoaded(model)
        assertEquals(model.uiState.value.list.single { it.active }.id, model.uiState.value.opened)
    }

    @Test fun writesDuringLoadAreDropped() {
        val id = recorded()
        val model = loadedModel()
        duringLoad(model) {
            model.event(WorkoutEvent.StartLive)
            model.event(WorkoutEvent.Remove(id))
            model.event(WorkoutEvent.StartLive)
        }
        awaitLoaded(model)
        FoodStore(app).use { store ->
            assertEquals(listOf("2", "1", "1"), store.row(
                "SELECT count(*), sum(live), sum(id=$id) FROM workout"
            ))
        }
    }

    @Test fun refreshErrorKeepsOpenedWorkoutAndNextRefreshClearsError() {
        val id = recorded()
        val model = loadedModel()
        ui.setContent { MaterialTheme { WorkoutContent(model.uiState.collectAsState().value, model::event) } }
        duringLoad(model, { writableDatabase.execSQL("ALTER TABLE workout RENAME TO unavailable_workout") }) {
            model.event(WorkoutEvent.Refresh)
            model.event(WorkoutEvent.Open(id))
        }
        try {
            awaitLoaded(model)
            assertEquals(id, model.uiState.value.opened)
            await("Не удалось открыть или сохранить тренировку").assertIsDisplayed()
        } finally {
            FoodStore(app).use {
                it.writableDatabase.execSQL("ALTER TABLE unavailable_workout RENAME TO workout")
            }
        }
        model.event(WorkoutEvent.Refresh)
        awaitLoaded(model)
        assertEquals(id, model.uiState.value.opened)
        ui.onNodeWithText("Не удалось открыть или сохранить тренировку").assertDoesNotExist()
    }

    @Test fun workoutRemovedDuringRefreshClosesWhenRowsArrive() {
        val id = recorded()
        val model = loadedModel()
        duringLoad(model) {
            model.event(WorkoutEvent.Refresh)
            model.event(WorkoutEvent.Open(id))
            writableDatabase.delete("workout", "id=?", arrayOf(id.toString()))
            assertEquals(id, model.uiState.value.opened)
        }
        awaitLoaded(model)
        assertNull(model.uiState.value.opened)
        assertTrue(model.uiState.value.list.isEmpty())
        assertNull(model.uiState.value.error)
    }

    @Test fun diaryOpensWorkoutDuringTabRefreshAndReturnsToHistoryDuringRefresh() {
        val id = recorded()
        FoodStore(app).use { it.fixtureSet(id, "bench", "Жим", "60", "8", null) }
        val model = loadedModel()
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        ViewModelProvider(owner, viewModelFactory { initializer { model } })
            .get("workout-0", WorkoutViewModel::class.java)
        OnboardingSettings(app).skipped = true
        ui.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                MaterialTheme { DiaryApp(ThemeMode.DARK) {} }
            }
        }
        val workouts = ui.onNode(hasText("Тренировки") and isSelectable())
        duringLoad(model) {
            workouts.performClick()
            ui.awaitUntil(10_000) { model.uiState.value.busy }
            reveal("Силовая").performClick()
            ui.onNodeWithText("Добавить упражнение").assertIsDisplayed()
        }
        awaitLoaded(model)
        ui.onNodeWithText("Добавить упражнение").assertIsDisplayed()
        ui.onNode(hasText("История") and isSelectable()).performClick()
        await("Упражнения").performClick()
        reveal("Силовая").performClick()
        awaitLoaded(model)
        await("Добавить упражнение")
        duringLoad(model) {
            ui.runOnIdle { model.event(WorkoutEvent.Refresh) }
            ui.headerBack()
            await("Упражнения").assertIsDisplayed()
        }
        awaitLoaded(model)
        workouts.performClick()
        awaitLoaded(model)
        reveal("Силовая").assertIsDisplayed()
        ui.onNodeWithText("Добавить упражнение").assertDoesNotExist()
    }

}
