package app.calorietraining

import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class GoalAnyDayAndStopwatchTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2026, 11, 10)

    private fun setGoalOnOpenDay() {
        val model = FoodViewModel(app)
        fun await() {
            ui.awaitUntil(10_000) {
                !model.uiState.value.busy
            }
            assertNull(model.uiState.value.error)
        }
        await()
        ui.runOnIdle { model.onEvent(FoodEvent.Day(day)) }
        await()
        assertEquals(day, model.uiState.value.day)
        ui.runOnIdle { model.onEvent(FoodEvent.Goal(2000, MacroGoals())) }
        await()
    }

    @Test fun startDateIsOpenDay() {
        val today = LocalDate.of(2026, 11, 22)
        SystemClock.setCurrentTimeMillis(today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        setGoalOnOpenDay()
        FoodStore(app).use {
            assertEquals(DailyGoals(2000), it.goalsFor(day))
            assertEquals(DailyGoals(), it.goalsFor(day.minusDays(1)))
            assertEquals(DailyGoals(2000), it.goalsFor(today))
        }
    }

    @Test fun goalAppliesUpToNextChange() {
        val first = day.withDayOfMonth(1)
        val next = day.withDayOfMonth(20)
        FoodStore(app).use {
            it.setGoals(first, DailyGoals(2200))
            it.setGoals(next, DailyGoals(1800))
        }
        setGoalOnOpenDay()
        FoodStore(app).use { store ->
            for (date in first.datesUntil(day).toList()) assertEquals(DailyGoals(2200), store.goalsFor(date))
            for (date in day.datesUntil(next).toList()) assertEquals(DailyGoals(2000), store.goalsFor(date))
            assertEquals(DailyGoals(1800), store.goalsFor(next))
            assertEquals(DailyGoals(1800), store.goalsFor(next.plusDays(1)))
        }
    }

    @Test fun pastDayShowsEffectiveDateInGoalDialog() {
        val yesterday = LocalDate.now().minusDays(1)
        val screen = ui.setScreenshotContent(ThemeMode.DARK) {
            FoodContent(FoodUiState(day = yesterday, loaded = true, goals = DailyGoals(2200)), {})
        }
        ui.onNodeWithText("Изменить цель").assertIsDisplayed().performClick()
        ui.onNodeWithText("Действует с ${yesterday.caption()} до следующего изменения").assertIsDisplayed()
        screen.captureScreen("goal-dialog-past-dark.png", includeDialog = true)
    }

    @Test fun idleTimerStartsStopwatchAtCurrentElapsedTime() {
        var change: Triple<Long, Long, Long>? = null
        var clickTime = 0L
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) {
                RestTimer(0, 0, 0) { end, total, started ->
                    clickTime = SystemClock.elapsedRealtime()
                    change = Triple(end, total, started)
                }
            }
        }
        val buttons = listOf("Засечь", "1 мин", "2 мин", "3 мин").map { ui.onNodeWithText(it) }
        val row = buttons.first().fetchSemanticsNode().boundsInRoot.top
        for (button in buttons) {
            button.assertIsDisplayed()
            assertEquals(row, button.fetchSemanticsNode().boundsInRoot.top, 0f)
        }
        buttons.first().performClick()
        assertTrue(clickTime > 0L)
        assertEquals(Triple(0L, 0L, clickTime), change)
    }

    @Test fun runningStopwatchShowsElapsedTimeAndStops() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(60))
        val started = SystemClock.elapsedRealtime() - 42_000
        var change: Triple<Long, Long, Long>? = null
        val screen = ui.setScreenshotContent(ThemeMode.DARK) {
            RestTimer(0, 0, started) { end, total, start -> change = Triple(end, total, start) }
        }
        ui.onNodeWithText("ОТДЫХ ИДЁТ").assertIsDisplayed()
        ui.onNodeWithText("0:42").assertIsDisplayed()
        ui.onNodeWithTag("rest-ring").assertIsDisplayed()
        ui.onNodeWithText("+30 с").assertDoesNotExist()
        screen.captureScreen("workout-stopwatch-dark.png")
        ui.onNodeWithText("Стоп").assertIsDisplayed().performClick()
        assertEquals(Triple(0L, 0L, 0L), change)
    }

    @Test fun formatsMinutesAndHours() {
        assertEquals("0:59", formatRestElapsed(59))
        assertEquals("1:02:05", formatRestElapsed(3725))
    }
}
