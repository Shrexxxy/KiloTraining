package app.calorietraining

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w393dp-h850dp")
class WeightMidnightTest {
    @get:Rule(order = 0) internal val skipOnboarding = SkipOnboardingRule()
    @get:Rule(order = 1) val ui = createComposeRule()

    private class MutableClock(var instant: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone() = zone
        override fun withZone(zone: ZoneId) = MutableClock(instant, zone)
        override fun instant() = instant
    }

    private fun stored(day: LocalDate) = FoodStore(RuntimeEnvironment.getApplication()).use { store ->
        store.readableDatabase.rawQuery("SELECT weight,fat FROM measurement WHERE day=?", arrayOf(day.toString())).use {
            if (it.moveToFirst()) it.getString(0) to it.getString(1) else null
        }
    }

    @Test fun weightGoesToTheDayOfTheSaveTap() {
        val zone = ZoneId.of("Europe/Moscow")
        val evening = LocalDate.of(2026, 6, 15)
        val clock = MutableClock(evening.atTime(23, 59).atZone(zone).toInstant(), zone)
        FoodStore(RuntimeEnvironment.getApplication()).use { it.saveMeasurement(Measurement(evening, fat = "18")) }
        ui.setContent {
            DiaryTheme(ThemeMode.DARK) {
                HomeScreen(RuntimeEnvironment.getApplication(), WorkoutState(clock = clock), {}, {}, {})
            }
        }
        ui.awaitUntil(10_000) { ui.onAllNodesWithText("Вес ещё не записан").fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodesWithText("Записать вес").onFirst().performScrollTo().performClick()
        ui.awaitUntil(10_000) { ui.onAllNodesWithTag("weight-readout").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("weight-readout").performTextReplacement("80")
        clock.instant = evening.plusDays(1).atTime(0, 1).atZone(zone).toInstant()
        ui.onNode(hasText("Сохранить") and hasClickAction()).performClick()
        ui.awaitUntil(10_000) { stored(evening.plusDays(1)) != null }
        assertEquals("80" to "", stored(evening.plusDays(1)))
        assertEquals("" to "18", stored(evening))
        assertNull(stored(evening.plusDays(2)))
    }
}
