package app.calorietraining

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.activity.ComponentDialog
import org.robolectric.shadows.ShadowDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class WorkoutStatusTest {
    @get:Rule val ui = createComposeRule()
    private val app = RuntimeEnvironment.getApplication()
    private val clock = WorkoutTestClock()

    private fun show(): WorkoutViewModel {
        val model = WorkoutViewModel(app, clock)
        ui.setContent { MaterialTheme { WorkoutContent(model.uiState.collectAsState().value, model::event) } }
        awaitLoaded(model)
        return model
    }

    private fun awaitLoaded(model: WorkoutViewModel) = ui.awaitUntil(10_000) { !model.uiState.value.busy }

    private fun await(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        ui.awaitUntil(5_000) { ui.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }
        return ui.onNodeWithText(text, substring)
    }

    private fun reveal(text: String): SemanticsNodeInteraction {
        val list = hasScrollToIndexAction()
        ui.awaitUntil(5_000) { ui.onAllNodes(list).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(list).performScrollToNode(hasText(text))
        return ui.onNodeWithText(text)
    }

    private fun exists(text: String, substring: Boolean = false) =
        ui.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty()

    /** Завершённая: не идущая, с выполненным подходом и без плановых. */
    private fun completed(title: String = "Готовая", withPlanned: Boolean = false, finished: Boolean = false): Long =
        FoodStore(app, clock = clock).use { store ->
            val id = store.fixture(title, "2026-11-20T18:00")
            store.fixtureSet(id, "bench", "Жим штанги лёжа", "60", "8", null)
            if (withPlanned) store.saveSet(id, SetDraft(group = "row", exercise = "Тяга", weight = "40", reps = "10"), planned = true)
            if (finished) store.writableDatabase.execSQL(
                "UPDATE workout SET finished_at='2026-11-20T19:00', minutes=60 WHERE id=$id")
            id
        }

    private fun addSet(name: String, weight: String, reps: String) {
        await("Добавить упражнение").performClick()
        ui.onNodeWithText("Название упражнения").performTextInput(name)
        ui.onNodeWithText("Вес, кг").performTextInput(weight)
        ui.onNodeWithText("Повторы").performTextInput(reps)
        ui.onNodeWithText("Записать подход").performScrollTo().performClick()
    }

    private fun openFromFeed(title: String) {
        await(title).performClick()
    }

    @Test fun feedShowsStatusOfEachWorkout() {
        FoodStore(app, clock = clock).use { store ->
            store.fixture("Идущая", "2026-11-23T19:00", live = true)
            completed("Готовая")
            val plan = store.fixture("Плановая", "2026-11-19T18:00")
            store.saveSet(plan, SetDraft(group = "g", exercise = "Жим", weight = "50", reps = "8"), planned = true)
            store.fixture("Пустая", "2026-11-18T18:00")
        }
        show()
        reveal("Пустая")
        assertEquals(1, ui.onAllNodesWithText("· Идёт", substring = true).fetchSemanticsNodes().size)
        assertEquals(1, ui.onAllNodesWithText("· Завершена", substring = true).fetchSemanticsNodes().size)
        assertEquals(1, ui.onAllNodesWithText("· План", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun completedWorkoutOpensForViewingAndEditsOnRequest() {
        completed()
        show()
        openFromFeed("Готовая")
        await("Редактировать").assertIsDisplayed()
        await("· Завершена", substring = true).assertIsDisplayed()
        ui.onNodeWithText("Добавить упражнение").assertDoesNotExist()
        ui.onNodeWithText("Засчитать").assertDoesNotExist()
        reveal("60 кг × 8").assertHasNoClickAction()
        ui.onNodeWithText("+ Подход").assertDoesNotExist()
        ui.onNodeWithText("Редактировать").performClick()
        reveal("Добавить упражнение").assertIsDisplayed()
        reveal("60 кг × 8").assertHasClickAction()
        reveal("+ Подход").assertIsDisplayed()
        ui.onNodeWithText("Готово").assertIsDisplayed()
        ui.onNodeWithText("Редактировать").assertDoesNotExist()
        ui.onNodeWithText("Готово").performClick()
        await("Редактировать")
        ui.onNodeWithText("Добавить упражнение").assertDoesNotExist()
        reveal("60 кг × 8").assertHasNoClickAction()
    }

    @Test fun viewingOffersOnlyRepeatAndTemplates() {
        val id = completed()
        FoodStore(app).use { it.saveTemplate(id, "Верх А") }
        show()
        openFromFeed("Готовая")
        await("Действия").performClick()
        await("Повторить").assertIsDisplayed()
        ui.onNodeWithText("Сохранить как шаблон").assertIsDisplayed()
        ui.onNodeWithText("Обновить шаблон").assertIsDisplayed()
        for (hidden in listOf("Переименовать", "Задать название", "Изменить время начала", "Длительность",
            "Перенести на дату", "Удалить")) ui.onNodeWithText(hidden).assertDoesNotExist()
    }

    @Test fun editingShowsAllActionsIncludingDelete() {
        completed()
        show()
        openFromFeed("Готовая")
        await("Редактировать").performClick()
        await("Действия").performClick()
        for (shown in listOf("Переименовать", "Изменить время начала", "Длительность", "Сохранить как шаблон",
            "Перенести на дату", "Повторить", "Удалить")) ui.onNodeWithText(shown).assertIsDisplayed()
    }

    @Test fun editingSetOfFinishedWorkoutKeepsTimesAndShowsNoTimer() {
        val id = FoodStore(app, clock = clock).use { store ->
            val id = store.fixture("Силовая", "2026-11-23T17:00", live = true)
            store.fixtureSet(id, "bench", "Жим", "60", "8", "2026-11-23T17:10:00")
            store.writableDatabase.execSQL("UPDATE workout SET finished_at='2026-11-23T18:00', minutes=60 WHERE id=$id")
            id
        }
        val before = FoodStore(app).use {
            it.row("SELECT started_at,finished_at,minutes,(SELECT done_at FROM lifting WHERE session=$id) FROM workout WHERE id=$id")
        }
        show()
        openFromFeed("Силовая")
        await("Редактировать").performClick()
        reveal("60 кг × 8").performClick()
        ui.onNodeWithText("Повторы").performTextReplacement("7")
        ui.onNodeWithText("Сохранить изменения").performScrollTo().performClick()
        await("Готово")
        reveal("60 кг × 7").assertIsDisplayed()
        ui.onNodeWithTag("rest-ring").assertDoesNotExist()
        FoodStore(app).use {
            assertEquals(before, it.row(
                "SELECT started_at,finished_at,minutes,(SELECT done_at FROM lifting WHERE session=$id) FROM workout WHERE id=$id"))
            assertEquals("7", it.row("SELECT reps FROM lifting WHERE session=$id")[0])
        }
    }

    @Test fun finishingLiveWorkoutOpensViewingAtOnce() {
        show()
        ui.onNodeWithText("Начать тренировку").performClick()
        addSet("Жим лёжа", "40", "12")
        await("Завершить тренировку").performClick()
        ui.onNodeWithText("Завершить").performClick()
        await("Редактировать").assertIsDisplayed()
        ui.onNodeWithText("Добавить упражнение").assertDoesNotExist()
        await("· Завершена", substring = true)
    }

    @Test fun recordedWorkoutStaysInEditAfterFirstSetAndOpensViewingLater() {
        show()
        ui.onNodeWithText("Записать тренировку").performClick()
        ui.onNodeWithText("Дата").performClick()
        ui.onNodeWithText(", 21 ноября 2026", substring = true).performClick()
        ui.onNodeWithText("Выбрать").performClick()
        ui.onNodeWithText("Записать").performClick()
        addSet("Жим лёжа", "40", "12")
        await("Готово").assertIsDisplayed()
        reveal("Добавить упражнение").assertIsDisplayed()
        ui.headerBack()
        openFromFeed("Тренировка")
        await("Редактировать").assertIsDisplayed()
        ui.onNodeWithText("Добавить упражнение").assertDoesNotExist()
        await("· Завершена", substring = true)
    }

    @Test fun repeatedWorkoutOpensAsPlanInEditAndBecomesCompletedKeepingEdit() {
        val id = completed()
        val model = show()
        ui.runOnIdle { model.event(WorkoutEvent.Repeat(id, LocalDate.now(clock))) }
        awaitLoaded(model)
        await("· План", substring = true).assertIsDisplayed()
        reveal("Добавить упражнение").assertIsDisplayed()
        reveal("Засчитать").performClick()
        await("· Завершена", substring = true).assertIsDisplayed()
        await("Готово").assertIsDisplayed()
        reveal("Добавить упражнение").assertIsDisplayed()
    }

    @Test fun completedWorkoutWithPlannedSetsOpensForViewing() {
        completed(withPlanned = true, finished = true)
        show()
        openFromFeed("Готовая")
        await("Редактировать").assertIsDisplayed()
        ui.onNodeWithText("Засчитать").assertDoesNotExist()
        ui.onNodeWithText("Добавить упражнение").assertDoesNotExist()
        await("· Завершена", substring = true)
    }

    @Test fun planOpensInEditAndEmptyWorkoutHasNoStatus() {
        FoodStore(app, clock = clock).use { store ->
            val plan = store.fixture("Плановая", "2026-11-19T18:00")
            store.saveSet(plan, SetDraft(group = "g", exercise = "Жим", weight = "50", reps = "8"), planned = true)
            store.fixture("Пустая", "2026-11-18T18:00")
        }
        show()
        openFromFeed("Плановая")
        reveal("Добавить упражнение").assertIsDisplayed()
        ui.onNodeWithText("Редактировать").assertDoesNotExist()
        ui.headerBack()
        openFromFeed("Пустая")
        reveal("Добавить упражнение").assertIsDisplayed()
        ui.onNodeWithText("Редактировать").assertDoesNotExist()
    }

    @Test fun deleteDialogCancelAndBackKeepWorkoutAndConfirmRemovesIt() {
        val id = completed()
        show()
        openFromFeed("Готовая")
        await("Редактировать").performClick()
        openDeleteDialog()
        ui.onNodeWithText("Подходы тоже удалятся. Отменить нельзя.").assertIsDisplayed()
        ui.onNodeWithText("Отмена").performClick()
        ui.awaitUntil(5_000) { !exists("Удалить тренировку?") }
        assertEquals("1", FoodStore(app).use { it.row("SELECT count(*) FROM workout WHERE id=$id")[0] })
        openDeleteDialog()
        ui.runOnIdle { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        ui.awaitUntil(5_000) { !exists("Удалить тренировку?") }
        assertEquals("1", FoodStore(app).use { it.row("SELECT count(*) FROM workout WHERE id=$id")[0] })
        openDeleteDialog()
        ui.onNodeWithText("Удалить").performClick()
        await("Записать тренировку")
        FoodStore(app).use {
            assertEquals(listOf("0", "0"), it.row("SELECT (SELECT count(*) FROM workout), (SELECT count(*) FROM lifting)"))
        }
    }

    private fun openDeleteDialog() {
        await("Действия").performClick()
        await("Удалить").performClick()
        await("Удалить тренировку?").assertIsDisplayed()
    }

    @Test fun eventsChangingCompletedWorkoutOutsideEditAreIgnored() {
        val id = completed(withPlanned = true, finished = true)
        val plannedId = FoodStore(app).use { it.row("SELECT id FROM lifting WHERE planned=1")[0]!!.toLong() }
        val snapshot = {
            FoodStore(app).use {
                it.row("SELECT title,day,started_at,finished_at,minutes,(SELECT count(*) FROM lifting),(SELECT sum(planned) FROM lifting) FROM workout")
            }
        }
        val before = snapshot()
        val model = WorkoutViewModel(app, clock)
        awaitLoaded(model)
        model.event(WorkoutEvent.Open(id))
        val set = SetDraft(id = plannedId, group = "row", exercise = "Тяга", weight = "40", reps = "10", planned = true)
        val rejected = listOf(
            WorkoutEvent.Edit(id, set), WorkoutEvent.Complete(id, plannedId), WorkoutEvent.Rename(id, "Другое"),
            WorkoutEvent.Move(id, LocalDate.parse("2026-11-10")), WorkoutEvent.Duration(id, 30),
            WorkoutEvent.ChangeStart(id, LocalDateTime.parse("2026-11-10T10:00")), WorkoutEvent.Remove(id),
            WorkoutEvent.Save(), WorkoutEvent.DeleteSet,
        )
        for (event in rejected) {
            model.event(event)
            assertFalse("$event не должно начинать запись", model.uiState.value.busy)
            assertNull("$event", model.uiState.value.draft)
        }
        awaitLoaded(model)
        assertEquals(before, snapshot())

        model.event(WorkoutEvent.StartEdit(id))
        model.event(WorkoutEvent.Rename(id, "Другое"))
        awaitLoaded(model)
        assertEquals("Другое", snapshot()[0])
        model.event(WorkoutEvent.StopEdit)
        model.event(WorkoutEvent.Rename(id, "Третье"))
        assertFalse(model.uiState.value.busy)
        assertEquals("Другое", snapshot()[0])
    }

    @Test fun editToggleDuringLoadSurvivesIt() {
        val id = completed()
        val model = WorkoutViewModel(GatedApplication(app), clock)
        awaitLoaded(model)
        model.event(WorkoutEvent.Open(id))
        val gated = model.getApplication<GatedApplication>()
        val gate = CountDownLatch(1)
        gated.gate = gate
        try {
            model.event(WorkoutEvent.Refresh)
            assertTrue(model.uiState.value.busy)
            model.event(WorkoutEvent.StartEdit(id))
            assertEquals(id, model.uiState.value.editing)
        } finally {
            gated.gate = null
            gate.countDown()
        }
        awaitLoaded(model)
        assertEquals(id, model.uiState.value.editing)
        gate.let { gated.gate = CountDownLatch(1) }
        try {
            model.event(WorkoutEvent.Refresh)
            assertTrue(model.uiState.value.busy)
            model.event(WorkoutEvent.StopEdit)
            assertNull(model.uiState.value.editing)
        } finally {
            val held = gated.gate
            gated.gate = null
            held?.countDown()
        }
        awaitLoaded(model)
        assertNull(model.uiState.value.editing)
    }

    @Test fun deleteIsDisabledWhileBusyAndRemovesOnceAfterwards() {
        val id = completed()
        val model = WorkoutViewModel(GatedApplication(app), clock)
        awaitLoaded(model)
        ui.setContent { MaterialTheme { WorkoutContent(model.uiState.collectAsState().value, model::event) } }
        openFromFeed("Готовая")
        await("Редактировать").performClick()
        openDeleteDialog()
        val gated = model.getApplication<GatedApplication>()
        val gate = CountDownLatch(1)
        gated.gate = gate
        try {
            ui.runOnIdle { model.event(WorkoutEvent.Refresh) }
            ui.awaitUntil(5_000) { model.uiState.value.busy }
            ui.onNodeWithText("Удалить тренировку?").assertIsDisplayed()
            ui.onNodeWithText("Удалить").assertIsNotEnabled()
            ui.onNodeWithText("Удалить").performClick()
            assertEquals("1", FoodStore(app).use { it.row("SELECT count(*) FROM workout WHERE id=$id")[0] })
        } finally {
            gated.gate = null
            gate.countDown()
        }
        awaitLoaded(model)
        ui.onNodeWithText("Удалить тренировку?").assertIsDisplayed()
        ui.awaitUntil(5_000) { runCatching { ui.onNodeWithText("Удалить").assertIsEnabled() }.isSuccess }
        ui.onNodeWithText("Удалить").performClick()
        await("Записать тренировку")
        assertEquals("0", FoodStore(app).use { it.row("SELECT count(*) FROM workout")[0] })
    }

    @Test fun setStateIsClearedWhenEditEndsSoSaveAndDeleteCannotReachCompletedWorkout() {
        val id = completed()
        val setId = FoodStore(app).use { it.row("SELECT id FROM lifting")[0]!!.toLong() }
        val snapshot = { FoodStore(app).use { it.row("SELECT title,reps,(SELECT count(*) FROM lifting) FROM workout, lifting") } }
        val before = snapshot()
        val model = WorkoutViewModel(app, clock)
        awaitLoaded(model)
        model.event(WorkoutEvent.Open(id))
        model.event(WorkoutEvent.StartEdit(id))
        model.event(WorkoutEvent.Edit(id, SetDraft(id = setId, group = "bench", exercise = "Жим штанги лёжа", weight = "60", reps = "1")))
        assertNotNull(model.uiState.value.draft)
        model.event(WorkoutEvent.StopEdit)
        assertNull(model.uiState.value.session)
        assertNull(model.uiState.value.draft)
        model.event(WorkoutEvent.Save())
        model.event(WorkoutEvent.DeleteSet)
        assertFalse(model.uiState.value.busy)
        assertEquals(before, snapshot())
    }

    @Test fun leavingWorkoutResetsEdit() {
        val id = completed()
        val model = WorkoutViewModel(app, clock)
        awaitLoaded(model)
        model.event(WorkoutEvent.Open(id))
        model.event(WorkoutEvent.StartEdit(id))
        assertEquals(id, model.uiState.value.editing)
        model.event(WorkoutEvent.BackToList)
        awaitLoaded(model)
        assertNull(model.uiState.value.editing)
        model.event(WorkoutEvent.Open(id))
        assertNull(model.uiState.value.editing)
    }

    @Test fun tabTappedAgainReturnsFromWorkoutMusclesAndHistoryToFeed() {
        completed()
        OnboardingSettings(app).skipped = true
        ui.setContent { MaterialTheme { DiaryApp(ThemeMode.DARK) {} } }
        val tab = hasText("Тренировки") and isSelectable()
        ui.onNode(tab).performClick()
        openFromFeed("Готовая")
        await("Редактировать")
        ui.onNode(tab).performClick()
        await("Записать тренировку")

        openFromFeed("Готовая")
        await("Редактировать")
        reveal("Жим штанги лёжа")
        ui.onNodeWithContentDescription("Мышцы и техника").performClick()
        ui.onNode(tab).performClick()
        await("Записать тренировку")

        ui.onNodeWithText("История").performClick()
        await("Упражнения").performClick()
        await("Готовая").performClick()
        await("Редактировать")
        ui.onNode(tab).performClick()
        await("Записать тренировку")
        openFromFeed("Готовая")
        await("Редактировать")
        ui.onNodeWithContentDescription("Назад").performClick()
        await("Записать тренировку")
        ui.onNodeWithText("Упражнения").assertDoesNotExist()
    }

    @Test fun tabTappedDuringLoadReturnsToFeed() {
        completed()
        val gated = GatedApplication(app)
        val model = WorkoutViewModel(gated, clock)
        awaitLoaded(model)
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
        val tab = hasText("Тренировки") and isSelectable()
        ui.onNode(tab).performClick()
        awaitLoaded(model)
        openFromFeed("Готовая")
        await("Редактировать")
        awaitLoaded(model)
        val gate = CountDownLatch(1)
        gated.gate = gate
        try {
            ui.runOnIdle { model.event(WorkoutEvent.Refresh) }
            assertTrue(model.uiState.value.busy)
            ui.onNode(tab).performClick()
            assertNull(model.uiState.value.opened)
        } finally {
            gated.gate = null
            gate.countDown()
        }
        awaitLoaded(model)
        await("Записать тренировку")
        assertNull(model.uiState.value.opened)
    }

    @Test fun setFormIsDroppedByLeavingToFeed() {
        val id = completed()
        val model = WorkoutViewModel(app, clock)
        awaitLoaded(model)
        model.event(WorkoutEvent.Open(id))
        model.event(WorkoutEvent.StartEdit(id))
        model.event(WorkoutEvent.Edit(id, SetDraft(exercise = "Жим", weight = "1", reps = "2")))
        assertNotNull(model.uiState.value.draft)
        model.event(WorkoutEvent.BackToList)
        assertNull(model.uiState.value.draft)
        assertNull(model.uiState.value.session)
        assertNull(model.uiState.value.opened)
    }

    private fun musclesRights(scale: Float): List<Float> {
        val names = listOf("Жим штанги лёжа", exerciseCatalog.maxBy { it.name.length }.name, "Присед",
            "Очень длинное название собственного упражнения для проверки переноса строки")
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                MaterialTheme {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        names.forEach { name ->
                            ExerciseCard(listOf(SetDraft(1, name, name, "60", "8")), emptyMap(), {}, edit = {})
                        }
                    }
                }
            }
        }
        val buttons = ui.onAllNodes(hasContentDescription("Мышцы упражнения") or hasContentDescription("Мышцы и техника"))
        assertEquals(names.size, buttons.fetchSemanticsNodes().size)
        return buttons.fetchSemanticsNodes().map { it.positionInRoot.x + it.size.width }
    }

    @Test fun muscleIconSitsAtSameRightEdgeAtNormalFont() {
        val rights = musclesRights(1f)
        assertTrue("Правые края $rights", rights.max() - rights.min() <= 1f)
    }

    @Test fun muscleIconSitsAtSameRightEdgeAtLargeFont() {
        val rights = musclesRights(2f)
        assertTrue("Правые края $rights", rights.max() - rights.min() <= 1f)
    }

    private class GatedApplication(base: Context) : Application() {
        @Volatile var gate: CountDownLatch? = null

        init {
            attachBaseContext(base)
        }

        override fun getDatabasePath(name: String): File {
            gate?.await()
            return super.getDatabasePath(name)
        }
    }
}
